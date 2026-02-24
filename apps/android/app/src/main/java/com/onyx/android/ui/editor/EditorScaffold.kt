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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.InputSettings
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.objects.model.InsertAction
import com.onyx.android.objects.model.PageObject
import com.onyx.android.objects.model.ShapeType
import com.onyx.android.pdf.DEFAULT_PDF_TILE_SIZE_PX
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.ValidatingTile
import com.onyx.android.recognition.ConvertedTextBlock
import com.onyx.android.ui.EDGE_GLOW_ALPHA_MAX
import com.onyx.android.ui.EDGE_GLOW_WIDTH_DP
import com.onyx.android.ui.EraserFilter
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
private val PAGE_PILL_COLOR = Color(0xFF2B3144)
private val PAGE_PILL_TEXT_COLOR = Color(0xFFF2F5FF)
private const val EDITOR_VIEWPORT_TEST_TAG = "note-editor-viewport"
private const val PAGE_GAP_DP = 8
private const val PAGE_TRACKING_DEBOUNCE_MS = 100L
private const val PINCH_ZOOM_CHANGE_MIN = 0.92f
private const val PINCH_ZOOM_CHANGE_MAX = 1.08f
private const val PINCH_ZOOM_EPSILON = 0.002f
private val ZOOM_PRESET_OPTIONS = listOf(50, 100, 200, 300, 400)

