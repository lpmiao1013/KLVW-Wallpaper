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

    /**
     * Destination rect in screen pixels for [contentW]x[contentH] under [mode].
     * Content size should already reflect any logical 90° auto-rotate.
     */
    fun destinationRect(
        contentW: Int,
        contentH: Int,
        screenW: Int,
        screenH: Int,
        mode: WallpaperScaleMode
    ): RectF {
        val dw = screenW.toFloat()
        val dh = screenH.toFloat()
        if (contentW <= 0 || contentH <= 0 || screenW <= 0 || screenH <= 0) {
            return RectF(0f, 0f, dw, dh)
        }
        val sw = contentW.toFloat()
        val sh = contentH.toFloat()
        return when (mode) {
            WallpaperScaleMode.STRETCH -> RectF(0f, 0f, dw, dh)
            WallpaperScaleMode.FILL -> {
                val scale = max(dw / sw, dh / sh)
                val tw = sw * scale
                val th = sh * scale
                val left = (dw - tw) / 2f
                val top = (dh - th) / 2f
                RectF(left, top, left + tw, top + th)
            }
            WallpaperScaleMode.FIT -> {
                val scale = min(dw / sw, dh / sh)
                val tw = sw * scale
                val th = sh * scale
                val left = (dw - tw) / 2f
                val top = (dh - th) / 2f
                RectF(left, top, left + tw, top + th)
            }
        }
    }

    /**
     * Video GL quad matching WallpaperSwitcher: interleaved NDC x,y + UV,
     * triangle-strip BL→BR→TL→TR, with UV (0,0) at bottom-left (SurfaceTexture).
     * Pass already-swapped [vidW]/[vidH] when an extra 90° UV turn will be applied.
     */
    fun computeVideoQuad(
        vidW: Float,
        vidH: Float,
        screenW: Float,
        screenH: Float,
        mode: WallpaperScaleMode
    ): FloatArray {
        if (vidW <= 0f || vidH <= 0f || screenW <= 0f || screenH <= 0f) {
            return floatArrayOf(
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f
            )
        }
        val va = vidW / vidH
        val sa = screenW / screenH
        val (dw, dh) = when (mode) {
            WallpaperScaleMode.FIT ->
                if (va > sa) 1f to (sa / va) else (va / sa) to 1f
            WallpaperScaleMode.FILL ->
                if (va > sa) (va / sa) to 1f else 1f to (sa / va)
            WallpaperScaleMode.STRETCH -> 1f to 1f
        }
        return floatArrayOf(
            -dw, -dh, 0f, 0f,
            dw, -dh, 1f, 0f,
            -dw, dh, 0f, 1f,
            dw, dh, 1f, 1f
        )
    }

    fun nearlySquare(w: Int, h: Int, epsilon: Float = 0.08f): Boolean {
        if (w <= 0 || h <= 0) return true
        return abs(w.toFloat() / h - 1f) < epsilon
    }
}
