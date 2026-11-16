package com.klvw.wallpaper.util

import android.content.Context
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
}
