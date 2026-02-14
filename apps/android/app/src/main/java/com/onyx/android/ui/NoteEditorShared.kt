@file:Suppress("FunctionName", "LongParameterList", "TooManyFunctions")

package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.AsyncPdfPipeline
import com.onyx.android.pdf.DEFAULT_PDF_TILE_SIZE_PX
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.PdfVisiblePageRect
import com.onyx.android.pdf.createPdfTileCache
import com.onyx.android.pdf.maxTileIndexForPage
import com.onyx.android.pdf.pageRectToTileRange
import com.onyx.android.pdf.tileKeysForRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val PDF_RENDER_BUCKET_BASE = 1f
private const val PDF_RENDER_BUCKET_HIGH = 2f
private const val PDF_RENDER_BUCKET_MAX = 4f
private const val MIN_ZOOM_FACTOR_OF_FIT = 0.75f
private const val MAX_ZOOM_FACTOR_OF_FIT = 6f
private const val DEFAULT_FIT_ZOOM = 1f
private val PDF_RENDER_SCALE_BUCKETS =
    floatArrayOf(
        PDF_RENDER_BUCKET_BASE,
        PDF_RENDER_BUCKET_HIGH,
        PDF_RENDER_BUCKET_MAX,
    )
private const val PDF_RENDER_MAX_PIXELS = 16_000_000f
private const val PDF_RENDER_MIN_SCALE = 0.5f
private const val PDF_BUCKET_SWITCH_UP_MULTIPLIER = 1.1f
private const val PDF_BUCKET_SWITCH_DOWN_MULTIPLIER = 0.9f
private const val PDF_TILE_PREFETCH_DISTANCE = 1

internal data class ZoomLimits(
    val minZoom: Float,
    val maxZoom: Float,
    val fitZoom: Float,
)

