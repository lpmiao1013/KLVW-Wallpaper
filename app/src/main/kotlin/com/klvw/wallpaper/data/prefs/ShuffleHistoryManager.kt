package com.klvw.wallpaper.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val Context.shuffleDataStore: DataStore<Preferences> by preferencesDataStore(name = "klvw_shuffle")

/**
 * Tracks which items have been shown in the current cycle so every item in a folder is
 * used before any item repeats (shuffle-bag / non-repeating random).
 *
 * History is persisted in a separate DataStore so it survives process restarts between timer fires.
 * Each folder+type pair has its own independent history bucket.
 */
@Singleton
class ShuffleHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Pick the next item from [items] for a single-folder selection.
     * [folderUri] + [type] identify the history bucket.
     */
    suspend fun pickNext(
        items: List<WallpaperItem>,
        folderUri: String,
        type: FolderType
    ): WallpaperItem? = pick(items, bucketKey(folderUri, type.name))

    /**
     * Pick the next item from a combined multi-folder pool.
     * The bucket key is derived from the sorted distinct folder URIs present in [items],
     * so the key is stable regardless of the order folders are returned from the DB.
     */
    suspend fun pickNextFromPool(
        items: List<WallpaperItem>,
        type: FolderType
    ): WallpaperItem? {
        if (items.isEmpty()) return null
        val poolSeed = items.map { it.folderUri }.distinct().sorted().joinToString("\n")
        return pick(items, bucketKey(poolSeed, type.name))
    }

    private suspend fun pick(
        items: List<WallpaperItem>,
        key: Preferences.Key<String>
    ): WallpaperItem? {
        if (items.isEmpty()) return null

        val usedUris = loadHistory(key)
        val unused = items.filter { it.uri.toString() !in usedUris }

        val selected = if (unused.isNotEmpty()) {
            unused.random()
        } else {
            // Every item in this folder has been shown — wipe the slate and start a new cycle.
            clearHistory(key)
            items.random()
        }

        appendToHistory(key, selected.uri.toString())
        return selected
    }

    private suspend fun loadHistory(key: Preferences.Key<String>): Set<String> {
        val json = context.shuffleDataStore.data.first()[key] ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            buildSet { repeat(arr.length()) { i -> add(arr.getString(i)) } }
        } catch (_: Exception) { emptySet() }
    }

    private suspend fun appendToHistory(key: Preferences.Key<String>, uriString: String) {
        context.shuffleDataStore.edit { prefs ->
            val arr = try {
                prefs[key]?.let { JSONArray(it) } ?: JSONArray()
            } catch (_: Exception) { JSONArray() }
            arr.put(uriString)
            prefs[key] = arr.toString()
        }
    }

    private suspend fun clearHistory(key: Preferences.Key<String>) {
        context.shuffleDataStore.edit { it.remove(key) }
    }

    /** Drop a deleted file URI from every shuffle bucket (no-op if absent). */
    suspend fun forgetUri(uriString: String) {
        if (uriString.isBlank()) return
        context.shuffleDataStore.edit { prefs ->
            // asMap() yields Key<*>; rebuild typed Key<String> via name for writes.
            for ((anyKey, value) in prefs.asMap()) {
                if (value !is String) continue
                val key = stringPreferencesKey(anyKey.name)
                val arr = try {
                    JSONArray(value)
                } catch (_: Exception) {
                    continue
                }
                var changed = false
                val next = JSONArray()
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s == uriString) {
                        changed = true
                    } else if (s.isNotEmpty()) {
                        next.put(s)
                    }
                }
                if (changed) {
                    if (next.length() == 0) prefs.remove(key)
                    else prefs[key] = next.toString()
                }
            }
        }
    }

    /**
     * Generate a stable DataStore key for a given [seed]+[typeName] combination.
     * Java's String.hashCode() is specification-mandated and deterministic across restarts.
     */
    private fun bucketKey(seed: String, typeName: String): Preferences.Key<String> {
        val hash = "$seed:$typeName".hashCode().toUInt().toString(16)
        return stringPreferencesKey("sh_$hash")
    }
}
