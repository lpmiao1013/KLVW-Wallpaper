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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WallpaperTimerReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TimerEntryPoint {
        fun prefs(): SettingsPreferences
        fun wallpaperRepository(): WallpaperRepository
        fun folderRepository(): FolderRepository
        fun timerManager(): WallpaperTimerManager
        fun shuffleHistoryManager(): ShuffleHistoryManager
        fun scheduleManager(): com.klvw.wallpaper.schedule.ScheduleManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WallpaperTimerManager.ACTION_TIMER_FIRE -> handleTimerFire(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> { /* WallpaperTimerManager.init() reschedules alarms on process start */ }
        }
    }

    private fun handleTimerFire(context: Context, intent: Intent) {
        val key = intent.getStringExtra(WallpaperTimerManager.EXTRA_TIMER_KEY) ?: return
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, TimerEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerManager = ep.timerManager()
                // Check both in-memory state and the persisted DataStore set (generalPausedTimers).
                // If the process was killed while locked, _paused resets to false but
                // generalPausedTimers still records the pause (covers both P.o.L and notification
                // pauses) — the DataStore check is the safety net for process restart.
                val paused = timerManager.paused.value[key] == true
                          || ep.prefs().generalPausedTimers.first().contains(key)
                if (!paused && ep.prefs().appEnabled.first()) {
                    val (type, target) = keyToTypeTarget(key)
                    val uri = ep.scheduleManager().resolveFolderUri(target, type)
                    if (uri != null) {
                        val allItems = ep.folderRepository().getItemsFromFolder(uri, type)
                        val item = ep.shuffleHistoryManager().pickNext(allItems, uri, type)
                        if (item != null) ep.wallpaperRepository().setWallpaper(item, target)
                    }
                }
                // Only reschedule when not paused — calling scheduleAlarm while paused clears
                // _pausedRemainingMs, destroying the remaining time that resume() needs to restore.
                // When paused, launchTimer handles rescheduling once the timer is resumed.
                if (!paused) {
                    val intervalMin = intervalForKey(ep.prefs(), key)
                    timerManager.scheduleAlarm(key, intervalMin)
                    TimerStatusNotificationHelper.refreshIfVisible(context, ep.prefs(), timerManager)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun keyToTypeTarget(key: String): Pair<FolderType, WallpaperTarget> = when (key) {
        "home_image" -> FolderType.IMAGE to WallpaperTarget.HOME
        "home_video" -> FolderType.VIDEO to WallpaperTarget.HOME
        "lock_image" -> FolderType.IMAGE to WallpaperTarget.LOCK
        "lock_video" -> FolderType.VIDEO to WallpaperTarget.LOCK
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    private suspend fun intervalForKey(prefs: SettingsPreferences, key: String): Int = when (key) {
        "home_image" -> prefs.homeImageTimerIntervalMin.first()
        "home_video" -> prefs.homeVideoTimerIntervalMin.first()
        "lock_image" -> prefs.lockImageTimerIntervalMin.first()
        "lock_video" -> prefs.lockVideoTimerIntervalMin.first()
        else -> 60
    }
}
