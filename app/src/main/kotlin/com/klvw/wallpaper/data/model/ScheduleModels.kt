package com.klvw.wallpaper.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One daily time window that drives a single screen (home or lock). */
data class ScheduleRule(
    val id: String = UUID.randomUUID().toString(),
    /** [WallpaperTarget.HOME] or [WallpaperTarget.LOCK] only. */
    val target: WallpaperTarget,
    val media: FolderType,
    val folderUri: String,
    /** Inclusive minute-of-day 0..1439 (e.g. 08:00 = 480). */
    val startMinute: Int,
    /** Inclusive minute-of-day 0..1439 (e.g. 12:00 = 720). May be < start when wrapping midnight. */
    val endMinute: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("target", target.name)
        put("media", media.name)
        put("folderUri", folderUri)
        put("startMinute", startMinute)
        put("endMinute", endMinute)
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleRule? {
            val target = when (obj.optString("target")) {
                WallpaperTarget.HOME.name -> WallpaperTarget.HOME
                WallpaperTarget.LOCK.name -> WallpaperTarget.LOCK
                else -> return null
            }
            val media = when (obj.optString("media")) {
                FolderType.IMAGE.name -> FolderType.IMAGE
                FolderType.VIDEO.name -> FolderType.VIDEO
                else -> return null
            }
            val folder = obj.optString("folderUri").trim()
            if (folder.isEmpty()) return null
            val start = obj.optInt("startMinute", -1)
            val end = obj.optInt("endMinute", -1)
            if (start !in 0..1439 || end !in 0..1439) return null
            return ScheduleRule(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                target = target,
                media = media,
                folderUri = folder,
                startMinute = start,
                endMinute = end
            )
        }
    }
}

data class ScheduleGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rules: List<ScheduleRule> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("rules", JSONArray().also { arr -> rules.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleGroup {
            val rulesArr = obj.optJSONArray("rules") ?: JSONArray()
            val rules = buildList {
                for (i in 0 until rulesArr.length()) {
                    fromRuleOrNull(rulesArr.optJSONObject(i))?.let { add(it) }
                }
            }
            return ScheduleGroup(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name").ifBlank { "未命名分组" },
                rules = rules
            )
        }

        private fun fromRuleOrNull(obj: JSONObject?): ScheduleRule? =
            obj?.let { ScheduleRule.fromJson(it) }

        fun listFromJson(json: String): List<ScheduleGroup> = try {
            val arr = JSONArray(json.ifBlank { "[]" })
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }

        fun List<ScheduleGroup>.toJsonString(): String =
            JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }.toString()
    }
}
