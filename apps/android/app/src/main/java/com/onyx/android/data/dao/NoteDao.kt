package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE noteId = :noteId")
    suspend fun getById(noteId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateTimestamp(
        noteId: String,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET title = :title, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateTitle(
        noteId: String,
        title: String,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET deletedAt = :timestamp WHERE noteId = :noteId")
    suspend fun softDelete(
        noteId: String,
        timestamp: Long,
    )
}
