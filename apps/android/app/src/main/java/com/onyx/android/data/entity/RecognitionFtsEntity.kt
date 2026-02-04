package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 table for full-text search on recognized text.
 *
 * Room FTS Content Sync: The @Fts4(contentEntity) annotation creates an FTS table
 * that automatically syncs with RecognitionIndexEntity. Room generates triggers
 * to keep the FTS index updated when RecognitionIndexEntity rows are inserted/updated/deleted.
 *
 * The FTS table's rowid automatically maps to the content entity's rowid,
 * allowing JOIN queries to link back to the source entity (and its pageId/noteId).
 */
@Fts4(contentEntity = RecognitionIndexEntity::class)
@Entity(tableName = "recognition_fts")
data class RecognitionFtsEntity(
    val recognizedText: String,
)
