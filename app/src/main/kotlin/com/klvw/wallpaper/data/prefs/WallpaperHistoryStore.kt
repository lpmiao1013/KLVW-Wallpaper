package com.klvw.wallpaper.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wallpaperHistoryStore: DataStore<Preferences> by preferencesDataStore(
    name = "klvw_wallpaper_history"
)

data class WallpaperHistoryEntry(
    val id: String,
    val timestamp: Long,
    val target: String,
    val type: String,
    val folderUri: String,
    val folderLabel: String,
    val fileUri: String,
    val displayName: String
) {
    fun toWallpaperItem(): WallpaperItem? {
        val folderType = when (type) {
            "video" -> FolderType.VIDEO
            "static" -> FolderType.STATIC
            else -> FolderType.IMAGE
        }
        return try {
            WallpaperItem(
                uri = android.net.Uri.parse(fileUri),
                type = folderType,
                displayName = displayName,
                folderUri = folderUri
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Flat newest-first history of successful wallpaper applies (manual / timer / unlock / etc.).
 * Cap [MAX_ENTRIES] so the store stays bounded.
 */
@Singleton
class WallpaperHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("entries_json")

    val entries: Flow<List<WallpaperHistoryEntry>> = context.wallpaperHistoryStore.data
        .map { prefs -> parse(prefs[key].orEmpty()) }
        .distinctUntilChanged()

    suspend fun record(item: WallpaperItem, target: WallpaperTarget) {
        if (item.uri.toString().isBlank()) return
        val screens = when (target) {
            WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
            WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
            else -> listOf(WallpaperTarget.HOME)
        }
        val now = System.currentTimeMillis()
        val folderLabel = folderLabelOf(item.folderUri)
        val typeName = when (item.type) {
            FolderType.VIDEO -> "video"
            FolderType.STATIC -> "static"
            else -> "image"
        }
        context.wallpaperHistoryStore.edit { prefs ->
            val list = parse(prefs[key].orEmpty()).toMutableList()
            screens.forEachIndexed { index, screen ->
                list.add(
                    0,
                    WallpaperHistoryEntry(
                        id = UUID.randomUUID().toString(),
                        timestamp = now + index,
                        target = if (screen == WallpaperTarget.LOCK) "lock" else "home",
                        type = typeName,
                        folderUri = item.folderUri,
                        folderLabel = folderLabel,
                        fileUri = item.uri.toString(),
                        displayName = item.displayName.ifBlank { item.uri.lastPathSegment.orEmpty() }
                    )
                )
            }
            while (list.size > MAX_ENTRIES) list.removeAt(list.lastIndex)
            prefs[key] = encode(list)
        }
    }

    suspend fun removeByFileUri(fileUri: String) {
        if (fileUri.isBlank()) return
        context.wallpaperHistoryStore.edit { prefs ->
            val list = parse(prefs[key].orEmpty()).filterNot { it.fileUri == fileUri }
            prefs[key] = encode(list)
        }
    }

    suspend fun removeById(id: String) {
        context.wallpaperHistoryStore.edit { prefs ->
            val list = parse(prefs[key].orEmpty()).filterNot { it.id == id }
            prefs[key] = encode(list)
        }
    }

    /** Rewrite folderUri (and tree-based fileUri prefix) after SAF re-auth returns a new tree URI. */
    suspend fun remapFolderUri(oldFolderUri: String, newFolderUri: String) {
        if (oldFolderUri.isBlank() || newFolderUri.isBlank() || oldFolderUri == newFolderUri) return
        context.wallpaperHistoryStore.edit { prefs ->
            val list = parse(prefs[key].orEmpty()).map { e ->
                if (e.folderUri != oldFolderUri) e
                else e.copy(
                    folderUri = newFolderUri,
                    fileUri = e.fileUri.replace(oldFolderUri, newFolderUri)
                )
            }
            prefs[key] = encode(list)
        }
    }

    private fun folderLabelOf(folderUri: String): String {
        if (folderUri.isBlank()) return "未知文件夹"
        return try {
            android.net.Uri.decode(folderUri.substringAfterLast('/').substringAfterLast(':'))
                .ifBlank { "文件夹" }
        } catch (_: Exception) {
            "文件夹"
        }
    }

    private fun parse(json: String): List<WallpaperHistoryEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        WallpaperHistoryEntry(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            timestamp = o.optLong("ts"),
                            target = o.optString("target", "home"),
                            type = o.optString("type", "image"),
                            folderUri = o.optString("folderUri"),
                            folderLabel = o.optString("folderLabel"),
                            fileUri = o.optString("fileUri"),
                            displayName = o.optString("displayName")
                        )
                    )
                }
            }.sortedByDescending { it.timestamp }.take(MAX_ENTRIES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encode(list: List<WallpaperHistoryEntry>): String {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("ts", e.timestamp)
                    .put("target", e.target)
                    .put("type", e.type)
                    .put("folderUri", e.folderUri)
                    .put("folderLabel", e.folderLabel)
                    .put("fileUri", e.fileUri)
                    .put("displayName", e.displayName)
            )
        }
        return arr.toString()
    }

    companion object {
        const val MAX_ENTRIES = 100
    }
}
