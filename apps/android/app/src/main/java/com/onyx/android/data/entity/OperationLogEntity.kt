package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "operation_log",
    indices = [Index("noteId"), Index("lamportClock"), Index(value = ["noteId", "lamportClock"])],
)
data class OperationLogEntity(
    @PrimaryKey val opId: String,
    val noteId: String,
    val deviceId: String,
    val lamportClock: Long,
    val operationType: String,
    val payload: String,
    val createdAt: Long,
)
