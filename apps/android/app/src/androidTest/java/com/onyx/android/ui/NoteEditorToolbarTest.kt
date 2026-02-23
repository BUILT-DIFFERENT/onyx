package com.onyx.android.ui

import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.onyx.android.objects.model.InsertAction
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
        var latestBrush: Brush? = null

        composeRule.setContent {
            MaterialTheme {
                var currentBrush by remember { mutableStateOf(Brush(tool = Tool.PEN)) }
                var lastNonEraserTool by remember { mutableStateOf(Tool.HIGHLIGHTER) }
                NoteEditorScaffold(
                    topBarState = defaultTopBarState(),
                    toolbarState =
                        NoteEditorToolbarState(
                            brush = currentBrush,
                            lastNonEraserTool = lastNonEraserTool,
                            isStylusButtonEraserActive = false,
                            templateState = PageTemplateState.BLANK,
                            onBrushChange = { brush ->
                                latestBrush = brush
                                currentBrush = brush
                                if (brush.tool != Tool.ERASER) {
                                    lastNonEraserTool = brush.tool
                                }
                            },
                            onTemplateChange = {},
                        ),
                    contentState = defaultContentState(),
                    transformState = rememberTransformableState { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription(ERASER).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.ERASER, latestBrush?.tool)
        }

        composeRule.onNodeWithContentDescription(ERASER).performClick()
        composeRule.runOnIdle {
            assertEquals(Tool.HIGHLIGHTER, latestBrush?.tool)
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
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = { brush -> updatedBrush = brush },
                    onTemplateChange = {},
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
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = {},
                    onTemplateChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription("Pen").performTouchInput {
            down(center)
            advanceEventTime(650)
            up()
        }
        composeRule.onNodeWithText("Pen settings").assertIsDisplayed()
        composeRule.onNodeWithText("Smoothing").assertIsDisplayed()
        composeRule.onNodeWithText("End taper").assertIsDisplayed()
    }

    @Test
    fun longPress_paletteSwatch_opensColorPickerDialog() {
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.PEN, color = "#111111"),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = {},
                    onTemplateChange = {},
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
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = { brush -> updatedBrush = brush },
                    onTemplateChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription("Brush color blue").performClick()

        composeRule.runOnIdle {
            assertEquals(Tool.PEN, updatedBrush?.tool)
            assertEquals("#1E88E5", updatedBrush?.color)
        }
    }

    @Test
    fun longPress_eraserButton_showsStrokeOnlyEraserSettings() {
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.ERASER),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = {},
                    onTemplateChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription(ERASER).performTouchInput {
            down(center)
            advanceEventTime(650)
            up()
        }
        composeRule.onNodeWithText("Eraser options").assertIsDisplayed()
        composeRule.onNodeWithText("Stroke eraser").assertIsDisplayed()
        composeRule.onAllNodesWithText("Brush size").assertCountEquals(0)
    }

    @Test
    fun insertMenu_exposesShapeActions_andDeferredPlaceholders() {
        var selectedInsertAction = InsertAction.NONE
        setEditorScaffold(
            toolbarState =
                NoteEditorToolbarState(
                    brush = Brush(tool = Tool.PEN),
                    lastNonEraserTool = Tool.PEN,
                    isStylusButtonEraserActive = false,
                    activeInsertAction = InsertAction.NONE,
                    templateState = PageTemplateState.BLANK,
                    onBrushChange = {},
                    onInsertActionSelected = { action -> selectedInsertAction = action },
                    onTemplateChange = {},
                ),
        )

        composeRule.onNodeWithContentDescription("Insert menu").performClick()
        composeRule.onNodeWithText("Line").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(InsertAction.LINE, selectedInsertAction)
        }

        composeRule.onNodeWithContentDescription("Insert menu").performClick()
        composeRule.onNodeWithText("Rectangle").assertIsDisplayed()
        composeRule.onNodeWithText("Ellipse").assertIsDisplayed()
        composeRule.onNodeWithText("Image (coming next wave)").assertIsDisplayed()
        composeRule.onNodeWithText("Text (coming next wave)").assertIsDisplayed()
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
        isPdfDocument = false,
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
        onOpenOutline = {},
        isRecognitionOverlayEnabled = false,
        onToggleRecognitionOverlay = {},
    )

private fun defaultContentState(): NoteEditorContentState =
    NoteEditorContentState(
        isPdfPage = false,
        isReadOnly = false,
        pdfTiles = emptyMap(),
        pdfRenderScaleBucket = null,
        pdfPreviousScaleBucket = null,
        pdfTileSizePx = 512,
        pdfCrossfadeProgress = 1f,
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
        interactionMode = InteractionMode.DRAW,
        thumbnails = emptyList(),
        currentPageIndex = 0,
        templateState = PageTemplateState.BLANK,
        isRecognitionOverlayEnabled = false,
        recognitionText = null,
        convertedTextBlocks = emptyList(),
        onConvertedTextBlockSelected = {},
        onStrokeFinished = {},
        onStrokeErased = {},
        onStylusButtonEraserActiveChanged = {},
        onTransformGesture = { _, _, _, _, _ -> },
        onPanGestureEnd = { _, _ -> },
        onViewportSizeChanged = { _: IntSize -> },
        onPageSelected = {},
        onTemplateChange = {},
    )
