package com.klvw.wallpaper.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimerActionPickerActivity : ComponentActivity() {

    @Inject lateinit var timerManager: WallpaperTimerManager
    @Inject lateinit var prefs: SettingsPreferences
    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.getStringExtra(TimerStatusNotificationHelper.EXTRA_ACTION)
            ?: run { finish(); return }
        val keys = intent.getStringArrayListExtra(TimerStatusNotificationHelper.EXTRA_TIMER_KEYS)
            ?: run { finish(); return }

        val title = when (action) {
            TimerStatusNotificationHelper.VALUE_PAUSE      -> "暂停定时"
            TimerStatusNotificationHelper.VALUE_RESUME     -> "继续定时"
            TimerStatusNotificationHelper.VALUE_CHANGE_NOW -> "立即更换壁纸"
            else -> "重置定时"
        }

        setContent {
            KLVWTheme {
                TimerPickerDialog(
                    title = title,
                    keys = keys,
                    onSelect = { key ->
                        lifecycleScope.launch {
                            if (action == TimerStatusNotificationHelper.VALUE_CHANGE_NOW) {
                                TimerActionReceiver.applyChangeNow(
                                    key, prefs, folderRepository, wallpaperRepository, shuffleHistoryManager
                                )
                            } else {
                                TimerActionReceiver.applyAction(action, key, timerManager, prefs)
                            }
                            TimerStatusNotificationHelper.showFromState(
                                applicationContext, prefs, timerManager
                            )
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun TimerPickerDialog(
    title: String,
    keys: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                keys.forEach { key ->
                    TextButton(
                        onClick = { onSelect(key) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            TimerStatusNotificationHelper.timerLabel(key),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
