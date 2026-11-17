package com.klvw.wallpaper.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel

@Composable
fun PresetSettingsSection(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickTarget by remember { mutableStateOf<String?>(null) } // "home" | "lock"

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = pickTarget
        pickTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        when (target) {
            "home" -> viewModel.setPresetHome(uri.toString(), state.presetHomeIsVideo)
            "lock" -> viewModel.setPresetLock(uri.toString(), state.presetLockIsVideo)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "最高优先级场景覆盖。启用后暂停时段方案；关闭后恢复启用前的配置。双击/定时/适应模式仍可用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("启用预设方案", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (state.presetEnabled) "已生效（覆盖时段与普通模式）" else "未启用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.presetEnabled,
                onCheckedChange = { on ->
                    if (on) {
                        viewModel.enablePreset { err ->
                            if (err != null) toast(err) else toast("预设方案已启用")
                        }
                    } else {
                        viewModel.disablePreset { err ->
                            if (err != null) toast(err) else toast("已关闭预设，已恢复历史配置")
                        }
                    }
                }
            )
        }

        HorizontalDivider(thickness = 0.5.dp)

        PresetScreenBlock(
            title = "桌面",
            isVideo = state.presetHomeIsVideo,
            fileLabel = state.presetHomeUri?.let { labelOfUri(it) },
            onSelectImage = { viewModel.setPresetHomeMedia(isVideo = false) },
            onSelectVideo = { viewModel.setPresetHomeMedia(isVideo = true) },
            onPickFile = {
                pickTarget = "home"
                picker.launch(mimeFor(state.presetHomeIsVideo))
            },
            onClear = { viewModel.setPresetHome(null, state.presetHomeIsVideo) }
        )

        HorizontalDivider(thickness = 0.5.dp)

        PresetScreenBlock(
            title = "锁屏",
            isVideo = state.presetLockIsVideo,
            fileLabel = state.presetLockUri?.let { labelOfUri(it) },
            onSelectImage = { viewModel.setPresetLockMedia(isVideo = false) },
            onSelectVideo = { viewModel.setPresetLockMedia(isVideo = true) },
            onPickFile = {
                pickTarget = "lock"
                picker.launch(mimeFor(state.presetLockIsVideo))
            },
            onClear = { viewModel.setPresetLock(null, state.presetLockIsVideo) }
        )

        if (state.presetEnabled) {
            OutlinedButton(
                onClick = {
                    viewModel.enablePreset { err ->
                        if (err != null) toast(err) else toast("已重新应用预设")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("重新应用预设文件")
            }
        }
    }
}

private fun mimeFor(isVideo: Boolean): Array<String> =
    if (isVideo) arrayOf("video/*") else arrayOf("image/*")

private fun labelOfUri(uri: String): String =
    uri.substringAfterLast('/').substringAfterLast("%2F").ifBlank { "已选文件" }

@Composable
private fun PresetScreenBlock(
    title: String,
    isVideo: Boolean,
    fileLabel: String?,
    onSelectImage: () -> Unit,
    onSelectVideo: () -> Unit,
    onPickFile: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isVideo,
                onClick = onSelectImage,
                label = { Text("图片") }
            )
            FilterChip(
                selected = isVideo,
                onClick = onSelectVideo,
                label = { Text("视频") }
            )
        }
        Text(
            "当前：${if (isVideo) "视频" else "图片"} · ${fileLabel ?: "未选择文件"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickFile) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("选择文件")
            }
            if (fileLabel != null) {
                TextButton(onClick = onClear) { Text("清除") }
            }
        }
    }
}
