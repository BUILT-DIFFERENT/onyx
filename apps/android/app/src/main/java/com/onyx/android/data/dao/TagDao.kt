package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.NoteTagCrossRef
import com.onyx.android.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE tagId = :tagId")
    suspend fun getById(tagId: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE tagId = :tagId")
    suspend fun delete(tagId: String)

    // Note-Tag relationship operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToNote(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM NoteTagCrossRef WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun removeTagFromNote(
        noteId: String,
        tagId: String,
    )

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN NoteTagCrossRef ntr ON t.tagId = ntr.tagId
        WHERE ntr.noteId = :noteId
        ORDER BY t.name ASC
    """,
    )
    fun getTagsForNote(noteId: String): Flow<List<TagEntity>>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN NoteTagCrossRef ntr ON n.noteId = ntr.noteId
        WHERE ntr.tagId = :tagId AND n.deletedAt IS NULL
        ORDER BY n.updatedAt DESC
    """,
    )
    fun getNotesWithTag(tagId: String): Flow<List<com.onyx.android.data.entity.NoteEntity>>

    /** Test-only query to get all tags synchronously */
    @Query("SELECT * FROM tags ORDER BY tagId")
    suspend fun getAllTagsForTesting(): List<TagEntity>
}
