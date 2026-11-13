package com.klvw.wallpaper.tasker

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.schedule.ScheduleManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class WallpaperIntentReceiver : BroadcastReceiver() {

    @Inject lateinit var folderRepository: FolderRepository
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var shuffleHistoryManager: ShuffleHistoryManager
    @Inject lateinit var scheduleManager: ScheduleManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME)?.trim()
        val type = if (intent.getStringExtra(EXTRA_TYPE)?.lowercase() == "video") FolderType.VIDEO else FolderType.IMAGE
        val target = when (intent.getStringExtra(EXTRA_TARGET)?.lowercase()) {
            "lock" -> WallpaperTarget.LOCK
            "both" -> WallpaperTarget.BOTH
            else -> WallpaperTarget.HOME
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (scheduleManager.isEnabled() && folderName.isNullOrBlank()) {
                    val screens = when (target) {
                        WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
                        WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
                        else -> listOf(WallpaperTarget.HOME)
                    }
                    for (screen in screens) {
                        val rule = scheduleManager.currentRule(screen) ?: continue
                        val item = shuffleHistoryManager.pickNext(
                            folderRepository.getItemsFromFolder(rule.folderUri, rule.media),
                            rule.folderUri, rule.media
                        ) ?: continue
                        wallpaperRepository.setWallpaper(item, screen)
                    }
                    return@launch
                }
                val item = if (folderName.isNullOrBlank()) {
                    val items = folderRepository.getFoldersByType(type).first()
                        .flatMap { folderRepository.getItemsFromFolder(it.uri, type) }
                    shuffleHistoryManager.pickNextFromPool(items, type)
                } else {
                    folderRepository.findFolderByDisplayName(folderName, type)?.let { folder ->
                        shuffleHistoryManager.pickNext(
                            folderRepository.getItemsFromFolder(folder.uri, type), folder.uri, type
                        )
                    }
                }
                item?.let { wallpaperRepository.setWallpaper(it, target) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.klvw.wallpaper.SET_WALLPAPER"
        const val EXTRA_FOLDER_NAME = "folder_name"
        const val EXTRA_TARGET = "target"
        const val EXTRA_TYPE = "type"

        fun openTemplateInTasker(context: Context) {
            val xml = buildTaskXml()
            val filename = "klvw_set_wallpaper.tsk"
            saveToDownloads(context, filename, xml)
            // Open Tasker first so it's in the foreground, then the toast is visible on top.
            openTasker(context)
            Toast.makeText(
                context,
                "Saved to Downloads/$filename — long-press task list → Import",
                Toast.LENGTH_LONG
            ).show()
        }

        private fun saveToDownloads(context: Context, filename: String, content: String) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/xml")
                    }
                    val uri = context.contentResolver.insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        values
                    ) ?: return
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                } else {
                    @Suppress("DEPRECATION")
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                        .writeText(content, Charsets.UTF_8)
                }
            } catch (_: Exception) {}
        }

        private fun openTasker(context: Context) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("net.dinglisch.android.taskerm")
                    ?: return
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        private fun buildTaskXml(): String {
            val ts = System.currentTimeMillis()
            return """<?xml version="1.0" encoding="utf-8"?>
<TaskerData sr="" dvi="1" tv="6.3.13">
    <Task sr="task1">
        <cdate>$ts</cdate>
        <edate>$ts</edate>
        <id>1</id>
        <nme>KLVW Set Wallpaper</nme>
        <Action sr="act0" ve="7">
            <code>130</code>
            <Str sr="arg0" ve="3">$ACTION</Str>
            <Str sr="arg1" ve="3"></Str>
            <Str sr="arg2" ve="3"></Str>
            <Str sr="arg3" ve="3">$EXTRA_FOLDER_NAME:%KLVWFOLDER
$EXTRA_TARGET:home</Str>
            <Str sr="arg4" ve="3"></Str>
            <Str sr="arg5" ve="3"></Str>
            <Str sr="arg6" ve="3"></Str>
            <Str sr="arg7" ve="3">com.klvw.wallpaper</Str>
            <Str sr="arg8" ve="3"></Str>
            <Int sr="arg9" val="1"/>
        </Action>
    </Task>
</TaskerData>"""
        }
    }
}
