@file:Suppress(
    "FunctionName",
    "LongMethod",
    "TooManyFunctions",
    "MagicNumber",
    "MaxLineLength",
    "CyclomaticComplexMethod",
)

package com.onyx.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.InkCanvas
import com.onyx.android.ink.ui.InkCanvasCallbacks
import com.onyx.android.ink.ui.InkCanvasState
import com.onyx.android.pdf.DEFAULT_PDF_TILE_SIZE_PX
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.ValidatingTile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush as ComposeBrush
import com.onyx.android.ink.model.Stroke as InkStroke

private const val HEX_COLOR_LENGTH_RGB = 7
private const val MIN_COLOR_CHANNEL = 0
private const val MAX_COLOR_CHANNEL = 255
private const val COLOR_ALPHA_MAX_FLOAT = 255f
private const val DEFAULT_HIGHLIGHTER_OPACITY = 0.35f
private const val DEFAULT_HIGHLIGHTER_BASE_WIDTH = 6.5f
private val HEX_COLOR_REGEX = Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$")
private val NOTEWISE_CHROME = Color(0xFF20263A)
private val NOTEWISE_PILL = Color(0xFF2B3144)
private val NOTEWISE_ICON = Color(0xFFF2F5FF)
private val NOTEWISE_ICON_MUTED = Color(0xFFA9B0C5)
private val NOTEWISE_SELECTED = Color(0xFF136CC5)
private val NOTEWISE_STROKE = Color(0xFF3B435B)
private val NOTE_PAPER = Color(0xFFFDFDFD)
private val NOTE_PAPER_STROKE = Color(0xFFCBCED6)
private val NOTE_PAPER_SHADOW = Color(0x15000000)
private val EDGE_GLOW_COLOR = Color(0x40000000)
private const val NOTEWISE_LEFT_GROUP_WIDTH_DP = 360
private const val NOTEWISE_RIGHT_GROUP_WIDTH_DP = 82
private const val EDITOR_VIEWPORT_TEST_TAG = "note-editor-viewport"
private const val TITLE_INPUT_TEST_TAG = "note-title-input"

private enum class ToolPanelType {
    PEN,
    HIGHLIGHTER,
    ERASER,
}

