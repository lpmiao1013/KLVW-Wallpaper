package com.klvw.wallpaper.preset

import android.net.Uri
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Highest-priority scene preset: home/lock each pick image XOR video, from a file or folder.
 * When active, schedule auto-apply is suspended; timers / double-tap / scale still work.
 * Folder source also feeds [resolveFolderUri] for timers while preset is on.
 */
@Singleton
class PresetManager @Inject constructor(
    private val prefs: SettingsPreferences,
    private val wallpaperRepository: WallpaperRepository,
    private val folderRepository: FolderRepository,
    private val shuffleHistoryManager: ShuffleHistoryManager
) {
    suspend fun isActive(): Boolean = prefs.presetEnabled.first()

    /**
     * Enable preset. Returns error toast text, or null on success.
     */
    suspend fun enable(): String? {
        validateConfigured()?.let { return it }
        if (!isActive()) {
            prefs.setPresetHistoryJson(captureHistory().toString())
            prefs.setPresetEnabled(true)
        }
        applyPresetWallpapers()?.let { return it }
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
            if (!history.scheduleEnabled) {
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

    /**
     * While preset is active and that screen uses a folder source with matching media,
     * return the preset folder for timers / double-tap.
     */
    suspend fun resolveFolderUri(target: WallpaperTarget, type: FolderType): String? {
        if (!isActive()) return null
        if (target != WallpaperTarget.HOME && target != WallpaperTarget.LOCK) return null
        val isVideo = type == FolderType.VIDEO
        val (source, uri, configuredVideo) = when (target) {
            WallpaperTarget.HOME -> Triple(
                prefs.presetHomeSource.first(),
                prefs.presetHomeUri.first(),
                prefs.presetHomeIsVideo.first()
            )
            WallpaperTarget.LOCK -> Triple(
                prefs.presetLockSource.first(),
                prefs.presetLockUri.first(),
                prefs.presetLockIsVideo.first()
            )
            else -> return null
        }
        if (source != "folder") return null
        if (configuredVideo != isVideo) return null
        return uri?.takeIf { it.isNotBlank() }
    }

    private suspend fun validateConfigured(): String? {
        val homeUri = prefs.presetHomeUri.first()
        val lockUri = prefs.presetLockUri.first()
        val homeSrc = prefs.presetHomeSource.first()
        val lockSrc = prefs.presetLockSource.first()
        if (homeUri.isNullOrBlank()) {
            return if (homeSrc == "folder") "请先配置桌面预设文件夹" else "请先配置桌面预设文件"
        }
        if (lockUri.isNullOrBlank()) {
            return if (lockSrc == "folder") "请先配置锁屏预设文件夹" else "请先配置锁屏预设文件"
        }
        return null
    }

    private suspend fun applyPresetWallpapers(): String? {
        applySide(
            WallpaperTarget.HOME,
            prefs.presetHomeIsVideo.first(),
            prefs.presetHomeSource.first(),
            prefs.presetHomeUri.first()
        )?.let { return it }
        applySide(
            WallpaperTarget.LOCK,
            prefs.presetLockIsVideo.first(),
            prefs.presetLockSource.first(),
            prefs.presetLockUri.first()
        )?.let { return it }
        return null
    }

    private suspend fun applySide(
        target: WallpaperTarget,
        isVideo: Boolean,
        source: String,
        uri: String?
    ): String? {
        if (uri.isNullOrBlank()) return "预设路径无效"
        val type = if (isVideo) FolderType.VIDEO else FolderType.IMAGE
        val label = if (target == WallpaperTarget.LOCK) "锁屏" else "桌面"
        if (source == "folder") {
            val items = folderRepository.getItemsFromFolder(uri, type)
            val item = shuffleHistoryManager.pickNext(items, uri, type)
                ?: return "${label}预设文件夹为空"
            wallpaperRepository.setWallpaper(item, target)
        } else {
            wallpaperRepository.setWallpaper(
                WallpaperItem(Uri.parse(uri), type, "preset_$label", ""),
                target
            )
        }
        return null
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
