package com.klvw.wallpaper.tile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.schedule.ScheduleManager
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
        fun scheduleManager(): ScheduleManager
    }

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

                // Screen-level unlock: follow current media type + scheme priority.
                val appliedHome = if (prefs.displayControlHome.first()) {
                    applyCurrentTypeForScreen(WallpaperTarget.HOME, ep)
                } else false
                val appliedLock = if (prefs.displayControlLock.first()) {
                    applyCurrentTypeForScreen(WallpaperTarget.LOCK, ep)
                } else false

                if (appliedHome && prefs.displayControlResetHomeTimer.first()) {
                    resetTimersForScreen(WallpaperTimerManager.KEY_HOME, prefs, ep.timerManager())
                }
                if (appliedLock && prefs.displayControlResetLockTimer.first()) {
                    resetTimersForScreen(WallpaperTimerManager.KEY_LOCK, prefs, ep.timerManager())
                }

                val polPaused = prefs.polPausedTimers.first()
                if (polPaused.isNotEmpty()) {
                    val remainingTimes = prefs.polPausedFireTimes.first()
                    polPaused.map { WallpaperTimerManager.normalizeKey(it) }.toSet().forEach { key ->
                        ep.timerManager().resumeWithRemainingMs(key, remainingTimes[key] ?: remainingTimes.entries
                            .firstOrNull { WallpaperTimerManager.normalizeKey(it.key) == key }?.value)
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
     * Unlock rotate for one screen: use current wallpaper type, then
     * preset → schedule → tabbar → default via [ScheduleManager.resolveFolderUri].
     */
    private suspend fun applyCurrentTypeForScreen(
        target: WallpaperTarget,
        ep: UnlockEntryPoint
    ): Boolean {
        val prefs = ep.prefs()
        val isVideo = when (target) {
            WallpaperTarget.HOME -> prefs.homeIsVideo.first()
            WallpaperTarget.LOCK -> prefs.lockIsVideo.first()
            else -> false
        }
        val type = if (isVideo) FolderType.VIDEO else FolderType.IMAGE
        val folderUri = ep.scheduleManager().resolveFolderUri(target, type) ?: return false
        val items = ep.folderRepository().getItemsFromFolder(folderUri, type)
        val item = ep.shuffleHistoryManager().pickNext(items, folderUri, type) ?: return false
        ep.wallpaperRepository().setWallpaper(item, target)
        return true
    }

    private suspend fun showTimerNotificationIfNeeded(
        context: Context,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val enabledKeys = listOf(
            WallpaperTimerManager.KEY_HOME.takeIf { prefs.homeTimerEnabled.first() },
            WallpaperTimerManager.KEY_LOCK.takeIf { prefs.lockTimerEnabled.first() }
        ).filterNotNull()
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
        key: String,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val enabled = when (key) {
            WallpaperTimerManager.KEY_LOCK -> prefs.lockTimerEnabled.first()
            else -> prefs.homeTimerEnabled.first()
        }
        val interval = when (key) {
            WallpaperTimerManager.KEY_LOCK -> prefs.lockTimerIntervalMin.first()
            else -> prefs.homeTimerIntervalMin.first()
        }
        val paused = timerManager.paused.value[key] == true
        if (enabled && !paused) timerManager.scheduleAlarm(key, interval)
    }
}
