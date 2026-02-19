package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_text_index",
    indices = [
        Index("pageId"),
        Index("pdfAssetId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PdfTextIndexEntity(
    @PrimaryKey val pageId: String,
    val pdfAssetId: String,
    val pageNo: Int,
    val extractedText: String?,
    val extractedAt: Long,
    val extractorVersion: String? = null,
)
