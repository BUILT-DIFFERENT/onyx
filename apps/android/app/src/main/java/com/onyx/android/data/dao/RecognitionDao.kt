package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.RecognitionIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionDao {
    @Query("SELECT * FROM recognition_index WHERE pageId = :pageId")
    suspend fun getByPageId(pageId: String): RecognitionIndexEntity?

    @Query(
        """
        SELECT ri.* FROM recognition_index ri
        INNER JOIN recognition_fts fts ON ri.rowid = fts.docid
        WHERE fts.recognizedText MATCH :query
        """,
    )
    fun search(query: String): Flow<List<RecognitionIndexEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recognition: RecognitionIndexEntity)

    @Query(
        """
        UPDATE recognition_index
        SET recognizedText = :text,
            recognizerVersion = :version,
            updatedAt = :updatedAt
        WHERE pageId = :pageId
        """,
    )
    suspend fun updateRecognition(
        pageId: String,
        text: String?,
        version: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM recognition_index WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: String)
}
