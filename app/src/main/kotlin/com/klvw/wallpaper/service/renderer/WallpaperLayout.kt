package com.klvw.wallpaper.service.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.klvw.wallpaper.data.model.WallpaperScaleMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared layout helpers for image wallpapers (and for deciding video auto-rotate).
 */
object WallpaperLayout {

    /**
     * When source orientation mismatches the screen (portrait vs landscape),
     * returns whether a 90° turn would better fill the screen for FILL/STRETCH.
     * FIT also benefits from turning so more of the content is visible.
     */
    fun shouldAutoRotate(
        srcW: Int,
        srcH: Int,
        screenW: Int,
        screenH: Int,
        enabled: Boolean
    ): Boolean {
        if (!enabled || srcW <= 0 || srcH <= 0 || screenW <= 0 || screenH <= 0) return false
        val srcLandscape = srcW > srcH
        val screenLandscape = screenW > screenH
        return srcLandscape != screenLandscape
    }

    /**
     * Layout [src] onto a screen-sized bitmap according to [mode].
     * Optional 90° rotation when [rotateCw] is non-null.
     */
    fun layoutToScreen(
        src: Bitmap,
        screenW: Int,
        screenH: Int,
        mode: WallpaperScaleMode,
        rotateCw: Boolean?
    ): Bitmap {
        val working = if (rotateCw != null) {
            rotate90(src, clockwise = rotateCw).also { if (it !== src) { /* keep src ownership with caller */ } }
        } else {
            src
        }
        // If we created a rotated copy, recycle it after composing; never recycle [src] here.
        val recycledRotated = working !== src

        val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        val sw = working.width.toFloat()
        val sh = working.height.toFloat()
        val dw = screenW.toFloat()
        val dh = screenH.toFloat()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        when (mode) {
            WallpaperScaleMode.STRETCH -> {
                canvas.drawBitmap(working, null, RectF(0f, 0f, dw, dh), paint)
            }
            WallpaperScaleMode.FILL -> {
                val scale = max(dw / sw, dh / sh)
                val tw = sw * scale
                val th = sh * scale
                val left = (dw - tw) / 2f
                val top = (dh - th) / 2f
                canvas.drawBitmap(working, null, RectF(left, top, left + tw, top + th), paint)
            }
            WallpaperScaleMode.FIT -> {
                val scale = min(dw / sw, dh / sh)
                val tw = sw * scale
                val th = sh * scale
                val left = (dw - tw) / 2f
                val top = (dh - th) / 2f
                canvas.drawBitmap(working, null, RectF(left, top, left + tw, top + th), paint)
            }
        }

        if (recycledRotated) working.recycle()
        return out
    }

    fun rotate90(src: Bitmap, clockwise: Boolean): Bitmap {
        val m = Matrix().apply {
            postRotate(if (clockwise) 90f else -90f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Effective display size after optional 90° turn (for video decisions). */
    fun effectiveSize(w: Int, h: Int, rotate: Boolean): Pair<Int, Int> =
        if (rotate) h to w else w to h

    fun nearlySquare(w: Int, h: Int, epsilon: Float = 0.08f): Boolean {
        if (w <= 0 || h <= 0) return true
        return abs(w.toFloat() / h - 1f) < epsilon
    }
}
