package com.onyx.android.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toolbar_usesBalancedCompactHeight() {
        assertEquals(48, TOOLBAR_ROW_HEIGHT_DP)
    }

    @Test
    fun eraserAction_isVisible_andTogglesToolThroughCallback() {
        var updatedBrush: Brush? = null

        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.PEN),
                    lastNonEraserTool = Tool.HIGHLIGHTER,
                    isStylusButtonEraserActive = false,
                    onBrushChange = { brush -> updatedBrush = brush },
                ),
        )

        composeRule.onNodeWithContentDescription(ERASER).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.ERASER, updatedBrush?.tool)
        }

        updatedBrush = null

        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.ERASER),
                    lastNonEraserTool = Tool.HIGHLIGHTER,
                    isStylusButtonEraserActive = false,
                    onBrushChange = { brush -> updatedBrush = brush },
                ),
        )

        composeRule.onNodeWithContentDescription(ERASER).performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.HIGHLIGHTER, updatedBrush?.tool)
        }
    }

    @Test
    fun toolButtons_switchToPenAndHighlighter() {
        var updatedBrush: Brush? = null
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.ERASER),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    onBrushChange = { brush -> updatedBrush = brush },
                ),
        )

        composeRule.onNodeWithContentDescription("Pen").performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.PEN, updatedBrush?.tool)
        }

        composeRule.onNodeWithContentDescription("Highlighter").performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.HIGHLIGHTER, updatedBrush?.tool)
        }
    }

    @Test
    fun longPress_penButton_opensPenSettingsPanel() {
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.PEN),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    onBrushChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription("Pen").performTouchInput {
            down(center)
            advanceEventTime(650)
            up()
        }
        composeRule.onNodeWithText("Pen settings").assertIsDisplayed()
        composeRule.onNodeWithText("Stabilization").assertIsDisplayed()
    }

    @Test
    fun longPress_paletteSwatch_opensColorPickerDialog() {
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.PEN, color = "#111111"),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    onBrushChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription("Brush color black").performTouchInput {
            down(center)
            advanceEventTime(650)
            up()
        }
        composeRule.onNodeWithText("Custom brush color").assertIsDisplayed()
        composeRule.onNodeWithText("Hex color").assertIsDisplayed()
    }

    @Test
    fun colorTap_whenEraserSelected_switchesToLastNonEraserTool_andAppliesColor() {
        var updatedBrush: Brush? = null
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.ERASER, color = "#111111"),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    onBrushChange = { brush -> updatedBrush = brush },
                ),
        )

        composeRule.onNodeWithContentDescription("Brush color blue").performClick()

        composeRule.runOnIdle {
            assertEquals(Tool.PEN, updatedBrush?.tool)
            assertEquals("#1E88E5", updatedBrush?.color)
        }
    }

    private fun setEditorScaffold(toolbarState: NoteEditorToolbarState) {
        composeRule.setContent {
            MaterialTheme {
                NoteEditorScaffold(
                    topBarState = defaultTopBarState(),
                    toolbarState = toolbarState,
                    contentState = defaultContentState(),
                    transformState = rememberTransformableState { _, _, _ -> },
                )
            }
        }
    }
}

private const val ERASER = "Eraser"

private fun defaultTopBarState(): NoteEditorTopBarState =
    NoteEditorTopBarState(
        noteTitle = "Physics Notes",
        totalPages = 1,
        currentPageIndex = 0,
        isReadOnly = false,
        canNavigatePrevious = false,
        canNavigateNext = false,
        canUndo = false,
        canRedo = false,
        onNavigateBack = {},
        onNavigatePrevious = {},
        onNavigateNext = {},
        onCreatePage = {},
        onUpdateTitle = {},
        onUndo = {},
        onRedo = {},
        onToggleReadOnly = {},
    )

private fun defaultContentState(): NoteEditorContentState =
    NoteEditorContentState(
        isPdfPage = false,
        isReadOnly = false,
        pdfBitmap = null,
        pdfRenderer = null,
        currentPage = null,
        viewTransform = ViewTransform.DEFAULT,
        pageWidthDp = 0.dp,
        pageHeightDp = 0.dp,
        pageWidth = 0f,
        pageHeight = 0f,
        strokes = emptyList(),
        brush = Brush(),
        isStylusButtonEraserActive = false,
        onStrokeFinished = {},
        onStrokeErased = {},
        onStylusButtonEraserActiveChanged = {},
        onTransformGesture = { _, _, _, _, _ -> },
        onPanGestureEnd = { _, _ -> },
        onViewportSizeChanged = { _: IntSize -> },
    )
