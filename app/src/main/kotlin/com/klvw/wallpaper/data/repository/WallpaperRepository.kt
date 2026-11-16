package com.klvw.wallpaper.data.repository

import android.app.WallpaperManager
import android.content.Context
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.VideoSharpnessMode
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.util.DisplayUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPreferences,
    private val displayUtils: DisplayUtils
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    suspend fun setWallpaper(item: WallpaperItem, target: WallpaperTarget) = withContext(Dispatchers.IO) {
        if (!prefs.appEnabled.first()) return@withContext
        val sharpness = VideoSharpnessMode.fromPref(prefs.videoSharpnessMode.first())
        if (sharpness != VideoSharpnessMode.POWER) {
            displayUtils.suggestDesiredWallpaperSize()
        }
        val isVideo = item.type == FolderType.VIDEO
        when (target) {
            WallpaperTarget.HOME -> {
                prefs.homeWallpaperUri.first()?.let { prefs.savePreviousHome(it, prefs.homeIsVideo.first()) }
                prefs.setHomeWallpaper(item.uri.toString(), isVideo)
            }
            WallpaperTarget.LOCK -> {
                prefs.lockWallpaperUri.first()?.let { prefs.savePreviousLock(it, prefs.lockIsVideo.first()) }
                prefs.setLockWallpaper(item.uri.toString(), isVideo)
            }
            WallpaperTarget.BOTH -> {
                prefs.homeWallpaperUri.first()?.let { prefs.savePreviousHome(it, prefs.homeIsVideo.first()) }
                prefs.lockWallpaperUri.first()?.let { prefs.savePreviousLock(it, prefs.lockIsVideo.first()) }
                prefs.setHomeWallpaper(item.uri.toString(), isVideo)
                prefs.setLockWallpaper(item.uri.toString(), isVideo)
            }
        }
    }

    fun isHomeServiceActive(): Boolean =
        wallpaperManager.wallpaperInfo?.serviceName == "com.klvw.wallpaper.service.KLVWallpaperService"

    @Suppress("DEPRECATION")
    suspend fun isLockServiceActive(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            wallpaperManager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.serviceName ==
                "com.klvw.wallpaper.service.KLVWallpaperServiceLock"
        } else {
            prefs.lockServiceActivated.first()
        }
}
