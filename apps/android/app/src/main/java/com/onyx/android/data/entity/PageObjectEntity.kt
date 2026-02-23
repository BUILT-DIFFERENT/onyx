package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_objects",
    indices = [
        Index("pageId"),
        Index("noteId"),
        Index("kind"),
        Index(value = ["pageId", "zIndex"]),
    ],
)
data class PageObjectEntity(
    @PrimaryKey val objectId: String,
    val pageId: String,
    val noteId: String,
    val kind: String,
    val zIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float,
    val payloadJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
