package com.onyx.android.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorReadOnlyModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readOnlyMode_keepsPanGestureEnabled_whileEditingCallbacksStayInactive() {
        var transformCalls = 0
        var strokeFinishedCalls = 0
        var strokeErasedCalls = 0

        composeRule.setContent {
            MaterialTheme {
                NoteEditorScaffold(
                    topBarState = defaultTopBarState(),
                    toolbarState = defaultToolbarState(),
                    contentState =
                        defaultContentState(
                            isReadOnly = true,
                            onTransformGesture = { _, _, _, _, _ -> transformCalls += 1 },
                            onStrokeFinished = { strokeFinishedCalls += 1 },
                            onStrokeErased = { strokeErasedCalls += 1 },
                        ),
                    transformState = rememberTransformableState { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Editor viewport").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 60f))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(transformCalls > 0)
            assertEquals(0, strokeFinishedCalls)
            assertEquals(0, strokeErasedCalls)
        }
    }
}

private fun defaultTopBarState(): NoteEditorTopBarState =
    NoteEditorTopBarState(
        noteTitle = "Physics Notes",
        totalPages = 1,
        currentPageIndex = 0,
        isReadOnly = true,
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

private fun defaultToolbarState(): NoteEditorToolbarState =
    NoteEditorToolbarState(
        brush = Brush(tool = Tool.PEN),
        lastNonEraserTool = Tool.PEN,
        isStylusButtonEraserActive = false,
        onBrushChange = {},
    )

private fun defaultContentState(
    isReadOnly: Boolean,
    onTransformGesture: (Float, Float, Float, Float, Float) -> Unit,
    onStrokeFinished: () -> Unit,
    onStrokeErased: () -> Unit,
): NoteEditorContentState =
    NoteEditorContentState(
        isPdfPage = false,
        isReadOnly = isReadOnly,
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
        onStrokeFinished = { onStrokeFinished() },
        onStrokeErased = { onStrokeErased() },
        onStylusButtonEraserActiveChanged = {},
        onTransformGesture = onTransformGesture,
        onPanGestureEnd = { _, _ -> },
        onViewportSizeChanged = { _: IntSize -> },
    )
