package com.onyx.android.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "editor_settings")
data class EditorSettingsEntity(
    @PrimaryKey val settingsId: String = "default",
    val selectedTool: String = "PEN",
    val penTool: String = "PEN",
    val penColor: String = "#000000",
    val penBaseWidth: Float = 2.0f,
    val penMinWidthFactor: Float = 0.85f,
    val penMaxWidthFactor: Float = 1.15f,
    val penSmoothingLevel: Float = 0.35f,
    val penEndTaperStrength: Float = 0.35f,
    val highlighterTool: String = "HIGHLIGHTER",
    val highlighterColor: String = "#B31E88E5",
    val highlighterBaseWidth: Float = 6.5f,
    val highlighterMinWidthFactor: Float = 0.85f,
    val highlighterMaxWidthFactor: Float = 1.15f,
    val highlighterSmoothingLevel: Float = 0.35f,
    val highlighterEndTaperStrength: Float = 0.35f,
    val lastNonEraserTool: String = "PEN",
    val singleFingerMode: String = "PAN",
    val doubleFingerMode: String = "ZOOM_PAN",
    val stylusPrimaryAction: String = "ERASER_HOLD",
    val stylusSecondaryAction: String = "ERASER_HOLD",
    val stylusLongHoldAction: String = "NO_ACTION",
    val doubleTapZoomAction: String = "NONE",
    val doubleTapZoomPointerMode: String = "FINGER_ONLY",
    val twoFingerTapAction: String = "UNDO",
    val threeFingerTapAction: String = "REDO",
    val latencyOptimizationMode: String = "NORMAL",
    val updatedAt: Long = 0L,
)
