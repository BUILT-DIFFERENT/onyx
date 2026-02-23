package com.onyx.android.objects.model

enum class PageObjectKind(
    val storageValue: String,
) {
    SHAPE("shape"),
    IMAGE("image"),
    TEXT("text"),
    ;

    companion object {
        fun fromStorageValue(value: String): PageObjectKind {
            return entries.firstOrNull { kind -> kind.storageValue == value } ?: SHAPE
        }
    }
}

data class PageObject(
    val objectId: String,
    val pageId: String,
    val noteId: String,
    val kind: PageObjectKind,
    val zIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float,
    val payloadJson: String,
    val shapePayload: ShapePayload? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
