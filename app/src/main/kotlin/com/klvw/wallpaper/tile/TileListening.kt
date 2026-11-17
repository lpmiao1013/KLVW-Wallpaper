package com.klvw.wallpaper.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/** Ask the system to refresh a QS tile after prefs change outside the tile process. */
fun requestTileListening(context: Context, service: Class<out TileService>) {
    try {
        TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context.applicationContext, service)
        )
    } catch (_: Exception) {
        // Older OEMs / missing tile — ignore.
    }
}
