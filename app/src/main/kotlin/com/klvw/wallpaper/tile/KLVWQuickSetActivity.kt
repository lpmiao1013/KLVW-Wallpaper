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
        if (scheduleManager.isEnabled() && target != WallpaperTarget.BOTH) {
            val rule = scheduleManager.currentRule(target) ?: return
            val item = shuffleHistoryManager.pickNext(
                folderRepository.getItemsFromFolder(rule.folderUri, rule.media),
                rule.folderUri, rule.media
            ) ?: return
            wallpaperRepository.setWallpaper(item, target)
            return
        }
        when (action) {
            "random_image" -> {
                val uri = resolveDefaultFolderUri(FolderType.IMAGE, target) ?: return
                val item = shuffleHistoryManager.pickNext(
                    folderRepository.getItemsFromFolder(uri, FolderType.IMAGE), uri, FolderType.IMAGE
                ) ?: return
                wallpaperRepository.setWallpaper(item, target)
            }
            "random_video" -> {
                val uri = resolveDefaultFolderUri(FolderType.VIDEO, target) ?: return
                val item = shuffleHistoryManager.pickNext(
                    folderRepository.getItemsFromFolder(uri, FolderType.VIDEO), uri, FolderType.VIDEO
                ) ?: return
                wallpaperRepository.setWallpaper(item, target)
            }
            "static_image" -> {
                if (scheduleManager.isEnabled()) return
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
        val keys = listOf("home_image", "home_video", "lock_image", "lock_video")
            .filter { key ->
                when (key) {
                    "home_image" -> prefs.homeImageTimerEnabled.first()
                    "home_video" -> prefs.homeVideoTimerEnabled.first()
                    "lock_image" -> prefs.lockImageTimerEnabled.first()
                    "lock_video" -> prefs.lockVideoTimerEnabled.first()
                    else -> false
                }
            }
            .filter { key -> timerManager.paused.value[key] != true }
        keys.forEach { key -> timerManager.pause(key) }
        return keys
    }

    private suspend fun resolveDefaultFolderUri(type: FolderType, target: WallpaperTarget): String? = when {
        type == FolderType.IMAGE && target == WallpaperTarget.HOME -> prefs.defaultHomeImageFolderUri.first()
        type == FolderType.IMAGE && target == WallpaperTarget.LOCK -> prefs.defaultLockImageFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.HOME -> prefs.defaultHomeVideoFolderUri.first()
        type == FolderType.VIDEO && target == WallpaperTarget.LOCK -> prefs.defaultLockVideoFolderUri.first()
        else -> null
    }
}
