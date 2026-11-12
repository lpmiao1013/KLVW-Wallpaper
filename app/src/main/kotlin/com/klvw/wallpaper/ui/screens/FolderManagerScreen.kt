package com.klvw.wallpaper.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.aer.AerFolder
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel
import com.klvw.wallpaper.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FolderManagerScreen(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var addingType by remember { mutableStateOf<FolderType?>(null) }
    var addingAerType by remember { mutableStateOf<FolderType?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { picked ->
            PermissionUtils.persistFolderPermission(context, picked)
            addingType?.let { type -> viewModel.addFolder(picked, type) }
            addingType = null
        }
    }

    if (addingAerType != null) {
        AerFolderPickerDialog(
            type = addingAerType!!,
            viewModel = viewModel,
            onSelect = { aerFolder ->
                viewModel.addAerFolder(aerFolder, addingAerType!!)
                addingAerType = null
            },
            onDismiss = { addingAerType = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AddFolderButton(
                label = "添加图片文件夹",
                icon = Icons.Default.Image,
                modifier = Modifier.weight(1f),
                onClick = { addingType = FolderType.IMAGE; folderPicker.launch(null) }
            )
            AddFolderButton(
                label = "添加视频文件夹",
                icon = Icons.Default.VideoFile,
                modifier = Modifier.weight(1f),
                onClick = { addingType = FolderType.VIDEO; folderPicker.launch(null) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AddFolderButton(
                label = "添加 Aer 图片",
                icon = Icons.Default.Lock,
                modifier = Modifier.weight(1f),
                onClick = { addingAerType = FolderType.IMAGE }
            )
            AddFolderButton(
                label = "添加 Aer 视频",
                icon = Icons.Default.Lock,
                modifier = Modifier.weight(1f),
                onClick = { addingAerType = FolderType.VIDEO }
            )
        }

        if (state.imageFolders.isNotEmpty()) {
            FolderSection(
                title = "图片文件夹",
                folders = state.imageFolders,
                onRemove = { viewModel.removeFolder(it) }
            )
        }

        if (state.videoFolders.isNotEmpty()) {
            FolderSection(
                title = "视频文件夹",
                folders = state.videoFolders,
                onRemove = { viewModel.removeFolder(it) }
            )
        }

        if (state.imageFolders.isEmpty() && state.videoFolders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("还没有添加文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (state.imageFolders.isNotEmpty() || state.videoFolders.isNotEmpty()) {
            DefaultFoldersSection(
                imageFolders = state.imageFolders,
                videoFolders = state.videoFolders,
                defaultHomeImageUri = state.defaultHomeImageFolderUri,
                defaultLockImageUri = state.defaultLockImageFolderUri,
                defaultHomeVideoUri = state.defaultHomeVideoFolderUri,
                defaultLockVideoUri = state.defaultLockVideoFolderUri,
                onSetDefault = { target, mediaType, uri -> viewModel.setDefaultFolder(target, mediaType, uri) }
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun DefaultFoldersSection(
    imageFolders: List<FolderEntity>,
    videoFolders: List<FolderEntity>,
    defaultHomeImageUri: String?,
    defaultLockImageUri: String?,
    defaultHomeVideoUri: String?,
    defaultLockVideoUri: String?,
    onSetDefault: (target: String, mediaType: String, uri: String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "默认文件夹",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            "弹层项目或 Tasker 动作未指定文件夹时使用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        listOf(
            Triple("home", "image", defaultHomeImageUri) to "Home — Images",
            Triple("lock", "image", defaultLockImageUri) to "Lock — Images",
            Triple("home", "video", defaultHomeVideoUri) to "Home — Videos",
            Triple("lock", "video", defaultLockVideoUri) to "Lock — Videos",
        ).forEach { (triple, label) ->
            val (target, mediaType, currentUri) = triple
            val folders = if (mediaType == "image") imageFolders else videoFolders
            DefaultFolderRow(
                label = label,
                folders = folders,
                currentUri = currentUri,
                onSelect = { uri -> onSetDefault(target, mediaType, uri) }
            )
        }
    }
}

@Composable
private fun DefaultFolderRow(
    label: String,
    folders: List<FolderEntity>,
    currentUri: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentFolder = folders.find { it.uri == currentUri }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                currentFolder?.displayName ?: "Not set",
                style = MaterialTheme.typography.bodySmall,
                color = if (currentFolder != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            TextButton(
                onClick = { if (folders.isNotEmpty()) expanded = true },
                enabled = folders.isNotEmpty()
            ) {
                Text(if (currentFolder != null) "Change" else "Set",
                    style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (currentFolder != null) {
                    DropdownMenuItem(
                        text = { Text("清除", color = MaterialTheme.colorScheme.error) },
                        onClick = { onSelect(null); expanded = false }
                    )
                    HorizontalDivider()
                }
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.displayName) },
                        onClick = { onSelect(folder.uri); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFolderButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FolderSection(
    title: String,
    folders: List<FolderEntity>,
    onRemove: (FolderEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        folders.forEach { folder ->
            FolderItem(folder = folder, onRemove = { onRemove(folder) })
        }
    }
}

@Composable
private fun FolderItem(folder: FolderEntity, onRemove: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val isAer = folder.uri.startsWith("klvw-aer://")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when {
                isAer -> Icons.Default.Lock
                folder.type == FolderType.IMAGE -> Icons.Default.Image
                else -> Icons.Default.VideoFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(folder.displayName, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isAer) "Aer private storage"
                else "Added ${dateFormat.format(Date(folder.addedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "移除",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AerFolderPickerDialog(
    type: FolderType,
    viewModel: WallpaperViewModel,
    onSelect: (AerFolder) -> Unit,
    onDismiss: () -> Unit
) {
    var currentRelPath by remember { mutableStateOf<String?>(null) }
    var pathComponents by remember { mutableStateOf<List<String>>(emptyList()) }
    var subfolders by remember { mutableStateOf<List<AerFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(currentRelPath) {
        loading = true
        subfolders = withContext(Dispatchers.IO) { viewModel.getAvailableAerFolders(currentRelPath) }
        loading = false
    }

    val currentFolder = AerFolder(
        name = pathComponents.lastOrNull() ?: "Aer 根目录",
        relPath = currentRelPath ?: ""
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("添加 Aer ${if (type == FolderType.IMAGE) "图片" else "视频"}")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Aer 根目录",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pathComponents.isEmpty()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.clickable(enabled = pathComponents.isNotEmpty()) {
                            currentRelPath = null
                            pathComponents = emptyList()
                        }
                    )
                    pathComponents.forEachIndexed { idx, part ->
                        Text(
                            " / ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isLast = idx == pathComponents.lastIndex
                        Text(
                            part,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLast) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.clickable(enabled = !isLast) {
                                val newComponents = pathComponents.take(idx + 1)
                                pathComponents = newComponents
                                currentRelPath = newComponents.joinToString("/")
                            }
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelect(currentFolder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "选择「${currentFolder.name}」",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (subfolders.isEmpty()) {
                    Text(
                        "这里没有子文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(subfolders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        pathComponents = pathComponents + folder.name
                                        currentRelPath = folder.relPath
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pathComponents.isNotEmpty()) {
                    TextButton(onClick = {
                        val newComponents = pathComponents.dropLast(1)
                        pathComponents = newComponents
                        currentRelPath = newComponents.joinToString("/").ifEmpty { null }
                    }) { Text("返回") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
