package com.klvw.wallpaper.schedule

import com.klvw.wallpaper.data.model.ScheduleRule
import com.klvw.wallpaper.data.model.WallpaperTarget
import java.util.Calendar

/**
 * Pure daily schedule math: inclusive minute windows, optional midnight wrap,
 * overlap + full-day coverage checks.
 */
object ScheduleMath {

    const val MINUTES_PER_DAY = 1440

    fun minuteOfDay(wallClockMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = wallClockMs
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun formatMinute(minute: Int): String {
        val m = minute.coerceIn(0, 1439)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun parseMinute(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    /** Inclusive window; [start] > [end] means wraps across midnight. */
    fun containsMinute(minute: Int, start: Int, end: Int): Boolean {
        val m = minute.coerceIn(0, 1439)
        return if (start <= end) m in start..end else m >= start || m <= end
    }

    fun ruleContains(rule: ScheduleRule, minute: Int): Boolean =
        containsMinute(minute, rule.startMinute, rule.endMinute)

    fun hit(rules: List<ScheduleRule>, target: WallpaperTarget, minute: Int): ScheduleRule? =
        rules.firstOrNull { it.target == target && ruleContains(it, minute) }

    /**
     * True when the two inclusive windows share any minute.
     * Same-target image vs video also uses this (any overlap is forbidden).
     */
    fun windowsOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        for (m in 0 until MINUTES_PER_DAY) {
            if (containsMinute(m, aStart, aEnd) && containsMinute(m, bStart, bEnd)) return true
        }
        return false
    }

    fun rulesOverlap(a: ScheduleRule, b: ScheduleRule): Boolean {
        if (a.target != b.target) return false
        return windowsOverlap(a.startMinute, a.endMinute, b.startMinute, b.endMinute)
    }

    /**
     * Validates one group before save / enable.
     * - Every rule needs a non-blank folder
     * - Same target: no overlapping windows (image/video alike)
     * - Home and lock must each cover all 1440 minutes exactly once
     */
    fun validateGroup(rules: List<ScheduleRule>): String? {
        if (rules.isEmpty()) return "请至少配置桌面与锁屏的时段规则"
        rules.forEachIndexed { i, r ->
            if (r.folderUri.isBlank()) return "第 ${i + 1} 条规则未选择文件夹"
            if (r.startMinute !in 0..1439 || r.endMinute !in 0..1439) {
                return "第 ${i + 1} 条规则时间无效"
            }
            if (r.target != WallpaperTarget.HOME && r.target != WallpaperTarget.LOCK) {
                return "第 ${i + 1} 条规则目标无效"
            }
            if (r.media != com.klvw.wallpaper.data.model.FolderType.IMAGE &&
                r.media != com.klvw.wallpaper.data.model.FolderType.VIDEO
            ) {
                return "第 ${i + 1} 条规则类型无效"
            }
        }
        for (i in rules.indices) {
            for (j in i + 1 until rules.size) {
                if (rulesOverlap(rules[i], rules[j])) {
                    val t = if (rules[i].target == WallpaperTarget.HOME) "桌面" else "锁屏"
                    return "${t}时段存在交集：${formatMinute(rules[i].startMinute)}–${formatMinute(rules[i].endMinute)} 与 ${formatMinute(rules[j].startMinute)}–${formatMinute(rules[j].endMinute)}"
                }
            }
        }
        for (target in listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)) {
            val cover = BooleanArray(MINUTES_PER_DAY)
            rules.filter { it.target == target }.forEach { rule ->
                for (m in 0 until MINUTES_PER_DAY) {
                    if (ruleContains(rule, m)) cover[m] = true
                }
            }
            val missing = cover.indexOfFirst { !it }
            if (missing >= 0) {
                val label = if (target == WallpaperTarget.HOME) "桌面" else "锁屏"
                return "${label}未铺满 24 小时（缺 ${formatMinute(missing)} 起）"
            }
        }
        return null
    }

    /**
     * Next wall-clock instant after [nowMs] where the active rule for [target] changes.
     */
    fun nextBoundaryEpochMs(
        rules: List<ScheduleRule>,
        target: WallpaperTarget,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        val targetRules = rules.filter { it.target == target }
        if (targetRules.isEmpty()) return null
        val nowMinute = minuteOfDay(nowMs)
        val currentId = hit(targetRules, target, nowMinute)?.id

        val boundaryMinutes = (
            targetRules.map { it.startMinute } +
                targetRules.map { (it.endMinute + 1) % MINUTES_PER_DAY }
            ).distinct()

        var best: Long? = null
        for (m in boundaryMinutes) {
            val at = nextOccurrenceMs(nowMs, m) ?: continue
            val thenId = hit(targetRules, target, m)?.id
            if (thenId != currentId) {
                if (best == null || at < best) best = at
            }
        }
        return best ?: nextOccurrenceMs(nowMs, (nowMinute + 1) % MINUTES_PER_DAY)
    }

    /** Next time-of-day [minute] strictly after [nowMs]. */
    fun nextOccurrenceMs(nowMs: Long, minute: Int): Long? {
        if (minute !in 0..1439) return null
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
