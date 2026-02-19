package com.onyx.android.config

enum class FeatureFlag(
    val key: String,
    val defaultValue: Boolean,
) {
    INK_PREDICTION_ENABLED(
        key = "ink_prediction_enabled",
        defaultValue = true,
    ),
    UI_EDITOR_COMPACT_ENABLED(
        key = "ui_editor_compact_enabled",
        defaultValue = false,
    ),
}
