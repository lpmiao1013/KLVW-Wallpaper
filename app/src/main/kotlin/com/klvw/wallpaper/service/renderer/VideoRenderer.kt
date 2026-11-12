package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.klvw.wallpaper.data.model.WallpaperScaleMode
import com.klvw.wallpaper.util.DisplayUtils

/**
 * Video live-wallpaper renderer.
 *
 * Scale modes (ExoPlayer SurfaceHolder limits):
 * - FIT → letterbox (SCALE_TO_FIT)
 * - FILL → cover + crop (SCALE_TO_FIT_WITH_CROPPING)
 * - STRETCH → same as FILL here (SurfaceHolder cannot non-uniform stretch;
 *   true stretch is available for images via [WallpaperLayout])
 *
 * Auto-rotate: images get a real 90° turn in [EGLImageRenderer]. For video on
 * a raw SurfaceHolder we cannot freely rotate frames; when orientation
 * mismatches we keep the chosen FIT/FILL scaling so the user still gets a
 * predictable result.
 */
class VideoRenderer(
    private val context: Context,
    private val displayUtils: DisplayUtils
) {
    private var player: ExoPlayer? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var currentUri: Uri? = null
    private var intentionalRelease = false

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
    }

    fun load(
        uri: Uri,
        holder: SurfaceHolder,
        scaleMode: WallpaperScaleMode = WallpaperScaleMode.FILL,
        rotateMismatchEnabled: Boolean = false,
        @Suppress("UNUSED_PARAMETER") rotateMismatchCw: Boolean = true
    ) {
        release()
        intentionalRelease = false
        currentUri = uri
        surfaceHolder = holder

        val metrics = displayUtils.getScreenMetrics()
        val (vidW, vidH) = probeDisplaySize(uri)
        // Probe used so future GL rotate can plug in; SurfaceHolder path uses scale only.
        WallpaperLayout.shouldAutoRotate(
            vidW, vidH, metrics.widthPx, metrics.heightPx, rotateMismatchEnabled
        )

        val exo = ExoPlayer.Builder(context).build()
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.volume = 0f
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

    fun detachSurface() {
        try { player?.clearVideoSurfaceHolder(surfaceHolder) } catch (_: Exception) {}
    }

    fun pause() { try { player?.pause() } catch (_: Exception) {} }
    fun resume() { try { player?.play() } catch (_: Exception) {} }

    fun release() {
        intentionalRelease = true
        try { player?.removeListener(playerListener) } catch (_: Exception) {}
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        surfaceHolder = null
        currentUri = null
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
