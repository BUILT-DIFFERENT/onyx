package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "page_templates")
data class PageTemplateEntity(
    @PrimaryKey val templateId: String,
    val name: String,
    val backgroundKind: String,
    val spacing: Float? = null,
    val color: String? = null,
    val isBuiltIn: Boolean,
    val createdAt: Long,
)
