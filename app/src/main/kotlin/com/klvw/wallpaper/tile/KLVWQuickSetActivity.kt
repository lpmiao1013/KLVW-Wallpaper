package com.klvw.wallpaper.tile

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.schedule.ScheduleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KLVWQuickSetActivity : ComponentActivity() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager
    @Inject lateinit var timerManager: WallpaperTimerManager
    @Inject lateinit var scheduleManager: ScheduleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (prefs.appEnabled.first()) {
                val homeAction = prefs.quickSetHomeAction.first()
                val lockAction = prefs.quickSetLockAction.first()
                applyAction(homeAction, WallpaperTarget.HOME)
                applyAction(lockAction, WallpaperTarget.LOCK)
                applyWatchAction()
                if (homeAction == "static_image" && lockAction == "static_image") {
                    prefs.setAppEnabled(false)
                    // Pause all running timers if the user opted in to that behaviour
                    if (prefs.pauseTimersOnGlobalOff.first()) {
                        val pausedByGlobalOff = pauseAllEnabledTimers()
                        prefs.setGlobalOffPausedTimers(pausedByGlobalOff.toSet())
                        getSystemService(NotificationManager::class.java)
                            ?.cancel(TimerStatusNotificationHelper.NOTIFICATION_ID)
                    }
                }
            }
            finish()
        }
    }

    private suspend fun applyAction(action: String, target: WallpaperTarget) {
        when (action) {
            "random_image" -> applyResolvedRandom(FolderType.IMAGE, target)
            "random_video" -> applyResolvedRandom(FolderType.VIDEO, target)
            "static_image" -> {
                if (scheduleManager.isEnabled() || prefs.presetEnabled.first()) return
                val uri = when (target) {
                    WallpaperTarget.HOME -> prefs.quickSetHomeStaticUri.first()
                    WallpaperTarget.LOCK -> prefs.quickSetLockStaticUri.first()
                    else -> null
                } ?: return
                wallpaperRepository.setWallpaper(
                    WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                    target
                )
            }
        }
    }

    private suspend fun applyResolvedRandom(type: FolderType, target: WallpaperTarget) {
        val screens = when (target) {
            WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
            WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
            else -> listOf(WallpaperTarget.HOME)
        }
        for (screen in screens) {
            val uri = scheduleManager.resolveFolderUri(screen, type) ?: continue
            val item = shuffleHistoryManager.pickNext(
                folderRepository.getItemsFromFolder(uri, type), uri, type
            ) ?: continue
            wallpaperRepository.setWallpaper(item, screen)
        }
    }

    private suspend fun applyWatchAction() {
        val presetId = prefs.quickSetWatchPresetId.first() ?: return
        val preset = PujieWatchFacePreset.fromJsonArray(prefs.pujieWatchFacesJson.first())
            .find { it.id == presetId } ?: return
        val bundle = Bundle().apply {
            putString("watchface", preset.watchFaceName)
            putString("net.dinglisch.android.tasker.extras.ACTION_RUNNER_CLASS", preset.receiverClass)
            if (preset.inputClass.isNotBlank())
                putString("net.dinglisch.android.tasker.extras.ACTION_INPUT_CLASS", preset.inputClass)
            putBoolean("net.dinglisch.android.tasker.extras.EXTRA_WAS_CONFIGURED_BEFORE", true)
            putString("net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS", "watchface")
        }
        val intent = Intent("com.twofortyfouram.locale.intent.action.FIRE_SETTING").apply {
            setClassName("com.pujie.watchfaces",
                "com.joaomgcd.taskerpluginlibrary.action.IntentServiceAction")
            putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", bundle)
        }
        startForegroundService(intent)
    }

    private suspend fun pauseAllEnabledTimers(): List<String> {
        val keys = listOf(WallpaperTimerManager.KEY_HOME, WallpaperTimerManager.KEY_LOCK)
            .filter { key ->
                when (key) {
                    WallpaperTimerManager.KEY_HOME -> prefs.homeTimerEnabled.first()
                    WallpaperTimerManager.KEY_LOCK -> prefs.lockTimerEnabled.first()
                    else -> false
                }
            }
            .filter { key -> timerManager.paused.value[key] != true }
        keys.forEach { key -> timerManager.pause(key) }
        return keys
    }

}
