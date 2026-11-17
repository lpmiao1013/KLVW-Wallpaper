package com.klvw.wallpaper.preset

import android.net.Uri
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Highest-priority scene preset: fixed home/lock file (image XOR video each).
 * When active, schedule auto-apply is suspended; timers / double-tap / scale still work.
 * Disable restores the config snapshot taken at enable (not a specific shuffled file).
 */
@Singleton
class PresetManager @Inject constructor(
    private val prefs: SettingsPreferences,
    private val wallpaperRepository: WallpaperRepository
) {
    suspend fun isActive(): Boolean = prefs.presetEnabled.first()

    /**
     * Enable preset. Returns error toast text, or null on success.
     */
    suspend fun enable(): String? {
        val homeUri = prefs.presetHomeUri.first()
        val lockUri = prefs.presetLockUri.first()
        if (homeUri.isNullOrBlank()) return "请先配置桌面预设文件"
        if (lockUri.isNullOrBlank()) return "请先配置锁屏预设文件"
        if (isActive()) {
            applyPresetWallpapers()
            return null
        }
        prefs.setPresetHistoryJson(captureHistory().toString())
        prefs.setPresetEnabled(true)
        applyPresetWallpapers()
        alignMutex(WallpaperTarget.HOME, prefs.presetHomeIsVideo.first())
        alignMutex(WallpaperTarget.LOCK, prefs.presetLockIsVideo.first())
        return null
    }

    /**
     * Disable preset and restore history. Returns error toast text, or null.
     */
    suspend fun disable(): String? {
        if (!isActive()) return null
        val history = prefs.presetHistoryJson.first()?.let { parseHistory(it) }
        prefs.setPresetEnabled(false)
        prefs.setPresetHistoryJson(null)
        if (history != null) {
            prefs.setScheduleActiveGroupId(history.scheduleActiveGroupId)
            prefs.setScheduleEnabled(history.scheduleEnabled)
            if (history.scheduleEnabled) {
                // ScheduleManager watches scheduleEnabled + presetEnabled and will re-apply.
            } else {
                if (!history.homeUri.isNullOrBlank()) {
                    prefs.setHomeWallpaper(history.homeUri, history.homeIsVideo)
                }
                if (!history.lockUri.isNullOrBlank()) {
                    prefs.setLockWallpaper(history.lockUri, history.lockIsVideo)
                }
            }
        }
        return null
    }

    /** Toggle; returns error or null. */
    suspend fun toggle(): String? =
        if (isActive()) disable() else enable()

    private suspend fun applyPresetWallpapers() {
        val homeUri = prefs.presetHomeUri.first() ?: return
        val homeVideo = prefs.presetHomeIsVideo.first()
        val lockUri = prefs.presetLockUri.first() ?: return
        val lockVideo = prefs.presetLockIsVideo.first()
        wallpaperRepository.setWallpaper(
            WallpaperItem(
                Uri.parse(homeUri),
                if (homeVideo) FolderType.VIDEO else FolderType.IMAGE,
                "preset_home",
                ""
            ),
            WallpaperTarget.HOME
        )
        wallpaperRepository.setWallpaper(
            WallpaperItem(
                Uri.parse(lockUri),
                if (lockVideo) FolderType.VIDEO else FolderType.IMAGE,
                "preset_lock",
                ""
            ),
            WallpaperTarget.LOCK
        )
    }

    private suspend fun captureHistory(): JSONObject = JSONObject().apply {
        put("scheduleEnabled", prefs.scheduleEnabled.first())
        put("scheduleActiveGroupId", prefs.scheduleActiveGroupId.first() ?: JSONObject.NULL)
        put("homeUri", prefs.homeWallpaperUri.first() ?: JSONObject.NULL)
        put("homeIsVideo", prefs.homeIsVideo.first())
        put("lockUri", prefs.lockWallpaperUri.first() ?: JSONObject.NULL)
        put("lockIsVideo", prefs.lockIsVideo.first())
    }

    private fun parseHistory(json: String): History? = try {
        val o = JSONObject(json)
        fun strOrNull(key: String): String? =
            if (o.isNull(key)) null
            else o.optString(key).takeIf { it.isNotBlank() && it != "null" }
        History(
            scheduleEnabled = o.optBoolean("scheduleEnabled", false),
            scheduleActiveGroupId = strOrNull("scheduleActiveGroupId"),
            homeUri = strOrNull("homeUri"),
            homeIsVideo = o.optBoolean("homeIsVideo", false),
            lockUri = strOrNull("lockUri"),
            lockIsVideo = o.optBoolean("lockIsVideo", false)
        )
    } catch (_: Exception) {
        null
    }

    /** Keep display-control / timer mutex aligned with the media type in use. */
    private suspend fun alignMutex(target: WallpaperTarget, isVideo: Boolean) {
        val screen = if (target == WallpaperTarget.LOCK) "lock" else "home"
        val imageKey = "${screen}_image"
        val videoKey = "${screen}_video"
        val imageTimerOn = when (target) {
            WallpaperTarget.HOME -> prefs.homeImageTimerEnabled.first()
            else -> prefs.lockImageTimerEnabled.first()
        }
        val videoTimerOn = when (target) {
            WallpaperTarget.HOME -> prefs.homeVideoTimerEnabled.first()
            else -> prefs.lockVideoTimerEnabled.first()
        }
        val anyTimer = imageTimerOn || videoTimerOn
        if (!isVideo) {
            prefs.setDisplayControl(imageKey, true)
            if (anyTimer) prefs.setTimerEnabled(imageKey, true)
        } else {
            prefs.setDisplayControl(videoKey, true)
            if (anyTimer) prefs.setTimerEnabled(videoKey, true)
        }
    }

    private data class History(
        val scheduleEnabled: Boolean,
        val scheduleActiveGroupId: String?,
        val homeUri: String?,
        val homeIsVideo: Boolean,
        val lockUri: String?,
        val lockIsVideo: Boolean
    )
}
