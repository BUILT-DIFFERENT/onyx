package com.onyx.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class NoteEditorTopBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unifiedTopBar_rendersCoreControls_title_andPageIndicator() {
        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    noteTitle = "Physics Notes",
                    totalPages = 5,
                    currentPageIndex = 1,
                    canNavigatePrevious = true,
                    canNavigateNext = true,
                    canUndo = true,
                    canRedo = true,
                ),
        )

        composeRule.onNodeWithText("Physics Notes").assertIsDisplayed()
        composeRule.onNodeWithText("2/5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(BACK).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(PREVIOUS_PAGE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(NEXT_PAGE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(NEW_PAGE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(UNDO).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(REDO).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(VIEW_MODE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Highlighter").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(ERASER).assertIsDisplayed()
    }

    @Test
    fun unifiedTopBar_invokesExpectedHandlers() {
        var backClicks = 0
        var previousClicks = 0
        var nextClicks = 0
        var newPageClicks = 0
        var undoClicks = 0
        var redoClicks = 0
        var modeToggleClicks = 0

        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    canNavigatePrevious = true,
                    canNavigateNext = true,
                    canUndo = true,
                    canRedo = true,
                    onNavigateBack = { backClicks += 1 },
                    onNavigatePrevious = { previousClicks += 1 },
                    onNavigateNext = { nextClicks += 1 },
                    onCreatePage = { newPageClicks += 1 },
                    onUndo = { undoClicks += 1 },
                    onRedo = { redoClicks += 1 },
                    onToggleReadOnly = { modeToggleClicks += 1 },
                ),
        )

        composeRule.onNodeWithContentDescription(BACK).performClick()
        composeRule.onNodeWithContentDescription(PREVIOUS_PAGE).performClick()
        composeRule.onNodeWithContentDescription(NEXT_PAGE).performClick()
        composeRule.onNodeWithContentDescription(NEW_PAGE).performClick()
        composeRule.onNodeWithContentDescription(UNDO).performClick()
        composeRule.onNodeWithContentDescription(REDO).performClick()
        composeRule.onNodeWithContentDescription(VIEW_MODE).performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertEquals(1, previousClicks)
            assertEquals(1, nextClicks)
            assertEquals(1, newPageClicks)
            assertEquals(1, undoClicks)
            assertEquals(1, redoClicks)
            assertEquals(1, modeToggleClicks)
        }
    }

    private fun setEditorScaffold(topBarState: NoteEditorTopBarState) {
        composeRule.setContent {
            MaterialTheme {
                NoteEditorScaffold(
                    topBarState = topBarState,
                    toolbarState = defaultToolbarState(),
                    contentState = defaultContentState(),
                    transformState = rememberTransformableState { _, _, _ -> },
                )
            }
        }
    }
}

private const val BACK = "Back"
private const val PREVIOUS_PAGE = "Previous page"
private const val NEXT_PAGE = "Next page"
private const val NEW_PAGE = "New page"
private const val UNDO = "Undo"
private const val REDO = "Redo"
private const val VIEW_MODE = "View mode"
private const val ERASER = "Eraser"

private fun defaultTopBarState(): NoteEditorTopBarState =
    NoteEditorTopBarState(
        noteTitle = "",
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
        onUndo = {},
        onRedo = {},
        onToggleReadOnly = {},
    )

private fun defaultToolbarState(
    brush: Brush = Brush(),
    onBrushChange: (Brush) -> Unit = {},
): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = brush,
        lastNonEraserTool = Tool.PEN,
        onBrushChange = onBrushChange,
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
        strokes = emptyList(),
        brush = Brush(),
        onStrokeFinished = {},
        onStrokeErased = {},
        onTransformGesture = { _, _, _, _, _ -> },
    )
