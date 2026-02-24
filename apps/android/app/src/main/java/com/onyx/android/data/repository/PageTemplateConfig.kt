package com.onyx.android.data.repository

data class PageTemplateConfig(
    val templateId: String?,
    val backgroundKind: String,
    val spacing: Float,
    val lineWidth: Float,
    val colorHex: String,
) {
    companion object {
        const val KIND_BLANK = "blank"
        const val KIND_GRID = "grid"
        const val KIND_LINED = "lined"
        const val KIND_DOTTED = "dotted"
        const val KIND_CORNELL = "cornell"
        const val KIND_ENGINEERING = "engineering"
        const val KIND_MUSIC = "music"
        const val DEFAULT_SPACING = 24f
        const val DEFAULT_LINE_WIDTH = 1f
        const val DEFAULT_COLOR_HEX = "#E0E0E0"

        val BLANK =
            PageTemplateConfig(
                templateId = null,
                backgroundKind = KIND_BLANK,
                spacing = 0f,
                lineWidth = DEFAULT_LINE_WIDTH,
                colorHex = DEFAULT_COLOR_HEX,
            )
    }
}
