@file:Suppress("FunctionName", "TooManyFunctions")

package com.onyx.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.android.R
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.pdf.PdfTextExtractor
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.buildPdfTextSelection
import com.onyx.android.pdf.findPdfTextCharIndexAtPagePoint
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
    val appContext = LocalContext.current.applicationContext
    var textSelection by remember { mutableStateOf<TextSelection?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = remember { appContext.getSystemService(ClipboardManager::class.java) }

    LaunchedEffect(contentState.currentPage?.pageId) {
        textSelection = null
    }

    return PdfSelectionState(
        textExtractor = contentState.pdfRenderer,
        currentPage = contentState.currentPage,
        viewTransform = contentState.viewTransform,
        selection = textSelection,
        onSelectionChange = { textSelection = it },
        onCopySelection = {
            val text = textSelection?.text?.takeIf { value -> value.isNotBlank() }
            if (text != null) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("PDF Selection", text))
            }
        },
        coroutineScope = coroutineScope,
    )
}

@Composable
@Suppress("LongMethod")
private fun PdfPageLayers(
    contentState: NoteEditorContentState,
    selectionState: PdfSelectionState,
) {
    val bitmap = contentState.pdfBitmap
    val viewTransform = contentState.viewTransform
    val pageWidth = contentState.pageWidth
    val pageHeight = contentState.pageHeight
    val tileEntries = contentState.pdfTiles
    val tileScaleBucket = contentState.pdfRenderScaleBucket
    val tileSizePx = contentState.pdfTileSizePx

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pdfSelectionInput(selectionState),
    ) {
        if (bitmap != null || tileEntries.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
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
                if (tileEntries.isNotEmpty()) {
                    drawPdfTiles(
                        entries = tileEntries.entries,
                        viewTransform = viewTransform,
                        tileSizePx = tileSizePx,
                        activeScaleBucket = tileScaleBucket,
                    )
                }
            }
        }
        PdfSelectionOverlay(
            selection = selectionState.selection,
            viewTransform = viewTransform,
        )
        PdfSelectionCopyAction(selectionState)
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
    val textExtractor: PdfTextExtractor?,
    val currentPage: PageEntity?,
    val viewTransform: ViewTransform,
    val selection: TextSelection?,
    val onSelectionChange: (TextSelection?) -> Unit,
    val onCopySelection: () -> Unit,
    val coroutineScope: CoroutineScope,
)

