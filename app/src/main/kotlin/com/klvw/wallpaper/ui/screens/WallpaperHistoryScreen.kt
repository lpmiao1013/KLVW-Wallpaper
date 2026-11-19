package com.klvw.wallpaper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.prefs.WallpaperHistoryEntry
import com.klvw.wallpaper.data.prefs.WallpaperHistoryStore
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.DeleteSourcePrep
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WallpaperHistorySettingsScreen(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.wallpaperHistory.collectAsStateWithLifecycle()
    var previewEntry by remember { mutableStateOf<WallpaperHistoryEntry?>(null) }
    var blockMessage by remember { mutableStateOf<String?>(null) }
    var showNeedSwitch by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WallpaperHistoryEntry?>(null) }
    var switching by remember { mutableStateOf(false) }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    fun requestDelete(entry: WallpaperHistoryEntry) {
        val item = entry.toWallpaperItem() ?: run {
            blockMessage = "无法解析该历史记录"
            return
        }
        pendingDelete = entry
        viewModel.prepareDeleteSource(item) { prep ->
            when (prep) {
                is DeleteSourcePrep.Blocked -> {
                    pendingDelete = null
                    blockMessage = prep.message
                }
                is DeleteSourcePrep.NeedSwitchFirst -> showNeedSwitch = true
                is DeleteSourcePrep.ReadyToConfirm -> showDeleteConfirm = true
            }
        }
    }

    blockMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { blockMessage = null },
            text = {
                Text(msg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { blockMessage = null }) { Text("知道了") }
            }
        )
    }

    if (showNeedSwitch) {
        val entry = pendingDelete
        AlertDialog(
            onDismissRequest = { if (!switching) showNeedSwitch = false },
            text = {
                Text(
                    "请先切换资源",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !switching && entry != null,
                    onClick = {
                        val item = entry?.toWallpaperItem() ?: return@TextButton
                        switching = true
                        viewModel.switchAwayFromSource(item) { ok, err ->
                            switching = false
                            showNeedSwitch = false
                            if (ok) showDeleteConfirm = true
                            else {
                                pendingDelete = null
                                blockMessage = err ?: "切换资源失败"
                            }
                        }
                    }
                ) { Text(if (switching) "切换中…" else "切换资源") }
            },
            dismissButton = {
                TextButton(enabled = !switching, onClick = {
                    showNeedSwitch = false
                    pendingDelete = null
                }) { Text("取消") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "删除源文件，非应用内路径，请慎重操作！",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请再次确认",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = pendingDelete
                        val item = entry?.toWallpaperItem()
                        showDeleteConfirm = false
                        if (item != null) {
                            viewModel.deleteSourceFile(item) { ok ->
                                if (ok) pendingDelete = null
                            }
                        } else {
                            pendingDelete = null
                        }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    pendingDelete = null
                }) { Text("取消") }
            }
        )
    }

    previewEntry?.let { entry ->
        val item = entry.toWallpaperItem()
        if (item != null) {
            MediaPreviewDialog(
                item = item,
                onDismiss = { previewEntry = null },
                onPrepareDelete = { toDelete, onResult ->
                    viewModel.prepareDeleteSource(toDelete, onResult)
                },
                onSwitchAway = { toSwitch, onDone ->
                    viewModel.switchAwayFromSource(toSwitch, onDone)
                },
                onDeleteSource = { toDelete ->
                    viewModel.deleteSourceFile(toDelete) { ok ->
                        if (ok) previewEntry = null
                    }
                }
            )
        } else {
            previewEntry = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text("壁纸历史", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "最近 ${WallpaperHistoryStore.MAX_ENTRIES} 次设壁纸记录（解锁/定时/手动/随机），时间倒序。可快捷删除源文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("暂无历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryRow(
                        entry = entry,
                        timeText = timeFmt.format(Date(entry.timestamp)),
                        onOpen = { previewEntry = entry },
                        onDelete = { requestDelete(entry) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: WallpaperHistoryEntry,
    timeText: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val screenLabel = if (entry.target == "lock") "锁屏" else "桌面"
    val typeLabel = when (entry.type) {
        "video" -> "视频"
        "static" -> "静态"
        else -> "图片"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.displayName.ifBlank { "未命名" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$screenLabel · $typeLabel · ${entry.folderLabel.ifBlank { "文件夹" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除源文件",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
