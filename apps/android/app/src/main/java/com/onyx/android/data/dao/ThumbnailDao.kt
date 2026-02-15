package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.ThumbnailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThumbnailDao {
    @Query("SELECT * FROM thumbnails WHERE noteId = :noteId")
    suspend fun getByNoteId(noteId: String): ThumbnailEntity?

    @Query("SELECT * FROM thumbnails WHERE noteId = :noteId")
    fun getByNoteIdFlow(noteId: String): Flow<ThumbnailEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thumbnail: ThumbnailEntity)

    @Query("DELETE FROM thumbnails WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM thumbnails WHERE generatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int

    /** Test-only query to get all thumbnails synchronously */
    @Query("SELECT * FROM thumbnails ORDER BY noteId")
    suspend fun getAllThumbnailsForTesting(): List<ThumbnailEntity>
}
