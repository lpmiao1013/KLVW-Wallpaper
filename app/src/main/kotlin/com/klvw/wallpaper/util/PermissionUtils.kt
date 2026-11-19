package com.klvw.wallpaper.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasMediaPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private val readWriteFlags: Int =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Persist both read and write so later SAF deletes ([DocumentsContract.deleteDocument]) work.
     * Falls back to read-only if the grant does not include write.
     */
    fun persistFolderPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, readWriteFlags)
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
    }

    fun persistDocumentPermission(context: Context, uri: Uri) {
        persistFolderPermission(context, uri)
    }

    /** True when [uri] (typically a tree URI) has persisted write access. */
    fun hasPersistedWrite(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }

    /**
     * Try to upgrade/persist write on [uri] if the current grant still allows it
     * (e.g. right after OpenDocumentTree). Returns whether write is available afterward.
     */
    fun ensurePersistedWrite(context: Context, uri: Uri): Boolean {
        if (hasPersistedWrite(context, uri)) return true
        return try {
            context.contentResolver.takePersistableUriPermission(uri, readWriteFlags)
            hasPersistedWrite(context, uri)
        } catch (_: SecurityException) {
            false
        }
    }
}
