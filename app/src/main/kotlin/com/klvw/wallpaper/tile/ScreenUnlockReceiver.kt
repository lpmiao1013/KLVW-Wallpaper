package com.klvw.wallpaper.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenUnlockReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UnlockEntryPoint {
        fun prefs(): SettingsPreferences
        fun wallpaperRepository(): WallpaperRepository
        fun folderRepository(): FolderRepository
        fun timerManager(): WallpaperTimerManager
        fun shuffleHistoryManager(): ShuffleHistoryManager
    }

    private data class UnlockCandidate(
        val folderUri: String,
        val type: FolderType,
        val items: List<WallpaperItem>
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, UnlockEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = ep.prefs()
                if (!prefs.appEnabled.first()) return@launch

                // Pick among enabled types that actually have items — do NOT fall through
                // image → video on empty/failed image (that made lock "switch to video mode").
                val appliedHome = applyOneForScreen(
                    imageEnabled = prefs.displayControlHomeImage.first(),
                    imageFolder = prefs.defaultHomeImageFolderUri.first(),
                    videoEnabled = prefs.displayControlHomeVideo.first(),
                    videoFolder = prefs.defaultHomeVideoFolderUri.first(),
                    target = WallpaperTarget.HOME,
                    ep = ep
                )
                val appliedLock = applyOneForScreen(
                    imageEnabled = prefs.displayControlLockImage.first(),
                    imageFolder = prefs.defaultLockImageFolderUri.first(),
                    videoEnabled = prefs.displayControlLockVideo.first(),
                    videoFolder = prefs.defaultLockVideoFolderUri.first(),
                    target = WallpaperTarget.LOCK,
                    ep = ep
                )

                if (appliedHome && prefs.displayControlResetHomeTimer.first()) {
                    resetTimersForScreen("home", prefs, ep.timerManager())
                }
                if (appliedLock && prefs.displayControlResetLockTimer.first()) {
                    resetTimersForScreen("lock", prefs, ep.timerManager())
                }

                // P.o.L — resume any timers that were paused when the device locked
                // Must run before the notification so paused.value reflects the resumed state
                val polPaused = prefs.polPausedTimers.first()
                if (polPaused.isNotEmpty()) {
                    val remainingTimes = prefs.polPausedFireTimes.first()
                    polPaused.forEach { key ->
                        ep.timerManager().resumeWithRemainingMs(key, remainingTimes[key])
                    }
                    prefs.setPolPausedTimers(emptySet())
                    prefs.setPolPausedFireTimes(emptyMap())
                }

                if (prefs.timerUnlockNotification.first()) {
                    showTimerNotificationIfNeeded(context, prefs, ep.timerManager())
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Applies exactly one wallpaper for [target] from enabled types that actually have media.
     * Prefers image when both are ready (same as the old happy path), but never falls through
     * to video merely because the image folder was empty / missing while its toggle is on.
     */
    private suspend fun applyOneForScreen(
        imageEnabled: Boolean,
        imageFolder: String?,
        videoEnabled: Boolean,
        videoFolder: String?,
        target: WallpaperTarget,
        ep: UnlockEntryPoint
    ): Boolean {
        val candidates = mutableListOf<UnlockCandidate>()
        if (imageEnabled && imageFolder != null) {
            val items = ep.folderRepository().getItemsFromFolder(imageFolder, FolderType.IMAGE)
            if (items.isNotEmpty()) {
                candidates += UnlockCandidate(imageFolder, FolderType.IMAGE, items)
            }
        }
        if (videoEnabled && videoFolder != null) {
            val items = ep.folderRepository().getItemsFromFolder(videoFolder, FolderType.VIDEO)
            if (items.isNotEmpty()) {
                candidates += UnlockCandidate(videoFolder, FolderType.VIDEO, items)
            }
        }
        if (candidates.isEmpty()) return false

        val chosen = candidates.firstOrNull { it.type == FolderType.IMAGE } ?: candidates.first()
        val item = ep.shuffleHistoryManager().pickNext(chosen.items, chosen.folderUri, chosen.type)
            ?: return false
        ep.wallpaperRepository().setWallpaper(item, target)
        return true
    }

    private suspend fun showTimerNotificationIfNeeded(
        context: Context,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val allKeys = listOf("home_image", "home_video", "lock_image", "lock_video")
        val enabledKeys = allKeys.filter { key ->
            when (key) {
                "home_image" -> prefs.homeImageTimerEnabled.first()
                "home_video" -> prefs.homeVideoTimerEnabled.first()
                "lock_image" -> prefs.lockImageTimerEnabled.first()
                "lock_video" -> prefs.lockVideoTimerEnabled.first()
                else -> false
            }
        }
        if (enabledKeys.isEmpty()) return
        if (prefs.timerNotificationOnlyWhenRunning.first()) {
            val allPaused = enabledKeys.all { timerManager.paused.value[it] == true }
            if (allPaused) return
        }
        TimerStatusNotificationHelper.show(
            context,
            enabledKeys,
            timerManager.paused.value,
            timerManager.nextFireTimes.value
        )
    }

    private suspend fun resetTimersForScreen(
        screen: String,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val imageKey = "${screen}_image"
        val videoKey = "${screen}_video"
        val imageEnabled = when (screen) {
            "home" -> prefs.homeImageTimerEnabled.first()
            else   -> prefs.lockImageTimerEnabled.first()
        }
        val videoEnabled = when (screen) {
            "home" -> prefs.homeVideoTimerEnabled.first()
            else   -> prefs.lockVideoTimerEnabled.first()
        }
        val imageInterval = when (screen) {
            "home" -> prefs.homeImageTimerIntervalMin.first()
            else   -> prefs.lockImageTimerIntervalMin.first()
        }
        val videoInterval = when (screen) {
            "home" -> prefs.homeVideoTimerIntervalMin.first()
            else   -> prefs.lockVideoTimerIntervalMin.first()
        }
        val imagePaused = timerManager.paused.value[imageKey] == true
        val videoPaused = timerManager.paused.value[videoKey] == true

        if (imageEnabled && !imagePaused) timerManager.scheduleAlarm(imageKey, imageInterval)
        if (videoEnabled && !videoPaused) timerManager.scheduleAlarm(videoKey, videoInterval)
    }
}
