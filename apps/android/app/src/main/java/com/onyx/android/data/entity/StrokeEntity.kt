package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strokes")
data class StrokeEntity(
    @PrimaryKey val strokeId: String,
    val pageId: String,
    val strokeData: ByteArray,
    val style: String,
    val bounds: String,
    val createdAt: Long,
    val createdLamport: Long = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StrokeEntity

        return strokeId == other.strokeId &&
            pageId == other.pageId &&
            strokeData.contentEquals(other.strokeData) &&
            style == other.style &&
            bounds == other.bounds &&
            createdAt == other.createdAt &&
            createdLamport == other.createdLamport
    }

    override fun hashCode(): Int {
        var result = strokeId.hashCode()
        result = 31 * result + pageId.hashCode()
        result = 31 * result + strokeData.contentHashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + bounds.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + createdLamport.hashCode()
        return result
    }
}
