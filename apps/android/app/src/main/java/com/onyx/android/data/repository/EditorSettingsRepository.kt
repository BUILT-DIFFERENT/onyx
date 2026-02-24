package com.onyx.android.data.repository

import com.onyx.android.data.dao.EditorSettingsDao
import com.onyx.android.data.entity.EditorSettingsEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.InputSettings
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.input.StylusButtonAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class EditorSettings(
    val selectedTool: Tool,
    val penBrush: Brush,
    val highlighterBrush: Brush,
    val lastNonEraserTool: Tool,
    val inputSettings: InputSettings = InputSettings(),
)

@Singleton
class EditorSettingsRepository
    @Inject
    constructor(
        private val editorSettingsDao: EditorSettingsDao,
    ) {
        fun getSettings(): Flow<EditorSettings?> =
            editorSettingsDao.getSettings().map { entity ->
                entity?.toSettings()
            }

        suspend fun getSettingsOnce(): EditorSettings? = editorSettingsDao.getSettingsOnce()?.toSettings()

        suspend fun saveSettings(settings: EditorSettings) {
            val entity =
                EditorSettingsEntity(
                    settingsId = "default",
                    selectedTool = settings.selectedTool.name,
                    penTool = settings.penBrush.tool.name,
                    penColor = settings.penBrush.color,
                    penBaseWidth = settings.penBrush.baseWidth,
                    penMinWidthFactor = settings.penBrush.minWidthFactor,
                    penMaxWidthFactor = settings.penBrush.maxWidthFactor,
                    penSmoothingLevel = settings.penBrush.smoothingLevel,
                    penEndTaperStrength = settings.penBrush.endTaperStrength,
                    highlighterTool = settings.highlighterBrush.tool.name,
                    highlighterColor = settings.highlighterBrush.color,
                    highlighterBaseWidth = settings.highlighterBrush.baseWidth,
                    highlighterMinWidthFactor = settings.highlighterBrush.minWidthFactor,
                    highlighterMaxWidthFactor = settings.highlighterBrush.maxWidthFactor,
                    highlighterSmoothingLevel = settings.highlighterBrush.smoothingLevel,
                    highlighterEndTaperStrength = settings.highlighterBrush.endTaperStrength,
                    lastNonEraserTool = settings.lastNonEraserTool.name,
                    singleFingerMode = settings.inputSettings.singleFingerMode.name,
                    doubleFingerMode = settings.inputSettings.doubleFingerMode.name,
                    stylusPrimaryAction = settings.inputSettings.stylusPrimaryAction.name,
                    stylusSecondaryAction = settings.inputSettings.stylusSecondaryAction.name,
                    stylusLongHoldAction = settings.inputSettings.stylusLongHoldAction.name,
                    updatedAt = System.currentTimeMillis(),
                )
            editorSettingsDao.saveSettings(entity)
        }

        private fun EditorSettingsEntity.toSettings(): EditorSettings {
            val selected = runCatching { Tool.valueOf(selectedTool) }.getOrDefault(Tool.PEN)
            val penTool = runCatching { Tool.valueOf(penTool) }.getOrDefault(Tool.PEN)
            val highlighterTool = runCatching { Tool.valueOf(highlighterTool) }.getOrDefault(Tool.HIGHLIGHTER)
            val lastTool = runCatching { Tool.valueOf(lastNonEraserTool) }.getOrDefault(Tool.PEN)
            val singleFingerMode =
                runCatching { SingleFingerMode.valueOf(singleFingerMode) }
                    .getOrDefault(SingleFingerMode.PAN)
            val doubleFingerMode =
                runCatching { DoubleFingerMode.valueOf(doubleFingerMode) }.getOrDefault(DoubleFingerMode.ZOOM_PAN)
            val stylusPrimaryAction =
                runCatching { StylusButtonAction.valueOf(stylusPrimaryAction) }
                    .getOrDefault(StylusButtonAction.ERASER_HOLD)
            val stylusSecondaryAction =
                runCatching { StylusButtonAction.valueOf(stylusSecondaryAction) }
                    .getOrDefault(StylusButtonAction.ERASER_HOLD)
            val stylusLongHoldAction =
                runCatching { StylusButtonAction.valueOf(stylusLongHoldAction) }
                    .getOrDefault(StylusButtonAction.NO_ACTION)

            return EditorSettings(
                selectedTool = selected,
                penBrush =
                    Brush(
                        tool = penTool,
                        color = penColor,
                        baseWidth = penBaseWidth,
                        minWidthFactor = penMinWidthFactor,
                        maxWidthFactor = penMaxWidthFactor,
                        smoothingLevel = penSmoothingLevel,
                        endTaperStrength = penEndTaperStrength,
                    ),
                highlighterBrush =
                    Brush(
                        tool = highlighterTool,
                        color = highlighterColor,
                        baseWidth = highlighterBaseWidth,
                        minWidthFactor = highlighterMinWidthFactor,
                        maxWidthFactor = highlighterMaxWidthFactor,
                        smoothingLevel = highlighterSmoothingLevel,
                        endTaperStrength = highlighterEndTaperStrength,
                    ),
                lastNonEraserTool = lastTool,
                inputSettings =
                    InputSettings(
                        singleFingerMode = singleFingerMode,
                        doubleFingerMode = doubleFingerMode,
                        stylusPrimaryAction = stylusPrimaryAction,
                        stylusSecondaryAction = stylusSecondaryAction,
                        stylusLongHoldAction = stylusLongHoldAction,
                    ),
            )
        }

        companion object {
            const val DEFAULT_STACKED_HIGHLIGHTER_BASE_WIDTH = 6.5f

            val DEFAULT_SETTINGS =
                EditorSettings(
                    selectedTool = Tool.PEN,
                    penBrush = Brush(tool = Tool.PEN, color = "#000000", baseWidth = 2.0f),
                    highlighterBrush =
                        Brush(
                            tool = Tool.HIGHLIGHTER,
                            color = "#B31E88E5",
                            baseWidth = DEFAULT_STACKED_HIGHLIGHTER_BASE_WIDTH,
                        ),
                    lastNonEraserTool = Tool.PEN,
                    inputSettings = InputSettings(),
                )
        }
    }
