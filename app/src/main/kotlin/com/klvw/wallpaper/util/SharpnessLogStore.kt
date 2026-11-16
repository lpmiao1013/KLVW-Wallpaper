package com.klvw.wallpaper.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class SharpnessLogEntry(
    val id: Long,
    val timeMs: Long,
    val title: String,
    val lines: List<String>,
    val mode: String = ""
) {
    val timeLabel: String
        get() = TIME_FMT.format(Date(timeMs))

    companion object {
        private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}

/**
 * In-process ring buffer of video sharpness diagnostics for the settings UI.
 * Wallpaper engine and app UI share the same process, so entries are visible live.
 */
@Singleton
class SharpnessLogStore @Inject constructor() {
    private val seq = AtomicLong(0L)
    private val _entries = MutableStateFlow<List<SharpnessLogEntry>>(emptyList())
    val entries: StateFlow<List<SharpnessLogEntry>> = _entries.asStateFlow()

    fun append(title: String, lines: List<String>, mode: String = "") {
        val entry = SharpnessLogEntry(
            id = seq.incrementAndGet(),
            timeMs = System.currentTimeMillis(),
            title = title,
            lines = lines.filter { it.isNotBlank() },
            mode = mode
        )
        _entries.update { cur ->
            (listOf(entry) + cur).take(MAX_ENTRIES)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        private const val MAX_ENTRIES = 80
    }
}
