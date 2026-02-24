package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE noteId = :noteId")
    suspend fun getById(noteId: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateTimestamp(
        noteId: String,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET lastOpenedPageId = :pageId WHERE noteId = :noteId")
    suspend fun updateLastOpenedPage(
        noteId: String,
        pageId: String?,
    )

    @Query(
        """
        UPDATE notes
        SET isLocked = :isLocked,
            lockUpdatedAt = :updatedAt,
            updatedAt = :updatedAt
        WHERE noteId = :noteId
        """,
    )
    suspend fun updateLockState(
        noteId: String,
        isLocked: Boolean,
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

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun restore(
        noteId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM notes WHERE noteId = :noteId")
    suspend fun hardDelete(noteId: String)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY updatedAt DESC")
    fun getRootNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateFolder(
        noteId: String,
        folderId: String?,
        updatedAt: Long,
    )

    @Query("UPDATE notes SET folderId = NULL, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun moveNotesToRoot(
        folderId: String,
        updatedAt: Long,
    )

    // Sorted queries for root notes
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY title ASC, noteId ASC")
    fun getRootNotesSortedByNameAsc(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY title DESC, noteId DESC")
    fun getRootNotesSortedByNameDesc(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY createdAt DESC, noteId DESC")
    fun getRootNotesSortedByCreatedDesc(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY createdAt ASC, noteId ASC")
    fun getRootNotesSortedByCreatedAsc(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY updatedAt DESC, noteId DESC")
    fun getRootNotesSortedByModifiedDesc(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId IS NULL ORDER BY updatedAt ASC, noteId ASC")
    fun getRootNotesSortedByModifiedAsc(): Flow<List<NoteEntity>>

    // Sorted queries for folder notes
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY title ASC, noteId ASC")
    fun getNotesByFolderSortedByNameAsc(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY title DESC, noteId DESC")
    fun getNotesByFolderSortedByNameDesc(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY createdAt DESC, noteId DESC")
    fun getNotesByFolderSortedByCreatedDesc(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY createdAt ASC, noteId ASC")
    fun getNotesByFolderSortedByCreatedAsc(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY updatedAt DESC, noteId DESC")
    fun getNotesByFolderSortedByModifiedDesc(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND folderId = :folderId ORDER BY updatedAt ASC, noteId ASC")
    fun getNotesByFolderSortedByModifiedAsc(folderId: String): Flow<List<NoteEntity>>

    // Date range filtering for root notes
    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        AND folderId IS NULL
        AND updatedAt >= :startTime
        AND updatedAt < :endTime
        ORDER BY updatedAt DESC, noteId DESC
        """,
    )
    fun getRootNotesByDateRange(
        startTime: Long,
        endTime: Long,
    ): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        AND folderId IS NULL
        AND updatedAt < :endTime
        ORDER BY updatedAt DESC, noteId DESC
        """,
    )
    fun getRootNotesOlderThan(endTime: Long): Flow<List<NoteEntity>>

    // Date range filtering for folder notes
    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        AND folderId = :folderId
        AND updatedAt >= :startTime
        AND updatedAt < :endTime
        ORDER BY updatedAt DESC, noteId DESC
        """,
    )
    fun getNotesByFolderAndDateRange(
        folderId: String,
        startTime: Long,
        endTime: Long,
    ): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
        AND folderId = :folderId
        AND updatedAt < :endTime
        ORDER BY updatedAt DESC, noteId DESC
        """,
    )
    fun getNotesByFolderOlderThan(
        folderId: String,
        endTime: Long,
    ): Flow<List<NoteEntity>>

    /** Test-only query to get all notes synchronously */
    @Query("SELECT * FROM notes ORDER BY noteId")
    suspend fun getAllNotesForTesting(): List<NoteEntity>

    @Query("SELECT title FROM notes WHERE deletedAt IS NULL")
    suspend fun getActiveTitles(): List<String>
}
