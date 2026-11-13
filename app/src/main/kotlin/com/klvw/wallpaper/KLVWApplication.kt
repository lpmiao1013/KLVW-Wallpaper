package com.klvw.wallpaper

import android.app.Application
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.aer.AerLockStore
import com.klvw.wallpaper.service.renderer.VideoPlaybackPositions
import com.klvw.wallpaper.schedule.ScheduleManager
import com.klvw.wallpaper.tile.ScreenUnlockReceiver
import com.klvw.wallpaper.tile.TimerStatusNotificationHelper
import com.klvw.wallpaper.tile.WallpaperTimerManager
import com.klvw.wallpaper.wear.BtConfigServer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.json.JSONObject
import javax.inject.Inject

private const val TAG = "KLVWApp"

@HiltAndroidApp
class KLVWApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var timerManager: WallpaperTimerManager
    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager
    /** Eagerly create so schedule alarms re-arm after process start. */
    @Inject lateinit var scheduleManager: ScheduleManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        VideoPlaybackPositions.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("klvw_debug", "KLVW Debug", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("timer_status", "Timer Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows running timer status on screen unlock"
            }
        )
        // USER_PRESENT is sent by the system; NOT_EXPORTED can drop it on some OEMs (unlock rotate dies).
        registerReceiver(
            ScreenUnlockReceiver(),
            IntentFilter(Intent.ACTION_USER_PRESENT),
            Context.RECEIVER_EXPORTED
        )

        val powerManager = getSystemService(PowerManager::class.java)
        val screenOn = MutableStateFlow(powerManager?.isInteractive == true)
        registerReceiver(
            object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                screenOn.value = intent.action == Intent.ACTION_SCREEN_ON
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    // Acquire a wakelock so the coroutine below completes before the CPU sleeps.
                    val wl = powerManager?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "KLVW:ScreenOffWork"
                    )
                    wl?.acquire(5_000L)
                    appScope.launch {
                        try {
                            // Aer auto-unmount on lock
                            if (AerLockStore.isMounted && prefs.aerUnmountOnLock.first()) {
                                AerLockStore.lock(ctx.applicationContext)
                            }
                            // P.o.L — pause timers whose "Pause on Lock" option is enabled
                            val allKeys = listOf("home_image", "home_video", "lock_image", "lock_video")
                            val toPause = allKeys.filter { key ->
                                polEnabled(key) && timerIsEnabled(key) &&
                                timerManager.paused.value[key] != true
                            }
                            if (toPause.isNotEmpty()) {
                                // Capture remaining durations BEFORE pause() cancels alarms and removes _nextFireTimes.
                                // We store remaining ms (not absolute timestamps) so the countdown is frozen during
                                // the lock: on resume fire time = now + remaining, not the original absolute time.
                                val now = System.currentTimeMillis()
                                val remainingTimes = toPause.mapNotNull { key ->
                                    timerManager.nextFireTimes.value[key]?.let { absTime ->
                                        val remaining = absTime - now
                                        if (remaining > 0) key to remaining else null
                                    }
                                }.toMap()
                                toPause.forEach { timerManager.pause(it) }
                                prefs.setPolPausedTimers(toPause.toSet())
                                if (remainingTimes.isNotEmpty()) prefs.setPolPausedFireTimes(remainingTimes)
                            }
                        } finally {
                            if (wl?.isHeld == true) wl.release()
                        }
                    }
                }
            }
        },
            IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            },
            Context.RECEIVER_NOT_EXPORTED
        )

        // Schedule auto-unmount whenever Aer is mounted, based on user pref
        AerLockStore.addListener { isLocked ->
            if (!isLocked) {
                appScope.launch {
                    val minutes = prefs.aerAutoUnmountMinutes.first()
                    if (minutes > 0) {
                        AerLockStore.scheduleAutoUnmount(applicationContext, minutes * 60_000L)
                    }
                }
            }
        }

        // serverActive = false only during Android Doze (screen off 30+ min, device stationary).
        // Screen-off alone is NOT enough — the watch is typically used while the phone is
        // pocketed (screen off). Doze means the device is truly idle and the BT radio can sleep.
        val serverActive = MutableStateFlow(powerManager?.isDeviceIdleMode != true)
        registerReceiver(
            object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                serverActive.value = powerManager?.isDeviceIdleMode != true
            }
            },
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Start Bluetooth RFCOMM server — watch connects directly over classic BT.
        BtConfigServer(this, prefs, timerManager, appScope) { data ->
            handleWatchExecute(data)
        }.start(serverActive)

        appScope.launch {
            val enabledKeysFlow = combine(
                prefs.homeImageTimerEnabled,
                prefs.homeVideoTimerEnabled,
                prefs.lockImageTimerEnabled,
                prefs.lockVideoTimerEnabled
            ) { hi, hv, li, lv ->
                listOfNotNull(
                    "home_image".takeIf { hi },
                    "home_video".takeIf { hv },
                    "lock_image".takeIf { li },
                    "lock_video".takeIf { lv }
                )
            }

            screenOn.flatMapLatest { isOn ->
                if (!isOn) {
                    emptyFlow()
                } else {
                    val ticker = flow { while (true) { emit(Unit); delay(1_000L) } }
                    combine(
                        enabledKeysFlow,
                        timerManager.paused,
                        timerManager.nextFireTimes,
                        ticker
                    ) { enabled, paused, fires, _ ->
                        Triple(enabled, paused, fires)
                    }
                }
            }.collect { triple ->
                val (enabled, paused, fires) = triple
                if (enabled.isEmpty()) return@collect
                val notifMgr = getSystemService(NotificationManager::class.java) ?: return@collect
                if (notifMgr.activeNotifications.none {
                        it.id == TimerStatusNotificationHelper.NOTIFICATION_ID
                    }) return@collect
                TimerStatusNotificationHelper.show(applicationContext, enabled, paused, fires)
            }
        }
    }

    private suspend fun polEnabled(key: String): Boolean = when (key) {
        "home_image" -> prefs.homeImageTimerPauseOnLock.first()
        "home_video" -> prefs.homeVideoTimerPauseOnLock.first()
        "lock_image" -> prefs.lockImageTimerPauseOnLock.first()
        "lock_video" -> prefs.lockVideoTimerPauseOnLock.first()
        else -> false
    }

    private suspend fun timerIsEnabled(key: String): Boolean = when (key) {
        "home_image" -> prefs.homeImageTimerEnabled.first()
        "home_video" -> prefs.homeVideoTimerEnabled.first()
        "lock_image" -> prefs.lockImageTimerEnabled.first()
        "lock_video" -> prefs.lockVideoTimerEnabled.first()
        else -> false
    }

    private suspend fun handleWatchExecute(data: ByteArray) {
        try {
            val obj        = JSONObject(String(data, Charsets.UTF_8))
            val actionType = obj.optString("actionType")
            val target     = obj.optString("target", "home")
            val folderUri  = obj.optString("folderUri", "")

            val wallpaperTarget = when (target) {
                "lock" -> WallpaperTarget.LOCK
                "both" -> WallpaperTarget.BOTH
                else   -> WallpaperTarget.HOME
            }

            when (actionType) {
                "random_image", "random_video" -> {
                    val wantVideo = actionType == "random_video"
                    val screens = when (wallpaperTarget) {
                        WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
                        WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
                        else -> listOf(WallpaperTarget.HOME)
                    }
                    for (screen in screens) {
                        if (scheduleManager.isEnabled()) {
                            val rule = scheduleManager.currentRule(screen) ?: continue
                            val item = shuffleHistoryManager.pickNext(
                                folderRepository.getItemsFromFolder(rule.folderUri, rule.media),
                                rule.folderUri, rule.media
                            ) ?: continue
                            wallpaperRepository.setWallpaper(item, screen)
                        } else {
                            val type = if (wantVideo) FolderType.VIDEO else FolderType.IMAGE
                            val uri = folderUri.ifBlank {
                                when {
                                    screen == WallpaperTarget.LOCK && type == FolderType.IMAGE ->
                                        prefs.defaultLockImageFolderUri.first()
                                    screen == WallpaperTarget.LOCK -> prefs.defaultLockVideoFolderUri.first()
                                    type == FolderType.IMAGE -> prefs.defaultHomeImageFolderUri.first()
                                    else -> prefs.defaultHomeVideoFolderUri.first()
                                }
                            } ?: continue
                            val item = shuffleHistoryManager.pickNext(
                                folderRepository.getItemsFromFolder(uri, type), uri, type
                            ) ?: continue
                            wallpaperRepository.setWallpaper(item, screen)
                        }
                    }
                }
                "restore" -> {
                    prefs.setAppEnabled(true)
                    prefs.prevHomeWallpaperUri.first()?.let { uri ->
                        val vid = prefs.prevHomeIsVideo.first()
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri),
                                if (vid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.HOME
                        )
                    }
                    prefs.prevLockWallpaperUri.first()?.let { uri ->
                        val vid = prefs.prevLockIsVideo.first()
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri),
                                if (vid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                            WallpaperTarget.LOCK
                        )
                    }
                    val pausedKeys = prefs.globalOffPausedTimers.first()
                    if (pausedKeys.isNotEmpty()) {
                        pausedKeys.forEach { key -> timerManager.resume(key) }
                        prefs.setGlobalOffPausedTimers(emptySet())
                    }
                }
                "global_off" -> {
                    // Apply quickSet static wallpapers first (records prevHomeWallpaperUri
                    // so a subsequent Restore can go back, matching QS tile behaviour).
                    prefs.quickSetHomeStaticUri.first()?.let { uri ->
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                            WallpaperTarget.HOME
                        )
                    }
                    prefs.quickSetLockStaticUri.first()?.let { uri ->
                        wallpaperRepository.setWallpaper(
                            WallpaperItem(Uri.parse(uri), FolderType.IMAGE, "", ""),
                            WallpaperTarget.LOCK
                        )
                    }
                    prefs.setAppEnabled(false)
                    if (prefs.pauseTimersOnGlobalOff.first()) {
                        val keys = listOf("home_image", "home_video", "lock_image", "lock_video")
                            .filter { key ->
                                when (key) {
                                    "home_image" -> prefs.homeImageTimerEnabled.first()
                                    "home_video" -> prefs.homeVideoTimerEnabled.first()
                                    "lock_image" -> prefs.lockImageTimerEnabled.first()
                                    "lock_video" -> prefs.lockVideoTimerEnabled.first()
                                    else -> false
                                }
                            }
                            .filter { key -> timerManager.paused.value[key] != true }
                        keys.forEach { key -> timerManager.pause(key) }
                        prefs.setGlobalOffPausedTimers(keys.toSet())
                        getSystemService(NotificationManager::class.java)
                            ?.cancel(TimerStatusNotificationHelper.NOTIFICATION_ID)
                    }
                    if (prefs.watchGlobalOffVibrate.first()) {
                        getSystemService(android.os.Vibrator::class.java)
                            ?.vibrate(android.os.VibrationEffect.createOneShot(
                                300L, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            ))
                    }
                }
                else -> Log.w(TAG, "Unknown watch action: $actionType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Watch execute failed", e)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_thumbnails").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
