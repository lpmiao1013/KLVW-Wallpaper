package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import android.view.SurfaceHolder
import com.klvw.wallpaper.data.model.WallpaperScaleMode
import com.klvw.wallpaper.util.DisplayUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Renders static images via OpenGL ES 2.0 for GPU-accelerated display on the wallpaper surface. */
class EGLImageRenderer(
    private val context: Context,
    private val displayUtils: DisplayUtils
) {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglCtx: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurf: EGLSurface = EGL14.EGL_NO_SURFACE
    private var cfg: EGLConfig? = null
    private var prog = 0
    private val tex = IntArray(1)
    private var posAttr = 0
    private var uvAttr = 0
    private var hasTexture = false
    private var lastSurface: Surface? = null

    suspend fun load(
        uri: Uri,
        holder: SurfaceHolder,
        scaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL,
        rotateMismatchEnabled: Boolean = false,
        rotateMismatchCw: Boolean = true
    ) {
        val metrics = displayUtils.getScreenMetrics()
        val bmp = withContext(Dispatchers.IO) {
            val decoded = decodeBitmap(uri, metrics.widthPx, metrics.heightPx) ?: return@withContext null
            val rotate = if (WallpaperLayout.shouldAutoRotate(
                    decoded.width, decoded.height, metrics.widthPx, metrics.heightPx, rotateMismatchEnabled
                )
            ) rotateMismatchCw else null
            WallpaperLayout.layoutToScreen(
                decoded, metrics.widthPx, metrics.heightPx, scaleMode, rotate
            ).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        coroutineContext.ensureActive()
        if (bmp == null) return

        // After video GL / SurfaceHolder playback, the wallpaper Surface may stay busy briefly.
        var drawn = false
        var lastError: Throwable? = null
        for (attempt in 0 until 4) {
            coroutineContext.ensureActive()
            try {
                if (!setup(holder)) {
                    throw IllegalStateException("EGL setup/bind failed")
                }
                uploadAndDraw(bmp)
                drawn = true
                break
            } catch (e: Throwable) {
                lastError = e
                Log.w(TAG, "image EGL load attempt ${attempt + 1}/4 failed", e)
                // Drop a half-bound context so the next try starts clean.
                try { release() } catch (_: Throwable) {}
                delay(50L * (attempt + 1))
            }
        }
        bmp.recycle()
        if (!drawn) {
            throw lastError ?: IllegalStateException("image EGL load failed")
        }
    }

    fun render(holder: SurfaceHolder) {
        if (!hasTexture) return
        if (!bindSurface(holder)) return
        draw()
    }

    /** @return false if the wallpaper surface could not be bound for GLES. */
    private fun setup(holder: SurfaceHolder): Boolean {
        if (display == EGL14.EGL_NO_DISPLAY) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return false
            if (!EGL14.eglInitialize(display, null, 0, null, 0)) return false
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val cfgs = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, n, 0) || n[0] <= 0) {
                return false
            }
            cfg = cfgs[0]!!
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglCtx = EGL14.eglCreateContext(display, cfg!!, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            if (eglCtx == EGL14.EGL_NO_CONTEXT) return false
        }
        if (!bindSurface(holder)) return false
        if (prog == 0) initShaders()
        return prog != 0
    }

    private fun bindSurface(holder: SurfaceHolder): Boolean {
        if (display == EGL14.EGL_NO_DISPLAY || eglCtx == EGL14.EGL_NO_CONTEXT || cfg == null) {
            return false
        }
        val s = holder.surface
        if (s == null || !s.isValid) return false
        if (s == lastSurface && eglSurf != EGL14.EGL_NO_SURFACE) {
            return EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)
        }
        if (eglSurf != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurf)
            eglSurf = EGL14.EGL_NO_SURFACE
        }
        eglSurf = EGL14.eglCreateWindowSurface(display, cfg!!, s, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglSurf == null || eglSurf == EGL14.EGL_NO_SURFACE) {
            eglSurf = EGL14.EGL_NO_SURFACE
            lastSurface = null
            return false
        }
        if (!EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)) {
            EGL14.eglDestroySurface(display, eglSurf)
            eglSurf = EGL14.EGL_NO_SURFACE
            lastSurface = null
            return false
        }
        lastSurface = s
        return true
    }

    private fun initShaders() {
        val vs = "attribute vec2 aP;attribute vec2 aT;varying vec2 vT;void main(){gl_Position=vec4(aP,0.,1.);vT=aT;}"
        val fs = "precision mediump float;uniform sampler2D uS;varying vec2 vT;void main(){gl_FragColor=texture2D(uS,vT);}"
        prog = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vs), compileShader(GLES20.GL_FRAGMENT_SHADER, fs))
        posAttr = GLES20.glGetAttribLocation(prog, "aP")
        uvAttr  = GLES20.glGetAttribLocation(prog, "aT")
        GLES20.glGenTextures(1, tex, 0)
    }

    private fun uploadAndDraw(bmp: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        hasTexture = true
        draw()
    }

    private fun draw() {
        // Screen-sized bitmap already includes letterboxing; always draw full-screen.
        val v = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
        val buf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(v)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])

        buf.position(0)
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, 16, buf)

        buf.position(2)
        GLES20.glEnableVertexAttribArray(uvAttr)
        GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, 16, buf)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(display, eglSurf)
    }

    fun release() {
        if (display == EGL14.EGL_NO_DISPLAY) {
            hasTexture = false
            return
        }
        if (eglSurf != EGL14.EGL_NO_SURFACE && eglCtx != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)
            if (prog != 0) { GLES20.glDeleteProgram(prog); prog = 0 }
            if (tex[0] != 0) { GLES20.glDeleteTextures(1, tex, 0); tex[0] = 0 }
        }
        hasTexture = false
        lastSurface = null
        GLES20.glFinish()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (eglSurf != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(display, eglSurf); eglSurf = EGL14.EGL_NO_SURFACE }
        if (eglCtx != EGL14.EGL_NO_CONTEXT) { EGL14.eglDestroyContext(display, eglCtx); eglCtx = EGL14.EGL_NO_CONTEXT }
        EGL14.eglTerminate(display)
        display = EGL14.EGL_NO_DISPLAY
        cfg = null
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun linkProgram(vs: Int, fs: Int): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    private fun decodeBitmap(uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            // Decode a bit larger than screen so FIT/FILL still look sharp after rotate.
            val decodeTarget = max(targetW, targetH)
            opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, decodeTarget, decodeTarget)
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { null }
    }

    private fun calcSampleSize(sw: Int, sh: Int, dw: Int, dh: Int): Int {
        var s = 1; var w = sw / 2; var h = sh / 2
        while (w >= dw && h >= dh) { s *= 2; w /= 2; h /= 2 }
        return s
    }

    companion object {
        private const val TAG = "EGLImageRenderer"
    }
}
