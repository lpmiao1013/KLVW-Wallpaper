package com.klvw.wallpaper.service.renderer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared start/resume positions per video URI.
 *
 * - Preview 「设为起点」 writes here with [fromUser] = true (sticky until applied)
 * - Wallpaper [VideoRenderer] reads on load and updates on pause/release
 *   so the same node stays the continuation point — but must not overwrite a
 *   freshly user-pinned start before the engine has sought to it
 */
object VideoPlaybackPositions {
    const val ACTION_USER_START = "com.klvw.wallpaper.ACTION_VIDEO_USER_START"
    const val EXTRA_URI = "uri"
    const val EXTRA_POSITION_MS = "position_ms"

    private const val PREFS = "video_playback_positions"
    private const val MIN_RESUME_MS = 1_000L
    private const val END_MARGIN_MS = 1_500L

    private val memory = ConcurrentHashMap<String, Long>()
    /** URIs whose position was set from the preview UI; renderer must not clobber until applied. */
    private val userSticky = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
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
     * @param fromUser true when set from preview 「设为起点」— pins until [markUserStartApplied]
     */
    fun set(uriKey: String, positionMs: Long, durationMs: Long = 0L, fromUser: Boolean = false) {
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
        if (fromUser) {
            userSticky.add(uriKey)
            val ctx = appContext
            if (ctx != null) {
                ctx.sendBroadcast(
                    Intent(ACTION_USER_START)
                        .setPackage(ctx.packageName)
                        .putExtra(EXTRA_URI, uriKey)
                        .putExtra(EXTRA_POSITION_MS, positionMs)
                )
            }
        }
    }

    /**
     * Renderer pause/release persistence. Skips URIs that still have an unapplied user start
     * (fixes schedule/rule mode: live wallpaper was overwriting the preview pin immediately).
     */
    fun setFromRenderer(uriKey: String, positionMs: Long, durationMs: Long = 0L) {
        if (userSticky.contains(uriKey)) return
        set(uriKey, positionMs, durationMs, fromUser = false)
    }

    fun markUserStartApplied(uriKey: String) {
        userSticky.remove(uriKey)
    }

    fun clear(uriKey: String) {
        memory.remove(uriKey)
        userSticky.remove(uriKey)
        prefs?.edit()?.remove(uriKey)?.apply()
    }
}
