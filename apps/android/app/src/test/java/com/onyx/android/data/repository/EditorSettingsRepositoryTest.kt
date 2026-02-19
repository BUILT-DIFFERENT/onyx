package com.onyx.android.data.repository

import com.onyx.android.data.entity.EditorSettingsEntity
import com.onyx.android.ink.model.Tool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EditorSettingsRepositoryTest {
    @Test
    fun `getSettings returns null when no settings exist`() =
        runTest {
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    every { getSettings() } returns flowOf(null)
                }
            val repository = EditorSettingsRepository(dao)

            val result = repository.getSettings().first()

            assertNull(result)
        }

    @Test
    fun `getSettings returns mapped settings when settings exist`() =
        runTest {
            val entity =
                EditorSettingsEntity(
                    settingsId = "default",
                    selectedTool = "PEN",
                    penTool = "PEN",
                    penColor = "#FF0000",
                    penBaseWidth = 3.0f,
                    penMinWidthFactor = 0.9f,
                    penMaxWidthFactor = 1.1f,
                    penSmoothingLevel = 0.5f,
                    penEndTaperStrength = 0.4f,
                    highlighterTool = "HIGHLIGHTER",
                    highlighterColor = "#00FF00",
                    highlighterBaseWidth = 12.0f,
                    highlighterMinWidthFactor = 0.8f,
                    highlighterMaxWidthFactor = 1.2f,
                    highlighterSmoothingLevel = 0.3f,
                    highlighterEndTaperStrength = 0.2f,
                    lastNonEraserTool = "HIGHLIGHTER",
                    updatedAt = 123L,
                )
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    every { getSettings() } returns flowOf(entity)
                }
            val repository = EditorSettingsRepository(dao)

            val result = repository.getSettings().first()

            assertNotNull(result)
            assertEquals(Tool.PEN, result!!.selectedTool)
            assertEquals("#FF0000", result.penBrush.color)
            assertEquals(3.0f, result.penBrush.baseWidth)
            assertEquals(Tool.HIGHLIGHTER, result.lastNonEraserTool)
            assertEquals("#00FF00", result.highlighterBrush.color)
            assertEquals(12.0f, result.highlighterBrush.baseWidth)
        }

    @Test
    fun `getSettingsOnce returns null when no settings exist`() =
        runTest {
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    coEvery { getSettingsOnce() } returns null
                }
            val repository = EditorSettingsRepository(dao)

            val result = repository.getSettingsOnce()

            assertNull(result)
        }

    @Test
    fun `getSettingsOnce returns mapped settings when settings exist`() =
        runTest {
            val entity =
                EditorSettingsEntity(
                    settingsId = "default",
                    selectedTool = "ERASER",
                    penTool = "PEN",
                    penColor = "#0000FF",
                    penBaseWidth = 5.0f,
                    penMinWidthFactor = 0.85f,
                    penMaxWidthFactor = 1.15f,
                    penSmoothingLevel = 0.35f,
                    penEndTaperStrength = 0.35f,
                    highlighterTool = "HIGHLIGHTER",
                    highlighterColor = "#B31E88E5",
                    highlighterBaseWidth = 6.5f,
                    highlighterMinWidthFactor = 0.85f,
                    highlighterMaxWidthFactor = 1.15f,
                    highlighterSmoothingLevel = 0.35f,
                    highlighterEndTaperStrength = 0.35f,
                    lastNonEraserTool = "PEN",
                    updatedAt = 123L,
                )
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    coEvery { getSettingsOnce() } returns entity
                }
            val repository = EditorSettingsRepository(dao)

            val result = repository.getSettingsOnce()

            assertNotNull(result)
            assertEquals(Tool.ERASER, result!!.selectedTool)
            assertEquals("#0000FF", result.penBrush.color)
            assertEquals(5.0f, result.penBrush.baseWidth)
            assertEquals(Tool.PEN, result.lastNonEraserTool)
        }

    @Test
    fun `saveSettings upserts entity with correct values`() =
        runTest {
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    coEvery { saveSettings(any()) } returns Unit
                }
            val repository = EditorSettingsRepository(dao)
            val settings =
                EditorSettings(
                    selectedTool = Tool.HIGHLIGHTER,
                    penBrush =
                        com.onyx.android.ink.model.Brush(
                            tool = Tool.PEN,
                            color = "#123456",
                            baseWidth = 4.0f,
                            minWidthFactor = 0.7f,
                            maxWidthFactor = 1.3f,
                            smoothingLevel = 0.6f,
                            endTaperStrength = 0.5f,
                        ),
                    highlighterBrush =
                        com.onyx.android.ink.model.Brush(
                            tool = Tool.HIGHLIGHTER,
                            color = "#654321",
                            baseWidth = 8.0f,
                            minWidthFactor = 0.75f,
                            maxWidthFactor = 1.25f,
                            smoothingLevel = 0.4f,
                            endTaperStrength = 0.3f,
                        ),
                    lastNonEraserTool = Tool.HIGHLIGHTER,
                )

            repository.saveSettings(settings)

            coVerify {
                dao.saveSettings(
                    match { entity ->
                        entity.settingsId == "default" &&
                            entity.selectedTool == "HIGHLIGHTER" &&
                            entity.penColor == "#123456" &&
                            entity.penBaseWidth == 4.0f &&
                            entity.highlighterColor == "#654321" &&
                            entity.highlighterBaseWidth == 8.0f &&
                            entity.lastNonEraserTool == "HIGHLIGHTER"
                    },
                )
            }
        }

    @Test
    fun `DEFAULT_SETTINGS has expected values`() {
        val defaults = EditorSettingsRepository.DEFAULT_SETTINGS

        assertEquals(Tool.PEN, defaults.penBrush.tool)
        assertEquals(Tool.PEN, defaults.selectedTool)
        assertEquals("#000000", defaults.penBrush.color)
        assertEquals(2.0f, defaults.penBrush.baseWidth)
        assertEquals(Tool.HIGHLIGHTER, defaults.highlighterBrush.tool)
        assertEquals("#B31E88E5", defaults.highlighterBrush.color)
        assertEquals(Tool.PEN, defaults.lastNonEraserTool)
    }

    @Test
    fun `getSettings handles invalid tool values gracefully`() =
        runTest {
            val entity =
                EditorSettingsEntity(
                    settingsId = "default",
                    selectedTool = "INVALID_TOOL",
                    penTool = "INVALID_PEN",
                    penColor = "#000000",
                    penBaseWidth = 2.0f,
                    penMinWidthFactor = 0.85f,
                    penMaxWidthFactor = 1.15f,
                    penSmoothingLevel = 0.35f,
                    penEndTaperStrength = 0.35f,
                    highlighterTool = "INVALID_HIGHLIGHTER",
                    highlighterColor = "#B31E88E5",
                    highlighterBaseWidth = 6.5f,
                    highlighterMinWidthFactor = 0.85f,
                    highlighterMaxWidthFactor = 1.15f,
                    highlighterSmoothingLevel = 0.35f,
                    highlighterEndTaperStrength = 0.35f,
                    lastNonEraserTool = "INVALID_TOOL",
                    updatedAt = 123L,
                )
            val dao =
                mockk<com.onyx.android.data.dao.EditorSettingsDao> {
                    every { getSettings() } returns flowOf(entity)
                }
            val repository = EditorSettingsRepository(dao)

            val result = repository.getSettings().first()

            assertNotNull(result)
            assertEquals(Tool.PEN, result!!.selectedTool)
            assertEquals(Tool.PEN, result.penBrush.tool)
            assertEquals(Tool.HIGHLIGHTER, result.highlighterBrush.tool)
            assertEquals(Tool.PEN, result.lastNonEraserTool)
        }
}