@Composable
internal fun EditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        eraserFilter = contentState.eraserFilter,
                        activeInsertAction = contentState.activeInsertAction,
                        selectedObjectId = contentState.selectedObjectId,
                        interactionMode = contentState.interactionMode,
                        inputSettings = contentState.inputSettings,
                        isRecognitionOverlayEnabled = contentState.isRecognitionOverlayEnabled,
                        pdfRenderer = contentState.pdfRenderer,
                        onStrokeFinished = { stroke -> contentState.onStrokeFinished(stroke, pageState.page.pageId) },
                        onStrokeErased = { stroke -> contentState.onStrokeErased(stroke, pageState.page.pageId) },
                        onStrokeSplit = { original, segments -> contentState.onStrokeSplit(original, segments, pageState.page.pageId) },
                        onLassoMove = { deltaX, deltaY -> contentState.onLassoMove(pageState.page.pageId, deltaX, deltaY) },
                        onLassoResize = { scale, pivotX, pivotY ->
                            contentState.onLassoResize(pageState.page.pageId, scale, pivotX, pivotY)
                        },
                        onLassoConvertToText = { contentState.onLassoConvertToText(pageState.page.pageId) },
                        onInsertActionChanged = contentState.onInsertActionChanged,
                        onShapeObjectCreate = { shapeType, x, y, width, height ->
                            contentState.onShapeObjectCreate(pageState.page.pageId, shapeType, x, y, width, height)
                        },
                        onTextObjectCreate = { x, y ->
                            contentState.onTextObjectCreate(pageState.page.pageId, x, y)
                        },
                        onImageObjectCreate = { x, y ->
                            contentState.onImageObjectCreate(pageState.page.pageId, x, y)
                        },
                        onTextObjectEdit = { pageObject, text ->
                            contentState.onTextObjectEdit(pageState.page.pageId, pageObject, text)
                        },
                        onObjectSelected = contentState.onObjectSelected,
                        onObjectTransformed = { before, after ->
                            contentState.onObjectTransformed(pageState.page.pageId, before, after)
                        },
                        onDuplicateObject = { pageObject ->
                            contentState.onDuplicateObject(pageState.page.pageId, pageObject)
                        },
                        onDeleteObject = { pageObject ->
                            contentState.onDeleteObject(pageState.page.pageId, pageObject)
                        },
                        onConvertedTextBlockSelected = { block -> contentState.onConvertedTextBlockSelected(pageState.page.pageId, block) },
                        onStylusButtonEraserActiveChanged = contentState.onStylusButtonEraserActiveChanged,
                        onTransformGesture = contentState.onTransformGesture,
                        onPanGestureEnd = contentState.onPanGestureEnd,
                        onUndoShortcut = contentState.onUndoShortcut,
                        onRedoShortcut = contentState.onRedoShortcut,
                        onDoubleTapZoomRequested = contentState.onDoubleTapZoomRequested,
                    )
                }
            }
            PageZoomPill(
                currentPage = (contentState.firstVisiblePageIndex + 1).coerceAtLeast(1),
                totalPages = contentState.totalPages,
                zoomPercent = (contentState.documentZoom * 100f).roundToInt(),
                isZoomLocked = contentState.isZoomLocked,
                onPageSelected = { pageNumber ->
                    contentState.onPageSelected((pageNumber - 1).coerceAtLeast(0))
                },
                onZoomPresetSelected = contentState.onZoomPresetSelected,
                onFitZoomRequested = contentState.onFitZoomRequested,
                onZoomLockChanged = contentState.onZoomLockChanged,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
            )
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
    eraserFilter: EraserFilter,
    activeInsertAction: InsertAction,
    selectedObjectId: String?,
    interactionMode: InteractionMode,
    inputSettings: InputSettings,
    isRecognitionOverlayEnabled: Boolean,
    pdfRenderer: PdfDocumentRenderer?,
    onStrokeFinished: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke) -> Unit,
    onStrokeSplit: (InkStroke, List<InkStroke>) -> Unit,
    onLassoMove: (Float, Float) -> Unit,
    onLassoResize: (Float, Float, Float) -> Unit,
    onLassoConvertToText: () -> Unit,
    onInsertActionChanged: (InsertAction) -> Unit,
    onShapeObjectCreate: (ShapeType, Float, Float, Float, Float) -> Unit,
    onTextObjectCreate: (Float, Float) -> Unit,
    onImageObjectCreate: (Float, Float) -> Unit,
    onTextObjectEdit: (PageObject, String) -> Unit,
    onObjectSelected: (String?) -> Unit,
    onObjectTransformed: (PageObject, PageObject) -> Unit,
    onDuplicateObject: (PageObject) -> Unit,
    onDeleteObject: (PageObject) -> Unit,
    onConvertedTextBlockSelected: (ConvertedTextBlock) -> Unit,
    onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
    onTransformGesture: (Float, Float, Float, Float, Float) -> Unit,
    onPanGestureEnd: (Float, Float) -> Unit,
    onUndoShortcut: () -> Unit,
    onRedoShortcut: () -> Unit,
    onDoubleTapZoomRequested: () -> Unit,
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
                        pageObjects = pageState.pageObjects,
                        selectedObjectId = pageState.selectedObjectId,
                        brush = brush,
                        isStylusButtonEraserActive = isStylusButtonEraserActive,
                        isSegmentEraserEnabled = isSegmentEraserEnabled,
                        eraserFilter = eraserFilter,
                        activeInsertAction = activeInsertAction,
                        interactionMode = interactionMode,
                        allowCanvasFingerGestures = false,
                        thumbnails = emptyList(),
                        currentPageIndex = pageState.page.indexInNote,
                        totalPages = 1,
                        templateState = pageState.templateState,
                        lassoSelection = pageState.lassoSelection,
                        isTextSelectionEnabled = interactionMode == InteractionMode.TEXT_SELECTION,
                        loadThumbnail = { null },
                        onStrokeFinished = onStrokeFinished,
                        onStrokeErased = onStrokeErased,
                        onStrokeSplit = onStrokeSplit,
                        onLassoMove = onLassoMove,
                        onLassoResize = onLassoResize,
                        onLassoConvertToText = onLassoConvertToText,
                        onInsertActionChanged = onInsertActionChanged,
                        onShapeObjectCreate = onShapeObjectCreate,
                        onTextObjectCreate = onTextObjectCreate,
                        onImageObjectCreate = onImageObjectCreate,
                        onTextObjectEdit = onTextObjectEdit,
                        onObjectSelected = onObjectSelected,
                        onObjectTransformed = onObjectTransformed,
                        onDuplicateObject = onDuplicateObject,
                        onDeleteObject = onDeleteObject,
                        onSegmentEraserEnabledChange = {},
                        onStylusButtonEraserActiveChanged = onStylusButtonEraserActiveChanged,
                        onTransformGesture = onTransformGesture,
                        onPanGestureEnd = onPanGestureEnd,
                        onUndoShortcut = onUndoShortcut,
                        onRedoShortcut = onRedoShortcut,
                        onViewportSizeChanged = {},
                        onPageSelected = {},
                        onTemplateChange = {},
                    ),
            )
        } else {
            val inkCanvasState =
                remember(pageState.page.pageId, pageState.strokes, renderTransform, brush, inputSettings) {
                    InkCanvasState(
                        strokes = pageState.strokes,
                        viewTransform = renderTransform,
                        brush = brush,
                        lassoSelection = pageState.lassoSelection,
                        isSegmentEraserEnabled = isSegmentEraserEnabled,
                        eraseStrokePredicate = eraserFilterPredicate(eraserFilter),
                        pageWidth = pageState.pageWidth,
                        pageHeight = pageState.pageHeight,
                        allowEditing = !isReadOnly && activeInsertAction == InsertAction.NONE,
                        allowFingerGestures = inputSettings.allowsAnyFingerGesture(),
                        inputSettings = inputSettings,
                    )
                }
            val inkCanvasCallbacks =
                remember(
                    onStrokeFinished,
                    onStrokeErased,
                    onStrokeSplit,
                    onTransformGesture,
                    onPanGestureEnd,
                    onUndoShortcut,
                    onRedoShortcut,
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
                        onUndoShortcut = onUndoShortcut,
                        onRedoShortcut = onRedoShortcut,
                        onDoubleTapGesture = onDoubleTapZoomRequested,
                        onStylusButtonEraserActiveChanged = onStylusButtonEraserActiveChanged,
                    )
                }
            InkCanvas(
                state = inkCanvasState,
                callbacks = inkCanvasCallbacks,
                modifier = Modifier.fillMaxSize(),
            )
        }

        PageObjectLayer(
            pageObjects = pageState.pageObjects,
            selectedObjectId = selectedObjectId,
            activeInsertAction = activeInsertAction,
            viewTransform = renderTransform,
            isReadOnly = isReadOnly,
            isInteractionBlocked = interactionMode == InteractionMode.TEXT_SELECTION,
            onInsertActionChanged = onInsertActionChanged,
            onShapeObjectCreate = onShapeObjectCreate,
            onTextObjectCreate = onTextObjectCreate,
            onImageObjectCreate = onImageObjectCreate,
            onTextObjectEdit = onTextObjectEdit,
            onObjectSelected = onObjectSelected,
            onObjectTransformed = onObjectTransformed,
            onDuplicateObject = onDuplicateObject,
            onDeleteObject = onDeleteObject,
        )

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
        LassoConvertAction(
            selection = pageState.lassoSelection,
            viewTransform = renderTransform,
            enabled = !isReadOnly,
            onConvert = onLassoConvertToText,
        )
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
                        eraseStrokePredicate = eraserFilterPredicate(contentState.eraserFilter),
                        pageWidth = contentState.pageWidth,
                        pageHeight = contentState.pageHeight,
                        allowEditing = !contentState.isReadOnly && contentState.activeInsertAction == InsertAction.NONE,
                        allowFingerGestures = contentState.allowCanvasFingerGestures,
                        inputSettings = contentState.inputSettings,
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
                        onUndoShortcut = contentState.onUndoShortcut,
                        onRedoShortcut = contentState.onRedoShortcut,
                        onDoubleTapGesture = contentState.onDoubleTapZoomRequested,
                        onStylusButtonEraserActiveChanged = contentState.onStylusButtonEraserActiveChanged,
                    )
                InkCanvas(
                    state = inkCanvasState,
                    callbacks = inkCanvasCallbacks,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PageObjectLayer(
                pageObjects = contentState.pageObjects,
                selectedObjectId = contentState.selectedObjectId,
                activeInsertAction = contentState.activeInsertAction,
                viewTransform = contentState.viewTransform,
                isReadOnly = contentState.isReadOnly,
                isInteractionBlocked = contentState.interactionMode == InteractionMode.TEXT_SELECTION,
                onInsertActionChanged = contentState.onInsertActionChanged,
                onShapeObjectCreate = contentState.onShapeObjectCreate,
                onTextObjectCreate = contentState.onTextObjectCreate,
                onImageObjectCreate = contentState.onImageObjectCreate,
                onTextObjectEdit = contentState.onTextObjectEdit,
                onObjectSelected = contentState.onObjectSelected,
                onObjectTransformed = contentState.onObjectTransformed,
                onDuplicateObject = contentState.onDuplicateObject,
                onDeleteObject = contentState.onDeleteObject,
            )
            if (contentState.isRecognitionOverlayEnabled) {
                RecognitionOverlayLayer(
                    viewTransform = contentState.viewTransform,
                    recognitionText = contentState.recognitionText,
                    convertedTextBlocks = contentState.convertedTextBlocks,
                    onConvertedTextBlockSelected = contentState.onConvertedTextBlockSelected,
                )
            }
            LassoConvertAction(
                selection = contentState.lassoSelection,
                viewTransform = contentState.viewTransform,
                enabled = !contentState.isReadOnly,
                onConvert = contentState.onLassoConvertToText,
            )
            PageZoomPill(
                currentPage = (contentState.currentPageIndex + 1).coerceAtLeast(1),
                totalPages = contentState.totalPages,
                zoomPercent = (contentState.viewTransform.zoom * 100f).roundToInt(),
                isZoomLocked = contentState.isZoomLocked,
                onPageSelected = { pageNumber ->
                    contentState.onPageSelected((pageNumber - 1).coerceAtLeast(0))
                },
                onZoomPresetSelected = contentState.onZoomPresetSelected,
                onFitZoomRequested = contentState.onFitZoomRequested,
                onZoomLockChanged = contentState.onZoomLockChanged,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
            )
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
private fun LassoConvertAction(
    selection: com.onyx.android.ink.model.LassoSelection,
    viewTransform: ViewTransform,
    enabled: Boolean,
    onConvert: () -> Unit,
) {
    val bounds = selection.selectionBounds ?: return
    if (!selection.hasSelection) {
        return
    }
    val left = viewTransform.pageToScreenX(bounds.x)
    val top = viewTransform.pageToScreenY(bounds.y)
    TextButton(
        onClick = onConvert,
        enabled = enabled,
        modifier =
            Modifier
                .offset {
                    IntOffset(
                        x = left.roundToInt(),
                        y = (top - 40f).roundToInt(),
                    )
                }.semantics { contentDescription = "Convert lasso to text" }
                .testTag("lasso-convert-button"),
    ) {
        Text("Convert to text")
    }
}

@Composable
private fun PageZoomPill(
    currentPage: Int,
    totalPages: Int,
    zoomPercent: Int,
    isZoomLocked: Boolean,
    onPageSelected: (Int) -> Unit,
    onZoomPresetSelected: (Int) -> Unit,
    onFitZoomRequested: () -> Unit,
    onZoomLockChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var pageInput by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    Surface(
        modifier =
            modifier
                .padding(end = 12.dp, bottom = 12.dp)
                .testTag("page-zoom-pill"),
        shape = RoundedCornerShape(20.dp),
        color = PAGE_PILL_COLOR,
        shadowElevation = 3.dp,
    ) {
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier =
                    Modifier.semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        stateDescription = "Page $currentPage of $totalPages, zoom $zoomPercent percent"
                    }.testTag("page-zoom-pill-button"),
            ) {
                Text(
                    text = "P$currentPage/$totalPages • ${zoomPercent.coerceAtLeast(1)}%",
                    color = PAGE_PILL_TEXT_COLOR,
                    fontSize = 12.sp,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                ZOOM_PRESET_OPTIONS.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("$preset%") },
                        onClick = {
                            expanded = false
                            onZoomPresetSelected(preset)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Fit to page") },
                    onClick = {
                        expanded = false
                        onFitZoomRequested()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isZoomLocked) "Unlock zoom" else "Lock zoom") },
                    onClick = {
                        expanded = false
                        onZoomLockChanged(!isZoomLocked)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Next page") },
                    enabled = currentPage < totalPages,
                    onClick = {
                        expanded = false
                        onPageSelected((currentPage + 1).coerceAtMost(totalPages))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Previous page") },
                    enabled = currentPage > 1,
                    onClick = {
                        expanded = false
                        onPageSelected((currentPage - 1).coerceAtLeast(1))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Go to page...") },
                    enabled = totalPages > 1,
                    onClick = {
                        expanded = false
                        pageInput = currentPage.toString()
                        showPageJumpDialog = true
                    },
                )
            }
        }
    }
    if (showPageJumpDialog) {
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { updated ->
                        pageInput = updated.filter { it.isDigit() }.take(4)
                    },
                    singleLine = true,
                    label = { Text("Page (1-$totalPages)") },
                    modifier = Modifier.testTag("page-zoom-pill-page-input"),
                )
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                val selectedPage =
                    pageInput.toIntOrNull()
                        ?.coerceIn(1, totalPages)
                TextButton(
                    onClick = {
                        if (selectedPage != null) {
                            onPageSelected(selectedPage)
                        }
                        showPageJumpDialog = false
                    },
                    enabled = selectedPage != null,
                ) {
                    Text("Go")
                }
            },
        )
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

private fun InputSettings.allowsAnyFingerGesture(): Boolean =
    singleFingerMode != SingleFingerMode.IGNORE || doubleFingerMode != DoubleFingerMode.IGNORE

private fun eraserFilterPredicate(filter: EraserFilter): (InkStroke) -> Boolean =
    when (filter) {
        EraserFilter.ALL_STROKES -> {
            { true }
        }

        EraserFilter.PEN_ONLY -> {
            { stroke -> stroke.style.tool == Tool.PEN }
        }

        EraserFilter.HIGHLIGHTER_ONLY -> {
            { stroke -> stroke.style.tool == Tool.HIGHLIGHTER }
        }
    }
