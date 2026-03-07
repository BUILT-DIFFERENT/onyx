@file:Suppress("MagicNumber")

package com.onyx.android.ink.model

import kotlinx.serialization.Serializable

@Serializable
data class BrushPreset(
    val id: String,
    val name: String,
    val tool: Tool,
    val color: String,
    val baseWidth: Float,
    val smoothingLevel: Float = 0.5f,
    val endTaperStrength: Float = 0.6f,
    val minWidthFactor: Float = 0.15f,
    val maxWidthFactor: Float = 2.5f,
    val nibRotation: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Preset id must not be blank" }
        require(name.isNotBlank()) { "Preset name must not be blank" }
        require(HEX_COLOR_REGEX.matches(color)) { "Preset color must be hex format #RRGGBB" }
        require(baseWidth > 0f) { "Preset baseWidth must be > 0" }
        require(smoothingLevel in 0f..1f) { "Preset smoothingLevel must be in 0..1" }
        require(endTaperStrength in 0f..1f) { "Preset endTaperStrength must be in 0..1" }
        require(minWidthFactor > 0f) { "Preset minWidthFactor must be > 0" }
        require(maxWidthFactor >= minWidthFactor) { "Preset maxWidthFactor must be >= minWidthFactor" }
    }

    companion object {
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        val BALLPOINT =
            BrushPreset(
                id = "ballpoint",
                name = "Ballpoint",
                tool = Tool.PEN,
                color = "#1F2937",
                baseWidth = 1.5f,
                smoothingLevel = 0.5f,
                endTaperStrength = 0.6f,
                minWidthFactor = 0.15f,
                maxWidthFactor = 2.5f,
            )

        val FOUNTAIN =
            BrushPreset(
                id = "fountain",
                name = "Fountain",
                tool = Tool.PEN,
                color = "#0F172A",
                baseWidth = 2.0f,
                smoothingLevel = 0.5f,
                endTaperStrength = 0.6f,
                minWidthFactor = 0.2f,
                maxWidthFactor = 2.8f,
                nibRotation = true,
            )

        val PENCIL =
            BrushPreset(
                id = "pencil",
                name = "Pencil",
                tool = Tool.PEN,
                color = "#4B5563",
                baseWidth = 1.0f,
                smoothingLevel = 0.45f,
                endTaperStrength = 0.5f,
                minWidthFactor = 0.25f,
                maxWidthFactor = 1.8f,
            )

        val STANDARD_HIGHLIGHTER =
            BrushPreset(
                id = "standard_highlighter",
                name = "Standard",
                tool = Tool.HIGHLIGHTER,
                color = "#FDE047",
                baseWidth = 8.0f,
                smoothingLevel = 0.5f,
                endTaperStrength = 0.0f,
                minWidthFactor = 0.7f,
                maxWidthFactor = 1.3f,
            )

        val WIDE_HIGHLIGHTER =
            BrushPreset(
                id = "wide_highlighter",
                name = "Wide",
                tool = Tool.HIGHLIGHTER,
                color = "#FACC15",
                baseWidth = 16.0f,
                smoothingLevel = 0.5f,
                endTaperStrength = 0.0f,
                minWidthFactor = 0.7f,
                maxWidthFactor = 1.3f,
            )

        val DEFAULT_PRESETS =
            listOf(
                BALLPOINT,
                FOUNTAIN,
                PENCIL,
                STANDARD_HIGHLIGHTER,
                WIDE_HIGHLIGHTER,
            )
    }
}
