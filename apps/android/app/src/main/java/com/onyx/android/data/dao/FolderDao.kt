package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE folderId = :folderId")
    suspend fun getById(folderId: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getFoldersByParent(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE folderId = :folderId")
    suspend fun delete(folderId: String)

    /** Test-only query to get all folders synchronously */
    @Query("SELECT * FROM folders ORDER BY folderId")
    suspend fun getAllFoldersForTesting(): List<FolderEntity>
}
