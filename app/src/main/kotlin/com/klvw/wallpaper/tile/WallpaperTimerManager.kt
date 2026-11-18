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

/**
 * Screen-level timers (`home` / `lock`). Media type is resolved at fire time from
 * [SettingsPreferences.homeIsVideo] / [SettingsPreferences.lockIsVideo].
 */
@Singleton
class WallpaperTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _paused = MutableStateFlow(
        mapOf(
            KEY_HOME to false,
            KEY_LOCK to false
        )
    )
    val paused: StateFlow<Map<String, Boolean>> = _paused.asStateFlow()

    private val _nextFireTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val nextFireTimes: StateFlow<Map<String, Long>> = _nextFireTimes.asStateFlow()

    private val _pausedRemainingMs = MutableStateFlow<Map<String, Long>>(emptyMap())

    init {
        scope.launch {
            prefs.reconcileImageVideoMutex()
            cancelLegacyMediaAlarms()
            val pausedKeys = prefs.generalPausedTimers.first().map { normalizeKey(it) }.toSet()
            if (pausedKeys.isNotEmpty()) {
                _paused.update { current -> current + pausedKeys.associateWith { true } }
            }
            val remainingTimes = prefs.polPausedFireTimes.first()
                .mapKeys { (k, _) -> normalizeKey(k) }
            if (remainingTimes.isNotEmpty()) {
                _pausedRemainingMs.update { current -> current + remainingTimes }
            }
            launchTimer(KEY_HOME)
            launchTimer(KEY_LOCK)
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
                awaitCancellation()
            }
        }
    }

    fun scheduleAlarm(key: String, intervalMin: Int) {
        val k = normalizeKey(key)
        _pausedRemainingMs.update { it - k }
        val fireAt = System.currentTimeMillis() + intervalMin * 60_000L
        setWakeupAlarm(fireAt, buildPendingIntent(k))
        _nextFireTimes.update { it + (k to fireAt) }
    }

    private fun scheduleAlarmAt(key: String, fireAt: Long) {
        setWakeupAlarm(fireAt, buildPendingIntent(key))
        _nextFireTimes.update { it + (key to fireAt) }
    }

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
        val k = normalizeKey(key)
        context.getSystemService(AlarmManager::class.java).cancel(buildPendingIntent(k))
        _nextFireTimes.update { it - k }
    }

    private fun buildPendingIntent(key: String): PendingIntent {
        val requestCode = when (key) {
            KEY_HOME -> 3011
            KEY_LOCK -> 3012
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

    /** Drop old image/video alarm request codes so they cannot fire after migration. */
    private fun cancelLegacyMediaAlarms() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(3001, 3002, 3003, 3004).forEach { code ->
            val pi = PendingIntent.getBroadcast(
                context, code,
                Intent(context, WallpaperTimerReceiver::class.java).apply {
                    action = ACTION_TIMER_FIRE
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pi != null) am.cancel(pi)
        }
    }

    fun pause(key: String) {
        val k = normalizeKey(key)
        _nextFireTimes.value[k]?.let { ft ->
            val remaining = ft - System.currentTimeMillis()
            if (remaining > 0) _pausedRemainingMs.update { it + (k to remaining) }
        }
        cancelAlarm(k)
        _paused.update { it + (k to true) }
        scope.launch(Dispatchers.IO) { prefs.addGeneralPausedTimer(k) }
    }

    fun resume(key: String) {
        val k = normalizeKey(key)
        _paused.update { it + (k to false) }
        scope.launch(Dispatchers.IO) { prefs.removeGeneralPausedTimer(k) }
    }

    fun resumeWithRemainingMs(key: String, savedRemainingMs: Long?) {
        val k = normalizeKey(key)
        if (savedRemainingMs != null) {
            _pausedRemainingMs.update { current ->
                if (current.containsKey(k)) current else current + (k to savedRemainingMs)
            }
        }
        if (_paused.value[k] == false) {
            _paused.update { it + (k to true) }
        }
        _paused.update { it + (k to false) }
        scope.launch(Dispatchers.IO) { prefs.removeGeneralPausedTimer(k) }
    }

    private fun enabledFlow(key: String): Flow<Boolean> = when (key) {
        KEY_HOME -> prefs.homeTimerEnabled
        KEY_LOCK -> prefs.lockTimerEnabled
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    private fun intervalFlow(key: String): Flow<Int> = when (key) {
        KEY_HOME -> prefs.homeTimerIntervalMin
        KEY_LOCK -> prefs.lockTimerIntervalMin
        else -> throw IllegalArgumentException("Unknown timer key: $key")
    }

    companion object {
        const val ACTION_TIMER_FIRE = "com.klvw.wallpaper.TIMER_FIRE"
        const val EXTRA_TIMER_KEY = "timer_key"
        const val KEY_HOME = "home"
        const val KEY_LOCK = "lock"

        fun normalizeKey(key: String): String = when (key) {
            "home", "home_image", "home_video" -> KEY_HOME
            "lock", "lock_image", "lock_video" -> KEY_LOCK
            else -> key
        }
    }
}
