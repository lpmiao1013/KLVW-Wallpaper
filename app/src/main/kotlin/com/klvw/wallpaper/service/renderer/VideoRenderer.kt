package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.klvw.wallpaper.data.model.WallpaperScaleMode
import com.klvw.wallpaper.util.DisplayUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Video live-wallpaper renderer.
 *
 * ExoPlayer → SurfaceTexture (OES) → GLES onto the wallpaper [SurfaceHolder],
 * so FIT / FILL / STRETCH and optional 90° mismatch rotate actually apply.
 * (Direct SurfaceHolder playback ignores ExoPlayer videoScalingMode.)
 */
class VideoRenderer(
    private val context: Context,
    private val displayUtils: DisplayUtils
) {
    private var player: ExoPlayer? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var intentionalRelease = false

    private var glThread: HandlerThread? = null
    private var glHandler: Handler? = null

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglCtx: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurf: EGLSurface = EGL14.EGL_NO_SURFACE
    private var cfg: EGLConfig? = null

    private var prog = 0
    private var posAttr = 0
    private var uvAttr = 0
    private var mvpUniform = 0
    private var stUniform = 0
    private var samplerUniform = 0
    private val tex = IntArray(1)

    private var surfaceTexture: SurfaceTexture? = null
    private var decoderSurface: Surface? = null
    private val frameAvailable = AtomicBoolean(false)

    private var scaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL
    private var rotateMismatchEnabled = false
    private var rotateMismatchCw = true
    private var videoW = 0
    private var videoH = 0
    private var screenW = 0
    private var screenH = 0

    private val stMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!intentionalRelease) {
                try { player?.prepare() } catch (_: Exception) {}
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE && !intentionalRelease) {
                try { player?.prepare() } catch (_: Exception) {}
            }
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoW = videoSize.width
                videoH = videoSize.height
                glHandler?.post { requestDraw() }
            }
        }
    }

    fun load(
        uri: Uri,
        holder: SurfaceHolder,
        scaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL,
        rotateMismatchEnabled: Boolean = false,
        rotateMismatchCw: Boolean = true
    ) {
        release()
        intentionalRelease = false
        surfaceHolder = holder
        this.scaleMode = scaleMode
        this.rotateMismatchEnabled = rotateMismatchEnabled
        this.rotateMismatchCw = rotateMismatchCw

        val metrics = displayUtils.getScreenMetrics()
        screenW = metrics.widthPx
        screenH = metrics.heightPx
        val probed = probeDisplaySize(uri)
        videoW = probed.first
        videoH = probed.second

        val thread = HandlerThread("KLVW-VideoGL").also { it.start() }
        glThread = thread
        val handler = Handler(thread.looper)
        glHandler = handler

        handler.post {
            try {
                setupEgl(holder)
                setupGl()
                val st = SurfaceTexture(tex[0]).also { surfaceTexture = it }
                if (videoW > 0 && videoH > 0) st.setDefaultBufferSize(videoW, videoH)
                st.setOnFrameAvailableListener({
                    frameAvailable.set(true)
                    handler.post { drainAndDraw() }
                }, handler)
                decoderSurface = Surface(st)

                val exo = ExoPlayer.Builder(context).build()
                exo.setMediaItem(MediaItem.fromUri(uri))
                exo.repeatMode = Player.REPEAT_MODE_ONE
                exo.volume = 0f
                exo.setVideoSurface(decoderSurface)
                exo.addListener(playerListener)
                exo.prepare()
                exo.playWhenReady = true
                player = exo
            } catch (_: Throwable) {
                releaseOnGlThread()
            }
        }
    }

    fun detachSurface() {
        // Wallpaper surface going away — stop drawing; player may still hold decoder surface.
        glHandler?.post {
            if (eglSurf != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, eglSurf)
                eglSurf = EGL14.EGL_NO_SURFACE
            }
        }
    }

    fun pause() { try { player?.pause() } catch (_: Exception) {} }
    fun resume() { try { player?.play() } catch (_: Exception) {} }

    fun release() {
        intentionalRelease = true
        val handler = glHandler
        val thread = glThread
        if (handler != null && thread != null && thread.isAlive) {
            val done = Object()
            handler.post {
                try { player?.removeListener(playerListener) } catch (_: Exception) {}
                try { player?.stop() } catch (_: Exception) {}
                try { player?.release() } catch (_: Exception) {}
                player = null
                releaseOnGlThread()
                synchronized(done) { done.notifyAll() }
            }
            try {
                synchronized(done) { done.wait(1500L) }
            } catch (_: InterruptedException) {}
            thread.quitSafely()
        } else {
            try { player?.removeListener(playerListener) } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
            player = null
        }
        glHandler = null
        glThread = null
        surfaceHolder = null
    }

    private fun setupEgl(holder: SurfaceHolder) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        EGL14.eglInitialize(display, null, 0, null, 0)
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, n, 0)
        cfg = cfgs[0]!!
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglCtx = EGL14.eglCreateContext(display, cfg!!, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        eglSurf = EGL14.eglCreateWindowSurface(display, cfg!!, holder.surface, intArrayOf(EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)
    }

    private fun setupGl() {
        val vs = """
            attribute vec2 aP;
            attribute vec2 aT;
            uniform mat4 uMVP;
            uniform mat4 uST;
            varying vec2 vT;
            void main() {
              gl_Position = uMVP * vec4(aP, 0.0, 1.0);
              vT = (uST * vec4(aT, 0.0, 1.0)).xy;
            }
        """.trimIndent()
        val fs = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uS;
            varying vec2 vT;
            void main() {
              gl_FragColor = texture2D(uS, vT);
            }
        """.trimIndent()
        prog = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vs), compileShader(GLES20.GL_FRAGMENT_SHADER, fs))
        posAttr = GLES20.glGetAttribLocation(prog, "aP")
        uvAttr = GLES20.glGetAttribLocation(prog, "aT")
        mvpUniform = GLES20.glGetUniformLocation(prog, "uMVP")
        stUniform = GLES20.glGetUniformLocation(prog, "uST")
        samplerUniform = GLES20.glGetUniformLocation(prog, "uS")

        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun drainAndDraw() {
        if (!frameAvailable.compareAndSet(true, false)) return
        requestDraw()
    }

    private fun requestDraw() {
        val st = surfaceTexture ?: return
        if (display == EGL14.EGL_NO_DISPLAY || eglSurf == EGL14.EGL_NO_SURFACE) return
        try {
            EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
            drawFrame()
            EGL14.eglSwapBuffers(display, eglSurf)
        } catch (_: Throwable) {
        }
    }

    private fun drawFrame() {
        val sw = screenW.coerceAtLeast(1)
        val sh = screenH.coerceAtLeast(1)
        val vw = videoW.coerceAtLeast(1)
        val vh = videoH.coerceAtLeast(1)

        val rotate = WallpaperLayout.shouldAutoRotate(vw, vh, sw, sh, rotateMismatchEnabled)
        // Match image path: layout using post-rotate size, rotate samples via UVs.
        val (cw, ch) = WallpaperLayout.effectiveSize(vw, vh, rotate)
        val dest = WallpaperLayout.destinationRect(cw, ch, sw, sh, scaleMode)

        Matrix.setIdentityM(mvpMatrix, 0)
        // Pixel rect → NDC. Android Y grows down; GL Y grows up.
        val sx = 2f / sw
        val sy = -2f / sh
        Matrix.translateM(mvpMatrix, 0, -1f, 1f, 0f)
        Matrix.scaleM(mvpMatrix, 0, sx, sy, 1f)
        Matrix.translateM(mvpMatrix, 0, dest.left, dest.top, 0f)
        Matrix.scaleM(mvpMatrix, 0, dest.width(), dest.height(), 1f)

        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glUniform1i(samplerUniform, 0)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stUniform, 1, false, stMatrix, 0)

        // Unit quad [0,1]² → dest. UVs optionally 90°-rotated to match image auto-rotate.
        val uv = when {
            rotate && rotateMismatchCw -> floatArrayOf(
                // (u,v) → (v, 1-u)  90° CW
                0f, 1f,  0f, 0f,  1f, 1f,  1f, 0f
            )
            rotate && !rotateMismatchCw -> floatArrayOf(
                // (u,v) → (1-v, u)  90° CCW
                1f, 0f,  1f, 1f,  0f, 0f,  0f, 1f
            )
            else -> floatArrayOf(
                0f, 0f,  1f, 0f,  0f, 1f,  1f, 1f
            )
        }
        val verts = floatArrayOf(
            0f, 0f, uv[0], uv[1],
            1f, 0f, uv[2], uv[3],
            0f, 1f, uv[4], uv[5],
            1f, 1f, uv[6], uv[7]
        )
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts).position(0)
        val stride = 4 * 4
        buf.position(0)
        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, stride, buf)
        buf.position(2)
        GLES20.glEnableVertexAttribArray(uvAttr)
        GLES20.glVertexAttribPointer(uvAttr, 2, GLES20.GL_FLOAT, false, stride, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posAttr)
        GLES20.glDisableVertexAttribArray(uvAttr)
    }

    private fun releaseOnGlThread() {
        try { decoderSurface?.release() } catch (_: Exception) {}
        decoderSurface = null
        try { surfaceTexture?.release() } catch (_: Exception) {}
        surfaceTexture = null
        if (tex[0] != 0) {
            try { GLES20.glDeleteTextures(1, tex, 0) } catch (_: Exception) {}
            tex[0] = 0
        }
        if (prog != 0) {
            try { GLES20.glDeleteProgram(prog) } catch (_: Exception) {}
            prog = 0
        }
        if (eglSurf != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurf)
            eglSurf = EGL14.EGL_NO_SURFACE
        }
        if (eglCtx != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, eglCtx)
            eglCtx = EGL14.EGL_NO_CONTEXT
        }
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(display)
            display = EGL14.EGL_NO_DISPLAY
        }
        cfg = null
        frameAvailable.set(false)
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

    /** Display-oriented size (metadata rotation already applied). */
    private fun probeDisplaySize(uri: Uri): Pair<Int, Int> {
        return try {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) h to w else w to h
            }
        } catch (_: Exception) {
            0 to 0
        }
    }
}
