package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.PdfTextIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfTextIndexDao {
    @Query("SELECT * FROM pdf_text_index WHERE pageId = :pageId")
    suspend fun getByPageId(pageId: String): PdfTextIndexEntity?

    @Query("SELECT * FROM pdf_text_index WHERE pdfAssetId = :pdfAssetId")
    suspend fun getByPdfAssetId(pdfAssetId: String): List<PdfTextIndexEntity>

    @Query(
        """
        SELECT pti.* FROM pdf_text_index pti
        INNER JOIN pdf_text_fts fts ON pti.rowid = fts.docid
        WHERE fts.extractedText MATCH :query
        """,
    )
    fun search(query: String): Flow<List<PdfTextIndexEntity>>

    @Query(
        """
        SELECT pti.* FROM pdf_text_index pti
        INNER JOIN pdf_text_fts fts ON pti.rowid = fts.docid
        WHERE fts.extractedText MATCH :query
        """,
    )
    suspend fun searchSync(query: String): List<PdfTextIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(index: PdfTextIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indices: List<PdfTextIndexEntity>)

    @Query("DELETE FROM pdf_text_index WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: String)

    @Query("DELETE FROM pdf_text_index WHERE pdfAssetId = :pdfAssetId")
    suspend fun deleteByPdfAssetId(pdfAssetId: String)

    @Query("SELECT COUNT(*) FROM pdf_text_index")
    suspend fun getCount(): Int
}
