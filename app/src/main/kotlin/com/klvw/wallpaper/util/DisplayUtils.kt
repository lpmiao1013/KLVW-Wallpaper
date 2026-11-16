package com.klvw.wallpaper.util

import android.content.Context
import android.view.SurfaceHolder
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class ScreenMetrics(val widthPx: Int, val heightPx: Int, val densityDpi: Int)

@Singleton
class DisplayUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getScreenMetrics(): ScreenMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        val dm = context.resources.displayMetrics
        return ScreenMetrics(bounds.width(), bounds.height(), dm.densityDpi)
    }

    /**
     * Request a full-display wallpaper buffer.
     * Some OEMs otherwise hand the engine a much smaller Surface (e.g. ~720×560),
     * which makes 1080p video look soft when the system upscales it.
     * Same approach as SystemUI [ImageWallpaper].
     */
    fun requestFullDisplaySurface(holder: SurfaceHolder) {
        val m = getScreenMetrics()
        // CTS / some devices reject tiny surfaces; clamp like ImageWallpaper.
        val w = max(64, m.widthPx)
        val h = max(64, m.heightPx)
        if (w > 0 && h > 0) holder.setFixedSize(w, h)
    }
}
