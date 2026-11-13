package com.klvw.wallpaper.service

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperScaleMode
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.service.renderer.EGLImageRenderer
import com.klvw.wallpaper.service.renderer.VideoRenderer
import com.klvw.wallpaper.util.DisplayUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private data class HomeCollectQuad(
    val uri: String?,
    val isVideo: Boolean,
    val scale: String,
    val rotateEn: Boolean,
    val rotateCw: Boolean
)

@AndroidEntryPoint
class KLVWallpaperService : WallpaperService() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var displayUtils: DisplayUtils
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager

    override fun onCreateEngine(): Engine = KLVWallpaperEngine()

    inner class KLVWallpaperEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var collectorJob: Job? = null
        private var colorJob: Job? = null
        private var imageRenderer: EGLImageRenderer? = null
        private var videoRenderer: VideoRenderer? = null
        private var isVideo = false
        private var forceIconColor: String = "auto"
        private var computedWallpaperColors: WallpaperColors? = null
        private var lastTapUptimeMs = 0L
        private var advanceJob: Job? = null

        init {
            setTouchEventsEnabled(true)
        }

        private val screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF && isVideo) {
                    videoRenderer?.pause()
                }
            }
        }

        // Samsung requires HINT_FROM_BITMAP (set only by fromBitmap()) to trust WallpaperColors.
        // Pure white → maximum luminance → HINT_SUPPORTS_DARK_TEXT set → dark (black) icons.
        // Pure black → zero luminance → HINT_SUPPORTS_DARK_TEXT clear → light (white) icons.
        private val syntheticWhiteColors by lazy {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                .also { it.eraseColor(Color.WHITE) }
                .let { bmp -> WallpaperColors.fromBitmap(bmp).also { bmp.recycle() } }
        }
        private val syntheticBlackColors by lazy {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                .also { it.eraseColor(Color.BLACK) }
                .let { bmp -> WallpaperColors.fromBitmap(bmp).also { bmp.recycle() } }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            registerReceiver(
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED
            )
            // Seed with persisted value so the very first onComputeColors() call is correct.
            prefs.homeWallpaperIsLightCache.value?.let { isLight ->
                computedWallpaperColors = if (isLight) syntheticWhiteColors else syntheticBlackColors
            }
            startCollecting(holder)
            startColorCollecting()
        }

        private fun startColorCollecting() {
            colorJob?.cancel()
            colorJob = scope.launch {
                prefs.forceIconColor.collect { color ->
                    forceIconColor = color
                    notifyColorsChanged()
                }
            }
        }

        // Analyzes the top 60% of the wallpaper image to pick dark or light icon colors.
        // Always returns one of the synthetic fromBitmap() colors so Samsung respects the hint.
        private fun loadColorsAsync(uri: Uri, isVideo: Boolean) {
            scope.launch {
                try {
                    val result: Pair<Boolean, WallpaperColors>? = withContext(Dispatchers.IO) {
                        val bitmap = if (isVideo) {
                            MediaMetadataRetriever().use { r ->
                                r.setDataSource(applicationContext, uri)
                                r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            }
                        } else {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
                            applicationContext.contentResolver.openInputStream(uri)
                                ?.use { BitmapFactory.decodeStream(it, null, opts) }
                        }
                        bitmap?.let { bmp ->
                            try {
                                val topH = (bmp.height * 0.6f).toInt().coerceAtLeast(1)
                                val topBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, topH)
                                val light = isLightBitmap(topBmp)
                                topBmp.recycle()
                                light to (if (light) syntheticWhiteColors else syntheticBlackColors)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                    if (result != null) {
                        val (isLight, colors) = result
                        computedWallpaperColors = colors
                        prefs.setHomeWallpaperIsLightCache(isLight)
                    }
                    if (forceIconColor == "auto") notifyColorsChanged()
                } catch (_: Throwable) {}
            }
        }

        // Uses fromBitmap()-derived colors so HINT_FROM_BITMAP is always set.
        // Samsung ignores manually-constructed WallpaperColors without this flag.
        override fun onComputeColors(): WallpaperColors = when (forceIconColor) {
            "dark"  -> syntheticWhiteColors
            "light" -> syntheticBlackColors
            else    -> computedWallpaperColors ?: syntheticBlackColors
        }

        private fun startCollecting(holder: SurfaceHolder) {
            collectorJob?.cancel()
            collectorJob = scope.launch {
                combine(
                    prefs.homeWallpaperState,
                    prefs.wallpaperScaleMode,
                    prefs.rotateMismatchEnabled,
                    prefs.rotateMismatchCw
                ) { state, scale, rotateEn, rotateCw ->
                    HomeCollectQuad(state.first, state.second, scale, rotateEn, rotateCw)
                }.collect { q ->
                    try {
                        // Sync release: video uses SurfaceHolder (not GLES), so it can hand
                        // the same wallpaper Surface back to image EGL cleanly.
                        release()
                        val uri = q.uri ?: return@collect
                        isVideo = q.isVideo
                        val mode = WallpaperScaleMode.fromPref(q.scale)
                        if (q.isVideo) {
                            videoRenderer = VideoRenderer(applicationContext, displayUtils)
                            videoRenderer!!.load(
                                Uri.parse(uri), holder, mode, q.rotateEn, q.rotateCw
                            )
                        } else {
                            imageRenderer = EGLImageRenderer(applicationContext, displayUtils)
                            imageRenderer!!.load(
                                Uri.parse(uri), holder, mode, q.rotateEn, q.rotateCw
                            )
                        }
                        loadColorsAsync(Uri.parse(uri), q.isVideo)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        release()
                    }
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (!isVideo) imageRenderer?.render(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (isVideo) {
                if (visible) videoRenderer?.resume() else videoRenderer?.pause()
            }
        }

        // Engine.onTouchEvent is Unit on current SDK (not boolean like View).
        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked != MotionEvent.ACTION_UP) return
            val now = SystemClock.uptimeMillis()
            val timeout = ViewConfiguration.getDoubleTapTimeout().toLong()
            if (lastTapUptimeMs > 0L && now - lastTapUptimeMs <= timeout) {
                lastTapUptimeMs = 0L
                advanceByDoubleTap()
            } else {
                lastTapUptimeMs = now
            }
        }

        private fun advanceByDoubleTap() {
            if (advanceJob?.isActive == true) return
            val useVideo = isVideo
            advanceJob = scope.launch(Dispatchers.IO) {
                try {
                    if (!prefs.appEnabled.first() || !prefs.doubleTapEnabled.first()) return@launch
                    val type = if (useVideo) FolderType.VIDEO else FolderType.IMAGE
                    val folderUri = if (useVideo) {
                        prefs.defaultHomeVideoFolderUri.first()
                    } else {
                        prefs.defaultHomeImageFolderUri.first()
                    } ?: return@launch
                    val items = folderRepository.getItemsFromFolder(folderUri, type)
                    val item = shuffleHistoryManager.pickNext(items, folderUri, type) ?: return@launch
                    wallpaperRepository.setWallpaper(item, WallpaperTarget.HOME)
                } catch (_: Throwable) {
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
            collectorJob?.cancel()
            collectorJob = null
            colorJob?.cancel()
            colorJob = null
            videoRenderer?.detachSurface()
            release()
        }

        override fun onDestroy() {
            super.onDestroy()
            try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
            advanceJob?.cancel()
            advanceJob = null
            collectorJob?.cancel()
            collectorJob = null
            colorJob?.cancel()
            colorJob = null
            release()
            scope.cancel()
        }

        private fun release() {
            try { imageRenderer?.release() } catch (_: Throwable) {}
            imageRenderer = null
            try { videoRenderer?.release() } catch (_: Throwable) {}
            videoRenderer = null
            computedWallpaperColors = null
        }
    }
}
