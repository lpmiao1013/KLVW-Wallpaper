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
            Intent.ACTION_BOOT_COMPLETED -> { /* WallpaperTimerManager.init() reschedules */ }
        }
    }

    private fun handleTimerFire(context: Context, intent: Intent) {
        val rawKey = intent.getStringExtra(WallpaperTimerManager.EXTRA_TIMER_KEY) ?: return
        val key = WallpaperTimerManager.normalizeKey(rawKey)
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, TimerEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerManager = ep.timerManager()
                val paused = timerManager.paused.value[key] == true
                    || ep.prefs().generalPausedTimers.first().any {
                        WallpaperTimerManager.normalizeKey(it) == key
                    }
                if (!paused && ep.prefs().appEnabled.first()) {
                    val prefs = ep.prefs()
                    val target = when (key) {
                        WallpaperTimerManager.KEY_LOCK -> WallpaperTarget.LOCK
                        else -> WallpaperTarget.HOME
                    }
                    // Follow current wallpaper type — never force image/video from a stale toggle.
                    val isVideo = when (target) {
                        WallpaperTarget.LOCK -> prefs.lockIsVideo.first()
                        else -> prefs.homeIsVideo.first()
                    }
                    val type = if (isVideo) FolderType.VIDEO else FolderType.IMAGE
                    // Preset > schedule > tabbar > default
                    val uri = ep.scheduleManager().resolveFolderUri(target, type)
                    if (uri != null) {
                        val allItems = ep.folderRepository().getItemsFromFolder(uri, type)
                        val item = ep.shuffleHistoryManager().pickNext(allItems, uri, type)
                        if (item != null) ep.wallpaperRepository().setWallpaper(item, target)
                    }
                }
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

    private suspend fun intervalForKey(prefs: SettingsPreferences, key: String): Int = when (key) {
        WallpaperTimerManager.KEY_LOCK -> prefs.lockTimerIntervalMin.first()
        else -> prefs.homeTimerIntervalMin.first()
    }
}
