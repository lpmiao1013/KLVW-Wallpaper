package com.klvw.wallpaper.aer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.DocumentsContract
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.klvw.wallpaper.aer.AerDocumentsProvider
import com.klvw.wallpaper.ui.theme.SurfaceGlass

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AerScreen(
    vm: AerViewModel,
    onFinish: () -> Unit = {}
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isSelectionMode = uiState.selectedIds.isNotEmpty()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showImportMenu by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.checkMountState() }
    LaunchedEffect(uiState.staticAdded) {
        if (uiState.staticAdded > 0) {
            Toast.makeText(
                context,
                "${uiState.staticAdded} image${if (uiState.staticAdded > 1) "s" else ""} added to Static Wallpapers",
                Toast.LENGTH_SHORT
            ).show()
            vm.clearStaticAdded()
        }
    }
    BackHandler(enabled = isSelectionMode) { vm.clearSelection() }
    BackHandler(enabled = !isSelectionMode && uiState.pathComponents.isNotEmpty()) { vm.navigateBack() }

    val importFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris, context.contentResolver)
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { vm.importFolder(it, context.contentResolver) }
    }

    val hasSelectedImages = remember(uiState.selectedIds, uiState.items) {
        uiState.items.any { it is AerItem.MediaFile && !it.isVideo && it.id in uiState.selectedIds }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            isSelectionMode -> vm.clearSelection()
                            uiState.pathComponents.isNotEmpty() -> vm.navigateBack()
                            else -> onFinish()
                        }
                    }) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    if (isSelectionMode) {
                        Text("已选 ${uiState.selectedIds.size} 项",
                            style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Aer", style = MaterialTheme.typography.titleMedium)
                            uiState.pathComponents.forEach { part ->
                                Text(
                                    " / $part",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!isSelectionMode) {
                        // Mount toggle
                        if (uiState.isMounted) {
                            IconButton(onClick = { vm.unmount() }) {
                                Icon(Icons.Default.LockOpen, contentDescription = "卸载",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { vm.mount(context) }) {
                                Icon(Icons.Default.Lock, contentDescription = "挂载")
                            }
                        }
                        IconButton(onClick = { vm.showCreateFolderDialog(true); newFolderName = "" }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                        Box {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "导入")
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入文件") },
                                    leadingIcon = { Icon(Icons.Default.Image, null) },
                                    onClick = {
                                        showImportMenu = false
                                        importFilesLauncher.launch(arrayOf("image/*", "video/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导入文件夹") },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                                    onClick = {
                                        showImportMenu = false
                                        importFolderLauncher.launch(null)
                                    }
                                )
                            }
                        }
                    }
                }
            )

            // Mount status banner
            if (!uiState.isMounted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceGlass)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "已卸载 — 外部应用看不到这些文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.mount(context) }) { Text("挂载") }
                }
            }

            if (uiState.items.isEmpty() && !uiState.importing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOff, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(
                            "空空如也 — 点 + 导入媒体",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = if (isSelectionMode) 80.dp else 16.dp)
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        when (item) {
                            is AerItem.Folder -> FolderItem(
                                folder = item,
                                isSelected = item.id in uiState.selectedIds,
                                onClick = {
                                    if (isSelectionMode) vm.toggleSelection(item.id)
                                    else vm.navigate(item)
                                },
                                onLongClick = { vm.toggleSelection(item.id) }
                            )
                            is AerItem.MediaFile -> MediaItem(
                                file = item,
                                isSelected = item.id in uiState.selectedIds,
                                onClick = { vm.toggleSelection(item.id) },
                                onLongClick = { vm.toggleSelection(item.id) }
                            )
                        }
                    }
                }
            }
        }

        // Selection toolbar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "已选 ${uiState.selectedIds.size} 项",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.selectedIds.size == 1) {
                    val selectedItem = uiState.items.firstOrNull { it.id in uiState.selectedIds }
                    if (selectedItem != null) {
                        OutlinedButton(onClick = {
                            renameText = selectedItem.name
                            vm.showRenameDialog(selectedItem)
                        }) {
                            Icon(Icons.Default.DriveFileRenameOutline, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重命名")
                        }
                    }
                }
                if (hasSelectedImages) {
                    OutlinedButton(onClick = { vm.addSelectedAsStaticWallpapers() }) {
                        Icon(Icons.Default.Wallpaper, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("静态")
                    }
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }

        // Import progress
        if (uiState.importing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在导入…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // Create folder dialog
    if (uiState.showCreateFolder) {
        AlertDialog(
            onDismissRequest = { vm.showCreateFolderDialog(false) },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank()) {
                        vm.createFolder(newFolderName.trim())
                        vm.showCreateFolderDialog(false)
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showCreateFolderDialog(false) }) { Text("取消") }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        val count = uiState.selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除项目") },
            text = { Text("确定永久删除 $count 项吗？") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(); showDeleteConfirm = false }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // Rename dialog
    val renameTarget = uiState.renameTarget
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissRenameDialog() },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) vm.renameItem(renameTarget, renameText)
                }) { Text("重命名") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissRenameDialog() }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    folder: AerItem.Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .background(SurfaceGlass)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                folder.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        if (isSelected) SelectionBadge()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaItem(
    file: AerItem.MediaFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val uri = remember(file.relPath) {
        DocumentsContract.buildDocumentUri(AerDocumentsProvider.AUTHORITY, file.relPath)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (file.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(file.name, color = Color.White, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isSelected) SelectionBadge()
    }
}

@Composable
private fun BoxScope.SelectionBadge() {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
