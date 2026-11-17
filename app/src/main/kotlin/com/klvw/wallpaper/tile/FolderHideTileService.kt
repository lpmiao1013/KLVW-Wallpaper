package com.klvw.wallpaper.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.klvw.wallpaper.R
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Toggles global folder-hide mode: privacy-marked folders disappear from UI lists.
 */
@AndroidEntryPoint
class FolderHideTileService : TileService() {

    @Inject lateinit var prefs: SettingsPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val next = !prefs.folderHideMode.first()
            prefs.setFolderHideMode(next)
            // Turning hide mode on should not leave "show hidden" stuck on.
            if (next) prefs.setShowHiddenFolders(false)
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val on = prefs.folderHideMode.first()
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_folder_hide)
        tile.label = getString(R.string.folder_hide_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "隐藏中" else "未隐藏"
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
