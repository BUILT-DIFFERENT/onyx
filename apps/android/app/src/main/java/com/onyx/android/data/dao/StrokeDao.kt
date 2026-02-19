package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.StrokeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getByPageId(pageId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun getByPageIdFlow(pageId: String): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE strokeId = :strokeId")
    suspend fun getById(strokeId: String): StrokeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE strokeId = :strokeId")
    suspend fun delete(strokeId: String)

    @Query("DELETE FROM strokes WHERE strokeId IN (:strokeIds)")
    suspend fun deleteByIds(strokeIds: List<String>)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun deleteAllForPage(pageId: String)
}
