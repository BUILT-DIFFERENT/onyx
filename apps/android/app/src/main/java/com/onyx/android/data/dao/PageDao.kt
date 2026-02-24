package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY indexInNote ASC")
    fun getPagesForNote(noteId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY indexInNote ASC")
    suspend fun getPagesForNoteSync(noteId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE pageId = :pageId")
    suspend fun getById(pageId: String): PageEntity?

    @Query("SELECT MAX(indexInNote) FROM pages WHERE noteId = :noteId")
    suspend fun getMaxIndexForNote(noteId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Query("UPDATE pages SET updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateTimestamp(
        pageId: String,
        updatedAt: Long,
    )

    @Query("UPDATE pages SET kind = :kind, updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateKind(
        pageId: String,
        kind: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE pages SET templateId = :templateId, updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateTemplate(
        pageId: String,
        templateId: String?,
        updatedAt: Long,
    )

    @Query("UPDATE pages SET indexInNote = :indexInNote, updatedAt = :updatedAt WHERE pageId = :pageId")
    suspend fun updateIndex(
        pageId: String,
        indexInNote: Int,
        updatedAt: Long,
    )

    @Query("DELETE FROM pages WHERE pageId = :pageId")
    suspend fun delete(pageId: String)
}
