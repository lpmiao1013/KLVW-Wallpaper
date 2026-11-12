package com.klvw.wallpaper.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Scale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.MediaTab
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    picked, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            val displayName = picked.lastPathSegment?.substringAfterLast('/') ?: "图片"
            when (state.mediaTab) {
                MediaTab.STATIC -> viewModel.addStaticImage(picked, displayName)
                else -> {
                    val type = if (state.mediaTab == MediaTab.IMAGE) FolderType.IMAGE else FolderType.VIDEO
                    val item = WallpaperItem(picked, type, displayName, "")
                    viewModel.selectItem(item)
                }
            }
        }
    }

    val liveWallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.dismissLiveWallpaperPrompt() }

    state.promptActivateServiceClass?.let { serviceClass ->
        LaunchedEffect(serviceClass) {
            try {
                liveWallpaperLauncher.launch(
                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(context.packageName, serviceClass)
                        )
                    }
                )
            } catch (_: Exception) {
                viewModel.dismissLiveWallpaperPrompt()
            }
        }
    }

    state.successMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }

    val folders = if (state.mediaTab == MediaTab.VIDEO) state.videoFolders else state.imageFolders
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<WallpaperItem?>(null) }
    val onItemClick = remember(viewModel) { { item: WallpaperItem -> viewModel.selectItem(item) } }
    val onDeleteItem = remember(viewModel) { { item: WallpaperItem -> viewModel.removeStaticImage(item.uri) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Three-tab selector: Image | Static | Video
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceGlass)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(MediaTab.IMAGE to "图片", MediaTab.STATIC to "静态", MediaTab.VIDEO to "视频").forEach { (tab, label) ->
                val selected = state.mediaTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { viewModel.setMediaTab(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Target selector
        Text("应用到", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(WallpaperTarget.HOME to "桌面", WallpaperTarget.LOCK to "锁屏").forEach { (t, label) ->
                FilterChip(
                    selected = state.target == t,
                    onClick = { viewModel.setTarget(t) },
                    label = { Text(label) }
                )
            }
        }

        // Folder selector — only for Image and Video tabs
        if (state.mediaTab != MediaTab.STATIC && folders.isNotEmpty()) {
            val selUri = state.selectedFolderUri
            val buttonLabel = when {
                folders.size == 1 -> folders.first().displayName
                selUri == null -> "全部文件夹"
                else -> folders.find { it.uri == selUri }?.displayName ?: "选择文件夹"
            }
            val hasMultiple = folders.size > 1
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (hasMultiple) folderMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        buttonLabel,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (hasMultiple) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部文件夹") },
                        onClick = { viewModel.selectFolder(null); folderMenuExpanded = false },
                        leadingIcon = if (selUri == null) {
                            { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    HorizontalDivider()
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = { viewModel.selectFolder(folder.uri); folderMenuExpanded = false },
                            leadingIcon = when {
                                selUri == folder.uri -> { { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) } }
                                folder.uri.startsWith("klvw-aer://") -> { { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) } }
                                else -> null
                            }
                        )
                    }
                }
            }
        }

        // Media grid / loading / empty state
        when {
            state.isLoadingPreview -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            state.previewItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceGlass),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (state.mediaTab) {
                            MediaTab.STATIC -> "用下方按钮添加图片"
                            MediaTab.IMAGE -> if (folders.isEmpty()) "添加文件夹以查看图片" else "未找到图片"
                            MediaTab.VIDEO -> if (folders.isEmpty()) "添加文件夹以查看视频" else "未找到视频"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 90.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.previewItems, key = { it.uri.toString() }) { item ->
                        MediaThumbnail(
                            item = item,
                            isSelected = item.uri == state.selectedItem?.uri,
                            onClick = remember(item.uri) { { onItemClick(item) } },
                            onLongClick = remember(item.uri) { { previewItem = item } },
                            onDelete = if (state.mediaTab == MediaTab.STATIC) {
                                remember(item.uri) { { onDeleteItem(item) } }
                            } else null
                        )
                    }
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val mimes = if (state.mediaTab == MediaTab.VIDEO)
                        arrayOf("video/*") else arrayOf("image/*")
                    filePicker.launch(mimes)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.mediaTab == MediaTab.STATIC) "添加图片" else "从相册选择")
            }
            Button(
                onClick = { viewModel.setWallpaper() },
                modifier = Modifier.weight(1f),
                enabled = state.selectedItem != null && !state.isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("设为壁纸")
                }
            }
        }

        state.successMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp))
        }
    }

    previewItem?.let { item ->
        MediaPreviewDialog(item = item, onDismiss = { previewItem = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumbnail(
    item: WallpaperItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = remember(item.uri, item.lastModified) {
                val cacheKey = "${item.uri}_${item.lastModified}"
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(360, 640)
                    .scale(Scale.FILL)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCacheKey(MemoryCache.Key(cacheKey))
                    .diskCacheKey(cacheKey)
                    .build()
            },
            contentDescription = item.displayName,
            placeholder = remember { ColorPainter(Color(0xFF2A2A2A)) },
            error = remember { ColorPainter(Color(0xFF1A1A1A)) },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.type == FolderType.VIDEO && !isSelected) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp)
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
            )
        }
        // Delete button — shown on Static tab thumbnails when not selected
        if (onDelete != null && !isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaPreviewDialog(
    item: WallpaperItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val maxWidth = (config.screenWidthDp * 0.85f).dp
    val maxHeight = (config.screenHeightDp * 0.8f).dp
    val maxWidthPx = with(density) { maxWidth.roundToPx() }
    val maxHeightPx = with(density) { maxHeight.roundToPx() }
    var videoPlaying by remember { mutableStateOf(false) }
    var imageAspectRatio by remember(item.uri) { mutableStateOf<Float?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Video player needs a fixed size; images shrink the dialog to their natural aspect ratio.
        val boxModifier = if (item.type == FolderType.VIDEO && videoPlaying)
            Modifier.size(maxWidth, maxHeight)
        else
            Modifier.fillMaxWidth(0.85f).heightIn(max = maxHeight)
        Box(
            modifier = boxModifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (item.type == FolderType.VIDEO && videoPlaying) {
                VideoPlayer(uri = item.uri, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = remember(item.uri, item.lastModified) {
                        val cacheKey = "${item.uri}_${item.lastModified}"
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(maxWidthPx, maxHeightPx)
                            .scale(Scale.FIT)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCacheKey(MemoryCache.Key(cacheKey))
                            .diskCacheKey(cacheKey)
                            .build()
                    },
                    contentDescription = item.displayName,
                    placeholder = remember { ColorPainter(Color(0xFF2A2A2A)) },
                    error = remember { ColorPainter(Color(0xFF1A1A1A)) },
                    onSuccess = { state ->
                        val s = state.painter.intrinsicSize
                        if (s.height > 0f) imageAspectRatio = s.width / s.height
                    },
                    contentScale = ContentScale.Fit,
                    modifier = imageAspectRatio?.let { ratio ->
                        Modifier.fillMaxWidth().aspectRatio(ratio)
                    } ?: Modifier.fillMaxWidth().defaultMinSize(minHeight = 200.dp)
                )
                if (item.type == FolderType.VIDEO) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { videoPlaying = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Text(
                    item.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            // Close button always visible
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(uri: android.net.Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().also {
            it.setMediaItem(MediaItem.fromUri(uri))
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.prepare()
            it.playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = modifier
    )
}

