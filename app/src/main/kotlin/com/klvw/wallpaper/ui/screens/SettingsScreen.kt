package com.klvw.wallpaper.ui.screens

import android.app.Activity.RESULT_OK
import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import com.klvw.wallpaper.aer.AerLockStore
import com.klvw.wallpaper.aer.AerMountActivity
import com.klvw.wallpaper.aer.AerShell
import com.klvw.wallpaper.aer.ui.AerActivity
import com.klvw.wallpaper.tile.KLVWPopupViewModel
import com.klvw.wallpaper.tile.PopupItem
import com.klvw.wallpaper.tile.PujieWatchFacePreset
import com.klvw.wallpaper.wear.WatchPopupItem
import com.klvw.wallpaper.wear.toWatchJsonString
import com.klvw.wallpaper.tile.PujieWatchFacePreset.Companion.toJsonString
import com.klvw.wallpaper.ui.theme.SurfaceGlass
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel

@Composable
fun SettingsScreen(viewModel: WallpaperViewModel, popupViewModel: KLVWPopupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        SettingsSection(title = "性能") {
            SettingToggle(
                icon = Icons.Default.Bolt,
                title = "Vulkan GPU 渲染",
                subtitle = "使用 Vulkan 进行硬件加速图片渲染",
                checked = state.useVulkan,
                onCheckedChange = { viewModel.setUseVulkan(it) }
            )
            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
            val imageLoader = SingletonImageLoader.get(context)
            var cacheCleared by remember { mutableStateOf(false) }
            SettingAction(
                icon = Icons.Default.DeleteSweep,
                title = "清除缩略图缓存",
                subtitle = if (cacheCleared) "缓存已清除" else "清除缩略图缓存，使替换后的文件显示最新内容",
                onClick = {
                    imageLoader.memoryCache?.clear()
                    imageLoader.diskCache?.clear()
                    cacheCleared = true
                }
            )
        }

        SettingsSection(title = "壁纸显示") {
            WallpaperDisplaySection(viewModel = viewModel)
        }

        SettingsSection(title = "壁纸手势") {
            SettingToggle(
                icon = Icons.Default.TouchApp,
                title = "双击桌面切换壁纸",
                subtitle = "需已启用 KLVW 动态壁纸；按当前桌面图/视频类型，从对应默认文件夹轮换。部分桌面可能拦截触摸。",
                checked = state.doubleTapEnabled,
                onCheckedChange = { viewModel.setDoubleTapEnabled(it) }
            )
        }

        SettingsSection(title = "动态壁纸") {
            SettingAction(
                icon = Icons.Default.Tune,
                title = "启用动态壁纸",
                subtitle = "将 KLVW 设为动态壁纸服务",
                onClick = {
                    try {
                        val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            )
        }

        SettingsSection(title = "KLVW 显示控制") {
            DisplayControlSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "壁纸定时器") {
            TimerControlSettingsSection(popupViewModel = popupViewModel)
            TimerNotificationSection(popupViewModel = popupViewModel)
            TimerGlobalOffSection(popupViewModel = popupViewModel)
        }

        // Less-used: status bar / popup menu / tile / watch faces — above watch section
        SettingsSection(title = "状态栏图标") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("图标颜色", style = MaterialTheme.typography.bodyMedium)
                    Text("强制覆盖状态栏图标明暗", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("auto" to "自动", "dark" to "深色", "light" to "浅色").forEach { (value, label) ->
                            FilterChip(
                                selected = state.forceIconColor == value,
                                onClick = { viewModel.setForceIconColor(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        SettingsSection(title = "KLVW 弹层菜单") {
            KLVWPopupSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "KLVW 快捷设置磁贴") {
            QuickSetTileSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "Pujie 表盘") {
            PujieWatchFaceSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "KLVW 手表") {
            KLVWWatchSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "Aer 私密存储") {
            AerStorageSection(popupViewModel)
        }

        SettingsSection(title = "弹层外观") {
            PopupAppearanceSection(popupViewModel = popupViewModel)
        }

        SettingsSection(title = "关于") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlass)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("KLVW 壁纸", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("版本 7.0.0", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Samsung UI 8 · Android 16 · Vulkan GPU",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Tasker 插件：在 Tasker → 新建任务 → + → 插件 → KLVW Wallpaper 中添加动作",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun KLVWPopupSection(popupViewModel: KLVWPopupViewModel) {
    val items by popupViewModel.popupItems.collectAsStateWithLifecycle()
    val imageFolders by popupViewModel.imageFolders.collectAsStateWithLifecycle()
    val videoFolders by popupViewModel.videoFolders.collectAsStateWithLifecycle()
    val staticImages by popupViewModel.staticImages.collectAsStateWithLifecycle()
    val popupLayout by popupViewModel.popupLayout.collectAsStateWithLifecycle()
    val popupGridColumns by popupViewModel.popupGridColumns.collectAsStateWithLifecycle()
    val popupWidthFraction by popupViewModel.popupWidthFraction.collectAsStateWithLifecycle()
    val popupAutoScale by popupViewModel.popupAutoScale.collectAsStateWithLifecycle()
    val popupItemSizeDp by popupViewModel.popupItemSizeDp.collectAsStateWithLifecycle()
    val popupScaleIconColor by popupViewModel.popupScaleIconColor.collectAsStateWithLifecycle()
    val popupScaleFolderSelect by popupViewModel.popupScaleFolderSelect.collectAsStateWithLifecycle()
    val popupScaleTimer by popupViewModel.popupScaleTimer.collectAsStateWithLifecycle()
    val popupScaleFolderSelectAll by popupViewModel.popupScaleFolderSelectAll.collectAsStateWithLifecycle()
    val popupScaleDisplayControl by popupViewModel.popupScaleDisplayControl.collectAsStateWithLifecycle()

    var editorItem by remember { mutableStateOf<PopupItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OverlayPermissionRow()
        HorizontalDivider(thickness = 0.5.dp)

        // ── Layout & auto-scale (inline) ─────────────────────────────────────
        Text("项目布局", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = popupLayout == "list",
                    onClick = { popupViewModel.setPopupLayout("list") },
                    label = { Text("列表") },
                    leadingIcon = { Icon(Icons.Default.ViewList, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = popupLayout == "grid",
                    onClick = { popupViewModel.setPopupLayout("grid") },
                    label = { Text("网格") },
                    leadingIcon = { Icon(Icons.Default.GridView, null, Modifier.size(16.dp)) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Checkbox(
                    checked = popupAutoScale,
                    onCheckedChange = { popupViewModel.setPopupAutoScale(it) }
                )
                Text("自动缩放", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (popupLayout == "grid") {
            Text("列数", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..5).forEach { n ->
                    FilterChip(
                        selected = popupGridColumns == n,
                        onClick = { popupViewModel.setPopupGridColumns(n) },
                        label = { Text("$n") }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // ── Size & Scaling ────────────────────────────────────────────────────
        Text("弹层宽度", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.60f to "窄", 0.78f to "中", 0.92f to "宽").forEach { (frac, lbl) ->
                FilterChip(
                    selected = kotlin.math.abs(popupWidthFraction - frac) < 0.02f,
                    onClick = { popupViewModel.setPopupWidthFraction(frac) },
                    label = { Text(lbl) }
                )
            }
        }
        if (!popupAutoScale) {
            Text("手动项目尺寸", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(40 to "XS", 56 to "S", 72 to "M", 88 to "L", 104 to "XL").forEach { (sz, lbl) ->
                    FilterChip(
                        selected = popupItemSizeDp == sz,
                        onClick = { popupViewModel.setPopupItemSizeDp(sz) },
                        label = { Text(lbl) }
                    )
                }
            }
        }

        Text("将宽度应用到子弹层", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleIconColor, onCheckedChange = { popupViewModel.setPopupScaleIconColor(it) })
            Text("图标颜色选择器", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleFolderSelect, onCheckedChange = { popupViewModel.setPopupScaleFolderSelect(it) })
            Text("文件夹选择器", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleTimer, onCheckedChange = { popupViewModel.setPopupScaleTimer(it) })
            Text("定时弹层", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleFolderSelectAll, onCheckedChange = { popupViewModel.setPopupScaleFolderSelectAll(it) })
            Text("全部文件夹弹层", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = popupScaleDisplayControl, onCheckedChange = { popupViewModel.setPopupScaleDisplayControl(it) })
            Text("显示控制弹层", style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(thickness = 0.5.dp)

        if (items.isEmpty()) {
            Text(
                "还没有弹层项目。点「添加项目」开始配置弹层菜单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(item.actionSubtitle(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Move up
                    if (index > 0) {
                        IconButton(onClick = {
                            val mutable = items.toMutableList()
                            mutable.add(index - 1, mutable.removeAt(index))
                            popupViewModel.saveItems(mutable)
                        }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移",
                            modifier = Modifier.size(20.dp)) }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    // Move down
                    if (index < items.lastIndex) {
                        IconButton(onClick = {
                            val mutable = items.toMutableList()
                            mutable.add(index + 1, mutable.removeAt(index))
                            popupViewModel.saveItems(mutable)
                        }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移",
                            modifier = Modifier.size(20.dp)) }
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    // Edit
                    IconButton(onClick = { editorItem = item; showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(20.dp))
                    }
                    // Delete
                    IconButton(onClick = { popupViewModel.saveItems(items.filter { it.id != item.id }) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (index < items.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }

        Button(
            onClick = { editorItem = null; showEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("添加项目")
        }
    }

    if (showEditor) {
        PopupItemEditorDialog(
            existingItem = editorItem,
            imageFolders = imageFolders,
            videoFolders = videoFolders,
            staticImages = staticImages,
            onSave = { saved ->
                val mutable = items.toMutableList()
                val existingIndex = mutable.indexOfFirst { it.id == saved.id }
                if (existingIndex >= 0) mutable[existingIndex] = saved else mutable.add(saved)
                popupViewModel.saveItems(mutable)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun QuickSetTileSection(popupViewModel: KLVWPopupViewModel) {
    val homeAction by popupViewModel.quickSetHomeAction.collectAsStateWithLifecycle()
    val lockAction by popupViewModel.quickSetLockAction.collectAsStateWithLifecycle()
    val homeStaticUri by popupViewModel.quickSetHomeStaticUri.collectAsStateWithLifecycle()
    val lockStaticUri by popupViewModel.quickSetLockStaticUri.collectAsStateWithLifecycle()
    val staticImages by popupViewModel.staticImages.collectAsStateWithLifecycle()
    val watchPresetId by popupViewModel.quickSetWatchPresetId.collectAsStateWithLifecycle()
    val pujiePresets by popupViewModel.pujieWatchFaces.collectAsStateWithLifecycle()

    val actions = listOf(
        "random_image" to "随机图片",
        "random_video" to "随机视频",
        "static_image" to "静态图"
    )
    val isKillSwitch = homeAction == "static_image" && lockAction == "static_image"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "配置点按「KLVW 快捷设置」磁贴时的行为。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Home action
        Text("桌面", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { (value, label) ->
                FilterChip(
                    selected = homeAction == value,
                    onClick = { popupViewModel.setQuickSetHomeAction(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (homeAction == "static_image") {
            if (staticImages.isEmpty()) {
                Text(
                    "还没有保存静态图。请先在「KLVW 弹层菜单」中添加「静态图」动作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                staticImages.forEach { img ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = homeStaticUri == img.uri.toString(),
                            onClick = { popupViewModel.setQuickSetHomeStaticUri(img.uri.toString()) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // Lock action
        Text("锁屏", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEach { (value, label) ->
                FilterChip(
                    selected = lockAction == value,
                    onClick = { popupViewModel.setQuickSetLockAction(value) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        if (lockAction == "static_image") {
            if (staticImages.isEmpty()) {
                Text(
                    "还没有保存静态图。请先在「KLVW 弹层菜单」中添加「静态图」动作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                staticImages.forEach { img ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = lockStaticUri == img.uri.toString(),
                            onClick = { popupViewModel.setQuickSetLockStaticUri(img.uri.toString()) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(img.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        // Watch action
        Text("表盘", style = MaterialTheme.typography.labelMedium)
        if (pujiePresets.isEmpty()) {
            Text(
                "还没有保存 Pujie 表盘。请在下方「Pujie 表盘」区域添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = watchPresetId == null,
                    onClick = { popupViewModel.setQuickSetWatchPresetId(null) }
                )
                Spacer(Modifier.width(4.dp))
                Text("无（跳过表盘）", style = MaterialTheme.typography.bodySmall)
            }
            pujiePresets.forEach { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = watchPresetId == preset.id,
                        onClick = { popupViewModel.setQuickSetWatchPresetId(preset.id) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(preset.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (isKillSwitch) {
            HorizontalDivider(thickness = 0.5.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "桌面与锁屏都设为静态图时，此磁贴会变成总开关：点一下应用静态壁纸并停用全部 KLVW 壁纸功能；长按可重新启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Text(
            "随机图片/视频时，磁贴会使用弹层「文件夹选择」里设置的默认文件夹。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun PopupItem.actionSubtitle(): String {
    val targetLabel = target.replaceFirstChar { it.uppercase() }
    return when (actionType) {
        "random_image"   -> "$targetLabel · Random Image"
        "random_video"   -> "$targetLabel · Random Video"
        "static_image"   -> "$targetLabel · Static Image"
        "restore"        -> "$targetLabel · Restore Previous"
        "icon_color"     -> "Icon Color · ${iconColor.replaceFirstChar { it.uppercase() }}"
        "folder_select"  -> "$targetLabel · Set ${mediaType.replaceFirstChar { it.uppercase() }} Folder"
        else             -> actionType
    }
}

@Composable
private fun OverlayPermissionRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("显示在最上层", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Granted — popup can overlay other apps"
                else "Not granted — popup may not show over other apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }) { Text("授权") }
        }
    }
}

// ── Popup Appearance ──────────────────────────────────────────────────────────

private fun colorToHex(color: Color): String = "#%08X".format(color.toArgb())
private fun hexToColor(hex: String): Color? = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }

@Composable
private fun PujieWatchFaceSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val presets by popupViewModel.pujieWatchFaces.collectAsStateWithLifecycle()

    val pujieInstalled = remember {
        try { context.packageManager.getPackageInfo("com.pujie.watchfaces", 0); true }
        catch (_: Exception) { false }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bundle = result.data?.getBundleExtra("com.twofortyfouram.locale.intent.extra.BUNDLE")
                ?: return@rememberLauncherForActivityResult
            val watchFaceName = bundle.getString("watchface")
                ?: return@rememberLauncherForActivityResult
            val receiverClass = bundle.getString("net.dinglisch.android.tasker.extras.ACTION_RUNNER_CLASS")
                ?: "com.pujie.wristwear.pujieblack.tasker.EditPresetActivity\$TaskerWatchFaceReceiver"
            val inputClass = bundle.getString("net.dinglisch.android.tasker.extras.ACTION_INPUT_CLASS") ?: ""
            val newPreset = PujieWatchFacePreset(
                displayName = watchFaceName,
                watchFaceName = watchFaceName,
                receiverClass = receiverClass,
                inputClass = inputClass
            )
            popupViewModel.savePujieWatchFaces(presets + newPreset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!pujieInstalled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    "未找到 Pujie 表盘应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            if (presets.isEmpty()) {
                Text(
                    "还没有添加表盘。点「添加表盘」进行配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                presets.forEachIndexed { index, preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            preset.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { popupViewModel.savePujieWatchFaces(presets.toMutableList().also { it.removeAt(index) }) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
            }
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        Intent("com.twofortyfouram.locale.intent.action.EDIT_SETTING").apply {
                            setClassName("com.pujie.watchfaces",
                                "com.pujie.wristwear.pujieblack.tasker.EditPresetActivity")
                            // Required by joaomgcd's TaskerPluginLibrary: signals a Tasker-compatible host
                            putExtra("com.twofortyfouram.locale.intent.extra.BUNDLE", Bundle())
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加表盘", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "在弹层菜单中添加「Pujie 表盘」项目后，可从弹层访问这些表盘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DisplayControlSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val homeImage by popupViewModel.displayControlHomeImage.collectAsStateWithLifecycle()
    val homeVideo by popupViewModel.displayControlHomeVideo.collectAsStateWithLifecycle()
    val lockImage by popupViewModel.displayControlLockImage.collectAsStateWithLifecycle()
    val lockVideo by popupViewModel.displayControlLockVideo.collectAsStateWithLifecycle()
    val resetHomeTimer by popupViewModel.displayControlResetHomeTimer.collectAsStateWithLifecycle()
    val resetLockTimer by popupViewModel.displayControlResetLockTimer.collectAsStateWithLifecycle()
    val anyHomeEnabled = homeImage || homeVideo
    val anyLockEnabled = lockImage || lockVideo

    // Track permission state — re-evaluated on each recomposition (user may have just granted)
    var phoneStateGranted by remember { mutableStateOf(false) }
    var usageAccessGranted by remember { mutableStateOf(false) }
    var deviceAdminActive by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneStateGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_PHONE_STATE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
                usageAccessGranted = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                ) == android.app.AppOpsManager.MODE_ALLOWED

                val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val adminComponent = android.content.ComponentName(context, com.klvw.wallpaper.tile.KLVWDeviceAdminReceiver::class.java)
                deviceAdminActive = dpm.isAdminActive(adminComponent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val phoneStateRequest = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> phoneStateGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("权限", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        listOf(
            Triple("Phone State", phoneStateGranted) { phoneStateRequest.launch(android.Manifest.permission.READ_PHONE_STATE) },
            Triple("Usage Access", usageAccessGranted) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            Triple("Device Admin", deviceAdminActive) {
                val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val adminComponent = android.content.ComponentName(context, com.klvw.wallpaper.tile.KLVWDeviceAdminReceiver::class.java)
                context.startActivity(
                    Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Required for KLVW Display Control to read device lock state.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        ).forEach { (label, granted, grant) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (!granted) {
                    TextButton(onClick = grant) { Text("授权") }
                }
            }
        }

        HorizontalDivider()
        Text("解锁时 — 轮换壁纸", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "同一屏幕图片与视频只能开一个，避免互相覆盖。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        listOf(
            Triple("home_image", "桌面图片", homeImage),
            Triple("home_video", "桌面视频", homeVideo),
            Triple("lock_image", "锁屏图片", lockImage),
            Triple("lock_video", "锁屏视频", lockVideo)
        ).forEach { (key, label, enabled) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { popupViewModel.setDisplayControl(key, it) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        if (anyHomeEnabled || anyLockEnabled) {
            HorizontalDivider()
            Text("解锁时重置定时", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (anyHomeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = resetHomeTimer, onCheckedChange = { popupViewModel.setDisplayControlResetHomeTimer(it) })
                    Text("解锁时重置桌面定时", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (anyLockEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = resetLockTimer, onCheckedChange = { popupViewModel.setDisplayControlResetLockTimer(it) })
                    Text("解锁时重置锁屏定时", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PopupAppearanceSection(popupViewModel: KLVWPopupViewModel) {
    val bgHex by popupViewModel.popupBgColor.collectAsStateWithLifecycle()
    val primaryHex by popupViewModel.popupPrimaryTextColor.collectAsStateWithLifecycle()
    val secondaryHex by popupViewModel.popupSecondaryTextColor.collectAsStateWithLifecycle()
    val timerHighlightHex by popupViewModel.popupTimerHighlightColor.collectAsStateWithLifecycle()
    val timerBorderHex by popupViewModel.popupTimerBorderColor.collectAsStateWithLifecycle()

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val themePrimary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "自定义弹层颜色。保持「默认」则跟随系统主题。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ColorPickerRow(
            label = "背景色",
            storedHex = bgHex,
            defaultColor = themeSurface.copy(alpha = 0.95f),
            onColorChange = { popupViewModel.setPopupBgColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "主文字颜色",
            storedHex = primaryHex,
            defaultColor = themeOnSurface,
            onColorChange = { popupViewModel.setPopupPrimaryTextColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "次文字颜色",
            storedHex = secondaryHex,
            defaultColor = themeOnSurfaceVariant,
            onColorChange = { popupViewModel.setPopupSecondaryTextColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "定时间隔高亮色",
            storedHex = timerHighlightHex,
            defaultColor = themePrimary.copy(alpha = 0.35f),
            onColorChange = { popupViewModel.setPopupTimerHighlightColor(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        ColorPickerRow(
            label = "定时分区边框色",
            storedHex = timerBorderHex,
            defaultColor = Color.White.copy(alpha = 0.15f),
            onColorChange = { popupViewModel.setPopupTimerBorderColor(it) }
        )
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    storedHex: String?,
    defaultColor: Color,
    onColorChange: (String?) -> Unit
) {
    val resolvedColor = storedHex?.let { hexToColor(it) } ?: defaultColor
    var expanded by remember { mutableStateOf(false) }

    var alpha by remember { mutableFloatStateOf(resolvedColor.alpha * 255f) }
    var red   by remember { mutableFloatStateOf(resolvedColor.red   * 255f) }
    var green by remember { mutableFloatStateOf(resolvedColor.green * 255f) }
    var blue  by remember { mutableFloatStateOf(resolvedColor.blue  * 255f) }

    LaunchedEffect(storedHex) {
        val c = storedHex?.let { hexToColor(it) } ?: defaultColor
        alpha = c.alpha * 255f
        red   = c.red   * 255f
        green = c.green * 255f
        blue  = c.blue  * 255f
    }

    val previewColor = Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    fun commitColor() {
        onColorChange(colorToHex(Color(red / 255f, green / 255f, blue / 255f, alpha / 255f)))
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .background(previewColor)
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (storedHex != null) {
                TextButton(
                    onClick = { onColorChange(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("默认", style = MaterialTheme.typography.labelSmall) }
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                @Composable
                fun SliderRow(ch: String, value: Float, onValueChange: (Float) -> Unit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(ch, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(16.dp))
                        Slider(
                            value = value,
                            onValueChange = onValueChange,
                            onValueChangeFinished = { commitColor() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(value.toInt().toString(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                    }
                }
                SliderRow("A", alpha) { alpha = it }
                SliderRow("R", red)   { red   = it }
                SliderRow("G", green) { green = it }
                SliderRow("B", blue)  { blue  = it }
            }
        }
    }
}

@Composable
private fun TimerNotificationSection(popupViewModel: KLVWPopupViewModel) {
    val enabled by popupViewModel.timerUnlockNotification.collectAsStateWithLifecycle()
    val onlyWhenRunning by popupViewModel.timerNotificationOnlyWhenRunning.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("解锁时显示定时状态", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "解锁时显示通知，提示各定时器剩余时间。" +
                    "Expand it to pause or reset individual timers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { popupViewModel.setTimerUnlockNotification(it) },
                modifier = Modifier.height(24.dp)
            )
        }
        if (enabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("仅在运行时显示", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "全部定时器暂停时隐藏通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = onlyWhenRunning,
                    onCheckedChange = { popupViewModel.setTimerNotificationOnlyWhenRunning(it) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TimerGlobalOffSection(popupViewModel: KLVWPopupViewModel) {
    val pauseOnGlobalOff by popupViewModel.pauseTimersOnGlobalOff.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("全局关闭时暂停定时", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "按下「全局关闭」快捷设置按钮时，所有运行中的定时器会暂停。" +
                    "automatically. Resume them from the timer notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = pauseOnGlobalOff,
                onCheckedChange = { popupViewModel.setPauseTimersOnGlobalOff(it) },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

private val SETTINGS_TIMER_INTERVALS = listOf(5, 10, 15, 30, 45, 60, 120, 240, 480, 1440)

private fun settingsMinuteLabel(min: Int): String =
    if (min < 60) "${min} 分钟" else "${min / 60} 小时"

private fun settingsFormatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "即将切换"
    val totalSec = remainingMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s)
        else -> "%d:%02d".format(m, s)
    }
}

private data class SettingsTimerRow(
    val key: String,
    val label: String,
    val enabled: Boolean,
    val intervalMin: Int
)

@Composable
private fun TimerControlSettingsSection(popupViewModel: KLVWPopupViewModel) {
    val homeImageEnabled by popupViewModel.homeImageTimerEnabled.collectAsStateWithLifecycle()
    val homeImageInterval by popupViewModel.homeImageTimerIntervalMin.collectAsStateWithLifecycle()
    val homeVideoEnabled by popupViewModel.homeVideoTimerEnabled.collectAsStateWithLifecycle()
    val homeVideoInterval by popupViewModel.homeVideoTimerIntervalMin.collectAsStateWithLifecycle()
    val lockImageEnabled by popupViewModel.lockImageTimerEnabled.collectAsStateWithLifecycle()
    val lockImageInterval by popupViewModel.lockImageTimerIntervalMin.collectAsStateWithLifecycle()
    val lockVideoEnabled by popupViewModel.lockVideoTimerEnabled.collectAsStateWithLifecycle()
    val lockVideoInterval by popupViewModel.lockVideoTimerIntervalMin.collectAsStateWithLifecycle()
    val homeImagePol by popupViewModel.homeImageTimerPauseOnLock.collectAsStateWithLifecycle()
    val homeVideoPol by popupViewModel.homeVideoTimerPauseOnLock.collectAsStateWithLifecycle()
    val lockImagePol by popupViewModel.lockImageTimerPauseOnLock.collectAsStateWithLifecycle()
    val lockVideoPol by popupViewModel.lockVideoTimerPauseOnLock.collectAsStateWithLifecycle()
    val timerPaused by popupViewModel.timerPaused.collectAsStateWithLifecycle()
    val nextFireTimes by popupViewModel.timerNextFireTimes.collectAsStateWithLifecycle()

    val timers = listOf(
        SettingsTimerRow("home_image", "桌面图片", homeImageEnabled, homeImageInterval),
        SettingsTimerRow("home_video", "桌面视频", homeVideoEnabled, homeVideoInterval),
        SettingsTimerRow("lock_image", "锁屏图片", lockImageEnabled, lockImageInterval),
        SettingsTimerRow("lock_video", "锁屏视频", lockVideoEnabled, lockVideoInterval)
    )
    val pauseOnLock = mapOf(
        "home_image" to homeImagePol,
        "home_video" to homeVideoPol,
        "lock_image" to lockImagePol,
        "lock_video" to lockVideoPol
    )

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
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
            "分别开关桌面/锁屏定时并设置间隔。同一屏幕图片与视频定时互斥，开一个会关掉另一个。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val activeTimers = timers.filter { it.enabled }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("运行状态", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (activeTimers.isEmpty()) {
                Text(
                    "当前没有开启的定时器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                activeTimers.forEach { row ->
                    val paused = timerPaused[row.key] == true
                    val fireAt = nextFireTimes[row.key]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.label, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        when {
                            paused -> Text(
                                "已暂停",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            fireAt != null -> Text(
                                "${settingsFormatCountdown(fireAt - now)} 后",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        timers.forEach { row ->
            val paused = timerPaused[row.key] == true
            val listState = rememberLazyListState()
            val selectedIndex = SETTINGS_TIMER_INTERVALS.indexOf(row.intervalMin).coerceAtLeast(0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
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
                            onClick = {
                                if (paused) popupViewModel.resumeTimer(row.key)
                                else popupViewModel.pauseTimer(row.key)
                            },
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
                        onCheckedChange = { popupViewModel.setTimerEnabled(row.key, it) },
                        modifier = Modifier.height(24.dp)
                    )
                }
                if (row.enabled) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val viewportPx = with(density) { maxWidth.toPx() }
                        val chipHalfPx = with(density) { 36.dp.toPx() }
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
                            lazyItems(SETTINGS_TIMER_INTERVALS) { min ->
                                FilterChip(
                                    selected = row.intervalMin == min,
                                    onClick = { popupViewModel.setTimerInterval(row.key, min) },
                                    label = {
                                        Text(
                                            settingsMinuteLabel(min),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "锁屏时暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = pauseOnLock[row.key] == true,
                            onCheckedChange = { popupViewModel.setTimerPauseOnLock(row.key, it) },
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WallpaperDisplaySection(viewModel: WallpaperViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("适应模式", style = MaterialTheme.typography.bodyMedium)
            Text(
                "图片与视频均生效。填充：裁切铺满；适应：完整显示可留黑边；拉伸：强制铺满（视频近似为填充）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "fill" to "填充",
                    "fit" to "适应",
                    "stretch" to "拉伸"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = state.wallpaperScaleMode == value,
                        onClick = { viewModel.setWallpaperScaleMode(value) },
                        label = { Text(label) }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动旋转适配", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "方向与屏幕不一致时自动旋转 90°（图片与视频均支持）。可再选顺时针或逆时针。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.rotateMismatchEnabled,
                onCheckedChange = { viewModel.setRotateMismatchEnabled(it) },
                modifier = Modifier.height(24.dp)
            )
        }

        if (state.rotateMismatchEnabled) {
            Text("旋转方向", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.rotateMismatchCw,
                    onClick = { viewModel.setRotateMismatchCw(true) },
                    label = { Text("顺时针") }
                )
                FilterChip(
                    selected = !state.rotateMismatchCw,
                    onClick = { viewModel.setRotateMismatchCw(false) },
                    label = { Text("逆时针") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("打开") }
    }
}

// ── KLVW Watch ────────────────────────────────────────────────────────────────

@Composable
private fun KLVWWatchSection(popupViewModel: KLVWPopupViewModel) {
    val watchJson by popupViewModel.watchItemsJson.collectAsStateWithLifecycle()
    val imageFolders by popupViewModel.imageFolders.collectAsStateWithLifecycle()
    val videoFolders by popupViewModel.videoFolders.collectAsStateWithLifecycle()
    val globalOffVibrate by popupViewModel.watchGlobalOffVibrate.collectAsStateWithLifecycle()
    var items by remember(watchJson) {
        mutableStateOf(WatchPopupItem.listFromJson(watchJson))
    }
    var showEditor by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WatchPopupItem?>(null) }
    var showSideload by remember { mutableStateOf(false) }

    fun save(updated: List<WatchPopupItem>) {
        items = updated
        popupViewModel.saveWatchItems(updated.toWatchJsonString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "为 Galaxy Watch 配置动作。手表应用打开时会拉取此列表。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有手表项目。点「添加手表项目」开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (item.actionType) {
                            "random_image" -> Icons.Default.Shuffle
                            "random_video" -> Icons.Default.VideoLibrary
                            "restore"      -> Icons.Default.Restore
                            "global_off"   -> Icons.Default.PowerSettingsNew
                            "timers"       -> Icons.Default.Timer
                            else           -> Icons.Default.TouchApp
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (item.actionType == "global_off")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append(item.actionType.replace("_", " "))
                                if (item.target.isNotBlank() &&
                                    item.actionType != "global_off" &&
                                    item.actionType != "restore" &&
                                    item.actionType != "timers")
                                    append(" · ${item.target}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { save(items.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) },
                        enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { save(items.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) },
                        enabled = index < items.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { editingItem = item; showEditor = true }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { save(items.filter { it.id != item.id }) }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("全局关闭时震动", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "从手表触发「全局关闭」时震动手机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = globalOffVibrate,
                onCheckedChange = { popupViewModel.setWatchGlobalOffVibrate(it) }
            )
        }

        Button(
            onClick = { editingItem = null; showEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加手表项目")
        }

        WatchSideloadCard(expanded = showSideload, onToggle = { showSideload = !showSideload })
    }

    if (showEditor) {
        WatchItemEditorDialog(
            initial    = editingItem,
            imageFolders = imageFolders.map { it.uri },
            videoFolders = videoFolders.map { it.uri },
            onSave = { newItem ->
                save(
                    if (editingItem != null) items.map { if (it.id == editingItem!!.id) newItem else it }
                    else items + newItem
                )
                showEditor = false
                editingItem = null
            },
            onDismiss = { showEditor = false; editingItem = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchItemEditorDialog(
    initial: WatchPopupItem?,
    imageFolders: List<String>,
    videoFolders: List<String>,
    onSave: (WatchPopupItem) -> Unit,
    onDismiss: () -> Unit
) {
    var label      by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var actionType by remember(initial) { mutableStateOf(initial?.actionType ?: "random_image") }
    var target     by remember(initial) { mutableStateOf(initial?.target ?: "home") }
    var folderUri  by remember(initial) { mutableStateOf(initial?.folderUri ?: "") }
    var labelError by remember { mutableStateOf(false) }

    val watchActions = listOf("random_image", "random_video", "restore", "global_off", "timers")
    val actionLabels = mapOf(
        "random_image" to "随机图片",
        "random_video" to "随机视频",
        "restore"      to "Restore",
        "global_off"   to "Global Off",
        "timers"       to "Timers"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Edit Watch Item" else "添加手表项目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = { Text("标签") },
                    isError = labelError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("动作类型", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    watchActions.forEach { type ->
                        FilterChip(
                            selected = actionType == type,
                            onClick  = {
                                actionType = type
                                if (type == "restore" || type == "global_off" || type == "timers") {
                                    target    = "home"
                                    folderUri = ""
                                }
                            },
                            label = { Text(actionLabels[type] ?: type) }
                        )
                    }
                }

                if (actionType == "random_image" || actionType == "random_video") {
                    Text("应用到", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("home", "lock", "both").forEach { t ->
                            FilterChip(
                                selected = target == t,
                                onClick  = { target = t },
                                label    = { Text(t.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    val folders = if (actionType == "random_image") imageFolders else videoFolders
                    if (folders.isNotEmpty()) {
                        Text("来源文件夹（可选）", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = folderUri.isBlank(),
                                onClick  = { folderUri = "" },
                                label    = { Text("默认文件夹") }
                            )
                            folders.forEach { uri ->
                                val name = Uri.parse(uri).lastPathSegment?.substringAfterLast(':') ?: uri
                                FilterChip(
                                    selected = folderUri == uri,
                                    onClick  = { folderUri = uri },
                                    label    = { Text(name, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (label.isBlank()) { labelError = true; return@TextButton }
                onSave(
                    WatchPopupItem(
                        id         = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        label      = label.trim(),
                        actionType = actionType,
                        target     = target,
                        folderUri  = folderUri
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WatchSideloadCard(expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Watch, null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("安装手表应用", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("将 KLVW Watch 侧载到 Galaxy Watch：",
                    style = MaterialTheme.typography.bodySmall)
                Text("1. 手表：设置 → 开发者选项 → 无线调试 → 开启",
                    style = MaterialTheme.typography.bodySmall)
                Text("2. 电脑：用 ADB 配对并连接",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "   adb pair <ip>:<pairing-port>\n   adb connect <ip>:<port>",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Text("3. 安装手表 APK：",
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "   adb install -r wear/build/outputs/apk/debug/wear-debug.apk",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Text("4. 在手表应用列表中打开「KLVW Watch」。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AerStorageSection(popupViewModel: KLVWPopupViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isMounted by remember { mutableStateOf(AerLockStore.isMounted) }
    val aerUnmountOnLock by popupViewModel.aerUnmountOnLock.collectAsStateWithLifecycle()
    val aerAutoUnmountMinutes by popupViewModel.aerAutoUnmountMinutes.collectAsStateWithLifecycle()
    var showAutoUnmountDialog by remember { mutableStateOf(false) }
    var autoUnmountInput by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isMounted = AerLockStore.isMounted
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (isMounted) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isMounted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("状态", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (isMounted) "Mounted — external apps can see files" else "Unmounted — private from external apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isMounted) {
                OutlinedButton(onClick = {
                    AerLockStore.unmount(context)
                    isMounted = false
                }) { Text("卸载") }
            } else {
                Button(onClick = {
                    context.startActivity(Intent(context, AerMountActivity::class.java))
                }) { Text("挂载") }
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
        SettingToggle(
            icon = Icons.Default.ScreenLockPortrait,
            title = "设备锁屏时卸载",
            subtitle = "熄屏时自动卸载 Aer（外部应用不可见）",
            checked = aerUnmountOnLock,
            onCheckedChange = { popupViewModel.setAerUnmountOnLock(it) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.Timer,
            title = "超时后卸载",
            subtitle = if (aerAutoUnmountMinutes > 0)
                "Automatically locks after $aerAutoUnmountMinutes minute${if (aerAutoUnmountMinutes != 1) "s" else ""}"
            else
                "Disabled — tap to set a timeout",
            onClick = {
                autoUnmountInput = if (aerAutoUnmountMinutes > 0) aerAutoUnmountMinutes.toString() else ""
                showAutoUnmountDialog = true
            }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.FolderOpen,
            title = "在文件应用中打开",
            subtitle = "在系统文件管理器中浏览与播放",
            onClick = { AerShell.open(context) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        SettingAction(
            icon = Icons.Default.FolderSpecial,
            title = "管理 Aer 存储",
            subtitle = "导入文件，并可把图片设为壁纸",
            onClick = { context.startActivity(Intent(context, AerActivity::class.java)) }
        )
        HorizontalDivider(thickness = 0.5.dp)
        Text(
            "Aer 文件保存在应用私有存储。可在「文件应用」中用系统文件管理器浏览，并用默认播放器播放视频。用「管理」导入媒体。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
    }

    if (showAutoUnmountDialog) {
        AlertDialog(
            onDismissRequest = { showAutoUnmountDialog = false },
            title = { Text("超时后卸载") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "输入超时分钟数（0 或留空表示关闭）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = autoUnmountInput,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) autoUnmountInput = it },
                        label = { Text("分钟") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = autoUnmountInput.toIntOrNull() ?: 0
                    popupViewModel.setAerAutoUnmountMinutes(minutes)
                    showAutoUnmountDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoUnmountDialog = false }) { Text("取消") }
            }
        )
    }
}
