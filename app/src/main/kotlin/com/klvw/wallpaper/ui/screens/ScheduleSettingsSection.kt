package com.klvw.wallpaper.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.ScheduleGroup
import com.klvw.wallpaper.data.model.ScheduleRule
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.schedule.ScheduleMath
import com.klvw.wallpaper.ui.viewmodel.WallpaperViewModel
import com.klvw.wallpaper.util.PermissionUtils
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged


@Composable
fun ScheduleSettingsSection(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    val enabled by viewModel.scheduleEnabledFlow.collectAsStateWithLifecycle()
    val groupsJson by viewModel.scheduleGroupsJson.collectAsStateWithLifecycle()
    val activeId by viewModel.scheduleActiveGroupId.collectAsStateWithLifecycle()
    val groups = remember(groupsJson) { ScheduleGroup.listFromJson(groupsJson) }

    var editing by remember { mutableStateOf<ScheduleGroup?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("启用时段方案", style = MaterialTheme.typography.titleMedium)
                Text(
                    "按 24 小时铺满桌面+锁屏；与全局开关无关",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    viewModel.setScheduleEnabled(on) { err ->
                        if (err != null) toast(err)
                    }
                }
            )
        }

        HorizontalDivider(thickness = 0.5.dp)

        Text(
            "分组（单选生效）",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        groups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setScheduleActiveGroup(group.id) { err ->
                            if (err != null) toast(err)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = group.id == activeId,
                    onClick = {
                        viewModel.setScheduleActiveGroup(group.id) { err ->
                            if (err != null) toast(err)
                        }
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${group.rules.size} 条规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { editing = group }) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = {
                    val next = groups.filter { it.id != group.id }
                    if (enabled && group.id == activeId) {
                        toast("启用中不能删除当前分组")
                        return@IconButton
                    }
                    viewModel.saveScheduleGroups(next) { err ->
                        if (err != null) toast(err)
                        else if (group.id == activeId) {
                            viewModel.setScheduleActiveGroup(null) {}
                        }
                    }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }

        TextButton(
            onClick = {
                editing = ScheduleGroup(id = UUID.randomUUID().toString(), name = "新分组", rules = emptyList())
            },
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("新建分组")
        }
    }

    editing?.let { draft ->
        ScheduleGroupEditorDialog(
            initial = draft,
            onDismiss = { editing = null },
            onSave = { saved ->
                val err = ScheduleMath.validateGroup(saved.rules)
                if (err != null) {
                    toast(err)
                    return@ScheduleGroupEditorDialog
                }
                val next = groups.filter { it.id != saved.id } + saved
                viewModel.saveScheduleGroups(next) { saveErr ->
                    if (saveErr != null) toast(saveErr)
                    else {
                        if (activeId == null) {
                            viewModel.setScheduleActiveGroup(saved.id) {}
                        }
                        editing = null
                        toast("已保存")
                    }
                }
            }
        )
    }
}

@Composable
private fun ScheduleGroupEditorDialog(
    initial: ScheduleGroup,
    onDismiss: () -> Unit,
    onSave: (ScheduleGroup) -> Unit
) {
    val context = LocalContext.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var rules by remember(initial.id) { mutableStateOf(initial.rules) }
    var editingRule by remember { mutableStateOf<ScheduleRule?>(null) }
    var folderPickForRule by remember { mutableStateOf<ScheduleRule?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val rule = folderPickForRule
        folderPickForRule = null
        if (uri != null && rule != null) {
            PermissionUtils.persistFolderPermission(context, uri)
            val updated = rule.copy(folderUri = uri.toString())
            rules = rules.map { if (it.id == updated.id) updated else it }
            editingRule = updated
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.rules.isEmpty() && name == "新分组") "新建分组" else "编辑分组") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "桌面与锁屏都必须铺满 00:00–23:59；同屏时段不可相交；下一段从结束分钟+1 开始。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = { editingRule = rule },
                        onDelete = { rules = rules.filter { it.id != rule.id } }
                    )
                }
                TextButton(onClick = {
                    editingRule = ScheduleRule(
                        target = WallpaperTarget.HOME,
                        media = FolderType.IMAGE,
                        folderUri = "",
                        startMinute = 0,
                        endMinute = 0
                    )
                }) {
                    Text("添加规则")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initial.copy(name = name.ifBlank { "未命名分组" }, rules = rules))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    editingRule?.let { rule ->
        RuleEditorDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onPickFolder = {
                folderPickForRule = it
                folderPicker.launch(null)
            },
            onConfirm = { updated ->
                rules = if (rules.any { it.id == updated.id }) {
                    rules.map { if (it.id == updated.id) updated else it }
                } else {
                    rules + updated
                }
                editingRule = null
            }
        )
    }
}

