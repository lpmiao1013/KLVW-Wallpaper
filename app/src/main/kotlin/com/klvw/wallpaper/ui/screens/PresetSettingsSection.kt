package com.klvw.wallpaper.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.klvw.wallpaper.R
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.tile.PresetTileService
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel
import java.util.concurrent.Executor

@Composable
fun PresetSettingsSection(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickTarget by remember { mutableStateOf<String?>(null) } // "home" | "lock"

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(
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
            "home" -> viewModel.setPresetHome(uri.toString(), state.presetHomeIsVideo, "file")
            "lock" -> viewModel.setPresetLock(uri.toString(), state.presetLockIsVideo, "file")
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
            "最高优先级场景覆盖。每屏可选「文件」或「文件夹」。启用后暂停时段方案；关闭后恢复历史配置。",
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

        OutlinedButton(
            onClick = {
                requestAddPresetTile(context) { msg -> toast(msg) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加「KLVW 预设」磁贴")
        }
        Text(
            "也可在系统快捷设置编辑页中手动添加「KLVW 预设」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(thickness = 0.5.dp)

        PresetScreenBlock(
            title = "桌面",
            isVideo = state.presetHomeIsVideo,
            source = state.presetHomeSource,
            uri = state.presetHomeUri,
            folders = if (state.presetHomeIsVideo) state.videoFolders else state.imageFolders,
            onSelectImage = { viewModel.setPresetHomeMedia(isVideo = false) },
            onSelectVideo = { viewModel.setPresetHomeMedia(isVideo = true) },
            onSourceFile = { viewModel.setPresetHomeSource("file") },
            onSourceFolder = { viewModel.setPresetHomeSource("folder") },
            onPickFile = {
                pickTarget = "home"
                filePicker.launch(mimeFor(state.presetHomeIsVideo))
            },
            onPickFolder = { folderUri ->
                viewModel.setPresetHome(folderUri, state.presetHomeIsVideo, "folder")
            },
            onClear = { viewModel.setPresetHome(null, state.presetHomeIsVideo, state.presetHomeSource) }
        )

        HorizontalDivider(thickness = 0.5.dp)

        PresetScreenBlock(
            title = "锁屏",
            isVideo = state.presetLockIsVideo,
            source = state.presetLockSource,
            uri = state.presetLockUri,
            folders = if (state.presetLockIsVideo) state.videoFolders else state.imageFolders,
            onSelectImage = { viewModel.setPresetLockMedia(isVideo = false) },
            onSelectVideo = { viewModel.setPresetLockMedia(isVideo = true) },
            onSourceFile = { viewModel.setPresetLockSource("file") },
            onSourceFolder = { viewModel.setPresetLockSource("folder") },
            onPickFile = {
                pickTarget = "lock"
                filePicker.launch(mimeFor(state.presetLockIsVideo))
            },
            onPickFolder = { folderUri ->
                viewModel.setPresetLock(folderUri, state.presetLockIsVideo, "folder")
            },
            onClear = { viewModel.setPresetLock(null, state.presetLockIsVideo, state.presetLockSource) }
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
                Text("重新应用预设")
            }
        }
    }
}

private fun mimeFor(isVideo: Boolean): Array<String> =
    if (isVideo) arrayOf("video/*") else arrayOf("image/*")

private fun labelOfUri(uri: String): String =
    uri.substringAfterLast('/').substringAfterLast("%2F").ifBlank { "已选路径" }

private fun requestAddPresetTile(context: android.content.Context, onMsg: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onMsg("请下拉快捷设置 → 编辑，手动添加「KLVW 预设」")
        return
    }
    try {
        val sbm = context.getSystemService(StatusBarManager::class.java) ?: run {
            onMsg("无法打开磁贴添加面板")
            return
        }
        val component = ComponentName(context, PresetTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_qs_preset)
        val executor = Executor { it.run() }
        sbm.requestAddTileService(
            component,
            context.getString(R.string.preset_tile_label),
            icon,
            executor
        ) { result ->
            val msg = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加预设磁贴"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "磁贴已在快捷设置中"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加（可稍后在系统编辑页添加）"
                else -> "请在系统快捷设置编辑页中添加「KLVW 预设」"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onMsg(msg) }
        }
    } catch (_: Exception) {
        onMsg("请下拉快捷设置 → 编辑，手动添加「KLVW 预设」")
    }
}

@Composable
private fun PresetScreenBlock(
    title: String,
    isVideo: Boolean,
    source: String,
    uri: String?,
    folders: List<FolderEntity>,
    onSelectImage: () -> Unit,
    onSelectVideo: () -> Unit,
    onSourceFile: () -> Unit,
    onSourceFolder: () -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: (String) -> Unit,
    onClear: () -> Unit
) {
    val isFolder = source == "folder"
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isFolder,
                onClick = onSourceFile,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("文件")
                    }
                }
            )
            FilterChip(
                selected = isFolder,
                onClick = onSourceFolder,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("文件夹")
                    }
                }
            )
        }

        if (!isFolder) {
            Text(
                "当前文件：${uri?.let { labelOfUri(it) } ?: "未选择"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickFile) {
                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }
                if (uri != null) {
                    TextButton(onClick = onClear) { Text("清除") }
                }
            }
        } else {
            Text(
                "从已添加的${if (isVideo) "视频" else "图片"}文件夹中选择：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (folders.isEmpty()) {
                Text(
                    "还没有文件夹，请先在「文件夹」页添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                folders.forEach { folder ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = uri == folder.uri,
                            onClick = { onPickFolder(folder.uri) }
                        )
                        Text(
                            folder.displayName.ifBlank { labelOfUri(folder.uri) },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (uri != null) {
                TextButton(onClick = onClear) { Text("清除文件夹") }
            }
        }
    }
}