internal fun computeZoomLimits(
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ZoomLimits {
    if (!hasValidDimensions(pageWidth, pageHeight, viewportWidth, viewportHeight)) {
        val fallbackFitZoom = DEFAULT_FIT_ZOOM
        return ZoomLimits(
            minZoom = fallbackFitZoom * MIN_ZOOM_FACTOR_OF_FIT,
            maxZoom = fallbackFitZoom * MAX_ZOOM_FACTOR_OF_FIT,
            fitZoom = fallbackFitZoom,
        )
    }
    val fitZoom = min(viewportWidth / pageWidth, viewportHeight / pageHeight)
    return ZoomLimits(
        minZoom = fitZoom * MIN_ZOOM_FACTOR_OF_FIT,
        maxZoom = fitZoom * MAX_ZOOM_FACTOR_OF_FIT,
        fitZoom = fitZoom,
    )
}

@Composable
internal fun DisposePdfRenderer(pdfRenderer: PdfDocumentRenderer?) {
    DisposableEffect(pdfRenderer) {
        onDispose {
            pdfRenderer?.close()
        }
    }
}

@Composable
internal fun rememberTransformState(
    viewTransform: ViewTransform,
    zoomLimits: ZoomLimits,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    onTransformChange: (ViewTransform) -> Unit,
): TransformableState =
    rememberTransformableState { zoomChange, panChange, _ ->
        val transformed =
            viewTransform.copy(
                zoom = (viewTransform.zoom * zoomChange).coerceIn(zoomLimits.minZoom, zoomLimits.maxZoom),
                panX = viewTransform.panX + panChange.x,
                panY = viewTransform.panY + panChange.y,
            )
        onTransformChange(
            constrainTransformToViewport(
                transform = transformed,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            ),
        )
    }

internal data class TransformGesture(
    val zoomChange: Float,
    val panChangeX: Float,
    val panChangeY: Float,
    val centroidX: Float,
    val centroidY: Float,
)

internal fun applyTransformGesture(
    current: ViewTransform,
    gesture: TransformGesture,
    zoomLimits: ZoomLimits,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ViewTransform {
    val currentZoom = current.zoom
    val targetZoom = (currentZoom * gesture.zoomChange).coerceIn(zoomLimits.minZoom, zoomLimits.maxZoom)
    val appliedZoomChange =
        if (currentZoom > 0f) {
            targetZoom / currentZoom
        } else {
            1f
        }
    val anchoredPanX = gesture.centroidX - (gesture.centroidX - current.panX) * appliedZoomChange
    val anchoredPanY = gesture.centroidY - (gesture.centroidY - current.panY) * appliedZoomChange
    val transformed =
        current.copy(
            zoom = targetZoom,
            panX = anchoredPanX + gesture.panChangeX,
            panY = anchoredPanY + gesture.panChangeY,
        )
    return constrainTransformToViewport(
        transform = transformed,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
}

internal fun fitTransformToViewport(
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    zoomLimits: ZoomLimits = computeZoomLimits(pageWidth, pageHeight, viewportWidth, viewportHeight),
): ViewTransform {
    if (!hasValidDimensions(pageWidth, pageHeight, viewportWidth, viewportHeight)) {
        return ViewTransform.DEFAULT
    }
    val fitZoom = zoomLimits.fitZoom.coerceIn(zoomLimits.minZoom, zoomLimits.maxZoom)
    val contentWidth = pageWidth * fitZoom
    val contentHeight = pageHeight * fitZoom
    return ViewTransform(
        zoom = fitZoom,
        panX = (viewportWidth - contentWidth) / 2f,
        panY = (viewportHeight - contentHeight) / 2f,
    )
}

internal fun constrainTransformToViewport(
    transform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ViewTransform {
    if (!hasValidDimensions(pageWidth, pageHeight, viewportWidth, viewportHeight)) {
        return transform
    }
    val contentWidth = pageWidth * transform.zoom
    val contentHeight = pageHeight * transform.zoom

    val constrainedPanX =
        if (contentWidth <= viewportWidth) {
            (viewportWidth - contentWidth) / 2f
        } else {
            transform.panX.coerceIn(viewportWidth - contentWidth, 0f)
        }
    val constrainedPanY =
        if (contentHeight <= viewportHeight) {
            (viewportHeight - contentHeight) / 2f
        } else {
            transform.panY.coerceIn(viewportHeight - contentHeight, 0f)
        }

    return transform.copy(panX = constrainedPanX, panY = constrainedPanY)
}

private fun hasValidDimensions(
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Boolean = pageWidth > 0f && pageHeight > 0f && viewportWidth > 0f && viewportHeight > 0f

@Composable
internal fun rememberPdfBitmap(
    isPdfPage: Boolean,
    currentPage: PageEntity?,
    pdfRenderer: PdfDocumentRenderer?,
    viewZoom: Float,
): android.graphics.Bitmap? {
    var pdfBitmap by remember(currentPage?.pageId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var previousScaleBucket by remember(currentPage?.pageId) { mutableStateOf<Float?>(null) }
    val pageWidth = currentPage?.width ?: 0f
    val pageHeight = currentPage?.height ?: 0f
    val scaleBucket = zoomToRenderScaleBucket(viewZoom, previousScaleBucket)
    SideEffect {
        previousScaleBucket = scaleBucket
    }
    val renderScale = resolvePdfRenderScale(scaleBucket, pageWidth, pageHeight)
    LaunchedEffect(currentPage?.pageId, isPdfPage, pdfRenderer, renderScale) {
        if (!isPdfPage) {
            pdfBitmap = null
            return@LaunchedEffect
        }
        val renderer = pdfRenderer ?: return@LaunchedEffect
        val pageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        pdfBitmap =
            withContext(Dispatchers.Default) {
                renderer.renderPage(pageIndex, renderScale)
            }
    }
    return pdfBitmap
}

internal data class PdfTileRenderState(
    val tiles: Map<PdfTileKey, android.graphics.Bitmap>,
    val scaleBucket: Float?,
    val tileSizePx: Int,
)

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun rememberPdfTiles(
    isPdfPage: Boolean,
    currentPage: PageEntity?,
    pdfRenderer: PdfDocumentRenderer?,
    viewTransform: ViewTransform,
    viewportSize: IntSize,
    pageWidth: Float,
    pageHeight: Float,
): PdfTileRenderState {
    val appContext = LocalContext.current.applicationContext
    var tiles by
        remember(currentPage?.pageId) {
            mutableStateOf<Map<PdfTileKey, android.graphics.Bitmap>>(emptyMap())
        }
    var previousScaleBucket by remember(currentPage?.pageId) { mutableStateOf<Float?>(null) }
    val scaleBucket = zoomToRenderScaleBucket(viewTransform.zoom, previousScaleBucket)
    SideEffect {
        previousScaleBucket = scaleBucket
    }
    val tileCache = remember(pdfRenderer) { pdfRenderer?.let { createPdfTileCache(appContext) } }
    val pipeline =
        remember(pdfRenderer, tileCache) {
            if (pdfRenderer != null && tileCache != null) {
                AsyncPdfPipeline(pdfRenderer, tileCache)
            } else {
                null
            }
        }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(pipeline, tileCache) {
        onDispose {
            if (pipeline != null) {
                coroutineScope.launch {
                    pipeline.cancelAll()
                    tileCache?.clear()
                }
            }
        }
    }

    LaunchedEffect(currentPage?.pageId) {
        tiles = emptyMap()
    }

    LaunchedEffect(pipeline, currentPage?.pageId) {
        val activePipeline = pipeline ?: return@LaunchedEffect
        val activePageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        activePipeline.tileUpdates.collect { update ->
            if (update.key.pageIndex == activePageIndex && !update.bitmap.isRecycled) {
                tiles = tiles + (update.key to update.bitmap)
            }
        }
    }

    LaunchedEffect(
        pipeline,
        tileCache,
        isPdfPage,
        currentPage?.pageId,
        viewTransform.zoom,
        viewTransform.panX,
        viewTransform.panY,
        viewportSize,
        scaleBucket,
    ) {
        if (!isPdfPage) {
            return@LaunchedEffect
        }
        val activePipeline = pipeline ?: return@LaunchedEffect
        val activeTileCache = tileCache ?: return@LaunchedEffect
        val pageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        if (viewportSize.width <= 0 || viewportSize.height <= 0) {
            return@LaunchedEffect
        }
        val pageRect =
            visiblePageRect(
                viewTransform = viewTransform,
                viewportSize = viewportSize,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
            ) ?: return@LaunchedEffect
        val range =
            pageRectToTileRange(
                pageRect = pageRect,
                scaleBucket = scaleBucket,
                tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
            )
        if (!range.isValid) {
            return@LaunchedEffect
        }
        val (maxTileX, maxTileY) =
            maxTileIndexForPage(
                pageWidthPoints = pageWidth,
                pageHeightPoints = pageHeight,
                scaleBucket = scaleBucket,
                tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
            )
        val requestedRange =
            range
                .withPrefetch(PDF_TILE_PREFETCH_DISTANCE)
                .clampedToPage(pageMaxTileX = maxTileX, pageMaxTileY = maxTileY)
        if (!requestedRange.isValid) {
            return@LaunchedEffect
        }
        tiles = activeTileCache.snapshotForPage(pageIndex)
        activePipeline.requestTiles(
            tileKeysForRange(
                pageIndex = pageIndex,
                scaleBucket = scaleBucket,
                tileRange = requestedRange,
            ),
        )
    }

    return PdfTileRenderState(
        tiles = tiles.filterValues { bitmap -> !bitmap.isRecycled },
        scaleBucket = scaleBucket,
        tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
    )
}

internal fun resolvePdfRenderScale(
    bucketedScale: Float,
    pageWidth: Float,
    pageHeight: Float,
): Float {
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return bucketedScale
    }
    val maxScaleForPage =
        sqrt(PDF_RENDER_MAX_PIXELS / (pageWidth * pageHeight))
            .coerceAtLeast(PDF_RENDER_MIN_SCALE)
    return bucketedScale.coerceAtMost(maxScaleForPage)
}

internal fun zoomToRenderScaleBucket(
    zoom: Float,
    previousBucket: Float? = null,
): Float {
    val clampedZoom = zoom.coerceIn(ViewTransform.MIN_ZOOM, ViewTransform.MAX_ZOOM)
    val previousIndex =
        previousBucket?.let { bucket ->
            val exactIndex = PDF_RENDER_SCALE_BUCKETS.indexOfFirst { value -> value == bucket }
            if (exactIndex >= 0) {
                exactIndex
            } else {
                val firstAtOrAbove = PDF_RENDER_SCALE_BUCKETS.indexOfFirst { value -> bucket <= value }
                if (firstAtOrAbove >= 0) firstAtOrAbove else PDF_RENDER_SCALE_BUCKETS.lastIndex
            }
        }
    if (previousIndex == null) {
        return PDF_RENDER_SCALE_BUCKETS.firstOrNull { bucket -> clampedZoom <= bucket }
            ?: PDF_RENDER_SCALE_BUCKETS.last()
    }

    var selectedIndex = previousIndex
    while (
        selectedIndex < PDF_RENDER_SCALE_BUCKETS.lastIndex &&
        clampedZoom >= PDF_RENDER_SCALE_BUCKETS[selectedIndex + 1] * PDF_BUCKET_SWITCH_UP_MULTIPLIER
    ) {
        selectedIndex++
    }
    while (
        selectedIndex > 0 &&
        clampedZoom < PDF_RENDER_SCALE_BUCKETS[selectedIndex] * PDF_BUCKET_SWITCH_DOWN_MULTIPLIER
    ) {
        selectedIndex--
    }
    return PDF_RENDER_SCALE_BUCKETS[selectedIndex]
}

@Suppress("ComplexCondition", "ReturnCount")
internal fun visiblePageRect(
    viewTransform: ViewTransform,
    viewportSize: IntSize,
    pageWidth: Float,
    pageHeight: Float,
): PdfVisiblePageRect? {
    if (pageWidth <= 0f || pageHeight <= 0f || viewportSize.width <= 0 || viewportSize.height <= 0) {
        return null
    }
    val leftPage = viewTransform.screenToPageX(0f)
    val rightPage = viewTransform.screenToPageX(viewportSize.width.toFloat())
    val topPage = viewTransform.screenToPageY(0f)
    val bottomPage = viewTransform.screenToPageY(viewportSize.height.toFloat())
    val clampedLeft = max(0f, min(leftPage, rightPage)).coerceAtMost(pageWidth)
    val clampedRight = min(pageWidth, max(leftPage, rightPage)).coerceAtLeast(0f)
    val clampedTop = max(0f, min(topPage, bottomPage)).coerceAtMost(pageHeight)
    val clampedBottom = min(pageHeight, max(topPage, bottomPage)).coerceAtLeast(0f)
    if (clampedLeft >= clampedRight || clampedTop >= clampedBottom) {
        return null
    }
    return PdfVisiblePageRect(
        left = clampedLeft,
        top = clampedTop,
        right = clampedRight,
        bottom = clampedBottom,
    )
}
