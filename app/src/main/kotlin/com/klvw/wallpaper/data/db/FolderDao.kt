package com.klvw.wallpaper.data.db

import androidx.room.*
import com.klvw.wallpaper.data.model.FolderType
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FolderEntity>>

    /** All folders of [type], including privacy-marked. Visibility is filtered in repository. */
    @Query(
        "SELECT * FROM folders WHERE type = :type " +
            "ORDER BY hidden ASC, lastUsed DESC, addedAt DESC"
    )
    fun getByType(type: FolderType): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE hidden = 1 ORDER BY type ASC, addedAt DESC")
    fun getMarked(): Flow<List<FolderEntity>>

    @Query("UPDATE folders SET lastUsed = :time WHERE id = :id")
    suspend fun updateLastUsed(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE folders SET uri = :newUri WHERE uri = :oldUri")
    suspend fun updateUri(oldUri: String, newUri: String): Int

    @Query("SELECT * FROM folders WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE displayName = :name COLLATE NOCASE AND type = :type LIMIT 1")
    suspend fun findByDisplayName(name: String, type: FolderType): FolderEntity?

    @Query("SELECT * FROM folders WHERE type = :type ORDER BY lastUsed DESC, addedAt DESC")
    suspend fun getAllByType(type: FolderType): List<FolderEntity>
}
