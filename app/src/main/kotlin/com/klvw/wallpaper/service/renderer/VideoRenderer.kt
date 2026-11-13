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
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.media3.common.C
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Video live-wallpaper renderer.
 *
 * Preferred path: ExoPlayer → SurfaceTexture (OES) → GLES onto the wallpaper
 * SurfaceHolder, so FIT / FILL / STRETCH and optional 90° rotate apply.
 *
 * Fallback: direct SurfaceHolder playback (scale mode limited by ExoPlayer) if
 * EGL/GL setup fails — keeps double-tap / timer / unlock wallpaper changes alive.
 */
class VideoRenderer(
    private val context: Context,
    private val displayUtils: DisplayUtils
) {
    private val generation = AtomicInteger(0)

    @Volatile private var player: ExoPlayer? = null
    @Volatile private var usingGlPath = false

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
    private val texMatrix = FloatArray(16)

    companion object {
        /**
         * Extra 90° UV turn for orientation mismatch — same matrices as
         * WallpaperSwitcher (column-major):
         * CW:  x' = y,     y' = 1 - x
         * CCW: x' = 1 - y, y' = x
         * Applied as R * SurfaceTextureMatrix so text is not mirrored.
         */
        private val EXTRA_ROTATE_90_CW = floatArrayOf(
            0f, -1f, 0f, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
        )
        private val EXTRA_ROTATE_90_CCW = floatArrayOf(
            0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            1f, 0f, 0f, 1f
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!intentionalRelease) {
                postToPlayerThread { try { player?.prepare() } catch (_: Exception) {} }
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE && !intentionalRelease) {
                postToPlayerThread { try { player?.prepare() } catch (_: Exception) {} }
            }
        }
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoW = videoSize.width
                videoH = videoSize.height
                glHandler?.post { if (usingGlPath) requestDraw() }
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
        val gen = generation.incrementAndGet()
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
            if (gen != generation.get()) return@post
            try {
                setupEgl(holder)
                setupGl()
                val st = SurfaceTexture(tex[0]).also { surfaceTexture = it }
                if (videoW > 0 && videoH > 0) st.setDefaultBufferSize(videoW, videoH)
                st.setOnFrameAvailableListener({
                    frameAvailable.set(true)
                    handler.post { if (gen == generation.get()) drainAndDraw() }
                }, handler)
                decoderSurface = Surface(st)

                val exo = buildPlayer(uri)
                exo.setVideoSurface(decoderSurface)
                exo.addListener(playerListener)
                exo.prepare()
                exo.playWhenReady = true
                player = exo
                usingGlPath = true
            } catch (_: Throwable) {
                // Keep wallpaper switching working even if GL path fails.
                try {
                    releaseGlOnly()
                    startSurfaceHolderFallback(uri, holder, scaleMode, gen)
                } catch (_: Throwable) {
                    releaseGlOnly()
                }
            }
        }
    }

    private fun startSurfaceHolderFallback(
        uri: Uri,
        holder: SurfaceHolder,
        scaleMode: WallpaperScaleMode,
        gen: Int
    ) {
        if (gen != generation.get()) return
        usingGlPath = false
        val exo = buildPlayer(uri)
        exo.videoScalingMode = when (scaleMode) {
            WallpaperScaleMode.FIT -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            WallpaperScaleMode.FILL, WallpaperScaleMode.STRETCH ->
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
        exo.setVideoSurfaceHolder(holder)
        exo.addListener(playerListener)
        exo.prepare()
        exo.playWhenReady = true
        player = exo
    }

    private fun buildPlayer(uri: Uri): ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }

    fun detachSurface() {
        val handler = glHandler
        if (handler != null) {
            handler.post {
                try { player?.clearVideoSurface() } catch (_: Exception) {}
                if (eglSurf != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(display, eglSurf)
                    eglSurf = EGL14.EGL_NO_SURFACE
                }
            }
        } else {
            try { player?.clearVideoSurfaceHolder(surfaceHolder) } catch (_: Exception) {}
        }
    }

    fun pause() = postToPlayerThread { try { player?.pause() } catch (_: Exception) {} }
    fun resume() = postToPlayerThread { try { player?.play() } catch (_: Exception) {} }

    /**
     * Fire-and-forget cleanup for onDestroy / detach.
     * Prefer [releaseSuspending] before attaching an image EGL surface.
     */
    fun release() {
        intentionalRelease = true
        generation.incrementAndGet()
        val handler = glHandler
        val thread = glThread
        val holder = surfaceHolder
        glHandler = null
        glThread = null
        surfaceHolder = null
        usingGlPath = false

        if (handler != null && thread != null && thread.isAlive) {
            handler.post {
                releasePlayerAndGl(holder)
                thread.quitSafely()
            }
        } else {
            releasePlayerAndGl(holder)
        }
    }

    /**
     * Wait until ExoPlayer + EGL window surface are gone so image renderer can bind.
     * Async [release] left the wallpaper Surface busy → home image reloads looked “dead”.
     */
    suspend fun releaseSuspending() {
        intentionalRelease = true
        generation.incrementAndGet()
        val handler = glHandler
        val thread = glThread
        val holder = surfaceHolder
        glHandler = null
        glThread = null
        surfaceHolder = null
        usingGlPath = false

        if (handler == null || thread == null || !thread.isAlive) {
            releasePlayerAndGl(holder)
            // Brief settle so the next eglCreateWindowSurface can attach.
            delay(32)
            return
        }

        suspendCancellableCoroutine { cont ->
            val posted = handler.post {
                try {
                    releasePlayerAndGl(holder)
                } finally {
                    thread.quitSafely()
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            if (!posted && cont.isActive) {
                releasePlayerAndGl(holder)
                thread.quitSafely()
                cont.resume(Unit)
            }
        }
        // Ensure GL thread finished destroying the window surface before image EGL binds.
        withContext(Dispatchers.IO) {
            try { thread.join(1500) } catch (_: InterruptedException) {}
        }
        delay(32)
    }

    private fun releasePlayerAndGl(holder: SurfaceHolder? = surfaceHolder) {
        try { player?.removeListener(playerListener) } catch (_: Exception) {}
        try { player?.stop() } catch (_: Exception) {}
        try { player?.clearVideoSurface() } catch (_: Exception) {}
        // Fallback path used setVideoSurfaceHolder — clearVideoSurface alone is not enough.
        if (holder != null) {
            try { player?.clearVideoSurfaceHolder(holder) } catch (_: Exception) {}
        }
        try { player?.release() } catch (_: Exception) {}
        player = null
        releaseGlOnly()
    }

    private fun postToPlayerThread(block: () -> Unit) {
        val handler = glHandler
        if (handler != null) handler.post(block)
        else if (Looper.myLooper() == Looper.getMainLooper()) {
            // Fallback player may live on caller thread during brief windows.
            try { block() } catch (_: Exception) {}
        } else {
            Handler(Looper.getMainLooper()).post {
                try { block() } catch (_: Exception) {}
            }
        }
    }

    private fun setupEgl(holder: SurfaceHolder) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        check(EGL14.eglInitialize(display, null, 0, null, 0)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val cfgs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribs, 0, cfgs, 0, 1, n, 0) && n[0] > 0) {
            "eglChooseConfig failed"
        }
        cfg = cfgs[0]!!
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglCtx = EGL14.eglCreateContext(display, cfg!!, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(eglCtx != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        val surface = holder.surface
        check(surface != null && surface.isValid) { "wallpaper surface invalid" }
        eglSurf = EGL14.eglCreateWindowSurface(display, cfg!!, surface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurf != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        check(EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)) { "eglMakeCurrent failed" }
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
        if (!usingGlPath) return
        val st = surfaceTexture ?: return
        if (display == EGL14.EGL_NO_DISPLAY || eglSurf == EGL14.EGL_NO_SURFACE) return
        try {
            if (!EGL14.eglMakeCurrent(display, eglSurf, eglSurf, eglCtx)) return
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
        val (cw, ch) = WallpaperLayout.effectiveSize(vw, vh, rotate)
        val dest = WallpaperLayout.destinationRect(cw, ch, sw, sh, scaleMode)

        Matrix.setIdentityM(mvpMatrix, 0)
        val sx = 2f / sw
        val sy = -2f / sh
        Matrix.translateM(mvpMatrix, 0, -1f, 1f, 0f)
        Matrix.scaleM(mvpMatrix, 0, sx, sy, 1f)
        Matrix.translateM(mvpMatrix, 0, dest.left, dest.top, 0f)
        Matrix.scaleM(mvpMatrix, 0, dest.width(), dest.height(), 1f)

        // Match WallpaperSwitcher: R * SurfaceTextureMatrix with fixed UV 90° matrices.
        // Center rotateM / corner UV remaps both produced mirrored text here.
        if (rotate) {
            val r = if (rotateMismatchCw) EXTRA_ROTATE_90_CW else EXTRA_ROTATE_90_CCW
            Matrix.multiplyMM(texMatrix, 0, r, 0, stMatrix, 0)
        } else {
            System.arraycopy(stMatrix, 0, texMatrix, 0, 16)
        }

        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glUniform1i(samplerUniform, 0)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stUniform, 1, false, texMatrix, 0)

        // Unit quad + standard UVs; rotation lives entirely in texMatrix.
        val verts = floatArrayOf(
            0f, 0f, 0f, 0f,
            1f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f
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

    private fun releaseGlOnly() {
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
        usingGlPath = false
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
