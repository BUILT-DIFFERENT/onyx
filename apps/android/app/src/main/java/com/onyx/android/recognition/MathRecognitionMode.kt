package com.onyx.android.recognition

enum class MathRecognitionMode(
    val storageValue: String,
    val label: String,
) {
    OFF(storageValue = "off", label = "Off"),
    INLINE_PREVIEW(storageValue = "inline_preview", label = "Inline preview"),
    LATEX_ONLY(storageValue = "latex_only", label = "LaTeX output"),
    ;

    companion object {
        fun fromStorageValue(value: String?): MathRecognitionMode =
            entries.firstOrNull { mode ->
                mode.storageValue == value
            } ?: OFF
    }
}