@Composable
private fun Modifier.pdfSelectionInput(state: PdfSelectionState): Modifier =
    pointerInput(state.currentPage?.pageId, state.viewTransform, state.textExtractor) {
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
    val extractor = state.textExtractor ?: return
    val pageIndex = state.currentPage?.pdfPageNo ?: return
    val pageX = state.viewTransform.screenToPageX(offset.x)
    val pageY = state.viewTransform.screenToPageY(offset.y)
    state.coroutineScope.launch {
        val pageCharacters =
            withContext(Dispatchers.Default) {
                extractor.getCharacters(pageIndex)
            }
        val startCharIndex =
            findPdfTextCharIndexAtPagePoint(
                characters = pageCharacters,
                pageX = pageX,
                pageY = pageY,
            )
        if (startCharIndex != null) {
            val selection =
                buildPdfTextSelection(
                    characters = pageCharacters,
                    startIndex = startCharIndex,
                    endIndex = startCharIndex,
                )
            state.onSelectionChange(
                TextSelection(
                    pageIndex = pageIndex,
                    pageCharacters = pageCharacters,
                    startCharIndex = startCharIndex,
                    endCharIndex = startCharIndex,
                    selection = selection,
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
    val selection = state.selection
    if (selection != null) {
        val pageX = state.viewTransform.screenToPageX(change.position.x)
        val pageY = state.viewTransform.screenToPageY(change.position.y)
        val endCharIndex =
            findPdfTextCharIndexAtPagePoint(
                characters = selection.pageCharacters,
                pageX = pageX,
                pageY = pageY,
            )
        if (endCharIndex != null) {
            val updatedSelection =
                buildPdfTextSelection(
                    characters = selection.pageCharacters,
                    startIndex = selection.startCharIndex,
                    endIndex = endCharIndex,
                )
            state.onSelectionChange(
                selection.copy(
                    endCharIndex = endCharIndex,
                    selection = updatedSelection,
                ),
            )
            change.consume()
        }
    }
}

private fun handlePdfDragEnd(state: PdfSelectionState) {
    val selection = state.selection ?: return
    if (selection.selection.text.isBlank()) {
        state.onSelectionChange(null)
    }
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
            val p1X = viewTransform.pageToScreenX(quad.p1.x)
            val p1Y = viewTransform.pageToScreenY(quad.p1.y)
            val p2X = viewTransform.pageToScreenX(quad.p2.x)
            val p2Y = viewTransform.pageToScreenY(quad.p2.y)
            val p3X = viewTransform.pageToScreenX(quad.p3.x)
            val p3Y = viewTransform.pageToScreenY(quad.p3.y)
            val p4X = viewTransform.pageToScreenX(quad.p4.x)
            val p4Y = viewTransform.pageToScreenY(quad.p4.y)
            val path =
                Path().apply {
                    moveTo(p1X, p1Y)
                    lineTo(p2X, p2Y)
                    lineTo(p3X, p3Y)
                    lineTo(p4X, p4Y)
                    close()
                }
            drawPath(path, color = highlightColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfTiles(
    entries: Set<Map.Entry<PdfTileKey, android.graphics.Bitmap>>,
    viewTransform: ViewTransform,
    tileSizePx: Int,
    activeScaleBucket: Float?,
) {
    val (staleEntries, currentEntries) =
        entries.partition { entry ->
            activeScaleBucket == null || entry.key.scaleBucket != activeScaleBucket
        }
    staleEntries.forEach { entry ->
        drawPdfTile(entry.key, entry.value, viewTransform, tileSizePx)
    }
    currentEntries.forEach { entry ->
        drawPdfTile(entry.key, entry.value, viewTransform, tileSizePx)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfTile(
    key: PdfTileKey,
    bitmap: android.graphics.Bitmap,
    viewTransform: ViewTransform,
    tileSizePx: Int,
) {
    if (bitmap.isRecycled) {
        return
    }
    val scaleBucket = key.scaleBucket
    if (scaleBucket <= 0f) {
        return
    }
    val tilePageSize = tileSizePx / scaleBucket
    val tilePageLeft = key.tileX * tilePageSize
    val tilePageTop = key.tileY * tilePageSize
    val tilePageWidth = bitmap.width / scaleBucket
    val tilePageHeight = bitmap.height / scaleBucket
    val tileScreenLeft = viewTransform.pageToScreenX(tilePageLeft)
    val tileScreenTop = viewTransform.pageToScreenY(tilePageTop)
    val tileScreenWidth = (tilePageWidth * viewTransform.zoom).roundToInt().coerceAtLeast(1)
    val tileScreenHeight = (tilePageHeight * viewTransform.zoom).roundToInt().coerceAtLeast(1)
    drawImage(
        image = bitmap.asImageBitmap(),
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset =
            IntOffset(
                x = tileScreenLeft.roundToInt(),
                y = tileScreenTop.roundToInt(),
            ),
        dstSize = IntSize(width = tileScreenWidth, height = tileScreenHeight),
        filterQuality = FilterQuality.High,
    )
}

@Composable
private fun PdfSelectionCopyAction(state: PdfSelectionState) {
    if (state.selection == null) {
        return
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Button(
            modifier = Modifier.padding(12.dp),
            onClick = state.onCopySelection,
        ) {
            Text(stringResource(R.string.pdf_selection_copy))
        }
    }
}
