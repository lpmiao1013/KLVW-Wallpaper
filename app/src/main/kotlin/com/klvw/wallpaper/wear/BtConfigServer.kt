package com.klvw.wallpaper.wear

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.tile.WallpaperTimerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

private const val TAG = "BtConfigServer"

class BtConfigServer(
    private val context: Context,
    private val prefs: SettingsPreferences,
    private val timerManager: WallpaperTimerManager,
    private val scope: CoroutineScope,
    private val executeHandler: suspend (ByteArray) -> Unit
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    }

    private var serverSocket: BluetoothServerSocket? = null

    fun start(screenOn: StateFlow<Boolean>) {
        // Close the server socket whenever the screen turns off so the BT radio
        // can enter its low-power state during doze / idle.
        scope.launch(Dispatchers.IO) {
            screenOn.collect { isOn ->
                if (!isOn) {
                    serverSocket?.close()
                    serverSocket = null
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            var retryDelay = 2_000L
            while (isActive) {
                if (!screenOn.value) {
                    delay(2_000L)
                    retryDelay = 2_000L
                    continue
                }

                val adapter = try {
                    context.getSystemService(BluetoothManager::class.java)?.adapter
                } catch (_: Exception) { null }

                if (adapter == null || !adapter.isEnabled) {
                    delay(retryDelay)
                    retryDelay = minOf(retryDelay * 2, 30_000L)
                    continue
                }

                try {
                    @Suppress("MissingPermission")
                    val ss = adapter.listenUsingInsecureRfcommWithServiceRecord("KLVWConfig", SERVICE_UUID)
                    serverSocket = ss
                    retryDelay = 2_000L
                    while (isActive && screenOn.value) {
                        val socket = ss.accept()
                        scope.launch(Dispatchers.IO) { handleClient(socket) }
                    }
                    ss.close()
                    serverSocket = null
                } catch (e: Exception) {
                    serverSocket = null
                    if (isActive && screenOn.value) {
                        Log.e(TAG, "Server error: ${e.message}, retry in ${retryDelay}ms")
                        delay(retryDelay)
                        retryDelay = minOf(retryDelay * 2, 30_000L)
                    }
                }
            }
        }
    }

    private suspend fun handleClient(socket: BluetoothSocket) {
        try {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.inputStream, Charsets.UTF_8))
                val writer = PrintWriter(it.outputStream, true, Charsets.UTF_8)
                val line = reader.readLine() ?: return
                when {
                    line == "GET_CONFIG" -> {
                        val json = prefs.klvwWatchItemsJson.first()
                        writer.println(json.replace('\n', ' ').replace('\r', ' '))
                    }
                    line == "GET_TIMERS" -> {
                        writer.println(buildTimerJson().replace('\n', ' ').replace('\r', ' '))
                    }
                    line.startsWith("TIMER_ACTION:") -> {
                        val parts = line.removePrefix("TIMER_ACTION:").split(":")
                        if (parts.size >= 2) {
                            handleTimerAction(parts[0], parts[1])
                            writer.println(buildTimerJson().replace('\n', ' ').replace('\r', ' '))
                        } else {
                            writer.println("ERROR")
                        }
                    }
                    line.startsWith("EXEC:") -> {
                        executeHandler(line.removePrefix("EXEC:").toByteArray(Charsets.UTF_8))
                        writer.println("OK")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handling error: ${e.message}")
        }
    }

    private suspend fun buildTimerJson(): String {
        val keys = listOf(
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_HOME,
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_LOCK
        )
        val labels = mapOf(
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_HOME to "Home",
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_LOCK to "Lock"
        )
        val enabledMap = mapOf(
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_HOME to prefs.homeTimerEnabled.first(),
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_LOCK to prefs.lockTimerEnabled.first()
        )
        val intervalMap = mapOf(
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_HOME to prefs.homeTimerIntervalMin.first(),
            com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_LOCK to prefs.lockTimerIntervalMin.first()
        )
        val paused = timerManager.paused.value
        val fireTimes = timerManager.nextFireTimes.value

        val arr = JSONArray()
        for (key in keys) {
            if (enabledMap[key] != true) continue
            arr.put(JSONObject().apply {
                put("key", key)
                put("label", labels[key])
                put("intervalMin", intervalMap[key] ?: 60)
                put("paused", paused[key] == true)
                put("nextFireMs", if (paused[key] == true) 0L else fireTimes[key] ?: 0L)
            })
        }
        return arr.toString()
    }

    private suspend fun handleTimerAction(key: String, action: String) {
        val screen = com.klvw.wallpaper.tile.WallpaperTimerManager.normalizeKey(key)
        when (action) {
            "pause"  -> timerManager.pause(screen)
            "resume" -> timerManager.resume(screen)
            "reset"  -> {
                val intervalMin = when (screen) {
                    com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_HOME ->
                        prefs.homeTimerIntervalMin.first()
                    com.klvw.wallpaper.tile.WallpaperTimerManager.KEY_LOCK ->
                        prefs.lockTimerIntervalMin.first()
                    else -> return
                }
                timerManager.scheduleAlarm(screen, intervalMin)
            }
        }
    }
}
