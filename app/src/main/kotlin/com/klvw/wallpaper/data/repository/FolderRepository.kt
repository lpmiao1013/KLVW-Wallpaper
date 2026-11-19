package com.klvw.wallpaper.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.klvw.wallpaper.aer.AerDocumentsProvider
import com.klvw.wallpaper.aer.AerFolder
import com.klvw.wallpaper.aer.AerUri
import com.klvw.wallpaper.data.db.FolderDao
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderDao: FolderDao,
    private val prefs: SettingsPreferences
) {
    /** In-memory folder listing cache. Filled on first scan; cleared only via [invalidateItemsCache]. */
    private val itemsCache = ConcurrentHashMap<String, List<WallpaperItem>>()

    private fun itemsCacheKey(folderUri: String, type: FolderType) = "$folderUri\u0000${type.name}"
    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAll()

    /**
     * UI folder lists. When hide-mode is on, privacy-marked folders are omitted from
     * every UI list (picker / folder manager / popup). Wallpaper / timers still resolve by URI.
     */
    fun getFoldersByType(type: FolderType): Flow<List<FolderEntity>> =
        combine(
            folderDao.getByType(type),
            prefs.folderHideMode
        ) { all, hideMode ->
            if (!hideMode) all
            else all.filter { !it.hidden }
        }.distinctUntilChanged()

    /** Privacy-marked folders only. */
    fun getMarkedFolders(): Flow<List<FolderEntity>> = folderDao.getMarked()

    suspend fun addFolder(uri: Uri, type: FolderType): Long {
        val displayName = resolveDisplayName(uri) ?: uri.lastPathSegment ?: "Folder"
        val existing = folderDao.findByUri(uri.toString())
        if (existing != null) return existing.id
        return folderDao.insert(FolderEntity(uri = uri.toString(), displayName = displayName, type = type))
    }

    suspend fun addAerFolder(aerFolder: AerFolder, type: FolderType): Long {
        val uri = AerUri.of(aerFolder.relPath)
        val existing = folderDao.findByUri(uri)
        if (existing != null) return existing.id
        return folderDao.insert(FolderEntity(uri = uri, displayName = aerFolder.name, type = type))
    }

    suspend fun removeFolder(folder: FolderEntity) {
        invalidateItemsCache(folder.uri, folder.type)
        folderDao.delete(folder)
    }

    /** Set privacy mark (eligible to hide when hide-mode tile is on). */
    suspend fun setFolderPrivacyMark(folder: FolderEntity, marked: Boolean) {
        folderDao.setHidden(folder.id, marked)
    }

    /** Drop cached listings. Pass both args to clear one folder+type; omit to clear all. */
    fun invalidateItemsCache(folderUri: String? = null, type: FolderType? = null) {
        when {
            folderUri != null && type != null -> itemsCache.remove(itemsCacheKey(folderUri, type))
            folderUri != null -> {
                val prefix = "$folderUri\u0000"
                itemsCache.keys.filter { it.startsWith(prefix) }.forEach { itemsCache.remove(it) }
            }
            else -> itemsCache.clear()
        }
    }

    /**
     * Permanently deletes the underlying source file (SAF / Aer document), not an app DB path.
     */
    suspend fun deleteSourceFile(item: WallpaperItem): SourceDeleteResult = withContext(Dispatchers.IO) {
        val result = when {
            item.uri.authority == AerDocumentsProvider.AUTHORITY -> {
                // Aer provider has no deleteDocument; remove the app-private file directly.
                try {
                    val docId = DocumentsContract.getDocumentId(item.uri)
                    val file = File(File(context.filesDir, "aer"), docId)
                    when {
                        !file.exists() -> SourceDeleteResult.Ok
                        file.isFile && file.delete() -> SourceDeleteResult.Ok
                        else -> SourceDeleteResult.Err("无法删除 Aer 内文件")
                    }
                } catch (e: Exception) {
                    SourceDeleteResult.Err(e.message ?: "无法删除 Aer 内文件")
                }
            }
            else -> deleteSafDocument(item)
        }
        if (result is SourceDeleteResult.Ok) {
            if (item.folderUri.isNotBlank()) invalidateItemsCache(item.folderUri, item.type)
            else invalidateItemsCache()
        }
        result
    }

    /** Whether SAF write is available for deleting this item (Aer always true). */
    fun canWriteSource(item: WallpaperItem): Boolean {
        if (item.uri.authority == AerDocumentsProvider.AUTHORITY) return true
        val tree = item.folderUri.takeIf {
            it.isNotBlank() && !it.startsWith("klvw-aer://")
        }?.let { Uri.parse(it) }
        if (tree != null) {
            if (PermissionUtils.hasPersistedWrite(context, tree)) return true
            if (PermissionUtils.ensurePersistedWrite(context, tree)) return true
        }
        if (PermissionUtils.hasPersistedWrite(context, item.uri)) return true
        return PermissionUtils.ensurePersistedWrite(context, item.uri)
    }

    private fun deleteSafDocument(item: WallpaperItem): SourceDeleteResult {
        val tree = item.folderUri.takeIf {
            it.isNotBlank() && !it.startsWith("klvw-aer://")
        }?.let { Uri.parse(it) }
        if (tree != null) {
            PermissionUtils.ensurePersistedWrite(context, tree)
        } else {
            PermissionUtils.ensurePersistedWrite(context, item.uri)
        }
        if (!canWriteSource(item)) {
            return SourceDeleteResult.Err(
                "无写入权限。请重新点选原文件夹授权后再删（预设/时段不用重配）。"
            )
        }
        return try {
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, item.uri)
            if (deleted) SourceDeleteResult.Ok
            else SourceDeleteResult.Err("系统拒绝删除（文件可能受保护或只读）")
        } catch (_: SecurityException) {
            SourceDeleteResult.Err(
                "无写入权限。请重新点选原文件夹授权后再删（预设/时段不用重配）。"
            )
        } catch (_: IllegalArgumentException) {
            try {
                val n = context.contentResolver.delete(item.uri, null, null) ?: 0
                if (n > 0) SourceDeleteResult.Ok
                else SourceDeleteResult.Err("删除失败（不支持的文档类型）")
            } catch (e: Exception) {
                SourceDeleteResult.Err(e.message ?: "删除失败")
            }
        } catch (e: Exception) {
            SourceDeleteResult.Err(e.message ?: "删除失败")
        }
    }

    suspend fun touchFolder(id: Long) = folderDao.updateLastUsed(id)

    /** Point existing folder row at a newly granted tree URI (same folder, write re-auth). */
    suspend fun remapFolderUri(oldUri: String, newUri: String) {
        if (oldUri.isBlank() || newUri.isBlank() || oldUri == newUri) return
        folderDao.updateUri(oldUri, newUri)
        invalidateItemsCache(oldUri)
        invalidateItemsCache(newUri)
    }

    suspend fun findFolderByDisplayName(name: String, type: FolderType): FolderEntity? {
        val trimmed = name.trim()
        // Exact case-insensitive match via SQL
        folderDao.findByDisplayName(trimmed, type)?.let { return it }
        // Fuzzy fallback: the stored displayName may be "primary:Random" when the folder is "Random"
        // (happens when resolveDisplayName falls back to uri.lastPathSegment on SAF tree URIs)
        return folderDao.getAllByType(type).find { folder ->
            folder.displayName.substringAfterLast(':').trim().equals(trimmed, ignoreCase = true) ||
            folder.displayName.substringAfterLast('/').trim().equals(trimmed, ignoreCase = true)
        }
    }

    fun getAerFolders(relPath: String? = null): List<AerFolder> {
        val aerRoot = File(context.filesDir, "aer")
        val dir = if (relPath.isNullOrEmpty()) aerRoot else File(aerRoot, relPath)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.map { subdir ->
                val rel = subdir.relativeTo(aerRoot).path.replace('\\', '/')
                AerFolder(subdir.name, rel)
            }
            ?: emptyList()
    }

    /**
     * List media in [folderUri]. Uses an in-memory cache after the first successful scan.
     * Pass [forceRefresh] = true (e.g. user tapped 刷新列表) to rescan and replace the cache entry.
     */
    suspend fun getItemsFromFolder(
        folderUri: String,
        type: FolderType,
        forceRefresh: Boolean = false
    ): List<WallpaperItem> =
        withContext(Dispatchers.IO) {
            val key = itemsCacheKey(folderUri, type)
            if (!forceRefresh) {
                itemsCache[key]?.let { return@withContext it }
            }
            val results = if (folderUri.startsWith("klvw-aer://")) {
                getItemsFromAerFolder(folderUri, type)
            } else {
                val scanned = mutableListOf<WallpaperItem>()
                try {
                    val rootUri = Uri.parse(folderUri)
                    val rootDocId = DocumentsContract.getTreeDocumentId(rootUri)
                    scanDocumentTree(rootUri, rootDocId, type, folderUri, scanned, maxDepth = 2)
                } catch (_: Exception) {}
                scanned
            }
            itemsCache[key] = results
            results
        }

    private fun getItemsFromAerFolder(folderUri: String, type: FolderType): List<WallpaperItem> {
        val relPath = AerUri.relPath(folderUri)
        val aerRoot = File(context.filesDir, "aer")
        val dir = if (relPath.isNullOrEmpty()) aerRoot else File(aerRoot, relPath)
        if (!dir.exists()) return emptyList()
        val mimePrefix = if (type == FolderType.IMAGE) "image/" else "video/"
        val results = mutableListOf<WallpaperItem>()
        fun scan(d: File, depth: Int) {
            d.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { f ->
                when {
                    f.isDirectory && depth > 0 -> scan(f, depth - 1)
                    f.isFile -> {
                        val ext = f.extension.lowercase()
                        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: return@forEach
                        if (mime.startsWith(mimePrefix)) {
                            val docId = f.relativeTo(aerRoot).path.replace('\\', '/')
                            val contentUri = DocumentsContract.buildDocumentUri(AerDocumentsProvider.AUTHORITY, docId)
                            results += WallpaperItem(
                                uri = contentUri,
                                type = type,
                                displayName = f.name,
                                folderUri = folderUri,
                                lastModified = f.lastModified()
                            )
                        }
                    }
                }
            }
        }
        scan(dir, 2)
        return results
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        parentDocId: String,
        type: FolderType,
        folderUri: String,
        results: MutableList<WallpaperItem>,
        maxDepth: Int
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val mimePrefix = if (type == FolderType.IMAGE) "image/" else "video/"
        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2) ?: continue
                    val lastModified = if (!cursor.isNull(3)) cursor.getLong(3) else 0L
                    when {
                        mime.startsWith(mimePrefix) -> {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            results += WallpaperItem(
                                uri = fileUri,
                                type = type,
                                displayName = name,
                                folderUri = folderUri,
                                lastModified = lastModified
                            )
                        }
                        mime == DocumentsContract.Document.MIME_TYPE_DIR && maxDepth > 0 ->
                            scanDocumentTree(treeUri, docId, type, folderUri, results, maxDepth - 1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }
}

/** Outcome of deleting a wallpaper source file on disk / via SAF. */
sealed class SourceDeleteResult {
    data object Ok : SourceDeleteResult()
    data class Err(val reason: String) : SourceDeleteResult()
}
