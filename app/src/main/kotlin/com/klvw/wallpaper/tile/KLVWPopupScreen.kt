package com.klvw.wallpaper.tile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import com.klvw.wallpaper.aer.AerShell
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

private fun hexToComposeColor(hex: String): Color? = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { null }

@Composable
fun KLVWPopupScreen(
    onDismiss: () -> Unit,
    viewModel: KLVWPopupViewModel = hiltViewModel()
) {
    val items by viewModel.popupItems.collectAsStateWithLifecycle()
    val imageFolders by viewModel.imageFolders.collectAsStateWithLifecycle()
    val videoFolders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bgHex by viewModel.popupBgColor.collectAsStateWithLifecycle()
    val primaryHex by viewModel.popupPrimaryTextColor.collectAsStateWithLifecycle()
    val secondaryHex by viewModel.popupSecondaryTextColor.collectAsStateWithLifecycle()
    val timerHighlightHex by viewModel.popupTimerHighlightColor.collectAsStateWithLifecycle()
    val timerBorderHex by viewModel.popupTimerBorderColor.collectAsStateWithLifecycle()

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val popupLayout by viewModel.popupLayout.collectAsStateWithLifecycle()
    val popupGridColumns by viewModel.popupGridColumns.collectAsStateWithLifecycle()
    val popupWidthFraction by viewModel.popupWidthFraction.collectAsStateWithLifecycle()
    val popupAutoScale by viewModel.popupAutoScale.collectAsStateWithLifecycle()
    val popupItemSizeDp by viewModel.popupItemSizeDp.collectAsStateWithLifecycle()
    val popupScaleIconColor by viewModel.popupScaleIconColor.collectAsStateWithLifecycle()
    val popupScaleFolderSelect by viewModel.popupScaleFolderSelect.collectAsStateWithLifecycle()
    val popupScaleTimer by viewModel.popupScaleTimer.collectAsStateWithLifecycle()
    val popupScaleFolderSelectAll by viewModel.popupScaleFolderSelectAll.collectAsStateWithLifecycle()
    val appEnabled by viewModel.appEnabled.collectAsStateWithLifecycle()
    val defaultHomeImageFolderUri by viewModel.defaultHomeImageFolderUri.collectAsStateWithLifecycle()
    val defaultHomeVideoFolderUri by viewModel.defaultHomeVideoFolderUri.collectAsStateWithLifecycle()
    val defaultLockImageFolderUri by viewModel.defaultLockImageFolderUri.collectAsStateWithLifecycle()
    val defaultLockVideoFolderUri by viewModel.defaultLockVideoFolderUri.collectAsStateWithLifecycle()
    val timerPaused by viewModel.timerPaused.collectAsStateWithLifecycle()
    val timerNextFireTimes by viewModel.timerNextFireTimes.collectAsStateWithLifecycle()
    val homeImageTimerEnabled by viewModel.homeImageTimerEnabled.collectAsStateWithLifecycle()
    val homeImageTimerIntervalMin by viewModel.homeImageTimerIntervalMin.collectAsStateWithLifecycle()
    val homeVideoTimerEnabled by viewModel.homeVideoTimerEnabled.collectAsStateWithLifecycle()
    val homeVideoTimerIntervalMin by viewModel.homeVideoTimerIntervalMin.collectAsStateWithLifecycle()
    val lockImageTimerEnabled by viewModel.lockImageTimerEnabled.collectAsStateWithLifecycle()
    val lockImageTimerIntervalMin by viewModel.lockImageTimerIntervalMin.collectAsStateWithLifecycle()
    val lockVideoTimerEnabled by viewModel.lockVideoTimerEnabled.collectAsStateWithLifecycle()
    val lockVideoTimerIntervalMin by viewModel.lockVideoTimerIntervalMin.collectAsStateWithLifecycle()
    val homeImageTimerPauseOnLock by viewModel.homeImageTimerPauseOnLock.collectAsStateWithLifecycle()
    val homeVideoTimerPauseOnLock by viewModel.homeVideoTimerPauseOnLock.collectAsStateWithLifecycle()
    val lockImageTimerPauseOnLock by viewModel.lockImageTimerPauseOnLock.collectAsStateWithLifecycle()
    val lockVideoTimerPauseOnLock by viewModel.lockVideoTimerPauseOnLock.collectAsStateWithLifecycle()
    val displayControlHomeImage by viewModel.displayControlHomeImage.collectAsStateWithLifecycle()
    val displayControlHomeVideo by viewModel.displayControlHomeVideo.collectAsStateWithLifecycle()
    val displayControlLockImage by viewModel.displayControlLockImage.collectAsStateWithLifecycle()
    val displayControlLockVideo by viewModel.displayControlLockVideo.collectAsStateWithLifecycle()
    val displayControlResetHomeTimer by viewModel.displayControlResetHomeTimer.collectAsStateWithLifecycle()
    val displayControlResetLockTimer by viewModel.displayControlResetLockTimer.collectAsStateWithLifecycle()
    val popupScaleDisplayControl by viewModel.popupScaleDisplayControl.collectAsStateWithLifecycle()
    val pujieWatchFaces by viewModel.pujieWatchFaces.collectAsStateWithLifecycle()

    val cardBgColor = bgHex?.let { hexToComposeColor(it) } ?: themeSurface.copy(alpha = 0.95f)
    val primaryTextColor = primaryHex?.let { hexToComposeColor(it) } ?: themeOnSurface
    val secondaryTextColor = secondaryHex?.let { hexToComposeColor(it) } ?: themeOnSurfaceVariant
    val timerHighlightColor = timerHighlightHex?.let { hexToComposeColor(it) }
        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val timerBorderColor = timerBorderHex?.let { hexToComposeColor(it) }
        ?: Color.White.copy(alpha = 0.15f)

    var pendingFolderItem by remember { mutableStateOf<PopupItem?>(null) }
    var pendingIconColorItem by remember { mutableStateOf<PopupItem?>(null) }
    var showFileSelectDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showFolderManagerDialog by remember { mutableStateOf(false) }
    var showDisplayControlDialog by remember { mutableStateOf(false) }
    var showPujiePickerDialog by remember { mutableStateOf(false) }

    val childActive = pendingFolderItem != null || pendingIconColorItem != null || showFileSelectDialog || showTimerDialog || showFolderManagerDialog || showDisplayControlDialog || showPujiePickerDialog

    // Everything lives inside one Box so no new Android Window is created —
    // all overlays share the activity's transparent theme/status bar.
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Scrim + main popup card (hidden while a child overlay is open) ────
        AnimatedVisibility(
            visible = !childActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(popupWidthFraction)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBgColor)
                    .clickable(enabled = false, onClick = {})
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "KLVW",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = primaryTextColor)
                        }
                    }
                    HorizontalDivider()

                    if (items.isEmpty()) {
                        Text(
                            "尚未配置动作 — 打开设置添加项目",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    } else {
                        val onItemClick: (PopupItem) -> Unit = onItemClick@{ item ->
                            if (!appEnabled &&
                                item.actionType != POPUP_ACTION_ICON_COLOR &&
                                item.actionType != POPUP_ACTION_AER_MOUNT) return@onItemClick
                            when (item.actionType) {
                                POPUP_ACTION_FOLDER_SELECT     -> pendingFolderItem = item
                                POPUP_ACTION_FOLDER_SELECT_ALL -> showFolderManagerDialog = true
                                POPUP_ACTION_SELECT_FILE       -> showFileSelectDialog = true
                                POPUP_ACTION_ICON_COLOR        -> pendingIconColorItem = item
                                POPUP_ACTION_TIMER             -> showTimerDialog = true
                                POPUP_ACTION_DISPLAY_CONTROL   -> showDisplayControlDialog = true
                                POPUP_ACTION_PUJIE_WATCH_FACE  -> showPujiePickerDialog = true
                                POPUP_ACTION_AER_MOUNT         -> AerShell.open(context)
                                else -> scope.launch { viewModel.executeItem(item, context) }
                            }
                        }
                        if (popupLayout == "grid") {
                            PopupItemGrid(
                                items = items,
                                columns = popupGridColumns,
                                primaryColor = primaryTextColor,
                                autoScale = popupAutoScale,
                                itemSizeDp = popupItemSizeDp,
                                onItemClick = onItemClick
                            )
                        } else {
                            val listVertPadding = computeListItemPadding(
                                itemCount = items.size,
                                autoScale = popupAutoScale,
                                itemSizeDp = popupItemSizeDp
                            )
                            items.forEach { item ->
                                PopupItemRow(
                                    item = item,
                                    primaryColor = primaryTextColor,
                                    secondaryColor = secondaryTextColor,
                                    verticalPadding = listVertPadding,
                                    onClick = { onItemClick(item) }
                                )
                            }
                        }
                    }

                }
            }
        }
        } // end AnimatedVisibility

        // ── Folder select overlay ─────────────────────────────────────────────
        pendingFolderItem?.let { item ->
            val folders = if (item.mediaType == "video") videoFolders else imageFolders
            PopupOverlay(onDismiss = { pendingFolderItem = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (popupScaleFolderSelect) popupWidthFraction else 0.85f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBgColor)
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "选择${when (item.mediaType.lowercase()) { "video" -> "视频"; else -> "图片" }}文件夹（${when (item.target.lowercase()) { "lock" -> "锁屏"; "both" -> "两者"; else -> "桌面" }}）",
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryTextColor
                        )
                        Spacer(Modifier.height(4.dp))
                        if (folders.isEmpty()) {
                            Text(
                                "还没有保存文件夹。请先在应用中添加文件夹。",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        } else {
                            folders.forEach { folder ->
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.onFolderSelected(item.mediaType, item.target, folder.uri)
                                            pendingFolderItem = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        folder.displayName,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Icon color picker overlay ─────────────────────────────────────────
        pendingIconColorItem?.let {
            PopupOverlay(onDismiss = { pendingIconColorItem = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (popupScaleIconColor) popupWidthFraction else 0.75f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBgColor)
                        .clickable(enabled = false, onClick = {})
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("图标颜色", style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                        Spacer(Modifier.height(4.dp))
                        listOf("auto" to "自动", "dark" to "深色图标", "light" to "浅色图标")
                            .forEach { (v, label) ->
                                TextButton(
                                    onClick = {
                                        viewModel.setIconColor(v)
                                        pendingIconColorItem = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                    }
                }
            }
        }

        // ── File select overlay ───────────────────────────────────────────────
        if (showFileSelectDialog) {
            FileSelectOverlay(
                imageFolders = imageFolders,
                videoFolders = videoFolders,
                cardBgColor = cardBgColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                viewModel = viewModel,
                onDismiss = { showFileSelectDialog = false }
            )
        }

        // ── Folder manager overlay ────────────────────────────────────────────
        if (showFolderManagerDialog) {
            FolderManagerOverlay(
                imageFolders = imageFolders,
                videoFolders = videoFolders,
                cardBgColor = cardBgColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                popupWidthFraction = popupWidthFraction,
                scaleToPopupWidth = popupScaleFolderSelectAll,
                defaultHomeImageUri = defaultHomeImageFolderUri,
                defaultHomeVideoUri = defaultHomeVideoFolderUri,
                defaultLockImageUri = defaultLockImageFolderUri,
                defaultLockVideoUri = defaultLockVideoFolderUri,
                onFolderSelected = { target, mediaType, uri ->
                    scope.launch { viewModel.onFolderSelected(mediaType, target, uri) }
                },
                onDismiss = { showFolderManagerDialog = false }
            )
        }

        // ── Timer control overlay ─────────────────────────────────────────────
        if (showTimerDialog) {
            TimerControlOverlay(
                cardBgColor = cardBgColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                timerHighlightColor = timerHighlightColor,
                timerBorderColor = timerBorderColor,
                popupWidthFraction = popupWidthFraction,
                scaleToPopupWidth = popupScaleTimer,
                timerPaused = timerPaused,
                nextFireTimes = timerNextFireTimes,
                homeImageEnabled = homeImageTimerEnabled,
                homeImageIntervalMin = homeImageTimerIntervalMin,
                homeVideoEnabled = homeVideoTimerEnabled,
                homeVideoIntervalMin = homeVideoTimerIntervalMin,
                lockImageEnabled = lockImageTimerEnabled,
                lockImageIntervalMin = lockImageTimerIntervalMin,
                lockVideoEnabled = lockVideoTimerEnabled,
                lockVideoIntervalMin = lockVideoTimerIntervalMin,
                pauseOnLock = mapOf(
                    "home_image" to homeImageTimerPauseOnLock,
                    "home_video" to homeVideoTimerPauseOnLock,
                    "lock_image" to lockImageTimerPauseOnLock,
                    "lock_video" to lockVideoTimerPauseOnLock
                ),
                onToggleEnabled = { key, enabled -> viewModel.setTimerEnabled(key, enabled) },
                onSetInterval = { key, min -> viewModel.setTimerInterval(key, min) },
                onPause = { key -> viewModel.pauseTimer(key) },
                onResume = { key -> viewModel.resumeTimer(key) },
                onSetPauseOnLock = { key, enabled -> viewModel.setTimerPauseOnLock(key, enabled) },
                onDismiss = { showTimerDialog = false }
            )
        }

        // ── Display Control overlay ───────────────────────────────────────────
        if (showDisplayControlDialog) {
            DisplayControlOverlay(
                cardBgColor = cardBgColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                popupWidthFraction = popupWidthFraction,
                scaleToPopupWidth = popupScaleDisplayControl,
                homeImageEnabled = displayControlHomeImage,
                homeVideoEnabled = displayControlHomeVideo,
                lockImageEnabled = displayControlLockImage,
                lockVideoEnabled = displayControlLockVideo,
                resetHomeTimer = displayControlResetHomeTimer,
                resetLockTimer = displayControlResetLockTimer,
                onToggle = { key, enabled -> viewModel.setDisplayControl(key, enabled) },
                onResetHomeTimer = { viewModel.setDisplayControlResetHomeTimer(it) },
                onResetLockTimer = { viewModel.setDisplayControlResetLockTimer(it) },
                onDismiss = { showDisplayControlDialog = false }
            )
        }

        // ── Pujie Watch Face picker overlay ──────────────────────────────────────
        if (showPujiePickerDialog) {
            PujieWatchFacePickerOverlay(
                presets = pujieWatchFaces,
                cardBgColor = cardBgColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                popupWidthFraction = popupWidthFraction,
                onSelect = { preset ->
                    viewModel.firePujiePreset(preset, context)
                    showPujiePickerDialog = false
                },
                onDismiss = { showPujiePickerDialog = false }
            )
        }
    }
}

// ── Folder manager overlay ────────────────────────────────────────────────────

@Composable
private fun FolderManagerOverlay(
    imageFolders: List<com.klvw.wallpaper.data.db.FolderEntity>,
    videoFolders: List<com.klvw.wallpaper.data.db.FolderEntity>,
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    popupWidthFraction: Float,
    scaleToPopupWidth: Boolean,
    defaultHomeImageUri: String?,
    defaultHomeVideoUri: String?,
    defaultLockImageUri: String?,
    defaultLockVideoUri: String?,
    onFolderSelected: (target: String, mediaType: String, uri: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTarget by remember { mutableStateOf("home") }
    var selectedMediaType by remember { mutableStateOf("image") }

    val folders = if (selectedMediaType == "video") videoFolders else imageFolders
    val currentDefaultUri = when {
        selectedTarget == "home" && selectedMediaType == "image" -> defaultHomeImageUri
        selectedTarget == "home" && selectedMediaType == "video" -> defaultHomeVideoUri
        selectedTarget == "lock" && selectedMediaType == "image" -> defaultLockImageUri
        else                                                     -> defaultLockVideoUri
    }

    PopupOverlay(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (scaleToPopupWidth) popupWidthFraction else 0.85f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBgColor)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "默认文件夹",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = primaryTextColor)
                    }
                }
                HorizontalDivider()

                // Target + media type selectors
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("home" to "桌面", "lock" to "锁屏").forEach { (v, lbl) ->
                        FilterChip(
                            selected = selectedTarget == v,
                            onClick = { selectedTarget = v },
                            label = { Text(lbl, softWrap = false) }
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    listOf("image" to "图片", "video" to "视频").forEach { (v, lbl) ->
                        FilterChip(
                            selected = selectedMediaType == v,
                            onClick = { selectedMediaType = v },
                            label = { Text(lbl, softWrap = false) }
                        )
                    }
                }

                HorizontalDivider()

                // Folder list
                if (folders.isEmpty()) {
                    Text(
                        "还没有保存${selectedMediaType}文件夹。请先在应用中添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                } else {
                    folders.forEach { folder ->
                        val isSelected = folder.uri == currentDefaultUri
                        val isAer = folder.uri.startsWith("klvw-aer://")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable { onFolderSelected(selectedTarget, selectedMediaType, folder.uri) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                when {
                                    isSelected -> Icons.Default.CheckCircle
                                    isAer -> Icons.Default.Lock
                                    else -> Icons.Default.Folder
                                },
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                folder.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else primaryTextColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Timer control overlay ─────────────────────────────────────────────────────

private val TIMER_INTERVALS = listOf(5, 10, 15, 30, 45, 60, 120, 240, 480, 1440)
private fun minuteLabel(min: Int) = if (min < 60) "${min}m" else "${min / 60}h"

private fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0) return "firing soon"
    val totalSec = remainingMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
}

@Composable
private fun TimerControlOverlay(
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    timerHighlightColor: Color,
    timerBorderColor: Color,
    popupWidthFraction: Float,
    scaleToPopupWidth: Boolean,
    timerPaused: Map<String, Boolean>,
    nextFireTimes: Map<String, Long>,
    homeImageEnabled: Boolean,
    homeImageIntervalMin: Int,
    homeVideoEnabled: Boolean,
    homeVideoIntervalMin: Int,
    lockImageEnabled: Boolean,
    lockImageIntervalMin: Int,
    lockVideoEnabled: Boolean,
    lockVideoIntervalMin: Int,
    pauseOnLock: Map<String, Boolean>,
    onToggleEnabled: (String, Boolean) -> Unit,
    onSetInterval: (String, Int) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onSetPauseOnLock: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val timers = listOf(
        TimerRow("home_image", "桌面图片", homeImageEnabled, homeImageIntervalMin),
        TimerRow("home_video", "桌面视频", homeVideoEnabled, homeVideoIntervalMin),
        TimerRow("lock_image", "锁屏图片", lockImageEnabled, lockImageIntervalMin),
        TimerRow("lock_video", "锁屏视频", lockVideoEnabled, lockVideoIntervalMin)
    )
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    PopupOverlay(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (scaleToPopupWidth) popupWidthFraction else 0.85f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBgColor)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "壁纸定时器",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = primaryTextColor)
                    }
                }
                val activeTimers = timers.filter { it.enabled }
                Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeTimers.isEmpty()) {
                            Text(
                                "没有运行中的定时器",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        } else {
                            activeTimers.forEach { row ->
                                val paused = timerPaused[row.key] == true
                                val fireAt = nextFireTimes[row.key]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        row.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (paused) {
                                        Text(
                                            "已暂停",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else if (fireAt != null) {
                                        Text(
                                            "${formatCountdown(fireAt - now)} 后",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                HorizontalDivider()
                timers.forEach { row ->
                    val paused = timerPaused[row.key] == true
                    val listState = rememberLazyListState()
                    val selectedIndex = TIMER_INTERVALS.indexOf(row.intervalMin).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(0.5.dp, timerBorderColor, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    row.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = primaryTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                                if (row.enabled && paused) {
                                    Text(
                                        "已暂停",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                if (row.enabled) {
                                    IconButton(
                                        onClick = { if (paused) onResume(row.key) else onPause(row.key) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = if (paused) "继续" else "暂停",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = row.enabled,
                                    onCheckedChange = { onToggleEnabled(row.key, it) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            if (row.enabled) {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val density = LocalDensity.current
                                    val viewportPx = with(density) { maxWidth.toPx() }
                                    val chipHalfPx = with(density) { 28.dp.toPx() }
                                    LaunchedEffect(selectedIndex) {
                                        listState.animateScrollToItem(
                                            selectedIndex,
                                            scrollOffset = -(viewportPx / 2 - chipHalfPx).toInt()
                                        )
                                    }
                                    LazyRow(
                                        state = listState,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        lazyItems(TIMER_INTERVALS) { min ->
                                            FilterChip(
                                                selected = row.intervalMin == min,
                                                onClick = { onSetInterval(row.key, min) },
                                                label = { Text(minuteLabel(min), style = MaterialTheme.typography.labelSmall) },
                                                colors = if (row.intervalMin == min) FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = timerHighlightColor,
                                                    selectedLabelColor = primaryTextColor
                                                ) else FilterChipDefaults.filterChipColors()
                                            )
                                        }
                                    }
                                }
                                // P.o.L toggle — only shown when timer is enabled
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "锁屏时暂停",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = pauseOnLock[row.key] == true,
                                        onCheckedChange = { onSetPauseOnLock(row.key, it) },
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("关闭", color = secondaryTextColor) }
            }
        }
    }
}

private data class TimerRow(
    val key: String,
    val label: String,
    val enabled: Boolean,
    val intervalMin: Int
)

// ── Shared overlay scaffold ───────────────────────────────────────────────────

@Composable
private fun PopupOverlay(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
        content = content
    )
}

// ── File select overlay ───────────────────────────────────────────────────────

@Composable
private fun FileSelectOverlay(
    imageFolders: List<FolderEntity>,
    videoFolders: List<FolderEntity>,
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    viewModel: KLVWPopupViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedTarget by remember { mutableStateOf("home") }
    var mediaType by remember { mutableStateOf(FolderType.IMAGE) }
    val folders = if (mediaType == FolderType.VIDEO) videoFolders else imageFolders
    var selectedFolder by remember(mediaType) { mutableStateOf(folders.firstOrNull()) }
    var mediaItems by remember { mutableStateOf<List<WallpaperItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(mediaType, folders) {
        if (selectedFolder == null || folders.none { it.uri == selectedFolder?.uri }) {
            selectedFolder = folders.firstOrNull()
        }
    }

    LaunchedEffect(selectedFolder) {
        val sel = selectedFolder ?: run { mediaItems = emptyList(); return@LaunchedEffect }
        loading = true
        mediaItems = withContext(Dispatchers.IO) { viewModel.getItemsFromFolder(sel.uri, mediaType) }
        loading = false
    }

    var showOptions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBgColor)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选择壁纸",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showOptions = !showOptions }) {
                        Text(
                            if (showOptions) "完成" else "Options",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = primaryTextColor)
                    }
                }

                // Options panel
                AnimatedVisibility(
                    visible = showOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        HorizontalDivider()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("home" to "桌面", "lock" to "锁屏", "both" to "两者").forEach { (v, lbl) ->
                                    FilterChip(
                                        selected = selectedTarget == v,
                                        onClick = { selectedTarget = v },
                                        label = { Text(lbl) }
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                FilterChip(
                                    selected = mediaType == FolderType.IMAGE,
                                    onClick = { mediaType = FolderType.IMAGE },
                                    label = { Text("图片") },
                                    leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = mediaType == FolderType.VIDEO,
                                    onClick = { mediaType = FolderType.VIDEO },
                                    label = { Text("视频") },
                                    leadingIcon = { Icon(Icons.Default.VideoLibrary, null, Modifier.size(16.dp)) }
                                )
                            }

                            var folderMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { if (folders.isNotEmpty()) folderMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        if (selectedFolder?.uri?.startsWith("klvw-aer://") == true) Icons.Default.Lock
                                        else Icons.Default.Folder,
                                        null, Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        selectedFolder?.displayName ?: "无文件夹",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = folderMenuExpanded,
                                    onDismissRequest = { folderMenuExpanded = false }
                                ) {
                                    folders.forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(folder.displayName) },
                                            leadingIcon = if (folder.uri.startsWith("klvw-aer://")) {
                                                { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) }
                                            } else null,
                                            onClick = { selectedFolder = folder; folderMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Media grid
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        folders.isEmpty() -> Text(
                            "还没有保存${if (mediaType == FolderType.VIDEO) "视频" else "图片"}文件夹。\n请先在应用中添加。",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(24.dp)
                        )
                        loading -> CircularProgressIndicator()
                        mediaItems.isEmpty() -> Text(
                            "此文件夹中没有文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(24.dp)
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(2.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(mediaItems, key = { it.uri.toString() }) { wallpaperItem ->
                                MediaThumb(
                                    item = wallpaperItem,
                                    isVideo = mediaType == FolderType.VIDEO,
                                    onClick = {
                                        scope.launch {
                                            selectedFolder?.let { folder ->
                                                viewModel.onFolderSelected(
                                                    if (mediaType == FolderType.IMAGE) "image" else "video",
                                                    selectedTarget,
                                                    folder.uri
                                                )
                                            }
                                            viewModel.setFileWallpaper(wallpaperItem, selectedTarget)
                                            onDismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(
    item: WallpaperItem,
    isVideo: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = remember(item.uri) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(240, 426)
                    .scale(Scale.FILL)
                    .build()
            },
            contentDescription = item.displayName,
            placeholder = ColorPainter(Color(0xFF2A2A2A)),
            error = ColorPainter(Color(0xFF1A1A1A)),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isVideo) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }
    }
}

// ── Grid layout ───────────────────────────────────────────────────────────────

@Composable
private fun PopupItemGrid(
    items: List<PopupItem>,
    columns: Int,
    primaryColor: Color,
    autoScale: Boolean,
    itemSizeDp: Int,
    onItemClick: (PopupItem) -> Unit
) {
    val gap = 8.dp
    val hPad = 12.dp
    val rows = items.chunked(columns)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = hPad, vertical = hPad)
    ) {
        val availableWidth = maxWidth
        val cellFromWidth = (availableWidth - gap * (columns - 1)) / columns

        val screenH = LocalConfiguration.current.screenHeightDp.dp
        // Estimate popup content height: 72% of screen minus the header (~56dp) and the grid's own padding
        val targetH = screenH * 0.72f - 56.dp - hPad * 2
        val numRows = ceil(items.size.toFloat() / columns).toInt().coerceAtLeast(1)
        val cellFromHeight = (targetH - gap * (numRows - 1)) / numRows

        val cellSize: Dp = if (autoScale) {
            minOf(cellFromWidth, cellFromHeight).coerceIn(36.dp, 140.dp)
        } else {
            itemSizeDp.dp
        }

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
                ) {
                    rowItems.forEach { item ->
                        PopupGridCell(
                            item = item,
                            primaryColor = primaryColor,
                            cellSize = cellSize,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupGridCell(
    item: PopupItem,
    primaryColor: Color,
    cellSize: Dp,
    onClick: () -> Unit
) {
    val iconSize = (cellSize.value * 0.30f).coerceIn(14f, 36f).dp
    val textStyle = if (cellSize < 60.dp) MaterialTheme.typography.labelSmall
                    else MaterialTheme.typography.labelMedium
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(
                imageVector = item.actionIcon(),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.label,
                style = textStyle,
                color = primaryColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

// ── Display Control overlay ───────────────────────────────────────────────────

@Composable
private fun DisplayControlOverlay(
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    popupWidthFraction: Float,
    scaleToPopupWidth: Boolean,
    homeImageEnabled: Boolean,
    homeVideoEnabled: Boolean,
    lockImageEnabled: Boolean,
    lockVideoEnabled: Boolean,
    resetHomeTimer: Boolean,
    resetLockTimer: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onResetHomeTimer: (Boolean) -> Unit,
    onResetLockTimer: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = listOf(
        Triple("home_image", "桌面图片", homeImageEnabled),
        Triple("home_video", "桌面视频", homeVideoEnabled),
        Triple("lock_image", "锁屏图片", lockImageEnabled),
        Triple("lock_video", "锁屏视频", lockVideoEnabled)
    )
    val anyHomeEnabled = homeImageEnabled || homeVideoEnabled
    val anyLockEnabled = lockImageEnabled || lockVideoEnabled
    PopupOverlay(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (scaleToPopupWidth) popupWidthFraction else 0.85f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBgColor)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "显示控制",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = primaryTextColor)
                    }
                }
                Text(
                    "每次解锁时轮换壁纸。同一屏幕图片与视频只能开一个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                HorizontalDivider()
                rows.forEach { (key, label, enabled) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { onToggle(key, it) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                if (anyHomeEnabled || anyLockEnabled) {
                    HorizontalDivider()
                    Text(
                        "解锁时重置定时",
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryTextColor
                    )
                    if (anyHomeEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = resetHomeTimer,
                                onCheckedChange = onResetHomeTimer
                            )
                            Text(
                                "解锁时重置桌面定时",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryTextColor
                            )
                        }
                    }
                    if (anyLockEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = resetLockTimer,
                                onCheckedChange = onResetLockTimer
                            )
                            Text(
                                "解锁时重置锁屏定时",
                                style = MaterialTheme.typography.bodySmall,
                                color = primaryTextColor
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("关闭", color = secondaryTextColor) }
            }
        }
    }
}

// ── Pujie Watch Face picker overlay ──────────────────────────────────────────

@Composable
private fun PujieWatchFacePickerOverlay(
    presets: List<PujieWatchFacePreset>,
    cardBgColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    popupWidthFraction: Float,
    onSelect: (PujieWatchFacePreset) -> Unit,
    onDismiss: () -> Unit
) {
    PopupOverlay(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth(popupWidthFraction)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(cardBgColor)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pujie 表盘", style = MaterialTheme.typography.titleMedium, color = primaryTextColor)
                Spacer(Modifier.height(4.dp))
                if (presets.isEmpty()) {
                    Text(
                        "尚未配置表盘。\n请到设置 → Pujie 表盘 中添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                } else {
                    presets.forEach { preset ->
                        TextButton(
                            onClick = { onSelect(preset) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                preset.displayName,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("关闭", color = secondaryTextColor) }
            }
        }
    }
}

private fun PopupItem.actionIcon(): ImageVector = when (actionType) {
    POPUP_ACTION_RANDOM_IMAGE  -> Icons.Default.Image
    POPUP_ACTION_RANDOM_VIDEO  -> Icons.Default.VideoLibrary
    POPUP_ACTION_STATIC_IMAGE  -> Icons.Default.Photo
    POPUP_ACTION_RESTORE       -> Icons.Default.Restore
    POPUP_ACTION_ICON_COLOR    -> Icons.Default.Tune
    POPUP_ACTION_FOLDER_SELECT -> Icons.Default.Folder
    POPUP_ACTION_SELECT_FILE   -> Icons.Default.PermMedia
    POPUP_ACTION_TIMER             -> Icons.Default.Timer
    POPUP_ACTION_FOLDER_SELECT_ALL -> Icons.Default.FolderSpecial
    POPUP_ACTION_DISPLAY_CONTROL   -> Icons.Default.LockOpen
    POPUP_ACTION_PUJIE_WATCH_FACE  -> Icons.Default.Watch
    POPUP_ACTION_AER_MOUNT         -> Icons.Default.Lock
    else                           -> Icons.Default.Settings
}

private fun PopupItem.subtitle(): String {
    val targetLabel = target.replaceFirstChar { it.uppercase() }
    return when (actionType) {
        POPUP_ACTION_RANDOM_IMAGE  -> "$targetLabel · Random Image"
        POPUP_ACTION_RANDOM_VIDEO  -> "$targetLabel · Random Video"
        POPUP_ACTION_STATIC_IMAGE  -> "$targetLabel · Static Image"
        POPUP_ACTION_RESTORE       -> "$targetLabel · Restore Previous"
        POPUP_ACTION_ICON_COLOR    -> "Icon Color · ${iconColor.replaceFirstChar { it.uppercase() }}"
        POPUP_ACTION_FOLDER_SELECT -> "$targetLabel · Set ${mediaType.replaceFirstChar { it.uppercase() }} Folder"
        POPUP_ACTION_SELECT_FILE   -> "Browse & select file"
        POPUP_ACTION_TIMER             -> "壁纸定时器"
        POPUP_ACTION_FOLDER_SELECT_ALL -> "All Default Folders"
        POPUP_ACTION_DISPLAY_CONTROL   -> "显示控制"
        POPUP_ACTION_PUJIE_WATCH_FACE  -> "Pujie 表盘"
        POPUP_ACTION_AER_MOUNT         -> "Mount Aer Storage"
        else                           -> ""
    }
}

@Composable
private fun computeListItemPadding(itemCount: Int, autoScale: Boolean, itemSizeDp: Int): Dp {
    if (!autoScale) return itemSizeDp.dp.coerceIn(4.dp, 24.dp)
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    // Available popup height: 72% screen – header (~56dp)
    val availH = screenH * 0.72f - 56.dp
    // Each row has ~38dp of text content (bodyMedium + bodySmall); the rest is vertical padding
    val textH = 38.dp
    val count = itemCount.coerceAtLeast(1)
    val padPerSide = ((availH - textH * count) / count / 2).coerceIn(4.dp, 20.dp)
    return padPerSide
}

@Composable
private fun PopupItemRow(
    item: PopupItem,
    primaryColor: Color,
    secondaryColor: Color,
    verticalPadding: Dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = item.actionIcon(),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = primaryColor)
            Text(
                item.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
    }
}
