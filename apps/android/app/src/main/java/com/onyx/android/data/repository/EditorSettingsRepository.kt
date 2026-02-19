package com.onyx.android.data.repository

import com.onyx.android.data.dao.EditorSettingsDao
import com.onyx.android.data.entity.EditorSettingsEntity
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class EditorSettings(
    val selectedTool: Tool,
    val penBrush: Brush,
    val highlighterBrush: Brush,
    val lastNonEraserTool: Tool,
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
                    updatedAt = System.currentTimeMillis(),
                )
            editorSettingsDao.saveSettings(entity)
        }

        private fun EditorSettingsEntity.toSettings(): EditorSettings {
            val selected = runCatching { Tool.valueOf(selectedTool) }.getOrDefault(Tool.PEN)
            val penTool = runCatching { Tool.valueOf(penTool) }.getOrDefault(Tool.PEN)
            val highlighterTool = runCatching { Tool.valueOf(highlighterTool) }.getOrDefault(Tool.HIGHLIGHTER)
            val lastTool = runCatching { Tool.valueOf(lastNonEraserTool) }.getOrDefault(Tool.PEN)

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
                )
        }
    }
