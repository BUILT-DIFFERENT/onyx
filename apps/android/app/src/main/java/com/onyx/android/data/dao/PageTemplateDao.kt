package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.PageTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageTemplateDao {
    @Query("SELECT * FROM page_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<PageTemplateEntity>>

    @Query("SELECT * FROM page_templates WHERE templateId = :templateId")
    suspend fun getById(templateId: String): PageTemplateEntity?

    @Query("SELECT * FROM page_templates WHERE templateId IN (:templateIds)")
    suspend fun getByIds(templateIds: Set<String>): List<PageTemplateEntity>

    @Query("SELECT * FROM page_templates WHERE isBuiltIn = 1 ORDER BY name ASC")
    fun getBuiltInTemplates(): Flow<List<PageTemplateEntity>>

    @Query(
        """
        SELECT * FROM page_templates
        WHERE isBuiltIn = 0
          AND templateId LIKE 'custom-template-%'
        ORDER BY createdAt DESC
        """,
    )
    fun getCustomTemplates(): Flow<List<PageTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: PageTemplateEntity)

    @Query("DELETE FROM page_templates WHERE templateId = :templateId")
    suspend fun delete(templateId: String)

    @Query("SELECT * FROM page_templates ORDER BY templateId")
    suspend fun getAllTemplatesForTesting(): List<PageTemplateEntity>
}
