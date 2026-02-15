package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey val folderId: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long,
)
