package com.klvw.wallpaper.tile

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.klvw.wallpaper.R
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import kotlinx.coroutines.flow.first

object TimerStatusNotificationHelper {

    const val NOTIFICATION_ID = 7001
    const val CHANNEL_ID = "timer_status"

    const val ACTION_TIMER_ACTION = "com.klvw.wallpaper.TIMER_NOTIFICATION_ACTION"
    const val EXTRA_ACTION = "timer_notification_action"
    const val EXTRA_TIMER_KEY = "timer_notification_key"
    const val EXTRA_TIMER_KEYS = "timer_notification_keys"
    const val VALUE_PAUSE = "pause"
    const val VALUE_RESUME = "resume"
    const val VALUE_RESET = "reset"
    /** Fired by the notification body tap — pauses all active timers, or resumes all if all paused. */
    const val VALUE_TOGGLE_ALL = "toggle_all"
    /** Immediately picks and applies the next shuffle-bag wallpaper for the timer's linked folder. */
    const val VALUE_CHANGE_NOW = "change_now"

    fun show(
        context: Context,
        enabledKeys: List<String>,
        pausedMap: Map<String, Boolean>,
        nextFireTimes: Map<String, Long>
    ) {
        val now = System.currentTimeMillis()
        val pausedKeys = enabledKeys.filter { pausedMap[it] == true }
        val activeKeys  = enabledKeys.filter { pausedMap[it] != true }

        // Build per-timer status lines with live remaining time
        val lines = enabledKeys.map { key ->
            val paused = pausedMap[key] == true
            val fireAt = nextFireTimes[key]
            val status = when {
                paused -> "已暂停"
                fireAt != null -> {
                    val remaining = fireAt - now
                    if (remaining > 0) formatRemaining(remaining) else "即将触发…"
                }
                else -> "—"
            }
            "${timerLabel(key)}: $status"
        }

        val allPaused = pausedKeys.size == enabledKeys.size
        val contentText = when {
            enabledKeys.size == 1 -> lines.first()
            allPaused             -> "${pausedKeys.size} timers paused — tap to resume"
            activeKeys.isEmpty()  -> "${pausedKeys.size} timers paused"
            pausedKeys.isEmpty()  -> "${activeKeys.size} timers running — tap to pause"
            else -> "${activeKeys.size} running, ${pausedKeys.size} paused"
        }

        // Body tap: pause all active, or resume all if all are paused
        val contentIntent = PendingIntent.getBroadcast(
            context, 8004,
            Intent(ACTION_TIMER_ACTION, null, context, TimerActionReceiver::class.java).apply {
                putExtra(EXTRA_ACTION, VALUE_TOGGLE_ALL)
                putStringArrayListExtra(EXTRA_TIMER_KEYS, ArrayList(enabledKeys))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("KLVW 定时器")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(false)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .also { builder ->
                // Change Now — pick the next wallpaper immediately for the active timer(s)
                if (activeKeys.isNotEmpty()) {
                    builder.addAction(
                        android.R.drawable.ic_media_next,
                        "立即更换",
                        buildActionIntent(context, VALUE_CHANGE_NOW, activeKeys, reqCode = 8005)
                    )
                }
                // Resume — shown only when there are paused timers
                if (pausedKeys.isNotEmpty()) {
                    builder.addAction(
                        android.R.drawable.ic_media_play,
                        "继续",
                        buildActionIntent(context, VALUE_RESUME, pausedKeys, reqCode = 8003)
                    )
                }
                // Reset — always present
                builder.addAction(
                    android.R.drawable.ic_menu_rotate,
                    "重置",
                    buildActionIntent(context, VALUE_RESET, enabledKeys, reqCode = 8002)
                )
            }
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /**
     * Re-post the notification with current state only if it is already on-screen.
     * Called by the application-scope observer every second and after timer fires.
     */
    suspend fun refreshIfVisible(
        context: Context,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.activeNotifications.none { it.id == NOTIFICATION_ID }) return
        showFromState(context, prefs, timerManager)
    }

    /**
     * Build and post the notification unconditionally from current state.
     * Call this when the notification is already on screen (after any action button press).
     */
    suspend fun showFromState(
        context: Context,
        prefs: SettingsPreferences,
        timerManager: WallpaperTimerManager
    ) {
        val allKeys = listOf("home_image", "home_video", "lock_image", "lock_video")
        val enabledKeys = allKeys.filter { key ->
            when (key) {
                "home_image" -> prefs.homeImageTimerEnabled.first()
                "home_video" -> prefs.homeVideoTimerEnabled.first()
                "lock_image" -> prefs.lockImageTimerEnabled.first()
                "lock_video" -> prefs.lockVideoTimerEnabled.first()
                else -> false
            }
        }
        if (enabledKeys.isEmpty()) return
        if (prefs.timerNotificationOnlyWhenRunning.first()) {
            val allPaused = enabledKeys.all { timerManager.paused.value[it] == true }
            if (allPaused) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                return
            }
        }
        show(context, enabledKeys, timerManager.paused.value, timerManager.nextFireTimes.value)
    }

    private fun buildActionIntent(
        context: Context,
        action: String,
        keys: List<String>,
        reqCode: Int
    ): PendingIntent {
        return if (keys.size == 1) {
            // Single timer — fire broadcast directly to TimerActionReceiver
            PendingIntent.getBroadcast(
                context, reqCode,
                Intent(ACTION_TIMER_ACTION, null, context, TimerActionReceiver::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putExtra(EXTRA_TIMER_KEY, keys.first())
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            // Multiple timers — open picker activity
            PendingIntent.getActivity(
                context, reqCode,
                Intent(context, TimerActionPickerActivity::class.java).apply {
                    putExtra(EXTRA_ACTION, action)
                    putStringArrayListExtra(EXTRA_TIMER_KEYS, ArrayList(keys))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    fun timerLabel(key: String) = when (key) {
        "home_image" -> "桌面图片"
        "home_video" -> "桌面视频"
        "lock_image" -> "锁屏图片"
        "lock_video" -> "锁屏视频"
        else -> key
    }

    fun formatRemaining(ms: Long): String {
        if (ms <= 0) return "现在"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
