@file:Suppress("FunctionName", "TooManyFunctions", "LongMethod", "LongParameterList")

package com.onyx.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.android.R
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.pdf.PdfTextExtractor
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.ValidatingTile
import com.onyx.android.pdf.buildPdfTextSelection
import com.onyx.android.pdf.findNearestPdfTextCharIndex
import com.onyx.android.pdf.findPdfTextCharIndexAtPagePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush as ComposeBrush

private const val PDF_NOTE_PAPER_SHADOW = 0x15000000
private const val PDF_NOTE_PAPER_STROKE = 0xFFCBCED6
private const val PDF_EDGE_GLOW_COLOR = 0x40000000
private const val NOTE_EDITOR_PDF_TAG = "NoteEditorPdf"
private const val PDF_TILE_CROSSFADE_DURATION_MS = 250
private const val PDF_SELECTION_HANDLE_RADIUS_DP = 10
private const val PDF_SELECTION_HANDLE_STROKE_DP = 2
private const val PDF_SELECTION_HANDLE_OFFSET_DP = 2
private const val PDF_SELECTION_HANDLE_INNER_RADIUS_RATIO = 0.35f
private const val PDF_SELECTION_HANDLE_COLOR = 0xFF136CC5

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

    val selectionEnabled = contentState.interactionMode == InteractionMode.TEXT_SELECTION

    LaunchedEffect(contentState.currentPage?.pageId, selectionEnabled) {
        textSelection = null
    }

    return PdfSelectionState(
        textExtractor = contentState.pdfRenderer,
        currentPage = contentState.currentPage,
        viewTransform = contentState.viewTransform,
        isSelectionEnabled = selectionEnabled,
        selection = textSelection,
        onSelectionChange = { textSelection = it },
        onCopySelection = {
            val text = textSelection?.text?.takeIf { value -> value.isNotBlank() }
            if (text != null) {
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("PDF Selection", text))
                Toast
                    .makeText(
                        appContext,
                        appContext.getString(R.string.pdf_selection_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
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
    val tilePreviousScaleBucket = contentState.pdfPreviousScaleBucket
    val tileSizePx = contentState.pdfTileSizePx
    val crossfadeTargetProgress = contentState.pdfCrossfadeProgress
    val crossfadeProgress by
        animateFloatAsState(
            targetValue = crossfadeTargetProgress,
            animationSpec = tween(durationMillis = PDF_TILE_CROSSFADE_DURATION_MS),
            label = "pdf-tile-bucket-crossfade",
        )

    // Colors for page boundary indicators
    val notePaperShadow = Color(PDF_NOTE_PAPER_SHADOW)
    val notePaperStroke = Color(PDF_NOTE_PAPER_STROKE)
    val edgeGlowColor = Color(PDF_EDGE_GLOW_COLOR)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pdfSelectionInput(selectionState),
    ) {
        if (bitmap != null || tileEntries.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (pageWidth <= 0f || pageHeight <= 0f) {
                    return@Canvas
                }

                val left = viewTransform.panX
                val top = viewTransform.panY
                val pageWidthPx = (pageWidth * viewTransform.zoom).roundToInt().toFloat()
                val pageHeightPx = (pageHeight * viewTransform.zoom).roundToInt().toFloat()
                val shadowSpread = PAGE_SHADOW_SPREAD_DP.dp.toPx()
                val borderWidth = PAGE_BORDER_WIDTH_DP.dp.toPx()
                val edgeGlowWidth = EDGE_GLOW_WIDTH_DP.dp.toPx()

                // Draw page shadow (subtle drop shadow on right and bottom edges)
                drawPdfPageShadow(
                    left = left,
                    top = top,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    shadowSpread = shadowSpread,
                    shadowColor = notePaperShadow,
                )

                if (bitmap != null) {
                    if (bitmap.isRecycled) {
                        Log.w(NOTE_EDITOR_PDF_TAG, "Skipped drawing base PDF bitmap because it is recycled")
                    } else {
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
                if (tileEntries.isNotEmpty()) {
                    drawPdfTiles(
                        entries = tileEntries.entries,
                        viewTransform = viewTransform,
                        tileSizePx = tileSizePx,
                        previousScaleBucket = tilePreviousScaleBucket,
                        crossfadeProgress = crossfadeProgress,
                    )
                }

                // Draw page border
                drawRect(
                    color = notePaperStroke,
                    topLeft = Offset(left, top),
                    size = Size(pageWidthPx, pageHeightPx),
                    style = Stroke(width = borderWidth),
                )

                // Draw edge glow indicators when at document limits
                drawPdfEdgeGlowIndicators(
                    left = left,
                    top = top,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    viewTransform = viewTransform,
                    edgeGlowWidth = edgeGlowWidth,
                    viewportWidth = size.width,
                    viewportHeight = size.height,
                    edgeGlowColor = edgeGlowColor,
                )
            }
        }
        PdfSelectionOverlay(
            selection = selectionState.selection,
            viewTransform = viewTransform,
        )
        PdfSelectionHandles(state = selectionState)
        PdfSelectionCopyAction(selectionState)
        val inkCanvasState =
            InkCanvasState(
                strokes = contentState.strokes,
                viewTransform = contentState.viewTransform,
                brush = contentState.brush,
                isSegmentEraserEnabled = contentState.isSegmentEraserEnabled,
                eraseStrokePredicate =
                    when (contentState.eraserFilter) {
                        EraserFilter.ALL_STROKES -> {
                            { true }
                        }

                        EraserFilter.PEN_ONLY -> {
                            { stroke -> stroke.style.tool == Tool.PEN }
                        }

                        EraserFilter.HIGHLIGHTER_ONLY -> {
                            { stroke -> stroke.style.tool == Tool.HIGHLIGHTER }
                        }
                    },
                pageWidth = contentState.pageWidth,
                pageHeight = contentState.pageHeight,
                allowEditing =
                    !contentState.isReadOnly &&
                        contentState.interactionMode != InteractionMode.TEXT_SELECTION,
                allowFingerGestures = contentState.allowCanvasFingerGestures,
            )
        val inkCanvasCallbacks =
            InkCanvasCallbacks(
                onStrokeFinished = contentState.onStrokeFinished,
                onStrokeErased = contentState.onStrokeErased,
                onStrokeSplit = contentState.onStrokeSplit,
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
    val isSelectionEnabled: Boolean,
    val selection: TextSelection?,
    val onSelectionChange: (TextSelection?) -> Unit,
    val onCopySelection: () -> Unit,
    val coroutineScope: CoroutineScope,
)

@Composable
private fun Modifier.pdfSelectionInput(state: PdfSelectionState): Modifier =
    if (!state.isSelectionEnabled) {
        this
    } else {
        pointerInput(state.currentPage?.pageId, state.viewTransform, state.textExtractor, state.isSelectionEnabled) {
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
            ) ?: findNearestPdfTextCharIndex(
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
            ) ?: findNearestPdfTextCharIndex(
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

private enum class SelectionHandleType {
    START,
    END,
}

@Composable
@Suppress("ReturnCount")
private fun PdfSelectionHandles(state: PdfSelectionState) {
    if (!state.isSelectionEnabled) {
        return
    }
    val selection = state.selection ?: return
    val handleOffsetPx = with(LocalDensity.current) { PDF_SELECTION_HANDLE_OFFSET_DP.dp.toPx() }
    val handleSize = (PDF_SELECTION_HANDLE_RADIUS_DP * 2).dp

    val startQuad = selection.startHandleQuad() ?: return
    val endQuad = selection.endHandleQuad() ?: return
    val startHandleCenter =
        Offset(
            x = state.viewTransform.pageToScreenX(startQuad.p4.x),
            y = state.viewTransform.pageToScreenY(startQuad.p4.y) + handleOffsetPx,
        )
    val endHandleCenter =
        Offset(
            x = state.viewTransform.pageToScreenX(endQuad.p3.x),
            y = state.viewTransform.pageToScreenY(endQuad.p3.y) + handleOffsetPx,
        )

    PdfSelectionHandle(
        center = startHandleCenter,
        handleSize = handleSize,
        onDrag = { position ->
            updateSelectionFromHandleDrag(
                state = state,
                handle = SelectionHandleType.START,
                screenPosition = position,
            )
        },
    )
    PdfSelectionHandle(
        center = endHandleCenter,
        handleSize = handleSize,
        onDrag = { position ->
            updateSelectionFromHandleDrag(
                state = state,
                handle = SelectionHandleType.END,
                screenPosition = position,
            )
        },
    )
}

@Composable
private fun PdfSelectionHandle(
    center: Offset,
    handleSize: androidx.compose.ui.unit.Dp,
    onDrag: (Offset) -> Unit,
) {
    val density = LocalDensity.current
    val strokeWidth = with(density) { PDF_SELECTION_HANDLE_STROKE_DP.dp.toPx() }
    val handleSizePx = with(density) { handleSize.toPx() }
    val topLeftX = center.x - (handleSizePx / 2f)
    val topLeftY = center.y - (handleSizePx / 2f)
    Box(
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        x = topLeftX.roundToInt(),
                        y = topLeftY.roundToInt(),
                    )
                }.size(handleSize)
                .pointerInput(center) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            onDrag(
                                Offset(
                                    x = topLeftX + change.position.x,
                                    y = topLeftY + change.position.y,
                                ),
                            )
                            change.consume()
                        },
                    )
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(PDF_SELECTION_HANDLE_COLOR))
            drawCircle(
                color = Color.White,
                radius = size.minDimension * PDF_SELECTION_HANDLE_INNER_RADIUS_RATIO,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun updateSelectionFromHandleDrag(
    state: PdfSelectionState,
    handle: SelectionHandleType,
    screenPosition: Offset,
) {
    val currentSelection = state.selection ?: return
    val pageX = state.viewTransform.screenToPageX(screenPosition.x)
    val pageY = state.viewTransform.screenToPageY(screenPosition.y)
    val nearestIndex =
        findNearestPdfTextCharIndex(
            characters = currentSelection.pageCharacters,
            pageX = pageX,
            pageY = pageY,
        ) ?: return

    val normalizedStart = minOf(currentSelection.startCharIndex, currentSelection.endCharIndex)
    val normalizedEnd = maxOf(currentSelection.startCharIndex, currentSelection.endCharIndex)
    val updatedStart =
        if (handle == SelectionHandleType.START) {
            nearestIndex.coerceAtMost(normalizedEnd)
        } else {
            normalizedStart
        }
    val updatedEnd =
        if (handle == SelectionHandleType.END) {
            nearestIndex.coerceAtLeast(normalizedStart)
        } else {
            normalizedEnd
        }
    val updatedSelection =
        buildPdfTextSelection(
            characters = currentSelection.pageCharacters,
            startIndex = updatedStart,
            endIndex = updatedEnd,
        )
    state.onSelectionChange(
        currentSelection.copy(
            startCharIndex = updatedStart,
            endCharIndex = updatedEnd,
            selection = updatedSelection,
        ),
    )
}

private fun TextSelection.startHandleQuad() = pageCharacters.getOrNull(minOf(startCharIndex, endCharIndex))?.quad

private fun TextSelection.endHandleQuad() = pageCharacters.getOrNull(maxOf(startCharIndex, endCharIndex))?.quad

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfTiles(
    entries: Set<Map.Entry<PdfTileKey, ValidatingTile>>,
    viewTransform: ViewTransform,
    tileSizePx: Int,
    previousScaleBucket: Float?,
    crossfadeProgress: Float,
) {
    val (previousBucketEntries, currentBucketEntries) =
        entries.partition { entry ->
            previousScaleBucket != null && entry.key.scaleBucket == previousScaleBucket
        }

    val previousBucketAlpha = 1f - crossfadeProgress
    val currentBucketAlpha = crossfadeProgress

    if (previousBucketEntries.isNotEmpty() && previousBucketAlpha > 0f) {
        previousBucketEntries.forEach { entry ->
            drawPdfTile(
                key = entry.key,
                tile = entry.value,
                viewTransform = viewTransform,
                tileSizePx = tileSizePx,
                alpha = previousBucketAlpha,
            )
        }
    }

    // Draw current bucket tiles with fading in alpha
    currentBucketEntries.forEach { entry ->
        drawPdfTile(
            key = entry.key,
            tile = entry.value,
            viewTransform = viewTransform,
            tileSizePx = tileSizePx,
            alpha = currentBucketAlpha,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfTile(
    key: PdfTileKey,
    tile: ValidatingTile,
    viewTransform: ViewTransform,
    tileSizePx: Int,
    alpha: Float = 1f,
) {
    val drawn =
        tile.withBitmapIfValid { bitmap ->
            val scaleBucket = key.scaleBucket
            if (scaleBucket <= 0f) {
                return@withBitmapIfValid
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
                alpha = alpha,
            )
        }

    if (!drawn) {
        Log.w(NOTE_EDITOR_PDF_TAG, "Skipped drawing recycled/invalid tile for key=$key")
    }
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

/**
 * Draws a subtle drop shadow on the right and bottom edges of the PDF page.
 */
@Suppress("LongParameterList")
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfPageShadow(
    left: Float,
    top: Float,
    pageWidthPx: Float,
    pageHeightPx: Float,
    shadowSpread: Float,
    shadowColor: Color,
) {
    // Right edge shadow
    drawRect(
        brush =
            ComposeBrush.horizontalGradient(
                colors =
                    listOf(
                        shadowColor,
                        Color.Transparent,
                    ),
                startX = left + pageWidthPx,
                endX = left + pageWidthPx + shadowSpread,
            ),
        topLeft = Offset(left + pageWidthPx, top),
        size = Size(shadowSpread, pageHeightPx + shadowSpread),
    )

    // Bottom edge shadow
    drawRect(
        brush =
            ComposeBrush.verticalGradient(
                colors =
                    listOf(
                        shadowColor,
                        Color.Transparent,
                    ),
                startY = top + pageHeightPx,
                endY = top + pageHeightPx + shadowSpread,
            ),
        topLeft = Offset(left + shadowSpread, top + pageHeightPx),
        size = Size(pageWidthPx - shadowSpread, shadowSpread),
    )
}

/**
 * Draws edge glow indicators when the user pans to document limits.
 * Shows a subtle glow on the edge that has been reached.
 */
@Suppress("UNUSED_PARAMETER", "LongParameterList", "LongMethod")
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPdfEdgeGlowIndicators(
    left: Float,
    top: Float,
    pageWidthPx: Float,
    pageHeightPx: Float,
    viewTransform: ViewTransform,
    edgeGlowWidth: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    edgeGlowColor: Color,
) {
    // Calculate how close we are to each edge (0 = at edge, 1 = far from edge)
    val leftEdgeDistance = (left - 0f).coerceAtLeast(0f)
    val rightEdgeDistance = (viewportWidth - (left + pageWidthPx)).coerceAtLeast(0f)
    val topEdgeDistance = (top - 0f).coerceAtLeast(0f)
    val bottomEdgeDistance = (viewportHeight - (top + pageHeightPx)).coerceAtLeast(0f)

    // Calculate glow intensity based on distance (closer = more intense)
    val leftGlowAlpha = calculatePdfGlowAlpha(leftEdgeDistance, edgeGlowWidth)
    val rightGlowAlpha = calculatePdfGlowAlpha(rightEdgeDistance, edgeGlowWidth)
    val topGlowAlpha = calculatePdfGlowAlpha(topEdgeDistance, edgeGlowWidth)
    val bottomGlowAlpha = calculatePdfGlowAlpha(bottomEdgeDistance, edgeGlowWidth)

    // Draw left edge glow (when page left edge is near viewport left)
    if (leftGlowAlpha > 0f && left < edgeGlowWidth) {
        drawRect(
            brush =
                ComposeBrush.horizontalGradient(
                    colors =
                        listOf(
                            edgeGlowColor.copy(alpha = EDGE_GLOW_ALPHA_MAX * leftGlowAlpha),
                            Color.Transparent,
                        ),
                    startX = 0f,
                    endX = edgeGlowWidth,
                ),
            topLeft = Offset(0f, 0f),
            size = Size(edgeGlowWidth, viewportHeight),
        )
    }

    // Draw right edge glow (when page right edge is near viewport right)
    if (rightGlowAlpha > 0f && left + pageWidthPx > viewportWidth - edgeGlowWidth) {
        drawRect(
            brush =
                ComposeBrush.horizontalGradient(
                    colors =
                        listOf(
                            Color.Transparent,
                            edgeGlowColor.copy(alpha = EDGE_GLOW_ALPHA_MAX * rightGlowAlpha),
                        ),
                    startX = viewportWidth - edgeGlowWidth,
                    endX = viewportWidth,
                ),
            topLeft = Offset(viewportWidth - edgeGlowWidth, 0f),
            size = Size(edgeGlowWidth, viewportHeight),
        )
    }

    // Draw top edge glow (when page top edge is near viewport top)
    if (topGlowAlpha > 0f && top < edgeGlowWidth) {
        drawRect(
            brush =
                ComposeBrush.verticalGradient(
                    colors =
                        listOf(
                            edgeGlowColor.copy(alpha = EDGE_GLOW_ALPHA_MAX * topGlowAlpha),
                            Color.Transparent,
                        ),
                    startY = 0f,
                    endY = edgeGlowWidth,
                ),
            topLeft = Offset(0f, 0f),
            size = Size(viewportWidth, edgeGlowWidth),
        )
    }

    // Draw bottom edge glow (when page bottom edge is near viewport bottom)
    if (bottomGlowAlpha > 0f && top + pageHeightPx > viewportHeight - edgeGlowWidth) {
        drawRect(
            brush =
                ComposeBrush.verticalGradient(
                    colors =
                        listOf(
                            Color.Transparent,
                            edgeGlowColor.copy(alpha = EDGE_GLOW_ALPHA_MAX * bottomGlowAlpha),
                        ),
                    startY = viewportHeight - edgeGlowWidth,
                    endY = viewportHeight,
                ),
            topLeft = Offset(0f, viewportHeight - edgeGlowWidth),
            size = Size(viewportWidth, edgeGlowWidth),
        )
    }
}

/**
 * Calculate glow alpha based on distance from edge.
 * Returns 0 when far from edge, 1 when at edge.
 */
private fun calculatePdfGlowAlpha(
    distance: Float,
    threshold: Float,
): Float {
    if (distance >= threshold) return 0f
    return 1f - (distance / threshold)
}
