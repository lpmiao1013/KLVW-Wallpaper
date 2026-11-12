package com.klvw.wallpaper.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IconColorTileConfig : ComponentActivity() {
    @Inject lateinit var prefs: SettingsPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        setContent {
            KLVWTheme {
                val scope = rememberCoroutineScope()
                val color by prefs.forceIconColor.collectAsStateWithLifecycle("auto")
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text("图标颜色") },
                    text = {
                        Column {
                            listOf(
                                "auto" to "自动",
                                "dark" to "深色图标",
                                "light" to "浅色图标"
                            ).forEach { (v, l) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { scope.launch { prefs.setForceIconColor(v) } }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = color == v,
                                        onClick = { scope.launch { prefs.setForceIconColor(v) } }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(l, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = ::finish) { Text("完成") } }
                )
            }
        }
    }
}
