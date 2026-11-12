package com.klvw.wallpaper.tile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _paused = MutableStateFlow(
        mapOf(
            "home_image" to false,
            "home_video" to false,
            "lock_image" to false,
            "lock_video" to false
        )
    )
    val paused: StateFlow<Map<String, Boolean>> = _paused.asStateFlow()

    private val _nextFireTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val nextFireTimes: StateFlow<Map<String, Long>> = _nextFireTimes.asStateFlow()

    // Remaining milliseconds saved when a timer is paused; consumed on resume to restore the countdown.
    // Storing remaining duration (not absolute timestamp) so the countdown is frozen during the lock period:
    // on resume, fire time = now + remaining, not the original absolute time which keeps decreasing.
    // Cleared by scheduleAlarm() so explicit resets (VALUE_RESET) are unaffected.
    private val _pausedRemainingMs = MutableStateFlow<Map<String, Long>>(emptyMap())

    init {
        // Async: read persisted paused state from DataStore before starting timers so that
        // paused timers (both P.o.L and manual/notification) don't reschedule after process restart.
        scope.launch {
            val pausedKeys = prefs.generalPausedTimers.first()
            if (pausedKeys.isNotEmpty()) {
                _paused.update { current -> current + pausedKeys.associateWith { true } }
            }
            val remainingTimes = prefs.polPausedFireTimes.first()
            if (remainingTimes.isNotEmpty()) {
                _pausedRemainingMs.update { current -> current + remainingTimes }
            }
            launchTimer("home_image")
            launchTimer("home_video")
            launchTimer("lock_image")
            launchTimer("lock_video")
        }
    }

    private fun launchTimer(key: String) {
        scope.launch {
            combine(
                enabledFlow(key),
                intervalFlow(key),
                _paused.map { it[key] == true }.distinctUntilChanged()
            ) { enabled, intervalMin, paused ->
                Triple(enabled, intervalMin, paused)
            }.collectLatest { (enabled, intervalMin, paused) ->
                if (!enabled || paused) {
                    cancelAlarm(key)
                    return@collectLatest
                }
                val savedRemaining = _pausedRemainingMs.value[key]
                if (savedRemaining != null) {
                    _pausedRemainingMs.update { it - key }
                    if (savedRemaining > 0) {
                        scheduleAlarmAt(key, System.currentTimeMillis() + savedRemaining)
                        awaitCancellation()
                        return@collectLatest
                    }
                }
                scheduleAlarm(key, intervalMin)
                // Suspend until collectLatest cancels this block on next pref change
                awaitCancellation()
            }
        }
    }

    fun scheduleAlarm(key: String, intervalMin: Int) {
        _pausedRemainingMs.update { it - key }
        val fireAt = System.currentTimeMillis() + intervalMin * 60_000L
        setWakeupAlarm(fireAt, buildPendingIntent(key))
        _nextFireTimes.update { it + (key to fireAt) }
    }

    private fun scheduleAlarmAt(key: String, fireAt: Long) {
        setWakeupAlarm(fireAt, buildPendingIntent(key))
        _nextFireTimes.update { it + (key to fireAt) }
    }

    // Falls back when SCHEDULE_EXACT_ALARM is missing/revoked (otherwise SecurityException kills the process).
    private fun setWakeupAlarm(fireAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    fun cancelAlarm(key: String) {
        context.getSystemService(AlarmManager::class.java).cancel(buildPendingIntent(key))
        _nextFireTimes.update { it - key }
    }

    private fun buildPendingIntent(key: String): PendingIntent {
        val requestCode = when (key) {
            "home_image" -> 3001
            "home_video" -> 3002
            "lock_image" -> 3003
            "lock_video" -> 3004
            else -> throw IllegalArgumentException("Unknown timer key: $key")
        }
        return PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, WallpaperTimerReceiver::class.java).apply {
                action = ACTION_TIMER_FIRE
                putExtra(EXTRA_TIMER_KEY, key)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun pause(key: String) {
        _nextFireTimes.value[key]?.let { ft ->
            // Save remaining duration, not absolute timestamp, so the countdown is frozen
            // during the lock period. On resume: fire time = now + remaining.
            val remaining = ft - System.currentTimeMillis()
            if (remaining > 0) _pausedRemainingMs.update { it + (key to remaining) }
        }
        // Cancel the alarm synchronously here, not via launchTimer's async StateFlow response.
        // launchTimer runs on the Default dispatcher and is only scheduled after pause() returns —
        // by then the wakelock may be released and the CPU asleep, letting the alarm fire anyway.
        cancelAlarm(key)
        _paused.update { it + (key to true) }
        // Persist so the paused state survives process restart (Samsung kills background process).
        scope.launch(Dispatchers.IO) { prefs.addGeneralPausedTimer(key) }
    }

    fun resume(key: String) {
        _paused.update { it + (key to false) }
        scope.launch(Dispatchers.IO) { prefs.removeGeneralPausedTimer(key) }
    }

    // Resume after P.o.L, restoring the remaining duration. Works for both:
    //  - Process alive: _paused[key] is true, launchTimer is waiting → just set false so it picks up _pausedRemainingMs.
    //  - Process restart: _paused[key] defaulted to false and launchTimer already called scheduleAlarm → toggle
    //    true→false so launchTimer cancels the wrong alarm and reschedules with the correct remaining time.
    //    (With async init this path is now rare since init restores _paused from DataStore first.)
    fun resumeWithRemainingMs(key: String, savedRemainingMs: Long?) {
        if (savedRemainingMs != null) {
            _pausedRemainingMs.update { current ->
                if (current.containsKey(key)) current else current + (key to savedRemainingMs)
            }
        }
        if (_paused.value[key] == false) {
            // Process-restart path: force a pause→resume cycle so launchTimer re-runs.
            _paused.update { it + (key to true) }
        }
        _paused.update { it + (key to false) }
        scope.launch(Dispatchers.IO) { prefs.removeGeneralPausedTimer(key) }
    }

    private fun enabledFlow(key: String): Flow<Boolean> = when (key) {
        "home_image" -> prefs.homeImageTimerEnabled
        "home_video" -> prefs.homeVideoTimerEnabled
        "lock_image" -> prefs.lockImageTimerEnabled
        "lock_video" -> prefs.lockVideoTimerEnabled
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    private fun intervalFlow(key: String): Flow<Int> = when (key) {
        "home_image" -> prefs.homeImageTimerIntervalMin
        "home_video" -> prefs.homeVideoTimerIntervalMin
        "lock_image" -> prefs.lockImageTimerIntervalMin
        "lock_video" -> prefs.lockVideoTimerIntervalMin
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    companion object {
        const val ACTION_TIMER_FIRE = "com.klvw.wallpaper.TIMER_FIRE"
        const val EXTRA_TIMER_KEY = "timer_key"
    }
}
