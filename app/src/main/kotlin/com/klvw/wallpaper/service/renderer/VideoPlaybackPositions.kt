package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared start/resume positions per video URI.
 *
 * - Preview 「设为起点」 writes here
 * - Wallpaper [VideoRenderer] reads on load and updates on pause/release
 *   so the same node stays the continuation point
 */
object VideoPlaybackPositions {
    private const val PREFS = "video_playback_positions"
    private const val MIN_RESUME_MS = 1_000L
    private const val END_MARGIN_MS = 1_500L

    private val memory = ConcurrentHashMap<String, Long>()
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            val ms = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            if (ms != null && ms > 0L) memory[key] = ms
        }
    }

    fun get(uriKey: String): Long = memory[uriKey] ?: 0L

    /**
     * @param durationMs 0 if unknown; near-end positions are cleared so next play starts over
     */
    fun set(uriKey: String, positionMs: Long, durationMs: Long = 0L) {
        if (positionMs < MIN_RESUME_MS) {
            clear(uriKey)
            return
        }
        if (durationMs > 0L && positionMs >= durationMs - END_MARGIN_MS) {
            clear(uriKey)
            return
        }
        memory[uriKey] = positionMs
        prefs?.edit()?.putLong(uriKey, positionMs)?.apply()
    }

    fun clear(uriKey: String) {
        memory.remove(uriKey)
        prefs?.edit()?.remove(uriKey)?.apply()
    }
}
