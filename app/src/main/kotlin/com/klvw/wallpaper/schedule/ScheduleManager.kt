package com.klvw.wallpaper.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.ScheduleGroup
import com.klvw.wallpaper.data.model.ScheduleRule
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Daily time-window schedule: at each boundary, pickNext from the window's folder
 * and realign image/video mutex for that screen. Independent of [SettingsPreferences.appEnabled].
 */
@Singleton
class ScheduleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPreferences,
    private val folderRepository: FolderRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val shuffleHistoryManager: ShuffleHistoryManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            var wasEnabled = false
            var first = true
            combine(
                prefs.scheduleEnabled,
                prefs.scheduleActiveGroupId,
                prefs.scheduleGroupsJson
            ) { enabled, activeId, json -> Triple(enabled, activeId, json) }
                .distinctUntilChanged()
                .collect { (enabled, _, _) ->
                    if (enabled) {
                        applyWindows(pick = true)
                        rescheduleAlarms()
                    } else {
                        cancelAlarms()
                        if (!first && wasEnabled) applyDefaultsAfterDisable()
                    }
                    wasEnabled = enabled
                    first = false
                }
        }
    }

    suspend fun groups(): List<ScheduleGroup> =
        ScheduleGroup.listFromJson(prefs.scheduleGroupsJson.first())

    suspend fun activeGroup(): ScheduleGroup? {
        val id = prefs.scheduleActiveGroupId.first() ?: return null
        return groups().firstOrNull { it.id == id }
    }

    suspend fun isEnabled(): Boolean = prefs.scheduleEnabled.first()

    suspend fun currentRule(target: WallpaperTarget): ScheduleRule? {
        if (!isEnabled()) return null
        val group = activeGroup() ?: return null
        return ScheduleMath.hit(group.rules, target, ScheduleMath.minuteOfDay())
    }

    /**
     * Folder for timers / double-tap / picker.
     * Schedule on → current window folder only when [type] matches; else null.
     * Schedule off → default folder for [target]+[type].
     */
    suspend fun resolveFolderUri(target: WallpaperTarget, type: FolderType): String? {
        if (target != WallpaperTarget.HOME && target != WallpaperTarget.LOCK) return null
        if (isEnabled()) {
            val rule = currentRule(target) ?: return null
            return if (rule.media == type) rule.folderUri else null
        }
        return defaultFolder(target, type)
    }

    private suspend fun defaultFolder(target: WallpaperTarget, type: FolderType): String? = when {
        target == WallpaperTarget.HOME && type == FolderType.IMAGE ->
            prefs.defaultHomeImageFolderUri.first()
        target == WallpaperTarget.HOME && type == FolderType.VIDEO ->
            prefs.defaultHomeVideoFolderUri.first()
        target == WallpaperTarget.LOCK && type == FolderType.IMAGE ->
            prefs.defaultLockImageFolderUri.first()
        target == WallpaperTarget.LOCK && type == FolderType.VIDEO ->
            prefs.defaultLockVideoFolderUri.first()
        else -> null
    }

    /**
     * Enable only when active group validates; returns error toast text or null on success.
     */
    suspend fun setEnabled(enabled: Boolean): String? {
        if (enabled) {
            val group = activeGroup()
                ?: return "请先选择并保存一个完整分组"
            ScheduleMath.validateGroup(group.rules)?.let { return it }
            prefs.setScheduleEnabled(true)
        } else {
            prefs.setScheduleEnabled(false)
        }
        return null
    }

    suspend fun setActiveGroupId(id: String?): String? {
        if (id != null) {
            val group = groups().firstOrNull { it.id == id } ?: return "分组不存在"
            ScheduleMath.validateGroup(group.rules)?.let { return it }
        }
        prefs.setScheduleActiveGroupId(id)
        return null
    }

    /** Persist groups; every group must be a full valid 24h plan. */
    suspend fun saveGroups(groups: List<ScheduleGroup>): String? {
        if (isEnabled()) {
            val activeId = prefs.scheduleActiveGroupId.first()
            if (activeId != null && groups.none { it.id == activeId }) {
                return "启用中不能删除当前分组"
            }
        }
        groups.forEach { g ->
            ScheduleMath.validateGroup(g.rules)?.let { err ->
                return "「${g.name}」$err"
            }
        }
        prefs.setScheduleGroupsJson(with(ScheduleGroup) { groups.toJsonString() })
        return null
    }

    /** Alarm / boot / TIME_CHANGED entry. */
    suspend fun onBoundaryFired() {
        if (!isEnabled()) return
        applyWindows(pick = true)
        rescheduleAlarms()
    }

    suspend fun applyWindows(pick: Boolean) {
        if (!isEnabled()) return
        val group = activeGroup() ?: return
        for (target in listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)) {
            val rule = ScheduleMath.hit(group.rules, target, ScheduleMath.minuteOfDay()) ?: continue
            alignMutex(target, rule.media)
            if (pick) pickAndSet(rule)
        }
    }

    private suspend fun applyDefaultsAfterDisable() {
        for (target in listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)) {
            val isVideo = when (target) {
                WallpaperTarget.HOME -> prefs.homeIsVideo.first()
                WallpaperTarget.LOCK -> prefs.lockIsVideo.first()
                else -> false
            }
            val type = if (isVideo) FolderType.VIDEO else FolderType.IMAGE
            val uri = defaultFolder(target, type) ?: continue
            pickAndSet(
                ScheduleRule(
                    target = target,
                    media = type,
                    folderUri = uri,
                    startMinute = 0,
                    endMinute = 1439
                )
            )
        }
    }

    private suspend fun alignMutex(target: WallpaperTarget, media: FolderType) {
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
        if (media == FolderType.IMAGE) {
            prefs.setDisplayControl(imageKey, true)
            if (anyTimer) prefs.setTimerEnabled(imageKey, true)
        } else {
            prefs.setDisplayControl(videoKey, true)
            if (anyTimer) prefs.setTimerEnabled(videoKey, true)
        }
    }

    private suspend fun pickAndSet(rule: ScheduleRule) {
        val items = folderRepository.getItemsFromFolder(rule.folderUri, rule.media)
        val item = shuffleHistoryManager.pickNext(items, rule.folderUri, rule.media) ?: return
        wallpaperRepository.setWallpaper(item, rule.target)
    }

    suspend fun rescheduleAlarms() {
        cancelAlarms()
        if (!isEnabled()) return
        val group = activeGroup() ?: return
        val now = System.currentTimeMillis()
        var soonest: Long? = null
        for (target in listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)) {
            val t = ScheduleMath.nextBoundaryEpochMs(group.rules, target, now) ?: continue
            if (soonest == null || t < soonest) soonest = t
        }
        if (soonest != null) setAlarm(soonest)
    }

    private fun setAlarm(fireAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent()
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
        } catch (_: SecurityException) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            } catch (_: Exception) {
            }
        }
    }

    private fun cancelAlarms() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_BOUNDARY)
        return PendingIntent.getBroadcast(
            context, REQ_BOUNDARY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_BOUNDARY = "com.klvw.wallpaper.SCHEDULE_BOUNDARY"
        private const val REQ_BOUNDARY = 71001
    }
}
