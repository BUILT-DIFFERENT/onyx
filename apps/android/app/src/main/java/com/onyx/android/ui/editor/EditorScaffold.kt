@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "UNUSED_PARAMETER",
    "UnusedPrivateMember",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "MagicNumber",
    "MaxLineLength",
    "FunctionName",
)

package com.onyx.android.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.pdf.DEFAULT_PDF_TILE_SIZE_PX
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.ValidatingTile
import com.onyx.android.recognition.ConvertedTextBlock
import com.onyx.android.ui.EDGE_GLOW_ALPHA_MAX
import com.onyx.android.ui.EDGE_GLOW_WIDTH_DP
import com.onyx.android.ui.InteractionMode
import com.onyx.android.ui.MultiPageContentState
import com.onyx.android.ui.NoteEditorContentState
import com.onyx.android.ui.NoteEditorToolbarState
import com.onyx.android.ui.NoteEditorTopBarState
import com.onyx.android.ui.PAGE_BORDER_WIDTH_DP
import com.onyx.android.ui.PAGE_SHADOW_SPREAD_DP
import com.onyx.android.ui.PageItemState
import com.onyx.android.ui.PageTemplateBackground
import com.onyx.android.ui.PdfPageContent
import com.onyx.android.ui.PdfTileRenderState
import com.onyx.android.ui.TOOLBAR_CONTENT_PADDING_DP
import com.onyx.android.ui.ThumbnailStrip
import com.onyx.android.ui.rememberPdfBitmap
import com.onyx.android.ui.rememberPdfThumbnail
import com.onyx.android.ui.rememberPdfTiles
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush as ComposeBrush
import com.onyx.android.ink.model.Stroke as InkStroke

private val NOTE_PAPER = Color(0xFFFDFDFD)
private val NOTE_PAPER_STROKE = Color(0xFFCBCED6)
private val NOTE_PAPER_SHADOW = Color(0x15000000)
private val EDGE_GLOW_COLOR = Color(0x40000000)
private val SEARCH_HIGHLIGHT_FILL = Color(0x44FFEB3B)
private val SEARCH_HIGHLIGHT_STROKE = Color(0xFFFFA000)
private const val EDITOR_VIEWPORT_TEST_TAG = "note-editor-viewport"
private const val PAGE_GAP_DP = 8
private const val PAGE_TRACKING_DEBOUNCE_MS = 100L
private const val PINCH_ZOOM_CHANGE_MIN = 0.92f
private const val PINCH_ZOOM_CHANGE_MAX = 1.08f
private const val PINCH_ZOOM_EPSILON = 0.002f

@Composable
internal fun EditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EditorToolbar(
                topBarState = topBarState,
                toolbarState = toolbarState,
            )
        },
    ) { paddingValues ->
        NoteEditorContent(
            contentState = contentState,
            transformState = transformState,
            paddingValues = paddingValues,
        )
    }
}

/**
 * Scaffold for multi-page LazyColumn layout with continuous vertical scroll.
 */
@Composable
internal fun EditorMultiPageScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    multiPageContentState: MultiPageContentState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EditorToolbar(
                topBarState = topBarState,
                toolbarState = toolbarState,
            )
        },
    ) { paddingValues ->
        MultiPageEditorContent(
            contentState = multiPageContentState,
            paddingValues = paddingValues,
        )
    }
}

/**
 * Multi-page content with LazyColumn for continuous vertical scroll.
 */
