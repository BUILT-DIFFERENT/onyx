package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_index")
data class RecognitionIndexEntity(
    @PrimaryKey val pageId: String,
    val noteId: String,
    val recognizedText: String? = null,
    val recognizedAtLamport: Long? = null,
    val recognizerVersion: String? = null,
    val updatedAt: Long,
)
