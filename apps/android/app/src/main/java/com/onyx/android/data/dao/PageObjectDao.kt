package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.PageObjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("LongParameterList")
interface PageObjectDao {
    @Query("SELECT * FROM page_objects WHERE pageId = :pageId AND deletedAt IS NULL ORDER BY zIndex ASC, createdAt ASC")
    suspend fun getByPageId(pageId: String): List<PageObjectEntity>

    @Query("SELECT * FROM page_objects WHERE pageId = :pageId AND deletedAt IS NULL ORDER BY zIndex ASC, createdAt ASC")
    fun getByPageIdFlow(pageId: String): Flow<List<PageObjectEntity>>

    @Query("SELECT * FROM page_objects WHERE objectId = :objectId LIMIT 1")
    suspend fun getById(objectId: String): PageObjectEntity?

    @Query("SELECT COALESCE(MAX(zIndex), -1) FROM page_objects WHERE pageId = :pageId AND deletedAt IS NULL")
    suspend fun getMaxZIndex(pageId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pageObject: PageObjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pageObjects: List<PageObjectEntity>)

    @Query(
        """
        UPDATE page_objects
        SET x = :x,
            y = :y,
            width = :width,
            height = :height,
            rotationDeg = :rotationDeg,
            updatedAt = :updatedAt
        WHERE objectId = :objectId
        """,
    )
    suspend fun updateGeometry(
        objectId: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotationDeg: Float,
        updatedAt: Long,
    )

    @Query("UPDATE page_objects SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE objectId = :objectId")
    suspend fun markDeleted(
        objectId: String,
        deletedAt: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM page_objects WHERE pageId = :pageId")
    suspend fun deleteAllForPage(pageId: String)
}
