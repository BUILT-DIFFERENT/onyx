package com.onyx.android.objects.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TextAlign(
    val storageValue: String,
) {
    @SerialName("start")
    START("start"),

    @SerialName("center")
    CENTER("center"),

    @SerialName("end")
    END("end"),
}

@Serializable
data class TextPayload(
    val text: String = "Text",
    val align: TextAlign = TextAlign.START,
    val color: String = "#111111",
    val fontSizeSp: Float = 16f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
