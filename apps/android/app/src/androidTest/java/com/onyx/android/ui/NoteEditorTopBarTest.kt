package com.onyx.android.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
class NoteEditorTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithContentDescription("Editor viewport").assertIsDisplayed()
        composeRule.onNodeWithText("2/5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page 2 of 5").assertIsDisplayed()
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

    @Test
    fun titleEdit_commitsUpdatedTitle() {
        var committedTitle = ""
        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    noteTitle = "Physics Notes",
                    onUpdateTitle = { committedTitle = it },
                ),
        )

        composeRule.onNodeWithContentDescription("Note title").performClick()
        composeRule.onNodeWithTag(TITLE_INPUT_TEST_TAG).performTextClearance()
        composeRule.onNodeWithTag(TITLE_INPUT_TEST_TAG).performTextInput("Linear Algebra")
        composeRule.onNodeWithTag(TITLE_INPUT_TEST_TAG).performImeAction()

        composeRule.runOnIdle {
            assertEquals("Linear Algebra", committedTitle)
        }
    }

    @Test
    fun pageCounter_reflectsCurrentAndTotalPages() {
        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    totalPages = 12,
                    currentPageIndex = 2,
                ),
        )

        composeRule.onNodeWithText("3/12").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page 3 of 12").assertIsDisplayed()
    }

    @Test
    fun overflowMenu_exposesWorkingActions_andRemovesLegacyPlaceholder() {
        var modeToggleClicks = 0
        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    onToggleReadOnly = { modeToggleClicks += 1 },
                ),
        )

        composeRule.onNodeWithContentDescription(MORE_ACTIONS).performClick()
        composeRule.onNodeWithText("Rename note").assertIsDisplayed()
        composeRule.onNodeWithText(LEGACY_OVERFLOW_COPY).assertDoesNotExist()
        composeRule.onNodeWithText("Rename note").performClick()
        composeRule.onNodeWithTag(TITLE_INPUT_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(MORE_ACTIONS).performClick()
        composeRule.onNodeWithText("Switch to view mode").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, modeToggleClicks)
        }
    }

    @Test
    fun topBar_disablesUnavailableActions() {
        setEditorScaffold(
            topBarState =
                defaultTopBarState().copy(
                    isReadOnly = true,
                    canNavigatePrevious = false,
                    canNavigateNext = false,
                    canUndo = false,
                    canRedo = false,
                ),
        )

        composeRule.onNodeWithContentDescription(PREVIOUS_PAGE).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(NEXT_PAGE).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(NEW_PAGE).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(UNDO).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(REDO).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Note title").assertIsNotEnabled()
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
private const val TITLE_INPUT_TEST_TAG = "note-title-input"
private const val MORE_ACTIONS = "More actions"
private const val LEGACY_OVERFLOW_COPY = "Grid, search, and inbox are coming soon"

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
        onUpdateTitle = {},
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
        isStylusButtonEraserActive = false,
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
