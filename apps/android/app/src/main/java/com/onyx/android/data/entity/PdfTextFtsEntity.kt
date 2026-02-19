package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = PdfTextIndexEntity::class)
@Entity(tableName = "pdf_text_fts")
data class PdfTextFtsEntity(
    val extractedText: String,
)
