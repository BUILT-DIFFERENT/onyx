package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    indices = [Index("noteId"), Index("templateId")],
)
data class PageEntity(
    @PrimaryKey val pageId: String,
    val noteId: String,
    val kind: String,
    val geometryKind: String,
    val indexInNote: Int,
    val width: Float,
    val height: Float,
    val unit: String = "pt",
    val pdfAssetId: String? = null,
    val pdfPageNo: Int? = null,
    val templateId: String? = null,
    val updatedAt: Long,
    val contentLamportMax: Long = 0,
)