@Composable
private fun MultiPageEditorContent(
    contentState: MultiPageContentState,
    paddingValues: PaddingValues,
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = contentState.firstVisiblePageIndex)
    var pendingZoomAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(contentState.firstVisiblePageIndex) {
        val targetIndex = contentState.firstVisiblePageIndex
        if (targetIndex >= 0 && lazyListState.firstVisibleItemIndex != targetIndex) {
            lazyListState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                null
            } else {
                visibleItems.first().index..visibleItems.last().index
            }
        }.filter { it != null }
            .map { it ?: 0..0 }
            .distinctUntilChanged()
            .collect { visibleRange ->
                contentState.onVisiblePageChanged(visibleRange.first)
                contentState.onVisiblePagesImmediateChanged(visibleRange)
            }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                null
            } else {
                visibleItems.first().index..visibleItems.last().index
            }
        }.filter { it != null }
            .map { it ?: 0..0 }
            .distinctUntilChanged()
            .collectLatest { visibleRange ->
                kotlinx.coroutines.delay(PAGE_TRACKING_DEBOUNCE_MS)
                contentState.onVisiblePagesPrefetchChanged(visibleRange)
            }
    }

    LaunchedEffect(pendingZoomAnchor) {
        val anchor = pendingZoomAnchor ?: return@LaunchedEffect
        lazyListState.scrollToItem(
            index = anchor.first,
            scrollOffset = anchor.second,
        )
        pendingZoomAnchor = null
    }

    val zoomGestureModifier =
        Modifier.pointerInput(contentState.documentZoom, contentState.minDocumentZoom, contentState.maxDocumentZoom) {
            var previousDistance: Float? = null
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressedPointers = event.changes.filter { change -> change.pressed }
                    val activeTouchPointers = pressedPointers.filter { change -> change.type == PointerType.Touch }
                    val hasNonTouchPointers = pressedPointers.any { change -> change.type != PointerType.Touch }
                    val shouldHandlePinch =
                        pressedPointers.isNotEmpty() &&
                            !hasNonTouchPointers &&
                            activeTouchPointers.size >= 2
                    if (!shouldHandlePinch) {
                        previousDistance = null
                    } else {
                        val firstPointer = activeTouchPointers[0].position
                        val secondPointer = activeTouchPointers[1].position
                        val focalX = (firstPointer.x + secondPointer.x) / 2f
                        val focalY = (firstPointer.y + secondPointer.y) / 2f
                        val currentDistance = (firstPointer - secondPointer).getDistance()
                        val lastDistance = previousDistance
                        if (lastDistance != null && lastDistance > 0f && currentDistance > 0f) {
                            val zoomChange =
                                (currentDistance / lastDistance).coerceIn(
                                    PINCH_ZOOM_CHANGE_MIN,
                                    PINCH_ZOOM_CHANGE_MAX,
                                )
                            if (abs(zoomChange - 1f) > PINCH_ZOOM_EPSILON) {
                                val oldZoom = contentState.documentZoom
                                val newZoom =
                                    (oldZoom * zoomChange).coerceIn(
                                        contentState.minDocumentZoom,
                                        contentState.maxDocumentZoom,
                                    )
                                if (newZoom != oldZoom) {
                                    val zoomRatio = if (oldZoom > 0f) newZoom / oldZoom else 1f
                                    val viewportWidth =
                                        lazyListState.layoutInfo.viewportSize.width
                                            .toFloat()
                                    val focusedItemInfo =
                                        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
                                            focalY >= itemInfo.offset && focalY <= itemInfo.offset + itemInfo.size
                                        }
                                    if (focusedItemInfo != null) {
                                        val focusItemHeight = focusedItemInfo.size.toFloat().coerceAtLeast(1f)
                                        val focusFraction =
                                            ((focalY - focusedItemInfo.offset) / focusItemHeight).coerceIn(0f, 1f)
                                        val newFocusedHeight = focusItemHeight * zoomRatio
                                        val targetScrollOffset =
                                            ((focusFraction * newFocusedHeight) - focalY).roundToInt().coerceAtLeast(0)
                                        val currentScrollOffset = lazyListState.firstVisibleItemScrollOffset
                                        val needsScrollAdjust =
                                            focusedItemInfo.index != lazyListState.firstVisibleItemIndex ||
                                                abs(targetScrollOffset - currentScrollOffset) > 1
                                        if (needsScrollAdjust) {
                                            pendingZoomAnchor = focusedItemInfo.index to targetScrollOffset
                                        }
                                    }
                                    val focusedPage =
                                        focusedItemInfo?.let { itemInfo ->
                                            contentState.pages.getOrNull(itemInfo.index)
                                        }
                                    val oldContentWidth =
                                        focusedPage?.let { page ->
                                            page.pageWidth * page.renderTransform.zoom
                                        } ?: viewportWidth
                                    val newContentWidth = oldContentWidth * zoomRatio
                                    val minPanX = (viewportWidth - newContentWidth).coerceAtMost(0f)
                                    val oldPanX = contentState.documentPanX
                                    val newPanX =
                                        (focalX - ((focalX - oldPanX) * zoomRatio)).coerceIn(minPanX, 0f)
                                    contentState.onDocumentZoomChange(newZoom)
                                    contentState.onDocumentPanXChange(newPanX)
                                }
                                activeTouchPointers.forEach { change -> change.consume() }
                            }
                        }
                        previousDistance = currentDistance
                    }
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(zoomGestureModifier)
                    .onSizeChanged { size ->
                        contentState.onViewportSizeChanged(size)
                    }.testTag(EDITOR_VIEWPORT_TEST_TAG)
                    .semantics { contentDescription = "Editor viewport" },
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = TOOLBAR_CONTENT_PADDING_DP.dp + PAGE_GAP_DP.dp,
                        bottom = PAGE_GAP_DP.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp),
            ) {
                items(
                    count = contentState.pages.size,
                    key = { index -> contentState.pages[index].page.pageId },
                ) { index ->
                    val pageState = contentState.pages[index]
                    PageItem(
                        pageState = pageState,
                        isReadOnly = contentState.isReadOnly,
                        brush = contentState.brush,
                        isStylusButtonEraserActive = contentState.isStylusButtonEraserActive,
                        isSegmentEraserEnabled = contentState.isSegmentEraserEnabled,
                        interactionMode = contentState.interactionMode,
                        isRecognitionOverlayEnabled = contentState.isRecognitionOverlayEnabled,
                        pdfRenderer = contentState.pdfRenderer,
                        onStrokeFinished = { stroke -> contentState.onStrokeFinished(stroke, pageState.page.pageId) },
                        onStrokeErased = { stroke -> contentState.onStrokeErased(stroke, pageState.page.pageId) },
                        onStrokeSplit = { original, segments -> contentState.onStrokeSplit(original, segments, pageState.page.pageId) },
                        onLassoMove = { deltaX, deltaY -> contentState.onLassoMove(pageState.page.pageId, deltaX, deltaY) },
                        onLassoResize = { scale, pivotX, pivotY ->
                            contentState.onLassoResize(pageState.page.pageId, scale, pivotX, pivotY)
                        },
                        onConvertedTextBlockSelected = { block -> contentState.onConvertedTextBlockSelected(pageState.page.pageId, block) },
                        onStylusButtonEraserActiveChanged = contentState.onStylusButtonEraserActiveChanged,
                        onTransformGesture = contentState.onTransformGesture,
                        onPanGestureEnd = contentState.onPanGestureEnd,
                    )
                }
            }
        }

        // Thumbnail strip at bottom for PDF documents
        if (contentState.thumbnails.isNotEmpty()) {
            ThumbnailStrip(
                thumbnails = contentState.thumbnails,
                currentPageIndex = contentState.firstVisiblePageIndex,
                onPageSelected = contentState.onPageSelected,
                loadThumbnail = contentState.loadThumbnail,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Individual page item in the LazyColumn.
 */
@Composable
private fun PageItem(
    pageState: PageItemState,
    isReadOnly: Boolean,
    brush: Brush,
    isStylusButtonEraserActive: Boolean,
    isSegmentEraserEnabled: Boolean,
    interactionMode: InteractionMode,
    isRecognitionOverlayEnabled: Boolean,
    pdfRenderer: PdfDocumentRenderer?,
    onStrokeFinished: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke) -> Unit,
    onStrokeSplit: (InkStroke, List<InkStroke>) -> Unit,
    onLassoMove: (Float, Float) -> Unit,
    onLassoResize: (Float, Float, Float) -> Unit,
    onConvertedTextBlockSelected: (ConvertedTextBlock) -> Unit,
    onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
    onTransformGesture: (Float, Float, Float, Float, Float) -> Unit,
    onPanGestureEnd: (Float, Float) -> Unit,
) {
    val renderTransform = pageState.renderTransform
    val viewportWidthPx = (pageState.pageWidth * renderTransform.zoom).roundToInt().coerceAtLeast(1)
    val viewportHeightPx = (pageState.pageHeight * renderTransform.zoom).roundToInt().coerceAtLeast(1)
    val viewportSize = IntSize(viewportWidthPx, viewportHeightPx)

    val pdfTileState =
        if (pageState.isPdfPage && pdfRenderer != null && pageState.isVisible) {
            rememberPdfTiles(
                isPdfPage = true,
                currentPage = pageState.page,
                pdfRenderer = pdfRenderer,
                viewTransform = renderTransform,
                viewportSize = viewportSize,
                pageWidth = pageState.pageWidth,
                pageHeight = pageState.pageHeight,
            )
        } else {
            PdfTileRenderState(
                tiles = emptyMap(),
                scaleBucket = null,
                previousScaleBucket = null,
                tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
                crossfadeProgress = 1f,
            )
        }
    val pdfBitmap =
        if (pageState.isPdfPage && pdfRenderer != null) {
            if (pageState.isVisible) {
                rememberPdfBitmap(
                    isPdfPage = true,
                    currentPage = pageState.page,
                    pdfRenderer = pdfRenderer,
                    viewZoom = renderTransform.zoom,
                )
            } else {
                rememberPdfThumbnail(
                    isPdfPage = true,
                    currentPage = pageState.page,
                    pdfRenderer = pdfRenderer,
                )
            }
        } else {
            null
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(pageState.pageHeightDp)
                .clipToBounds()
                .background(NOTE_PAPER),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shadowSpread = PAGE_SHADOW_SPREAD_DP.dp.toPx()

            drawPageShadow(
                left = 0f,
                top = 0f,
                pageWidthPx = size.width,
                pageHeightPx = size.height,
                shadowSpread = shadowSpread,
            )
        }

        PageTemplateBackground(
            templateState = pageState.templateState,
            pageWidth = pageState.pageWidth,
            pageHeight = pageState.pageHeight,
            viewTransform = renderTransform,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val borderWidth = PAGE_BORDER_WIDTH_DP.dp.toPx()

            drawRect(
                color = NOTE_PAPER_STROKE,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                style = Stroke(width = borderWidth),
            )
        }

        if (pageState.isPdfPage && pdfRenderer != null) {
            PdfPageContent(
                contentState =
                    NoteEditorContentState(
                        isPdfPage = true,
                        isReadOnly = isReadOnly,
                        pdfTiles = pdfTileState.tiles,
                        pdfRenderScaleBucket = pdfTileState.scaleBucket,
                        pdfPreviousScaleBucket = pdfTileState.previousScaleBucket,
                        pdfTileSizePx = pdfTileState.tileSizePx,
                        pdfCrossfadeProgress = pdfTileState.crossfadeProgress,
                        pdfBitmap = pdfBitmap,
                        pdfRenderer = pdfRenderer,
                        currentPage = pageState.page,
                        viewTransform = renderTransform,
                        pageWidthDp = pageState.pageWidthDp,
                        pageHeightDp = pageState.pageHeightDp,
                        pageWidth = pageState.pageWidth,
                        pageHeight = pageState.pageHeight,
                        strokes = pageState.strokes,
                        brush = brush,
                        isStylusButtonEraserActive = isStylusButtonEraserActive,
                        isSegmentEraserEnabled = isSegmentEraserEnabled,
                        interactionMode = interactionMode,
                        allowCanvasFingerGestures = false,
                        thumbnails = emptyList(),
                        currentPageIndex = pageState.page.indexInNote,
                        templateState = pageState.templateState,
                        lassoSelection = pageState.lassoSelection,
                        isTextSelectionEnabled = interactionMode == InteractionMode.TEXT_SELECTION,
                        loadThumbnail = { null },
                        onStrokeFinished = onStrokeFinished,
                        onStrokeErased = onStrokeErased,
                        onStrokeSplit = onStrokeSplit,
                        onLassoMove = onLassoMove,
                        onLassoResize = onLassoResize,
                        onSegmentEraserEnabledChange = {},
                        onStylusButtonEraserActiveChanged = onStylusButtonEraserActiveChanged,
                        onTransformGesture = onTransformGesture,
                        onPanGestureEnd = onPanGestureEnd,
                        onViewportSizeChanged = {},
                        onPageSelected = {},
                        onTemplateChange = {},
                    ),
            )
        } else {
            val inkCanvasState =
                remember(pageState.page.pageId, pageState.strokes, renderTransform, brush) {
                    InkCanvasState(
                        strokes = pageState.strokes,
                        viewTransform = renderTransform,
                        brush = brush,
                        lassoSelection = pageState.lassoSelection,
                        isSegmentEraserEnabled = isSegmentEraserEnabled,
                        pageWidth = pageState.pageWidth,
                        pageHeight = pageState.pageHeight,
                        allowEditing = !isReadOnly,
                        allowFingerGestures = brush.tool == com.onyx.android.ink.model.Tool.LASSO,
                    )
                }
            val inkCanvasCallbacks =
                remember(
                    onStrokeFinished,
                    onStrokeErased,
                    onStrokeSplit,
                    onTransformGesture,
                    onPanGestureEnd,
                    onStylusButtonEraserActiveChanged,
                ) {
                    InkCanvasCallbacks(
                        onStrokeFinished = if (isReadOnly) ({}) else onStrokeFinished,
                        onStrokeErased = if (isReadOnly) ({}) else onStrokeErased,
                        onStrokeSplit = if (isReadOnly) ({ _, _ -> }) else onStrokeSplit,
                        onLassoMove = if (isReadOnly) ({ _, _ -> }) else onLassoMove,
                        onLassoResize = if (isReadOnly) ({ _, _, _ -> }) else onLassoResize,
                        onTransformGesture = onTransformGesture,
                        onPanGestureEnd = onPanGestureEnd,
                        onStylusButtonEraserActiveChanged = onStylusButtonEraserActiveChanged,
                    )
                }
            InkCanvas(
                state = inkCanvasState,
                callbacks = inkCanvasCallbacks,
                modifier = Modifier.fillMaxSize(),
            )
        }

        pageState.searchHighlightBounds?.let { bounds ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = renderTransform.pageToScreenX(bounds.left)
                val top = renderTransform.pageToScreenY(bounds.top)
                val width = renderTransform.pageWidthToScreen(bounds.width).coerceAtLeast(1f)
                val height = renderTransform.pageWidthToScreen(bounds.height).coerceAtLeast(1f)
                drawRect(
                    color = SEARCH_HIGHLIGHT_FILL,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                )
                drawRect(
                    color = SEARCH_HIGHLIGHT_STROKE,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        if (isRecognitionOverlayEnabled) {
            RecognitionOverlayLayer(
                viewTransform = renderTransform,
                recognitionText = pageState.recognitionText,
                convertedTextBlocks = pageState.convertedTextBlocks,
                onConvertedTextBlockSelected = onConvertedTextBlockSelected,
            )
        }
    }
}

@Composable
private fun PdfPageBitmap(
    bitmap: android.graphics.Bitmap,
    pageWidth: Float,
    pageHeight: Float,
    viewTransform: ViewTransform,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val destinationWidthPx = (pageWidth * viewTransform.zoom).roundToInt().coerceAtLeast(1)
        val destinationHeightPx = (pageHeight * viewTransform.zoom).roundToInt().coerceAtLeast(1)
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

@Composable
private fun PdfTilesOverlay(
    tiles: Map<PdfTileKey, ValidatingTile>,
    viewTransform: ViewTransform,
    tileSizePx: Int,
    previousScaleBucket: Float?,
    crossfadeProgress: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val (previousBucketEntries, currentBucketEntries) =
            tiles.entries.partition { entry ->
                previousScaleBucket != null && entry.key.scaleBucket == previousScaleBucket
            }

        val previousBucketAlpha = 1f - crossfadeProgress
        val currentBucketAlpha = crossfadeProgress

        if (previousBucketEntries.isNotEmpty() && previousBucketAlpha > 0f) {
            previousBucketEntries.forEach { entry ->
                val key = entry.key
                val tile = entry.value
                val bitmap = tile.getBitmapIfValid() ?: return@forEach

                val scaleBucket = key.scaleBucket
                if (scaleBucket <= 0f) return@forEach

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
                    dstOffset = IntOffset(tileScreenLeft.roundToInt(), tileScreenTop.roundToInt()),
                    dstSize = IntSize(tileScreenWidth, tileScreenHeight),
                    filterQuality = FilterQuality.High,
                    alpha = previousBucketAlpha,
                )
            }
        }

        currentBucketEntries.forEach { entry ->
            val key = entry.key
            val tile = entry.value
            val bitmap = tile.getBitmapIfValid() ?: return@forEach

            val scaleBucket = key.scaleBucket
            if (scaleBucket <= 0f) return@forEach

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
                dstOffset = IntOffset(tileScreenLeft.roundToInt(), tileScreenTop.roundToInt()),
                dstSize = IntSize(tileScreenWidth, tileScreenHeight),
                filterQuality = FilterQuality.High,
                alpha = currentBucketAlpha,
            )
        }
    }
}

