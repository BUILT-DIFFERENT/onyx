package com.onyx.android.data.repository

data class PageTemplateConfig(
    val templateId: String?,
    val backgroundKind: String,
    val spacing: Float,
    val colorHex: String,
) {
    companion object {
        const val KIND_BLANK = "blank"
        const val KIND_GRID = "grid"
        const val KIND_LINED = "lined"
        const val KIND_DOTTED = "dotted"
        const val DEFAULT_SPACING = 24f
        const val DEFAULT_COLOR_HEX = "#E0E0E0"

        val BLANK =
            PageTemplateConfig(
                templateId = null,
                backgroundKind = KIND_BLANK,
                spacing = 0f,
                colorHex = DEFAULT_COLOR_HEX,
            )
    }
}
