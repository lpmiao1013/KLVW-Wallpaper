package com.klvw.wallpaper.scheme

import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.model.ScheduleGroup
import com.klvw.wallpaper.preset.PresetManager
import com.klvw.wallpaper.schedule.ScheduleMath
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Four independent stores; pick by button state only (never merge stores):
 * 1. Preset   — preset* keys, when [SettingsPreferences.presetEnabled]
 * 2. Schedule — schedule groups/rules, when schedule on and preset off
 * 3. Tabbar   — per home/lock × image/video “指定” folders
 * 4. Default  — per home/lock × image/video defaults
 */
enum class WallpaperScheme {
    PRESET,
    SCHEDULE,
    TABBAR,
    DEFAULT
}

data class ResolvedFolder(
    val uri: String,
    val type: FolderType,
    val scheme: WallpaperScheme
)

@Singleton
class WallpaperSchemeResolver @Inject constructor(
    private val prefs: SettingsPreferences,
    private val presetManager: PresetManager
) {
    /** Which layer wins right now (buttons only). */
    suspend fun currentScheme(): WallpaperScheme = when {
        prefs.presetEnabled.first() -> WallpaperScheme.PRESET
        prefs.scheduleEnabled.first() -> WallpaperScheme.SCHEDULE
        hasAnyTabbar() -> WallpaperScheme.TABBAR
        else -> WallpaperScheme.DEFAULT
    }

    /**
     * Resolve folder for [target]+[type] from the winning scheme only.
     * No cross-store fallthrough inside a higher scheme (e.g. preset file → null).
     * Tabbar misses fall through to Default only when scheme is Tabbar/Default path
     * (i.e. preset & schedule both off).
     */
    suspend fun resolveFolder(target: WallpaperTarget, type: FolderType): ResolvedFolder? {
        if (target != WallpaperTarget.HOME && target != WallpaperTarget.LOCK) return null

        if (prefs.presetEnabled.first()) {
            val uri = presetManager.resolveFolderUri(target, type) ?: return null
            return ResolvedFolder(uri, type, WallpaperScheme.PRESET)
        }

        if (prefs.scheduleEnabled.first()) {
            val groupId = prefs.scheduleActiveGroupId.first() ?: return null
            val group = ScheduleGroup.listFromJson(prefs.scheduleGroupsJson.first())
                .firstOrNull { it.id == groupId } ?: return null
            val rule = ScheduleMath.hit(group.rules, target, ScheduleMath.minuteOfDay()) ?: return null
            if (rule.media != type) return null
            return ResolvedFolder(rule.folderUri, type, WallpaperScheme.SCHEDULE)
        }

        val tabbar = prefs.activeFolderUri(target, type).first()
        if (!tabbar.isNullOrBlank()) {
            return ResolvedFolder(tabbar, type, WallpaperScheme.TABBAR)
        }

        val def = prefs.defaultFolderUri(target, type).first() ?: return null
        return ResolvedFolder(def, type, WallpaperScheme.DEFAULT)
    }

    suspend fun resolveFolderUri(target: WallpaperTarget, type: FolderType): String? =
        resolveFolder(target, type)?.uri

    private suspend fun hasAnyTabbar(): Boolean {
        for (t in listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)) {
            for (m in listOf(FolderType.IMAGE, FolderType.VIDEO)) {
                if (!prefs.activeFolderUri(t, m).first().isNullOrBlank()) return true
            }
        }
        return false
    }
}
