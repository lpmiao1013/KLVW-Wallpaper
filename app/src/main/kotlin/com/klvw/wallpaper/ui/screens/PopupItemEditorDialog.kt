package com.klvw.wallpaper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.tile.*
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PopupItemEditorDialog(
    existingItem: PopupItem?,
    imageFolders: List<FolderEntity>,
    videoFolders: List<FolderEntity>,
    staticImages: List<WallpaperItem>,
    onSave: (PopupItem) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(existingItem?.label ?: "") }
    var actionType by remember { mutableStateOf(existingItem?.actionType ?: POPUP_ACTION_RANDOM_IMAGE) }
    var target by remember { mutableStateOf(existingItem?.target ?: "home") }
    var folderUri by remember { mutableStateOf(existingItem?.folderUri ?: "") }
    var imageUri by remember { mutableStateOf(existingItem?.imageUri ?: "") }
    var iconColor by remember { mutableStateOf(existingItem?.iconColor ?: "auto") }
    var mediaType by remember { mutableStateOf(existingItem?.mediaType ?: "image") }

    val isVideo = actionType == POPUP_ACTION_RANDOM_VIDEO
    val folders = if (isVideo) videoFolders else imageFolders

    val canSave = label.isNotBlank() && when (actionType) {
        POPUP_ACTION_STATIC_IMAGE -> imageUri.isNotBlank()
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingItem == null) "添加项目" else "Edit Item") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Action type
                Text("动作类型", style = MaterialTheme.typography.labelLarge)
                val actionTypes = listOf(
                    POPUP_ACTION_RANDOM_IMAGE      to "随机图片",
                    POPUP_ACTION_RANDOM_VIDEO      to "随机视频",
                    POPUP_ACTION_STATIC_IMAGE      to "静态图",
                    POPUP_ACTION_RESTORE           to "恢复上一张",
                    POPUP_ACTION_ICON_COLOR        to "图标颜色",
                    POPUP_ACTION_SELECT_FILE       to "Select File",
                    POPUP_ACTION_TIMER             to "Timer Control",
                    POPUP_ACTION_FOLDER_SELECT_ALL to "全部文件夹",
                    POPUP_ACTION_DISPLAY_CONTROL   to "显示控制",
                    POPUP_ACTION_PUJIE_WATCH_FACE  to "Pujie 表盘",
                    POPUP_ACTION_AER_MOUNT         to "Aer Mount"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actionTypes.forEach { (value, lbl) ->
                        FilterChip(
                            selected = actionType == value,
                            onClick = { actionType = value },
                            label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Action-specific options
                when (actionType) {
                    POPUP_ACTION_RANDOM_IMAGE, POPUP_ACTION_RANDOM_VIDEO -> {
                        Text("来源文件夹", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = folderUri.isEmpty(), onClick = { folderUri = "" })
                            Spacer(Modifier.width(4.dp))
                            Text("使用默认文件夹", style = MaterialTheme.typography.bodySmall)
                        }
                        folders.forEach { folder ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = folderUri == folder.uri,
                                    onClick = { folderUri = folder.uri }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(folder.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    POPUP_ACTION_STATIC_IMAGE -> {
                        Text("图片", style = MaterialTheme.typography.labelLarge)
                        if (staticImages.isEmpty()) {
                            Text(
                                "还没有保存静态图。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            staticImages.forEach { img ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = imageUri == img.uri.toString(),
                                        onClick = { imageUri = img.uri.toString() }
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    POPUP_ACTION_ICON_COLOR -> {
                        Text("颜色", style = MaterialTheme.typography.labelLarge)
                        listOf("auto" to "自动", "dark" to "深色", "light" to "浅色").forEach { (v, lbl) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = iconColor == v, onClick = { iconColor = v })
                                Spacer(Modifier.width(4.dp))
                                Text(lbl, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                val noTargetActions = setOf(
                    POPUP_ACTION_ICON_COLOR,
                    POPUP_ACTION_SELECT_FILE,
                    POPUP_ACTION_FOLDER_SELECT_ALL,
                    POPUP_ACTION_TIMER,
                    POPUP_ACTION_DISPLAY_CONTROL,
                    POPUP_ACTION_PUJIE_WATCH_FACE,
                    POPUP_ACTION_AER_MOUNT
                )
                if (actionType !in noTargetActions) {
                    Text("应用到", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("home" to "桌面", "lock" to "锁屏", "both" to "两者").forEach { (v, lbl) ->
                            FilterChip(
                                selected = target == v,
                                onClick = { target = v },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSave) return@Button
                    onSave(
                        PopupItem(
                            id = existingItem?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            actionType = actionType,
                            target = target,
                            folderUri = folderUri,
                            imageUri = imageUri,
                            iconColor = iconColor,
                            mediaType = mediaType
                        )
                    )
                },
                enabled = canSave
            ) { Text("保存") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}
