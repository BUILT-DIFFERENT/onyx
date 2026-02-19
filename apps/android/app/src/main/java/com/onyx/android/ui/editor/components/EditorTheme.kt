@file:Suppress("MagicNumber")

package com.onyx.android.ui.editor.components

import androidx.compose.ui.graphics.Color

internal val NOTEWISE_CHROME = Color(0xFF20263A)
internal val NOTEWISE_PILL = Color(0xFF2B3144)
internal val NOTEWISE_ICON = Color(0xFFF2F5FF)
internal val NOTEWISE_ICON_MUTED = Color(0xFFA9B0C5)
internal val NOTEWISE_SELECTED = Color(0xFF136CC5)
internal val NOTEWISE_STROKE = Color(0xFF3B435B)
internal val NOTE_PAPER = Color(0xFFFDFDFD)
internal val NOTE_PAPER_STROKE = Color(0xFFCBCED6)
internal val NOTE_PAPER_SHADOW = Color(0x15000000)
internal val EDGE_GLOW_COLOR = Color(0x40000000)

internal const val HEX_COLOR_LENGTH_RGB = 7
internal const val MIN_COLOR_CHANNEL = 0
internal const val MAX_COLOR_CHANNEL = 255
internal const val COLOR_ALPHA_MAX_FLOAT = 255f
internal const val DEFAULT_HIGHLIGHTER_OPACITY = 0.35f
internal const val DEFAULT_HIGHLIGHTER_BASE_WIDTH = 6.5f
internal val HEX_COLOR_REGEX = Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$")

internal const val NOTEWISE_LEFT_GROUP_WIDTH_DP = 360
internal const val NOTEWISE_RIGHT_GROUP_WIDTH_DP = 82
internal const val TOOLBAR_ROW_HEIGHT_DP = 48
internal const val TOOLBAR_VERTICAL_PADDING_DP = 8
internal const val TOOLBAR_HORIZONTAL_PADDING_DP = 12
internal const val TOOLBAR_ITEM_SPACING_DP = 4
internal const val TOOLBAR_GROUP_CORNER_RADIUS_DP = 8
internal const val TOOLBAR_GROUP_HORIZONTAL_PADDING_DP = 8
internal const val TOOLBAR_GROUP_VERTICAL_PADDING_DP = 6
internal const val TOOLBAR_DIVIDER_HORIZONTAL_PADDING_DP = 4
internal const val TOOLBAR_DIVIDER_HEIGHT_DP = 20
internal const val TOOLBAR_DIVIDER_WIDTH_DP = 1
internal const val TOOLBAR_TOUCH_TARGET_SIZE_DP = 48
internal const val ERASER_BUTTON_SIZE_DP = 48
internal const val PALETTE_SWATCH_SIZE_DP = 24
internal const val SELECTED_BORDER_WIDTH_DP = 2
internal const val UNSELECTED_BORDER_WIDTH_DP = 1
internal const val UNSELECTED_BORDER_ALPHA = 0.5f
internal const val TOOLBAR_CONTENT_PADDING_DP = 8
internal const val TOOL_SETTINGS_PANEL_MIN_WIDTH_DP = 280
internal const val TOOL_SETTINGS_PANEL_OFFSET_DP = 8
internal const val TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP = 16
internal const val TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP = 12
internal const val TOOL_SETTINGS_PANEL_ITEM_SPACING_DP = 8
internal const val TOOL_SETTINGS_DIALOG_SLIDER_STEPS = 4
internal const val BRUSH_SIZE_MIN = 0.5f
internal const val BRUSH_SIZE_MAX = 20f
internal const val BRUSH_SIZE_STEPS = 10
internal const val BRUSH_SIZE_INDICATOR_SCALE = 2f
internal const val BRUSH_SIZE_INDICATOR_MIN_DP = 4f
internal const val BRUSH_SIZE_INDICATOR_MAX_DP = 24f
internal const val BRUSH_SIZE_SLIDER_WIDTH_DP = 80
internal const val HIGHLIGHTER_OPACITY_MIN = 0.1f
internal const val HIGHLIGHTER_OPACITY_MAX = 0.6f
internal const val COLOR_PICKER_TEXT_FIELD_WIDTH_DP = 180
internal const val COMPACT_TOOLBAR_THRESHOLD_DP = 600
internal const val PAGE_SHADOW_SPREAD_DP = 12
internal const val PAGE_BORDER_WIDTH_DP = 1
internal const val EDGE_GLOW_WIDTH_DP = 32
internal const val EDGE_GLOW_ALPHA_MAX = 0.8f
internal const val EDITOR_VIEWPORT_TEST_TAG = "note-editor-viewport"
internal const val TITLE_INPUT_TEST_TAG = "note-title-input"

internal val DEFAULT_PALETTE =
    listOf(
        "#111111",
        "#1E88E5",
        "#E53935",
        "#43A047",
        "#8E24AA",
    )

internal enum class ToolPanelType {
    PEN,
    HIGHLIGHTER,
    ERASER,
}