@Composable
private fun NoteEditorContent(
    contentState: NoteEditorContentState,
    transformState: TransformableState,
    paddingValues: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = TOOLBAR_CONTENT_PADDING_DP.dp)
                    .onSizeChanged { size ->
                        contentState.onViewportSizeChanged(size)
                    }.testTag(EDITOR_VIEWPORT_TEST_TAG)
                    .semantics { contentDescription = "Editor viewport" },
        ) {
            if (!contentState.isPdfPage) {
                FixedPageBackground(contentState = contentState)
            }
            if (contentState.isPdfPage) {
                PdfPageContent(contentState)
            } else {
                val inkCanvasState =
                    InkCanvasState(
                        strokes = contentState.strokes,
                        viewTransform = contentState.viewTransform,
                        brush = contentState.brush,
                        lassoSelection = contentState.lassoSelection,
                        isSegmentEraserEnabled = contentState.isSegmentEraserEnabled,
                        pageWidth = contentState.pageWidth,
                        pageHeight = contentState.pageHeight,
                        allowEditing = !contentState.isReadOnly,
                    )
                val inkCanvasCallbacks =
                    InkCanvasCallbacks(
                        onStrokeFinished =
                            if (contentState.isReadOnly) {
                                {}
                            } else {
                                contentState.onStrokeFinished
                            },
                        onStrokeErased =
                            if (contentState.isReadOnly) {
                                {}
                            } else {
                                contentState.onStrokeErased
                            },
                        onStrokeSplit =
                            if (contentState.isReadOnly) {
                                { _, _ -> }
                            } else {
                                contentState.onStrokeSplit
                            },
                        onLassoMove = if (contentState.isReadOnly) ({ _, _ -> }) else contentState.onLassoMove,
                        onLassoResize =
                            if (contentState.isReadOnly) {
                                { _, _, _ -> }
                            } else {
                                contentState.onLassoResize
                            },
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
            if (contentState.isRecognitionOverlayEnabled) {
                RecognitionOverlayLayer(
                    viewTransform = contentState.viewTransform,
                    recognitionText = contentState.recognitionText,
                    convertedTextBlocks = contentState.convertedTextBlocks,
                    onConvertedTextBlockSelected = contentState.onConvertedTextBlockSelected,
                )
            }
        }

        // Thumbnail strip at bottom for PDF documents
        if (contentState.thumbnails.isNotEmpty()) {
            ThumbnailStrip(
                thumbnails = contentState.thumbnails,
                currentPageIndex = contentState.currentPageIndex,
                onPageSelected = contentState.onPageSelected,
                loadThumbnail = contentState.loadThumbnail,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RecognitionOverlayLayer(
    viewTransform: ViewTransform,
    recognitionText: String?,
    convertedTextBlocks: List<ConvertedTextBlock>,
    onConvertedTextBlockSelected: (ConvertedTextBlock) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        recognitionText
            ?.trim()
            ?.takeIf { text -> text.isNotBlank() }
            ?.let { text ->
                val left = viewTransform.pageToScreenX(12f)
                val top = viewTransform.pageToScreenY(12f)
                Text(
                    text = text,
                    color = Color(0xFF273043),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .background(Color(0xCCFFF7D6))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        convertedTextBlocks.forEach { block ->
            val bounds = block.bounds
            val left = viewTransform.pageToScreenX(bounds.x)
            val top = viewTransform.pageToScreenY(bounds.y)
            val widthPx = viewTransform.pageWidthToScreen(bounds.w).coerceAtLeast(56f)
            Text(
                text = block.text,
                color = Color(0xFF1E293B),
                fontSize = 13.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                        .width(with(density) { widthPx.toDp() })
                        .background(Color(0xE6FFFBEB))
                        .clickable { onConvertedTextBlockSelected(block) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FixedPageBackground(contentState: NoteEditorContentState) {
    val pageWidth = contentState.pageWidth
    val pageHeight = contentState.pageHeight
    val transform = contentState.viewTransform
    val templateState = contentState.templateState

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            return@Canvas
        }
        val left = transform.pageToScreenX(0f)
        val top = transform.pageToScreenY(0f)
        val pageWidthPx = transform.pageWidthToScreen(pageWidth)
        val pageHeightPx = transform.pageWidthToScreen(pageHeight)
        val shadowSpread = PAGE_SHADOW_SPREAD_DP.dp.toPx()
        val borderWidth = PAGE_BORDER_WIDTH_DP.dp.toPx()
        val edgeGlowWidth = EDGE_GLOW_WIDTH_DP.dp.toPx()

        // Draw page shadow (subtle drop shadow on right and bottom edges)
        drawPageShadow(
            left = left,
            top = top,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            shadowSpread = shadowSpread,
        )

        // Draw page background
        drawRect(
            color = NOTE_PAPER,
            topLeft = Offset(left, top),
            size = Size(pageWidthPx, pageHeightPx),
        )
    }

    // Draw template background (grid/lined/dotted) beneath strokes
    PageTemplateBackground(
        templateState = templateState,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewTransform = transform,
        modifier = Modifier.fillMaxSize(),
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (pageWidth <= 0f || pageHeight <= 0f) {
            return@Canvas
        }
        val left = transform.pageToScreenX(0f)
        val top = transform.pageToScreenY(0f)
        val pageWidthPx = transform.pageWidthToScreen(pageWidth)
        val pageHeightPx = transform.pageWidthToScreen(pageHeight)
        val borderWidth = PAGE_BORDER_WIDTH_DP.dp.toPx()
        val edgeGlowWidth = EDGE_GLOW_WIDTH_DP.dp.toPx()

        // Draw page border
        drawRect(
            color = NOTE_PAPER_STROKE,
            topLeft = Offset(left, top),
            size = Size(pageWidthPx, pageHeightPx),
            style = Stroke(width = borderWidth),
        )

        // Draw edge glow indicators when at document limits
        drawEdgeGlowIndicators(
            left = left,
            top = top,
            pageWidthPx = pageWidthPx,
            pageHeightPx = pageHeightPx,
            viewTransform = transform,
            edgeGlowWidth = edgeGlowWidth,
            viewportWidth = size.width,
            viewportHeight = size.height,
        )
    }
}

/**
 * Draws a subtle drop shadow on the right and bottom edges of the page.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageShadow(
    left: Float,
    top: Float,
    pageWidthPx: Float,
    pageHeightPx: Float,
    shadowSpread: Float,
) {
    // Right edge shadow
    drawRect(
        brush =
            ComposeBrush.horizontalGradient(
                colors =
                    listOf(
                        NOTE_PAPER_SHADOW,
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
                        NOTE_PAPER_SHADOW,
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
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeGlowIndicators(
    left: Float,
    top: Float,
    pageWidthPx: Float,
    pageHeightPx: Float,
    viewTransform: ViewTransform,
    edgeGlowWidth: Float,
    viewportWidth: Float,
    viewportHeight: Float,
) {
    // Calculate how close we are to each edge (0 = at edge, 1 = far from edge)
    val leftEdgeDistance = (left - 0f).coerceAtLeast(0f)
    val rightEdgeDistance = (viewportWidth - (left + pageWidthPx)).coerceAtLeast(0f)
    val topEdgeDistance = (top - 0f).coerceAtLeast(0f)
    val bottomEdgeDistance = (viewportHeight - (top + pageHeightPx)).coerceAtLeast(0f)

    // Calculate glow intensity based on distance (closer = more intense)
    val leftGlowAlpha = calculateGlowAlpha(leftEdgeDistance, edgeGlowWidth)
    val rightGlowAlpha = calculateGlowAlpha(rightEdgeDistance, edgeGlowWidth)
    val topGlowAlpha = calculateGlowAlpha(topEdgeDistance, edgeGlowWidth)
    val bottomGlowAlpha = calculateGlowAlpha(bottomEdgeDistance, edgeGlowWidth)

    // Draw left edge glow (when page left edge is near viewport left)
    if (leftGlowAlpha > 0f && left < edgeGlowWidth) {
        drawRect(
            brush =
                ComposeBrush.horizontalGradient(
                    colors =
                        listOf(
                            EDGE_GLOW_COLOR.copy(alpha = EDGE_GLOW_ALPHA_MAX * leftGlowAlpha),
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
                            EDGE_GLOW_COLOR.copy(alpha = EDGE_GLOW_ALPHA_MAX * rightGlowAlpha),
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
                            EDGE_GLOW_COLOR.copy(alpha = EDGE_GLOW_ALPHA_MAX * topGlowAlpha),
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
                            EDGE_GLOW_COLOR.copy(alpha = EDGE_GLOW_ALPHA_MAX * bottomGlowAlpha),
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
private fun calculateGlowAlpha(
    distance: Float,
    threshold: Float,
): Float {
    if (distance >= threshold) return 0f
    return 1f - (distance / threshold)
}
