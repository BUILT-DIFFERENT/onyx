package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val tagId: String,
    val name: String,
    val color: String,
    val createdAt: Long,
)
