@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.pdf.PdfRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun PdfPageContent(contentState: NoteEditorContentState) {
    val selectionState = rememberPdfSelectionState(contentState)
    PdfPageLayers(contentState, selectionState)
}

@Composable
private fun rememberPdfSelectionState(contentState: NoteEditorContentState): PdfSelectionState {
    var textSelection by remember { mutableStateOf<TextSelection?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(contentState.currentPage?.pageId) {
        textSelection = null
    }

    return PdfSelectionState(
        renderer = contentState.pdfRenderer,
        currentPage = contentState.currentPage,
        viewTransform = contentState.viewTransform,
        selection = textSelection,
        onSelectionChange = { textSelection = it },
        coroutineScope = coroutineScope,
    )
}

@Composable
private fun PdfPageLayers(
    contentState: NoteEditorContentState,
    selectionState: PdfSelectionState,
) {
    val bitmap = contentState.pdfBitmap
    val viewTransform = contentState.viewTransform
    val pageWidth = contentState.pageWidth
    val pageHeight = contentState.pageHeight

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pdfSelectionInput(selectionState),
    ) {
        if (bitmap != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val destinationWidthPx =
                    (pageWidth * viewTransform.zoom).roundToInt().coerceAtLeast(1)
                val destinationHeightPx =
                    (pageHeight * viewTransform.zoom).roundToInt().coerceAtLeast(1)
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset =
                        IntOffset(
                            viewTransform.panX.roundToInt(),
                            viewTransform.panY.roundToInt(),
                        ),
                    dstSize = IntSize(destinationWidthPx, destinationHeightPx),
                    filterQuality = FilterQuality.High,
                )
            }
        }
        PdfSelectionOverlay(
            selection = selectionState.selection,
            viewTransform = viewTransform,
        )
        val inkCanvasState =
            InkCanvasState(
                strokes = contentState.strokes,
                viewTransform = contentState.viewTransform,
                brush = contentState.brush,
                pageWidth = contentState.pageWidth,
                pageHeight = contentState.pageHeight,
            )
        val inkCanvasCallbacks =
            InkCanvasCallbacks(
                onStrokeFinished = contentState.onStrokeFinished,
                onStrokeErased = contentState.onStrokeErased,
                onTransformGesture = contentState.onTransformGesture,
                onPanGestureEnd = contentState.onPanGestureEnd,
                onStylusButtonEraserActiveChanged = contentState.onStylusButtonEraserActiveChanged,
            )
        InkCanvas(
            state = inkCanvasState,
            callbacks = inkCanvasCallbacks,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private data class PdfSelectionState(
    val renderer: PdfRenderer?,
    val currentPage: PageEntity?,
    val viewTransform: ViewTransform,
    val selection: TextSelection?,
    val onSelectionChange: (TextSelection?) -> Unit,
    val coroutineScope: CoroutineScope,
)

@Composable
private fun Modifier.pdfSelectionInput(state: PdfSelectionState): Modifier =
    pointerInput(state.currentPage?.pageId, state.viewTransform) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                handlePdfDragStart(state, offset)
            },
            onDrag = { change, _ ->
                handlePdfDragMove(state, change)
            },
            onDragEnd = {
                handlePdfDragEnd(state)
            },
        )
    }

private fun handlePdfDragStart(
    state: PdfSelectionState,
    offset: androidx.compose.ui.geometry.Offset,
) {
    val renderer = state.renderer ?: return
    val pageIndex = state.currentPage?.pdfPageNo ?: return
    val (pageX, pageY) = state.viewTransform.screenToPage(offset.x, offset.y)
    state.coroutineScope.launch {
        val structuredText =
            withContext(Dispatchers.Default) {
                renderer.extractTextStructure(pageIndex)
            }
        val startChar = renderer.findCharAtPagePoint(structuredText, pageX, pageY)
        if (startChar != null) {
            val quads = renderer.getSelectionQuads(structuredText, startChar, startChar)
            state.onSelectionChange(
                TextSelection(
                    structuredText = structuredText,
                    startChar = startChar,
                    endChar = startChar,
                    quads = quads,
                ),
            )
        } else {
            state.onSelectionChange(null)
        }
    }
}

private fun handlePdfDragMove(
    state: PdfSelectionState,
    change: PointerInputChange,
) {
    val renderer = state.renderer
    val selection = state.selection
    if (renderer != null && selection != null) {
        val (pageX, pageY) =
            state.viewTransform.screenToPage(
                change.position.x,
                change.position.y,
            )
        val endChar = renderer.findCharAtPagePoint(selection.structuredText, pageX, pageY)
        if (endChar != null) {
            val quads =
                renderer.getSelectionQuads(
                    selection.structuredText,
                    selection.startChar,
                    endChar,
                )
            state.onSelectionChange(
                selection.copy(
                    endChar = endChar,
                    quads = quads,
                ),
            )
            change.consume()
        }
    }
}

private fun handlePdfDragEnd(state: PdfSelectionState) {
    val renderer = state.renderer ?: return
    val selection = state.selection ?: return
    val selectedText =
        renderer.extractSelectedText(
            selection.structuredText,
            selection.startChar,
            selection.endChar,
        )
    android.util.Log.d("PdfSelection", "Selected text: $selectedText")
}

@Composable
private fun PdfSelectionOverlay(
    selection: TextSelection?,
    viewTransform: ViewTransform,
) {
    if (selection == null) return
    val highlightColor = Color(PDF_SELECTION_HIGHLIGHT_COLOR)
    Canvas(modifier = Modifier.fillMaxSize()) {
        selection.quads.forEach { quad ->
            val (ulX, ulY) = viewTransform.pageToScreen(quad.ul_x, quad.ul_y)
            val (urX, urY) = viewTransform.pageToScreen(quad.ur_x, quad.ur_y)
            val (lrX, lrY) = viewTransform.pageToScreen(quad.lr_x, quad.lr_y)
            val (llX, llY) = viewTransform.pageToScreen(quad.ll_x, quad.ll_y)
            val path =
                Path().apply {
                    moveTo(ulX, ulY)
                    lineTo(urX, urY)
                    lineTo(lrX, lrY)
                    lineTo(llX, llY)
                    close()
                }
            drawPath(path, color = highlightColor)
        }
    }
}
