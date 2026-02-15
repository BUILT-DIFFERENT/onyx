package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["noteId", "tagId"],
    indices = [
        Index("noteId"),
        Index("tagId"),
    ],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)
