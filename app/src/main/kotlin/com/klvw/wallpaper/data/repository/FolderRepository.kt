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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderDao: FolderDao
) {
    /** In-memory folder listing cache. Filled on first scan; cleared only via [invalidateItemsCache]. */
    private val itemsCache = ConcurrentHashMap<String, List<WallpaperItem>>()

    private fun itemsCacheKey(folderUri: String, type: FolderType) = "$folderUri\u0000${type.name}"
    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAll()

    fun getFoldersByType(type: FolderType): Flow<List<FolderEntity>> = folderDao.getByType(type)

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

    suspend fun touchFolder(id: Long) = folderDao.updateLastUsed(id)

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