private data class ToolButtonVisuals(
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun NoteEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    contentState: NoteEditorContentState,
    transformState: TransformableState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            NoteEditorTopBar(
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
internal fun MultiPageEditorScaffold(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
    multiPageContentState: MultiPageContentState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            NoteEditorTopBar(
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

private const val PAGE_GAP_DP = 8
private const val PAGE_TRACKING_DEBOUNCE_MS = 100L
private const val PINCH_ZOOM_CHANGE_MIN = 0.92f
private const val PINCH_ZOOM_CHANGE_MAX = 1.08f
private const val PINCH_ZOOM_EPSILON = 0.002f

/**
 * Multi-page content with LazyColumn for continuous vertical scroll.
 */
@Composable
@Suppress("LongMethod")
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
                        interactionMode = contentState.interactionMode,
                        pdfRenderer = contentState.pdfRenderer,
                        onStrokeFinished = { stroke -> contentState.onStrokeFinished(stroke, pageState.page.pageId) },
                        onStrokeErased = { stroke -> contentState.onStrokeErased(stroke, pageState.page.pageId) },
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Individual page item in the LazyColumn.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "UNUSED_PARAMETER")
private fun PageItem(
    pageState: PageItemState,
    isReadOnly: Boolean,
    brush: Brush,
    isStylusButtonEraserActive: Boolean,
    interactionMode: InteractionMode,
    pdfRenderer: PdfDocumentRenderer?,
    onStrokeFinished: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke) -> Unit,
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
        // Page shadow and border overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shadowSpread = PAGE_SHADOW_SPREAD_DP.dp.toPx()
            val borderWidth = PAGE_BORDER_WIDTH_DP.dp.toPx()

            // Draw page shadow (subtle drop shadow on right and bottom edges)
            drawPageShadow(
                left = 0f,
                top = 0f,
                pageWidthPx = size.width,
                pageHeightPx = size.height,
                shadowSpread = shadowSpread,
            )

            // Draw page border
            drawRect(
                color = NOTE_PAPER_STROKE,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                style = Stroke(width = borderWidth),
            )
        }

        // PDF content if applicable
        if (pageState.isPdfPage && pdfBitmap != null) {
            PdfPageBitmap(
                bitmap = pdfBitmap,
                pageWidth = pageState.pageWidth,
                pageHeight = pageState.pageHeight,
                viewTransform = renderTransform,
            )
        }

        // PDF tiles if available
        if (pageState.isPdfPage && pdfTileState.tiles.isNotEmpty()) {
            PdfTilesOverlay(
                tiles = pdfTileState.tiles,
                viewTransform = renderTransform,
                tileSizePx = pdfTileState.tileSizePx,
                previousScaleBucket = pdfTileState.previousScaleBucket,
                crossfadeProgress = pdfTileState.crossfadeProgress,
            )
        }

        // Ink canvas for drawing
        val inkCanvasState =
            remember(pageState.page.pageId, pageState.strokes, renderTransform, brush) {
                InkCanvasState(
                    strokes = pageState.strokes,
                    viewTransform = renderTransform,
                    brush = brush,
                    pageWidth = pageState.pageWidth,
                    pageHeight = pageState.pageHeight,
                    allowEditing = !isReadOnly,
                    allowFingerGestures = false,
                )
            }
        val inkCanvasCallbacks =
            remember(onStrokeFinished, onStrokeErased, onTransformGesture, onPanGestureEnd, onStylusButtonEraserActiveChanged) {
                InkCanvasCallbacks(
                    onStrokeFinished = if (isReadOnly) ({}) else onStrokeFinished,
                    onStrokeErased = if (isReadOnly) ({}) else onStrokeErased,
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
}

/**
 * Renders a PDF page bitmap.
 */
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

/**
 * Renders PDF tiles overlay.
 */
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
private fun NoteEditorTopBar(
    topBarState: NoteEditorTopBarState,
    toolbarState: NoteEditorToolbarState,
) {
    var activeToolPanel by rememberSaveable { mutableStateOf<ToolPanelType?>(null) }
    var isColorPickerVisible by rememberSaveable { mutableStateOf(false) }
    var colorPickerInput by rememberSaveable { mutableStateOf(toolbarState.brush.color) }
    var isTitleEditing by rememberSaveable { mutableStateOf(false) }
    var titleDraft by rememberSaveable { mutableStateOf(topBarState.noteTitle) }
    var isOverflowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val brush = toolbarState.brush
    val selectedTool =
        if (toolbarState.isStylusButtonEraserActive) {
            Tool.ERASER
        } else {
            brush.tool
        }
    val isEditingEnabled = !topBarState.isReadOnly
    val currentPageNumber =
        if (topBarState.totalPages > 0) {
            (topBarState.currentPageIndex + 1).coerceIn(1, topBarState.totalPages)
        } else {
            0
        }
    val pageCounterDescription = "Page $currentPageNumber of ${topBarState.totalPages}"
    val commitTitleEdit: () -> Unit = {
        topBarState.onUpdateTitle(titleDraft)
        isTitleEditing = false
        focusManager.clearFocus()
    }
    LaunchedEffect(topBarState.noteTitle, isTitleEditing) {
        if (!isTitleEditing) {
            titleDraft = topBarState.noteTitle
        }
    }
    LaunchedEffect(isEditingEnabled) {
        if (!isEditingEnabled) {
            isTitleEditing = false
        }
    }
    val onColorSelected: (String) -> Unit = { selectedColor ->
        val targetTool =
            if (brush.tool == Tool.ERASER) {
                toolbarState.lastNonEraserTool
            } else {
                brush.tool
            }
        val normalized = normalizeHexColor(selectedColor)
        val adjustedColor =
            if (targetTool == Tool.HIGHLIGHTER) {
                applyOpacity(normalized, resolveOpacity(brush))
            } else {
                normalized
            }
        toolbarState.onBrushChange(
            brush.copy(
                tool = targetTool,
                color = adjustedColor,
            ),
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NOTEWISE_CHROME,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height((TOOLBAR_ROW_HEIGHT_DP + TOOLBAR_VERTICAL_PADDING_DP * 2).dp)
                    .padding(horizontal = TOOLBAR_HORIZONTAL_PADDING_DP.dp)
                    .padding(top = TOOLBAR_VERTICAL_PADDING_DP.dp, bottom = TOOLBAR_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        ) {
            ToolbarGroup(
                contentDescription = "Navigation controls",
                modifier = Modifier.width(NOTEWISE_LEFT_GROUP_WIDTH_DP.dp),
            ) {
                IconButton(
                    onClick = topBarState.onNavigateBack,
                    modifier = Modifier.semantics { contentDescription = "Back" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
                ToolbarDivider()
                NoteTitleEditor(
                    noteTitle = topBarState.noteTitle,
                    titleDraft = titleDraft,
                    isEditing = isTitleEditing,
                    isEditingEnabled = isEditingEnabled,
                    onTitleDraftChange = { titleDraft = it },
                    onStartEditing = { isTitleEditing = true },
                    onCommit = commitTitleEdit,
                )
                Box {
                    IconButton(
                        onClick = { isOverflowMenuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = "More actions" },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = NOTEWISE_ICON,
                        )
                    }
                    DropdownMenu(
                        expanded = isOverflowMenuExpanded,
                        onDismissRequest = { isOverflowMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename note") },
                            enabled = isEditingEnabled,
                            onClick = {
                                isOverflowMenuExpanded = false
                                if (isEditingEnabled) {
                                    isTitleEditing = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (topBarState.isReadOnly) {
                                        "Switch to edit mode"
                                    } else {
                                        "Switch to view mode"
                                    },
                                )
                            },
                            onClick = {
                                isOverflowMenuExpanded = false
                                topBarState.onToggleReadOnly()
                            },
                        )
                    }
                }
            }

            ToolbarGroup(
                contentDescription = "Main tools",
                modifier = Modifier.weight(1f),
            ) {
                IconButton(
                    onClick = topBarState.onUndo,
                    enabled = topBarState.canUndo && isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "Undo" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = if (topBarState.canUndo && isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(
                    onClick = topBarState.onRedo,
                    enabled = topBarState.canRedo && isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "Redo" },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = null,
                        tint = if (topBarState.canRedo && isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                ToolbarDivider()

                val penVisuals =
                    remember {
                        ToolButtonVisuals(
                            label = "Pen",
                            icon = Icons.Filled.Create,
                        )
                    }
                val highlighterVisuals =
                    remember {
                        ToolButtonVisuals(
                            label = "Highlighter",
                            icon = Icons.Filled.BorderColor,
                        )
                    }
                Box {
                    ToolToggleButton(
                        visuals = penVisuals,
                        isSelected = selectedTool == Tool.PEN,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            toolbarState.onBrushChange(
                                brush.copy(
                                    tool = Tool.PEN,
                                    color = stripAlpha(normalizeHexColor(brush.color)),
                                ),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.PEN
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.PEN,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.PEN,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    ToolToggleButton(
                        visuals = highlighterVisuals,
                        isSelected = selectedTool == Tool.HIGHLIGHTER,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            val normalized = normalizeHexColor(brush.color)
                            val targetOpacity =
                                if (brush.tool == Tool.HIGHLIGHTER) {
                                    resolveOpacity(brush)
                                } else {
                                    DEFAULT_HIGHLIGHTER_OPACITY
                                }
                            toolbarState.onBrushChange(
                                brush.copy(
                                    tool = Tool.HIGHLIGHTER,
                                    color = applyOpacity(normalized, targetOpacity),
                                    baseWidth = maxOf(brush.baseWidth, DEFAULT_HIGHLIGHTER_BASE_WIDTH),
                                ),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.HIGHLIGHTER
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.HIGHLIGHTER,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.HIGHLIGHTER,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }
                Box {
                    EraserToggleButton(
                        isSelected = selectedTool == Tool.ERASER,
                        enabled = isEditingEnabled,
                        onToggle = {
                            activeToolPanel = null
                            toolbarState.onBrushChange(
                                toggleEraser(brush, toolbarState.lastNonEraserTool),
                            )
                        },
                        onLongPress = {
                            if (isEditingEnabled) {
                                isColorPickerVisible = false
                                activeToolPanel = ToolPanelType.ERASER
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = activeToolPanel == ToolPanelType.ERASER,
                        onDismissRequest = { activeToolPanel = null },
                    ) {
                        ToolSettingsDialog(
                            panelType = ToolPanelType.ERASER,
                            brush = brush,
                            onDismiss = { activeToolPanel = null },
                            onBrushChange = toolbarState.onBrushChange,
                        )
                    }
                }

                ToolbarDivider()

                Box {
                    PaletteRow(
                        selectedColor = brush.color,
                        enabled = isEditingEnabled,
                        onColorSelected = { color ->
                            activeToolPanel = null
                            onColorSelected(color)
                        },
                        onColorLongPress = { color ->
                            if (isEditingEnabled) {
                                activeToolPanel = null
                                colorPickerInput = color
                                isColorPickerVisible = true
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = isColorPickerVisible,
                        onDismissRequest = { isColorPickerVisible = false },
                    ) {
                        ColorPickerDialog(
                            initialValue = colorPickerInput,
                            currentBrush = brush,
                            onDismiss = { isColorPickerVisible = false },
                            onApply = { appliedColor ->
                                isColorPickerVisible = false
                                onColorSelected(appliedColor)
                            },
                        )
                    }
                }

                ToolbarDivider()

                IconButton(
                    onClick = topBarState.onNavigatePrevious,
                    enabled = topBarState.canNavigatePrevious,
                    modifier = Modifier.semantics { contentDescription = "Previous page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = null,
                        tint = if (topBarState.canNavigatePrevious) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                IconButton(
                    onClick = topBarState.onNavigateNext,
                    enabled = topBarState.canNavigateNext,
                    modifier = Modifier.semantics { contentDescription = "Next page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = if (topBarState.canNavigateNext) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
                Text(
                    text = "$currentPageNumber/${topBarState.totalPages}",
                    color = NOTEWISE_ICON,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.semantics { contentDescription = pageCounterDescription },
                )
                // Outline button for PDF documents
                if (topBarState.isPdfDocument) {
                    IconButton(
                        onClick = topBarState.onOpenOutline,
                        modifier = Modifier.semantics { contentDescription = "Table of contents" },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = NOTEWISE_ICON,
                        )
                    }
                }
                IconButton(
                    onClick = topBarState.onCreatePage,
                    enabled = isEditingEnabled,
                    modifier = Modifier.semantics { contentDescription = "New page" },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
                    )
                }
            }

            ToolbarGroup(
                contentDescription = "Mode controls",
                modifier = Modifier.width(NOTEWISE_RIGHT_GROUP_WIDTH_DP.dp),
            ) {
                IconButton(
                    onClick = topBarState.onToggleReadOnly,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (topBarState.isReadOnly) {
                                    "Edit mode"
                                } else {
                                    "View mode"
                                }
                        },
                ) {
                    Icon(
                        imageVector =
                            if (topBarState.isReadOnly) {
                                Icons.Filled.Edit
                            } else {
                                Icons.Filled.Visibility
                            },
                        contentDescription = null,
                        tint = NOTEWISE_ICON,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun RowScope.NoteTitleEditor(
    noteTitle: String,
    titleDraft: String,
    isEditing: Boolean,
    isEditingEnabled: Boolean,
    onTitleDraftChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onCommit: () -> Unit,
) {
    if (isEditing && isEditingEnabled) {
        OutlinedTextField(
            value = titleDraft,
            onValueChange = onTitleDraftChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(TITLE_INPUT_TEST_TAG)
                    .semantics { contentDescription = "Note title input" },
            textStyle = MaterialTheme.typography.titleSmall,
        )
    } else {
        val displayTitle = noteTitle.ifBlank { "Untitled note" }
        TextButton(
            onClick = onStartEditing,
            enabled = isEditingEnabled,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Note title" },
        ) {
            Text(
                text = displayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isEditingEnabled) NOTEWISE_ICON else NOTEWISE_ICON_MUTED,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolToggleButton(
    visuals: ToolButtonVisuals,
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val containerColor =
        if (isSelected) {
            NOTEWISE_SELECTED
        } else {
            Color.Transparent
        }
    val contentColor = NOTEWISE_ICON
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .size(TOOLBAR_TOUCH_TARGET_SIZE_DP.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isSelected) 0.dp else UNSELECTED_BORDER_WIDTH_DP.dp,
                    color = NOTEWISE_STROKE,
                    shape = CircleShape,
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ).semantics { contentDescription = visuals.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = visuals.icon,
            contentDescription = null,
            tint = contentColor,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EraserToggleButton(
    isSelected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selectedColor = NOTEWISE_SELECTED
    val defaultColor = Color.Transparent
    val containerColor = if (isSelected) selectedColor else defaultColor

    Box(
        modifier =
            Modifier
                .size(ERASER_BUTTON_SIZE_DP.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(
                    width = if (isSelected) 0.dp else UNSELECTED_BORDER_WIDTH_DP.dp,
                    color = NOTEWISE_STROKE,
                    shape = CircleShape,
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = onLongPress,
                ).semantics { contentDescription = "Eraser" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AutoFixOff,
            contentDescription = null,
            tint = NOTEWISE_ICON,
        )
    }
}

@Composable
private fun PaletteRow(
    selectedColor: String,
    enabled: Boolean,
    onColorSelected: (String) -> Unit,
    onColorLongPress: (String) -> Unit,
) {
    val normalizedSelected = normalizeHexColor(selectedColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DEFAULT_PALETTE.forEach { color ->
            val normalizedPaletteColor = normalizeHexColor(color).takeLast(HEX_COLOR_LENGTH_RGB - 1)
            PaletteSwatch(
                hexColor = color,
                isSelected = normalizedSelected == normalizedPaletteColor,
                enabled = enabled,
                onClick = { onColorSelected(color) },
                onLongPress = { onColorLongPress(color) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaletteSwatch(
    hexColor: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colorInt =
        runCatching { android.graphics.Color.parseColor(normalizeHexColor(hexColor)) }
            .getOrDefault(android.graphics.Color.BLACK)
    val color = Color(colorInt)
    val borderWidth = if (isSelected) SELECTED_BORDER_WIDTH_DP.dp else UNSELECTED_BORDER_WIDTH_DP.dp
    val borderColor =
        if (isSelected) {
            NOTEWISE_SELECTED
        } else {
            NOTEWISE_STROKE.copy(alpha = UNSELECTED_BORDER_ALPHA)
        }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .size(PALETTE_SWATCH_SIZE_DP.dp)
                .clip(CircleShape)
                .background(color)
                .border(width = borderWidth, color = borderColor, shape = CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongPress,
                ).semantics {
                    contentDescription = "Brush color ${paletteColorName(hexColor)}"
                },
    )
}

@Composable
private fun ToolbarGroup(
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(TOOLBAR_GROUP_CORNER_RADIUS_DP.dp),
        color = NOTEWISE_PILL,
        modifier =
            modifier
                .height(TOOLBAR_ROW_HEIGHT_DP.dp)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                },
        border = BorderStroke(1.dp, NOTEWISE_STROKE.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = TOOLBAR_GROUP_HORIZONTAL_PADDING_DP.dp,
                    vertical = TOOLBAR_GROUP_VERTICAL_PADDING_DP.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ToolbarDivider() {
    HorizontalDivider(
        modifier =
            Modifier
                .padding(horizontal = TOOLBAR_DIVIDER_HORIZONTAL_PADDING_DP.dp)
                .width(1.dp)
                .height(TOOLBAR_DIVIDER_HEIGHT_DP.dp),
        thickness = 1.dp,
        color = NOTEWISE_STROKE.copy(alpha = 0.65f),
    )
}

@Composable
private fun ToolSettingsDialog(
    panelType: ToolPanelType,
    brush: Brush,
    onDismiss: () -> Unit,
    onBrushChange: (Brush) -> Unit,
) {
    val title =
        when (panelType) {
            ToolPanelType.PEN -> "Pen settings"
            ToolPanelType.HIGHLIGHTER -> "Highlighter settings"
            ToolPanelType.ERASER -> "Eraser options"
        }

    Card(
        modifier =
            Modifier
                .widthIn(min = TOOL_SETTINGS_PANEL_MIN_WIDTH_DP.dp)
                .padding(TOOL_SETTINGS_PANEL_OFFSET_DP.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(
                        horizontal = TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP.dp,
                        vertical = TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(TOOL_SETTINGS_PANEL_ITEM_SPACING_DP.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            when (panelType) {
                ToolPanelType.PEN -> {
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        onBrushChange = onBrushChange,
                    )
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Smoothing", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = smoothing,
                        onValueChange = { level ->
                            onBrushChange(applySmoothingLevel(brush, level))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val taperStrength = resolveEndTaperStrength(brush)
                    Text(text = "End taper", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = taperStrength,
                        onValueChange = { strength ->
                            onBrushChange(applyEndTaperStrength(brush, strength))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                }

                ToolPanelType.HIGHLIGHTER -> {
                    BrushSizeControl(
                        brush = brush,
                        enabled = true,
                        onBrushChange = onBrushChange,
                    )
                    val opacity = resolveOpacity(brush)
                    Text(text = "Opacity", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = opacity,
                        onValueChange = { value ->
                            onBrushChange(
                                brush.copy(
                                    color = applyOpacity(brush.color, value),
                                ),
                            )
                        },
                        valueRange = HIGHLIGHTER_OPACITY_MIN..HIGHLIGHTER_OPACITY_MAX,
                    )
                    val smoothing = resolveSmoothingLevel(brush)
                    Text(text = "Smoothing", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = smoothing,
                        onValueChange = { level ->
                            onBrushChange(applySmoothingLevel(brush, level))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                    val taperStrength = resolveEndTaperStrength(brush)
                    Text(text = "End taper", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = taperStrength,
                        onValueChange = { strength ->
                            onBrushChange(applyEndTaperStrength(brush, strength))
                        },
                        valueRange = 0f..1f,
                        steps = TOOL_SETTINGS_DIALOG_SLIDER_STEPS,
                    )
                }

                ToolPanelType.ERASER -> {
                    Text(text = "Stroke eraser", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Erases entire strokes until segment eraser support is added.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun BrushSizeControl(
    brush: Brush,
    enabled: Boolean,
    onBrushChange: (Brush) -> Unit,
) {
    val normalizedValue =
        ((brush.baseWidth - BRUSH_SIZE_MIN) / (BRUSH_SIZE_MAX - BRUSH_SIZE_MIN))
            .coerceIn(0f, 1f)
    val indicatorSize =
        (brush.baseWidth * BRUSH_SIZE_INDICATOR_SCALE)
            .coerceIn(BRUSH_SIZE_INDICATOR_MIN_DP, BRUSH_SIZE_INDICATOR_MAX_DP)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Brush size",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(BRUSH_SIZE_SLIDER_WIDTH_DP.dp),
        )
        Box(
            modifier =
                Modifier
                    .size(indicatorSize.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
        )
    }
    Slider(
        value = normalizedValue,
        onValueChange = { ratio ->
            val baseWidth =
                BRUSH_SIZE_MIN + (BRUSH_SIZE_MAX - BRUSH_SIZE_MIN) * ratio.coerceIn(0f, 1f)
            onBrushChange(brush.copy(baseWidth = baseWidth))
        },
        enabled = enabled,
        valueRange = 0f..1f,
        steps = BRUSH_SIZE_STEPS,
    )
}

@Composable
private fun ColorPickerDialog(
    initialValue: String,
    currentBrush: Brush,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var hexInput by remember(initialValue) { mutableStateOf(normalizeHexColor(initialValue)) }
    val normalizedColor = normalizeHexColor(hexInput)
    val supportsApply = HEX_COLOR_REGEX.matches(normalizedColor)
    val previewColor =
        runCatching {
            Color(android.graphics.Color.parseColor(normalizedColor))
        }.getOrDefault(Color.Black)

    Card(
        modifier =
            Modifier
                .widthIn(min = TOOL_SETTINGS_PANEL_MIN_WIDTH_DP.dp)
                .padding(TOOL_SETTINGS_PANEL_OFFSET_DP.dp),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = TOOL_SETTINGS_PANEL_HORIZONTAL_PADDING_DP.dp,
                    vertical = TOOL_SETTINGS_PANEL_VERTICAL_PADDING_DP.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(TOOL_SETTINGS_PANEL_ITEM_SPACING_DP.dp),
        ) {
            Text(
                text = "Custom brush color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { candidate ->
                    hexInput = candidate.trim()
                },
                label = { Text("Hex color") },
                singleLine = true,
                modifier = Modifier.width(COLOR_PICKER_TEXT_FIELD_WIDTH_DP.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ITEM_SPACING_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Preview", style = MaterialTheme.typography.bodyMedium)
                Box(
                    modifier =
                        Modifier
                            .size(PALETTE_SWATCH_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(previewColor),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val normalized = normalizeHexColor(hexInput)
                        val finalColor =
                            if (currentBrush.tool == Tool.HIGHLIGHTER) {
                                applyOpacity(normalized, resolveOpacity(currentBrush))
                            } else {
                                normalized
                            }
                        onApply(finalColor)
                    },
                    enabled = supportsApply,
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
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

        // Thumbnail strip at bottom for PDF documents
        if (contentState.thumbnails.isNotEmpty()) {
            ThumbnailStrip(
                thumbnails = contentState.thumbnails,
                currentPageIndex = contentState.currentPageIndex,
                onPageSelected = contentState.onPageSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FixedPageBackground(contentState: NoteEditorContentState) {
    val pageWidth = contentState.pageWidth
    val pageHeight = contentState.pageHeight
    val transform = contentState.viewTransform

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
@Suppress("UNUSED_PARAMETER", "LongParameterList")
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

private fun resolveSmoothingLevel(brush: Brush): Float = brush.smoothingLevel.coerceIn(0f, 1f)

private fun applySmoothingLevel(
    brush: Brush,
    level: Float,
): Brush = brush.copy(smoothingLevel = level.coerceIn(0f, 1f))

private fun resolveEndTaperStrength(brush: Brush): Float = brush.endTaperStrength.coerceIn(0f, 1f)

private fun applyEndTaperStrength(
    brush: Brush,
    strength: Float,
): Brush = brush.copy(endTaperStrength = strength.coerceIn(0f, 1f))

private fun resolveOpacity(brush: Brush): Float {
    val parsedColor =
        runCatching {
            android.graphics.Color.parseColor(normalizeHexColor(brush.color))
        }.getOrNull()
    val alpha =
        if (parsedColor != null) {
            android.graphics.Color.alpha(parsedColor) / COLOR_ALPHA_MAX_FLOAT
        } else {
            HIGHLIGHTER_OPACITY_MAX
        }
    return alpha.coerceIn(HIGHLIGHTER_OPACITY_MIN, HIGHLIGHTER_OPACITY_MAX)
}

private fun applyOpacity(
    colorHex: String,
    opacity: Float,
): String {
    val normalizedColor = normalizeHexColor(colorHex)
    val parsedColor =
        runCatching {
            android.graphics.Color.parseColor(normalizedColor)
        }.getOrNull() ?: return normalizedColor
    val alpha =
        (opacity.coerceIn(0f, 1f) * COLOR_ALPHA_MAX_FLOAT)
            .roundToInt()
            .coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
    return String.format(
        Locale.US,
        "#%02X%02X%02X%02X",
        alpha,
        android.graphics.Color.red(parsedColor),
        android.graphics.Color.green(parsedColor),
        android.graphics.Color.blue(parsedColor),
    )
}

private fun normalizeHexColor(rawColor: String): String {
    val trimmed = rawColor.trim().uppercase(Locale.US)
    if (trimmed.isBlank()) {
        return "#000000"
    }
    val prefixed = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return when {
        HEX_COLOR_REGEX.matches(prefixed) -> {
            prefixed
        }

        prefixed.matches(Regex("^#[0-9A-F]{3}$")) -> {
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}"
        }

        prefixed.matches(Regex("^#[0-9A-F]{4}$")) -> {
            "#${prefixed[1]}${prefixed[1]}${prefixed[2]}${prefixed[2]}${prefixed[3]}${prefixed[3]}${prefixed[4]}${prefixed[4]}"
        }

        else -> {
            "#000000"
        }
    }
}

private fun stripAlpha(colorHex: String): String {
    val normalized = normalizeHexColor(colorHex)
    return if (normalized.length == 9) {
        "#${normalized.takeLast(6)}"
    } else {
        normalized
    }
}

private fun toggleEraser(
    brush: Brush,
    lastNonEraserTool: Tool,
): Brush =
    if (brush.tool == Tool.ERASER) {
        brush.copy(tool = lastNonEraserTool)
    } else {
        brush.copy(tool = Tool.ERASER)
    }

private fun paletteColorName(hexColor: String): String {
    val normalized = normalizeHexColor(hexColor).takeLast(HEX_COLOR_LENGTH_RGB - 1)
    return when (normalized) {
        "111111" -> "black"
        "1E88E5" -> "blue"
        "E53935" -> "red"
        "43A047" -> "green"
        "8E24AA" -> "purple"
        else -> "custom"
    }
}
