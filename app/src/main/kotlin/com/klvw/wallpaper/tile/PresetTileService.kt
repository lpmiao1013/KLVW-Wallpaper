package com.klvw.wallpaper.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.klvw.wallpaper.R
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.preset.PresetManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PresetTileService : TileService() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var presetManager: PresetManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val err = presetManager.toggle()
            withContext(Dispatchers.Main) {
                if (err != null) {
                    Toast.makeText(applicationContext, err, Toast.LENGTH_SHORT).show()
                }
            }
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val on = prefs.presetEnabled.first()
        val tile = qsTile ?: return
        // ACTIVE = system accent / white; INACTIVE = muted / no accent.
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_preset)
        tile.label = getString(R.string.preset_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "已启用" else "未启用"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
