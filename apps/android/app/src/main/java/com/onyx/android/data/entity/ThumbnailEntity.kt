package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thumbnails")
data class ThumbnailEntity(
    @PrimaryKey val noteId: String,
    val filePath: String,
    val contentHash: String,
    val generatedAt: Long,
)