@Composable
private fun RuleRow(
    rule: ScheduleRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val target = if (rule.target == WallpaperTarget.LOCK) "锁屏" else "桌面"
    val media = if (rule.media == FolderType.VIDEO) "视频" else "图片"
    val time = "${ScheduleMath.formatMinute(rule.startMinute)}–${ScheduleMath.formatMinute(rule.endMinute)}"
    val folder = rule.folderUri.substringAfterLast('%').substringAfterLast('/').ifBlank { "未选文件夹" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("$target · $media · $time", style = MaterialTheme.typography.bodyMedium)
            Text(folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null) }
    }
}

@Composable
private fun RuleEditorDialog(
    rule: ScheduleRule,
    onDismiss: () -> Unit,
    onPickFolder: (ScheduleRule) -> Unit,
    onConfirm: (ScheduleRule) -> Unit
) {
    var draft by remember(rule.id) { mutableStateOf(rule) }
    var picking by remember { mutableStateOf<TimePickTarget?>(null) }
    LaunchedEffect(rule.folderUri) {
        draft = draft.copy(folderUri = rule.folderUri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.target == WallpaperTarget.HOME,
                        onClick = { draft = draft.copy(target = WallpaperTarget.HOME) },
                        label = { Text("桌面") }
                    )
                    FilterChip(
                        selected = draft.target == WallpaperTarget.LOCK,
                        onClick = { draft = draft.copy(target = WallpaperTarget.LOCK) },
                        label = { Text("锁屏") }
                    )
                }
                Text("类型", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.media == FolderType.IMAGE,
                        onClick = { draft = draft.copy(media = FolderType.IMAGE) },
                        label = { Text("图片") }
                    )
                    FilterChip(
                        selected = draft.media == FolderType.VIDEO,
                        onClick = { draft = draft.copy(media = FolderType.VIDEO) },
                        label = { Text("视频") }
                    )
                }
                Text("时段", style = MaterialTheme.typography.labelMedium)
                TimeFieldRow(
                    label = "开始",
                    minute = draft.startMinute,
                    onClick = { picking = TimePickTarget.START }
                )
                TimeFieldRow(
                    label = "结束（含；跨午夜可小于开始）",
                    minute = draft.endMinute,
                    onClick = { picking = TimePickTarget.END }
                )
                TextButton(onClick = { onPickFolder(draft) }) {
                    Text(if (draft.folderUri.isBlank()) "选择文件夹" else "更换文件夹")
                }
                if (draft.folderUri.isNotBlank()) {
                    Text(
                        draft.folderUri,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            val ctx = LocalContext.current
            Button(onClick = {
                if (draft.folderUri.isBlank()) {
                    Toast.makeText(ctx, "请选择文件夹", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                onConfirm(draft)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    picking?.let { target ->
        val initial = if (target == TimePickTarget.START) draft.startMinute else draft.endMinute
        val title = if (target == TimePickTarget.START) "选择开始时间" else "选择结束时间"
        WheelTimePickerDialog(
            title = title,
            initialMinute = initial,
            onDismiss = { picking = null },
            onConfirm = { minute ->
                draft = if (target == TimePickTarget.START) {
                    draft.copy(startMinute = minute)
                } else {
                    draft.copy(endMinute = minute)
                }
                picking = null
            }
        )
    }
}

private enum class TimePickTarget { START, END }

@Composable
private fun TimeFieldRow(
    label: String,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            ScheduleMath.formatMinute(minute),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WheelTimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hour by remember { mutableIntStateOf((initialMinute / 60).coerceIn(0, 23)) }
    var minute by remember { mutableIntStateOf((initialMinute % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberWheel(
                    values = (0..23).toList(),
                    selected = hour,
                    onSelected = { hour = it },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                NumberWheel(
                    values = (0..59).toList(),
                    selected = minute,
                    onSelected = { minute = it },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour * 60 + minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5
) {
    val itemHeight = 40.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val initialIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val verticalPad = itemHeight * ((visibleCount - 1) / 2)

    LaunchedEffect(listState) {
        snapshotFlow {
            val offset = listState.firstVisibleItemScrollOffset
            val index = listState.firstVisibleItemIndex + if (offset > itemHeightPx / 2f) 1 else 0
            index.coerceIn(0, values.lastIndex)
        }
            .distinctUntilChanged()
            .collect { idx -> onSelected(values[idx]) }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = verticalPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { _, value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(value),
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.45f)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = -itemHeight / 2),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .offset(y = itemHeight / 2),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
