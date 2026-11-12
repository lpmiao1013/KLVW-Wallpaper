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

class TimerActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun timerManager(): WallpaperTimerManager
        fun prefs(): SettingsPreferences
        fun folderRepository(): FolderRepository
        fun wallpaperRepository(): WallpaperRepository
        fun shuffleHistoryManager(): ShuffleHistoryManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerStatusNotificationHelper.ACTION_TIMER_ACTION) return
        val action = intent.getStringExtra(TimerStatusNotificationHelper.EXTRA_ACTION) ?: return

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, ActionEntryPoint::class.java
        )
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    TimerStatusNotificationHelper.VALUE_TOGGLE_ALL -> {
                        val keys = intent.getStringArrayListExtra(
                            TimerStatusNotificationHelper.EXTRA_TIMER_KEYS
                        ) ?: return@launch
                        applyToggleAll(keys, ep.timerManager(), ep.prefs())
                    }
                    TimerStatusNotificationHelper.VALUE_CHANGE_NOW -> {
                        val key = intent.getStringExtra(
                            TimerStatusNotificationHelper.EXTRA_TIMER_KEY
                        ) ?: return@launch
                        applyChangeNow(
                            key, ep.prefs(),
                            ep.folderRepository(), ep.wallpaperRepository(), ep.shuffleHistoryManager()
                        )
                    }
                    else -> {
                        val key = intent.getStringExtra(
                            TimerStatusNotificationHelper.EXTRA_TIMER_KEY
                        ) ?: return@launch
                        applyAction(action, key, ep.timerManager(), ep.prefs())
                    }
                }
                TimerStatusNotificationHelper.showFromState(
                    context.applicationContext, ep.prefs(), ep.timerManager()
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {

        /** Pause / Resume / Reset a single timer key. Also called from TimerActionPickerActivity. */
        suspend fun applyAction(
            action: String,
            key: String,
            timerManager: WallpaperTimerManager,
            prefs: SettingsPreferences
        ) {
            when (action) {
                TimerStatusNotificationHelper.VALUE_PAUSE -> timerManager.pause(key)
                TimerStatusNotificationHelper.VALUE_RESUME -> {
                    timerManager.resume(key)
                }
                TimerStatusNotificationHelper.VALUE_RESET -> {
                    val interval = intervalForKey(prefs, key)
                    timerManager.resume(key)
                    timerManager.scheduleAlarm(key, interval)
                }
            }
        }

        /**
         * Notification body tap: if all timers are paused → resume all;
         * otherwise → pause all currently active ones.
         */
        suspend fun applyToggleAll(
            keys: List<String>,
            timerManager: WallpaperTimerManager,
            prefs: SettingsPreferences
        ) {
            val allPaused = keys.all { timerManager.paused.value[it] == true }
            if (allPaused) {
                keys.forEach { key -> timerManager.resume(key) }
            } else {
                keys.filter { timerManager.paused.value[it] != true }
                    .forEach { timerManager.pause(it) }
            }
        }

        /**
         * "立即更换" button: immediately pick the next shuffle-bag wallpaper for this
         * timer's linked folder and apply it, without touching the timer schedule.
         * Also called from TimerActionPickerActivity for the multi-timer picker path.
         */
        suspend fun applyChangeNow(
            key: String,
            prefs: SettingsPreferences,
            folderRepository: FolderRepository,
            wallpaperRepository: WallpaperRepository,
            shuffleHistoryManager: ShuffleHistoryManager
        ) {
            val (type, target) = keyToTypeTarget(key)
            val folderUri = defaultFolderUriForKey(prefs, key) ?: return
            val items = folderRepository.getItemsFromFolder(folderUri, type)
            val item = shuffleHistoryManager.pickNext(items, folderUri, type) ?: return
            wallpaperRepository.setWallpaper(item, target)
        }

        fun keyToTypeTarget(key: String): Pair<FolderType, WallpaperTarget> = when (key) {
            "home_image" -> FolderType.IMAGE to WallpaperTarget.HOME
            "home_video" -> FolderType.VIDEO to WallpaperTarget.HOME
            "lock_image" -> FolderType.IMAGE to WallpaperTarget.LOCK
            "lock_video" -> FolderType.VIDEO to WallpaperTarget.LOCK
            else -> throw IllegalArgumentException("Unknown timer key: $key")
        }

        suspend fun defaultFolderUriForKey(prefs: SettingsPreferences, key: String): String? =
            when (key) {
                "home_image" -> prefs.defaultHomeImageFolderUri.first()
                "home_video" -> prefs.defaultHomeVideoFolderUri.first()
                "lock_image" -> prefs.defaultLockImageFolderUri.first()
                "lock_video" -> prefs.defaultLockVideoFolderUri.first()
                else -> null
            }

        private suspend fun intervalForKey(prefs: SettingsPreferences, key: String) = when (key) {
            "home_image" -> prefs.homeImageTimerIntervalMin.first()
            "home_video" -> prefs.homeVideoTimerIntervalMin.first()
            "lock_image" -> prefs.lockImageTimerIntervalMin.first()
            "lock_video" -> prefs.lockVideoTimerIntervalMin.first()
            else -> 60
        }
    }
}
