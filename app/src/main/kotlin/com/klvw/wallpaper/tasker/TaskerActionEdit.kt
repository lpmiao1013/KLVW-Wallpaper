package com.klvw.wallpaper.tasker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.StaticImageRepository
import com.klvw.wallpaper.ui.theme.KLVWTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TaskerActionEdit : ComponentActivity() {

    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var staticImageRepository: StaticImageRepository

    private var pendingResultIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val existingBundle = intent.getBundleExtra(TaskerBundle.PLUGIN_BUNDLE_KEY)
        setContent {
            KLVWTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskerEditScreen(
                        folderRepository = folderRepository,
                        staticImageRepository = staticImageRepository,
                        existingBundle = existingBundle,
                        onSave = { resultBundle, blurb ->
                            pendingResultIntent = Intent().apply {
                                putExtra(TaskerBundle.PLUGIN_BUNDLE_KEY, resultBundle)
                                putExtra(TaskerBundle.PLUGIN_BLURB_KEY, blurb)
                            }
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    override fun finish() {
        pendingResultIntent?.let { setResult(RESULT_OK, it) }
        super.finish()
    }
}

@Composable
private fun TaskerEditScreen(
    folderRepository: FolderRepository,
    staticImageRepository: StaticImageRepository,
    existingBundle: Bundle?,
    onSave: (Bundle, String) -> Unit,
    onCancel: () -> Unit
) {
    val imageFolders by folderRepository.getFoldersByType(FolderType.IMAGE)
        .collectAsStateWithLifecycle(emptyList())
    val videoFolders by folderRepository.getFoldersByType(FolderType.VIDEO)
        .collectAsStateWithLifecycle(emptyList())
    val staticImages by staticImageRepository.getAll()
        .collectAsStateWithLifecycle(emptyList())

    var selectedAction by remember {
        mutableStateOf(existingBundle?.getString(TaskerBundle.BUNDLE_KEY_ACTION) ?: TaskerBundle.ACTION_RANDOM_IMAGE)
    }

    // ── folder ──
    val savedFolderUri = existingBundle?.getString(TaskerBundle.BUNDLE_KEY_FOLDER_URI)
        ?.takeUnless { it.startsWith("%") } ?: ""
    val savedFolderName = existingBundle?.getString(TaskerBundle.BUNDLE_KEY_FOLDER_NAME) ?: ""
    var selectedFolderUri by remember { mutableStateOf(savedFolderUri) }

    // ── static image ──
    val savedImageUri = existingBundle?.getString(TaskerBundle.BUNDLE_KEY_IMAGE_URI)
        ?.takeUnless { it.startsWith("%") } ?: ""
    var selectedImageUri by remember { mutableStateOf(savedImageUri) }

    // ── icon color ──
    val savedIconColor = existingBundle?.getString(TaskerBundle.BUNDLE_KEY_ICON_COLOR)
        ?.takeUnless { it.startsWith("%") } ?: TaskerBundle.ICON_COLOR_AUTO
    var selectedIconColor by remember { mutableStateOf(savedIconColor) }

    // ── target ──
    val savedTarget = existingBundle?.getString(TaskerBundle.BUNDLE_KEY_TARGET)
        ?.takeUnless { it.startsWith("%") } ?: TaskerBundle.TARGET_HOME
    var selectedTarget by remember { mutableStateOf(savedTarget) }

    val isStatic = selectedAction == TaskerBundle.ACTION_STATIC_IMAGE
    val isVideo = selectedAction == TaskerBundle.ACTION_RANDOM_VIDEO
    val isRestorePrevious = selectedAction == TaskerBundle.ACTION_RESTORE_PREVIOUS
    val isSetIconColor = selectedAction == TaskerBundle.ACTION_SET_ICON_COLOR
    val folders = if (isVideo) videoFolders else imageFolders

    val canSave = !isStatic || selectedImageUri.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("KLVW Tasker 动作", style = MaterialTheme.typography.headlineSmall)

        // ── action chips ──────────────────────────────────────────────────────
        Text("动作类型", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = selectedAction == TaskerBundle.ACTION_RANDOM_IMAGE,
                onClick = { selectedAction = TaskerBundle.ACTION_RANDOM_IMAGE; selectedFolderUri = "" },
                label = { Text("随机图片") }
            )
            FilterChip(
                selected = selectedAction == TaskerBundle.ACTION_STATIC_IMAGE,
                onClick = { selectedAction = TaskerBundle.ACTION_STATIC_IMAGE; selectedImageUri = "" },
                label = { Text("静态图") }
            )
            FilterChip(
                selected = selectedAction == TaskerBundle.ACTION_RANDOM_VIDEO,
                onClick = { selectedAction = TaskerBundle.ACTION_RANDOM_VIDEO; selectedFolderUri = "" },
                label = { Text("随机视频") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedAction == TaskerBundle.ACTION_RESTORE_PREVIOUS,
                onClick = { selectedAction = TaskerBundle.ACTION_RESTORE_PREVIOUS },
                label = { Text("恢复上一张") }
            )
            FilterChip(
                selected = selectedAction == TaskerBundle.ACTION_SET_ICON_COLOR,
                onClick = { selectedAction = TaskerBundle.ACTION_SET_ICON_COLOR },
                label = { Text("设置图标颜色") }
            )
        }

        // ── action-specific options ───────────────────────────────────────────
        when {
            isSetIconColor -> {
                Text("图标颜色", style = MaterialTheme.typography.labelLarge)
                listOf(
                    TaskerBundle.ICON_COLOR_AUTO        to "Auto (Android decides)",
                    TaskerBundle.ICON_COLOR_DARK        to "Dark Icons (black, for light wallpapers)",
                    TaskerBundle.ICON_COLOR_LIGHT       to "Light Icons (white, for dark wallpapers)",
                    TaskerBundle.ICON_COLOR_INTERACTIVE to "Ask at runtime (shows picker when task fires)"
                ).forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedIconColor == value, onClick = { selectedIconColor = value })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            isRestorePrevious -> Text(
                "恢复最近一次更换之前的壁纸。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            isStatic -> {
                Text("图片", style = MaterialTheme.typography.labelLarge)
                if (staticImages.isEmpty()) {
                    Text("还没有保存静态图。请先在「静态」页添加图片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    staticImages.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = selectedImageUri == item.uri.toString(),
                                onClick = { selectedImageUri = item.uri.toString() }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            else -> {
                // Random Image or Random Video
                Text("来源文件夹", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = selectedFolderUri == TaskerBundle.FOLDER_URI_DEFAULT,
                        onClick = { selectedFolderUri = TaskerBundle.FOLDER_URI_DEFAULT }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("全局默认文件夹", style = MaterialTheme.typography.bodyMedium)
                        Text("使用 KLVW → 文件夹选择器 中设置的默认文件夹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = selectedFolderUri.isEmpty(), onClick = { selectedFolderUri = "" })
                    Spacer(Modifier.width(8.dp))
                    Text("全部文件夹", style = MaterialTheme.typography.bodyMedium)
                }
                if (folders.isEmpty()) {
                    Text("还没有保存${if (isVideo) "视频" else "图片"}文件夹。请先在应用中添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    folders.forEach { folder ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(
                                selected = selectedFolderUri == folder.uri,
                                onClick = { selectedFolderUri = folder.uri }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(folder.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ── target ────────────────────────────────────────────────────────────
        if (!isSetIconColor) {
            Text("应用到", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TaskerBundle.TARGET_HOME to "桌面", TaskerBundle.TARGET_LOCK to "锁屏", TaskerBundle.TARGET_BOTH to "两者")
                    .forEach { (key, label) ->
                        FilterChip(
                            selected = selectedTarget == key,
                            onClick = { selectedTarget = key },
                            label = { Text(label) }
                        )
                    }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── save / cancel ─────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            OutlinedButton(onClick = onCancel) { Text("取消") }
            Button(
                onClick = {
                    if (!canSave) return@Button
                    val resultBundle = Bundle()
                    resultBundle.putString(TaskerBundle.BUNDLE_KEY_ACTION, selectedAction)
                    resultBundle.putString(TaskerBundle.BUNDLE_KEY_TARGET, selectedTarget)

                    val blurb: String
                    when {
                        isSetIconColor -> {
                            resultBundle.putString(TaskerBundle.BUNDLE_KEY_ICON_COLOR, selectedIconColor)
                            val colorLabel = when (selectedIconColor) {
                                TaskerBundle.ICON_COLOR_DARK        -> "深色"
                                TaskerBundle.ICON_COLOR_LIGHT       -> "浅色"
                                TaskerBundle.ICON_COLOR_INTERACTIVE -> "Ask at runtime"
                                else                                -> "自动"
                            }
                            blurb = "Icon Color - $colorLabel"
                        }
                        isRestorePrevious -> {
                            blurb = "${selectedTarget.replaceFirstChar { it.uppercase() }} - Restore Previous"
                        }
                        isStatic -> {
                            resultBundle.putString(TaskerBundle.BUNDLE_KEY_IMAGE_URI, selectedImageUri)
                            blurb = "${selectedTarget.replaceFirstChar { it.uppercase() }} - Static"
                        }
                        else -> {
                            val folderName = when {
                                selectedFolderUri == TaskerBundle.FOLDER_URI_DEFAULT -> "Global Default"
                                selectedFolderUri.isEmpty() -> "全部文件夹"
                                else -> folders.find { it.uri == selectedFolderUri }?.displayName
                                    ?: savedFolderName.ifBlank { Uri.decode(selectedFolderUri.substringAfterLast('/')) }
                            }
                            resultBundle.putString(TaskerBundle.BUNDLE_KEY_FOLDER_URI, selectedFolderUri)
                            resultBundle.putString(TaskerBundle.BUNDLE_KEY_FOLDER_NAME, folderName)
                            val typeLabel = if (isVideo) "视频" else "图片"
                            blurb = "${selectedTarget.replaceFirstChar { it.uppercase() }} - $typeLabel - $folderName"
                        }
                    }
                    onSave(resultBundle, blurb)
                },
                enabled = canSave
            ) { Text("保存") }
        }
    }
}
