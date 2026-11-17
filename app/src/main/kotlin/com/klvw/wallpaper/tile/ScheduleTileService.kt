package com.klvw.wallpaper.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.klvw.wallpaper.R
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.schedule.ScheduleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Toggles daily schedule on/off. Group/rules are configured only in Settings.
 */
@AndroidEntryPoint
class ScheduleTileService : TileService() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var scheduleManager: ScheduleManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (!scheduleManager.canUseScheduleTile()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "请先在设置中选择并保存完整时段分组",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                refreshTile()
                return@launch
            }
            val on = prefs.scheduleEnabled.first()
            val err = scheduleManager.setEnabled(!on)
            if (err != null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, err, Toast.LENGTH_SHORT).show()
                }
            }
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val ready = scheduleManager.canUseScheduleTile()
        val on = prefs.scheduleEnabled.first()
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_schedule)
        tile.label = getString(R.string.schedule_tile_label)
        when {
            !ready -> {
                tile.state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "未配置"
                }
            }
            on -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "已启用"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "已关闭"
                }
            }
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
