package com.klvw.wallpaper.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@OptIn(ExperimentalMaterial3Api::class)
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

    fun openNewRule() {
        val preferTarget = when {
            ScheduleMath.firstUncoveredWindow(rules, WallpaperTarget.HOME) != null ->
                WallpaperTarget.HOME
            ScheduleMath.firstUncoveredWindow(rules, WallpaperTarget.LOCK) != null ->
                WallpaperTarget.LOCK
            else -> null
        }
        if (preferTarget == null) {
            Toast.makeText(context, "桌面与锁屏均已铺满，请先缩短已有规则", Toast.LENGTH_SHORT).show()
            return
        }
        val gap = ScheduleMath.firstUncoveredWindow(rules, preferTarget)!!
        editingRule = ScheduleRule(
            target = preferTarget,
            media = FolderType.IMAGE,
            folderUri = "",
            startMinute = gap.startMinute,
            endMinute = gap.endMinute
        )
    }

    val title = if (initial.rules.isEmpty() && name == "新分组") "新建分组" else "编辑分组"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                onSave(initial.copy(name = name.ifBlank { "未命名分组" }, rules = rules))
                            }) { Text("保存") }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = ::openNewRule,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("添加规则")
                            }
                            Button(
                                onClick = {
                                    onSave(initial.copy(name = name.ifBlank { "未命名分组" }, rules = rules))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("保存") }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("分组名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "桌面与锁屏都必须铺满 00:00–23:59。添加规则会自动填入下一段空隙，也可在编辑里点「自动填入空隙」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DayTimeline(rules = rules, target = WallpaperTarget.HOME)
                    DayTimeline(rules = rules, target = WallpaperTarget.LOCK)
                    if (rules.isEmpty()) {
                        Text(
                            "暂无规则，点下方「添加规则」开始配置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        rules.forEach { rule ->
                            RuleRow(
                                rule = rule,
                                onEdit = { editingRule = rule },
                                onDelete = { rules = rules.filter { it.id != rule.id } }
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    editingRule?.let { rule ->
        RuleEditorDialog(
            rule = rule,
            siblingRules = rules,
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
private fun DayTimeline(
    rules: List<ScheduleRule>,
    target: WallpaperTarget,
    modifier: Modifier = Modifier
) {
    val label = if (target == WallpaperTarget.LOCK) "锁屏" else "桌面"
    val targetRules = remember(rules, target) { rules.filter { it.target == target } }
    val cover = remember(rules, target) { ScheduleMath.coverage(rules, target) }
    val hasGap = remember(cover) { cover.any { !it } }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                if (targetRules.isEmpty()) "未配置"
                else if (hasGap) "有空隙"
                else "已铺满",
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    targetRules.isEmpty() || hasGap -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(track)
        ) {
            val total = maxWidth
            val day = ScheduleMath.MINUTES_PER_DAY.toFloat()
            val segments = buildList {
                targetRules.forEach { rule ->
                    val color = if (rule.media == FolderType.VIDEO) tertiary else primary
                    if (rule.startMinute <= rule.endMinute) {
                        add(Triple(rule.startMinute, rule.endMinute, color))
                    } else {
                        add(Triple(rule.startMinute, 1439, color))
                        add(Triple(0, rule.endMinute, color))
                    }
                }
            }
            segments.forEach { (start, endInclusive, color) ->
                val x = total * (start / day)
                val w = total * ((endInclusive - start + 1) / day)
                Box(
                    Modifier
                        .offset(x = x)
                        .width(w.coerceAtLeast(1.dp))
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.85f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    val folder = folderDisplayName(rule.folderUri)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorDialog(
    rule: ScheduleRule,
    siblingRules: List<ScheduleRule>,
    onDismiss: () -> Unit,
    onPickFolder: (ScheduleRule) -> Unit,
    onConfirm: (ScheduleRule) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(rule.id) { mutableStateOf(rule) }
    var picking by remember { mutableStateOf<TimePickTarget?>(null) }
    LaunchedEffect(rule.folderUri) {
        draft = draft.copy(folderUri = rule.folderUri)
    }

    fun applyGapFor(target: WallpaperTarget) {
        val gap = ScheduleMath.firstUncoveredWindow(siblingRules, target, excludeRuleId = draft.id)
        if (gap == null) {
            Toast.makeText(context, "该目标已无空隙，请先缩短其他规则", Toast.LENGTH_SHORT).show()
            return
        }
        draft = draft.copy(target = target, startMinute = gap.startMinute, endMinute = gap.endMinute)
    }

    fun confirm() {
        if (draft.folderUri.isBlank()) {
            Toast.makeText(context, "请选择文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        onConfirm(draft)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("编辑规则") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        },
                        actions = {
                            TextButton(onClick = ::confirm) { Text("确定") }
                        }
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                            Button(
                                onClick = ::confirm,
                                modifier = Modifier.weight(1f)
                            ) { Text("确定") }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))
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
                    Text(
                        "${ScheduleMath.formatMinute(draft.startMinute)} – ${ScheduleMath.formatMinute(draft.endMinute)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { applyGapFor(draft.target) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("自动填入空隙") }
                    TimeFieldRow(
                        label = "开始（可手动改）",
                        minute = draft.startMinute,
                        onClick = { picking = TimePickTarget.START }
                    )
                    TimeFieldRow(
                        label = "结束（含；跨午夜可小于开始）",
                        minute = draft.endMinute,
                        onClick = { picking = TimePickTarget.END }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    Button(
                        onClick = { onPickFolder(draft) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (draft.folderUri.isBlank()) "选择文件夹" else "更换文件夹")
                    }
                    if (draft.folderUri.isNotBlank()) {
                        Text(
                            folderDisplayName(draft.folderUri),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

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

/** Friendly folder label from a SAF tree URI (matches SettingsScreen). */
private fun folderDisplayName(uri: String): String {
    if (uri.isBlank()) return "未选文件夹"
    return Uri.parse(uri).lastPathSegment
        ?.substringAfterLast(':')
        ?.let { Uri.decode(it) }
        ?.ifBlank { null }
        ?: Uri.decode(uri.substringAfterLast('/')).ifBlank { uri }
}

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
