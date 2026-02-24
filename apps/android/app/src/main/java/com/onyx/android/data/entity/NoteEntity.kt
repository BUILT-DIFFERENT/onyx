package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index("folderId")],
)
data class NoteEntity(
    @PrimaryKey val noteId: String,
    val ownerUserId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val folderId: String? = null,
    val lastOpenedPageId: String? = null,
)
