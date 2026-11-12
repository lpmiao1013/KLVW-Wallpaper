package com.klvw.wallpaper.tile

import android.app.Activity
import android.app.KeyguardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KLVWQuickSetConfirmActivity : ComponentActivity() {

    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var timerManager: WallpaperTimerManager

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            restoreAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setFinishOnTouchOutside(true)

        setContent {
            KLVWTheme {
                val scope = rememberCoroutineScope()
                val bgHex = prefs.popupBgColor.collectAsStateWithLifecycle(null).value
                val primaryHex = prefs.popupPrimaryTextColor.collectAsStateWithLifecycle(null).value
                val secondaryHex = prefs.popupSecondaryTextColor.collectAsStateWithLifecycle(null).value

                val themeSurface = MaterialTheme.colorScheme.surface
                val themeOnSurface = MaterialTheme.colorScheme.onSurface
                val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

                val cardBg = bgHex?.let { tryParseColor(it) } ?: themeSurface.copy(alpha = 0.95f)
                val primaryColor = primaryHex?.let { tryParseColor(it) } ?: themeOnSurface
                val secondaryColor = secondaryHex?.let { tryParseColor(it) } ?: themeOnSurfaceVariant

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "重新启用 KLVW？",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = primaryColor
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "这将重新启用全部壁纸功能，并恢复之前的桌面与锁屏壁纸。",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("否", color = secondaryColor)
                                }
                                Button(
                                    onClick = { launchAuth() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("是")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchAuth() {
        val km = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = km?.createConfirmDeviceCredentialIntent(
            "KLVW",
            "Authentication required to re-enable wallpaper"
        )
        if (intent != null) {
            authLauncher.launch(intent)
        } else {
            restoreAndFinish()
        }
    }

    private fun restoreAndFinish() {
        lifecycleScope.launch {
            prefs.setAppEnabled(true)
            restorePreviousWallpapers()
            restoreTimers()
            finish()
        }
    }

    private suspend fun restorePreviousWallpapers() {
        val prevHomeUri = prefs.prevHomeWallpaperUri.first()
        val prevHomeIsVid = prefs.prevHomeIsVideo.first()
        val prevLockUri = prefs.prevLockWallpaperUri.first()
        val prevLockIsVid = prefs.prevLockIsVideo.first()
        prevHomeUri?.let {
            wallpaperRepository.setWallpaper(
                WallpaperItem(Uri.parse(it), if (prevHomeIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                WallpaperTarget.HOME
            )
        }
        prevLockUri?.let {
            wallpaperRepository.setWallpaper(
                WallpaperItem(Uri.parse(it), if (prevLockIsVid) FolderType.VIDEO else FolderType.IMAGE, "", ""),
                WallpaperTarget.LOCK
            )
        }
    }

    private suspend fun restoreTimers() {
        val keys = prefs.globalOffPausedTimers.first()
        if (keys.isEmpty()) return
        keys.forEach { key -> timerManager.resume(key) }
        prefs.setGlobalOffPausedTimers(emptySet())
        TimerStatusNotificationHelper.showFromState(applicationContext, prefs, timerManager)
    }

    private fun tryParseColor(hex: String): Color? = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) { null }
}
