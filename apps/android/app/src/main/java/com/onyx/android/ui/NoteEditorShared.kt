@file:Suppress("FunctionName", "LongParameterList", "TooManyFunctions")

package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.ink.ui.TransformGesture
import com.onyx.android.pdf.AsyncPdfPipeline
import com.onyx.android.pdf.DEFAULT_PDF_TILE_SIZE_PX
import com.onyx.android.pdf.PdfDocumentRenderer
import com.onyx.android.pdf.PdfPipelineConfig
import com.onyx.android.pdf.PdfTileKey
import com.onyx.android.pdf.PdfVisiblePageRect
import com.onyx.android.pdf.ValidatingTile
import com.onyx.android.pdf.createPdfTileCache
import com.onyx.android.pdf.maxTileIndexForPage
import com.onyx.android.pdf.pageRectToTileRange
import com.onyx.android.pdf.tileKeysForRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
private const val PDF_TILE_PREFETCH_DISTANCE_DEFAULT = 1
private const val TILE_REQUEST_FRAME_DEBOUNCE_MS = 16L

private const val OVERSCROLL_MAX_DISTANCE_PX = 150f
private const val OVERSCROLL_RESISTANCE_FACTOR = 0.4f
private const val SNAP_BACK_DURATION_MS = 300L
private const val SNAP_BACK_FRAME_DELAY_MS = 16L

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

internal fun applyRubberBandTransform(
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
            applyRubberBandResistance(
                value = transform.panX,
                center = (viewportWidth - contentWidth) / 2f,
            )
        } else {
            applyRubberBandResistanceToBounds(
                value = transform.panX,
                min = viewportWidth - contentWidth,
                max = 0f,
            )
        }
    val constrainedPanY =
        if (contentHeight <= viewportHeight) {
            applyRubberBandResistance(
                value = transform.panY,
                center = (viewportHeight - contentHeight) / 2f,
            )
        } else {
            applyRubberBandResistanceToBounds(
                value = transform.panY,
                min = viewportHeight - contentHeight,
                max = 0f,
            )
        }

    return transform.copy(panX = constrainedPanX, panY = constrainedPanY)
}

private fun applyRubberBandResistance(
    value: Float,
    center: Float,
): Float {
    val offset = value - center
    val resistance = OVERSCROLL_RESISTANCE_FACTOR
    val dampedOffset = offset * resistance / (1 + kotlin.math.abs(offset) / OVERSCROLL_MAX_DISTANCE_PX)
    return center + dampedOffset.coerceIn(-OVERSCROLL_MAX_DISTANCE_PX, OVERSCROLL_MAX_DISTANCE_PX)
}

private fun applyRubberBandResistanceToBounds(
    value: Float,
    min: Float,
    max: Float,
): Float {
    if (value in min..max) return value
    val overscroll =
        when {
            value < min -> min - value
            else -> value - max
        }
    val resistance = OVERSCROLL_RESISTANCE_FACTOR
    val dampedOverscroll = overscroll * resistance / (1 + overscroll / OVERSCROLL_MAX_DISTANCE_PX)
    return when {
        value < min -> min - dampedOverscroll.coerceAtMost(OVERSCROLL_MAX_DISTANCE_PX)
        else -> max + dampedOverscroll.coerceAtMost(OVERSCROLL_MAX_DISTANCE_PX)
    }
}

internal fun needsRubberBandSnapBack(
    transform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Boolean {
    if (!hasValidDimensions(pageWidth, pageHeight, viewportWidth, viewportHeight)) {
        return false
    }
    val contentWidth = pageWidth * transform.zoom
    val contentHeight = pageHeight * transform.zoom
    val xOutOfBounds =
        if (contentWidth <= viewportWidth) {
            val center = (viewportWidth - contentWidth) / 2f
            kotlin.math.abs(transform.panX - center) > 1f
        } else {
            transform.panX < viewportWidth - contentWidth || transform.panX > 0f
        }
    val yOutOfBounds =
        if (contentHeight <= viewportHeight) {
            val center = (viewportHeight - contentHeight) / 2f
            kotlin.math.abs(transform.panY - center) > 1f
        } else {
            transform.panY < viewportHeight - contentHeight || transform.panY > 0f
        }
    return xOutOfBounds || yOutOfBounds
}

internal fun computeFocalPointPreservingTransform(
    previousTransform: ViewTransform,
    previousViewportWidth: Float,
    previousViewportHeight: Float,
    newViewportWidth: Float,
    newViewportHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    zoomLimits: ZoomLimits,
): ViewTransform {
    if (!hasValidDimensions(pageWidth, pageHeight, newViewportWidth, newViewportHeight)) {
        return previousTransform
    }
    val previousCenterScreenX = previousViewportWidth / 2f
    val previousCenterScreenY = previousViewportHeight / 2f
    val focalPageX = previousTransform.screenToPageX(previousCenterScreenX)
    val focalPageY = previousTransform.screenToPageY(previousCenterScreenY)
    val newCenterScreenX = newViewportWidth / 2f
    val newCenterScreenY = newViewportHeight / 2f
    val newPanX = newCenterScreenX - focalPageX * previousTransform.zoom
    val newPanY = newCenterScreenY - focalPageY * previousTransform.zoom
    val newZoom = previousTransform.zoom.coerceIn(zoomLimits.minZoom, zoomLimits.maxZoom)
    val unconstrained = previousTransform.copy(zoom = newZoom, panX = newPanX, panY = newPanY)
    return constrainTransformToViewport(
        transform = unconstrained,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        viewportWidth = newViewportWidth,
        viewportHeight = newViewportHeight,
    )
}

internal suspend fun animateSnapBackToValidBounds(
    currentTransform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    onUpdate: (ViewTransform) -> Unit,
) {
    val targetTransform =
        constrainTransformToViewport(
            transform = currentTransform,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    val startPanX = currentTransform.panX
    val startPanY = currentTransform.panY
    val targetPanX = targetTransform.panX
    val targetPanY = targetTransform.panY
    if (kotlin.math.abs(startPanX - targetPanX) < 1f && kotlin.math.abs(startPanY - targetPanY) < 1f) {
        return
    }
    val startTime = System.currentTimeMillis()
    var lastTransform = currentTransform
    while (true) {
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= SNAP_BACK_DURATION_MS) {
            onUpdate(targetTransform)
            break
        }
        val progress = (elapsed.toFloat() / SNAP_BACK_DURATION_MS).coerceIn(0f, 1f)
        val eased = easeOutCubic(progress)
        val currentPanX = startPanX + (targetPanX - startPanX) * eased
        val currentPanY = startPanY + (targetPanY - startPanY) * eased
        lastTransform = lastTransform.copy(panX = currentPanX, panY = currentPanY)
        onUpdate(lastTransform)
        kotlinx.coroutines.delay(SNAP_BACK_FRAME_DELAY_MS)
    }
}

private fun easeOutCubic(t: Float): Float {
    val t1 = t - 1f
    return t1 * t1 * t1 + 1f
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

@Composable
internal fun rememberPdfThumbnail(
    isPdfPage: Boolean,
    currentPage: PageEntity?,
    pdfRenderer: PdfDocumentRenderer?,
): android.graphics.Bitmap? {
    var pdfBitmap by remember(currentPage?.pageId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(currentPage?.pageId, isPdfPage, pdfRenderer) {
        if (!isPdfPage) {
            pdfBitmap = null
            return@LaunchedEffect
        }
        val renderer = pdfRenderer ?: return@LaunchedEffect
        val pageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        pdfBitmap =
            withContext(Dispatchers.Default) {
                renderer.renderThumbnail(pageIndex)
            }
    }
    return pdfBitmap
}

internal data class PdfTileRenderState(
    val tiles: Map<PdfTileKey, ValidatingTile>,
    val scaleBucket: Float?,
    val previousScaleBucket: Float?,
    val tileSizePx: Int,
    val crossfadeProgress: Float,
)

private const val PDF_CROSSFADE_DURATION_MS = 150
private const val PDF_CROSSFADE_FRAME_DELAY_MS = 16L

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
            mutableStateOf<Map<PdfTileKey, ValidatingTile>>(emptyMap())
        }
    // For hysteresis in bucket selection
    var hysteresisBucket by remember(currentPage?.pageId) { mutableStateOf<Float?>(null) }
    // For crossfade transition - tracks the bucket we're transitioning FROM
    var retainedPreviousBucket by remember(currentPage?.pageId) { mutableStateOf<Float?>(null) }
    // Track when bucket changed for crossfade timing
    var bucketChangeTimestamp by remember(currentPage?.pageId) { mutableLongStateOf(0L) }
    // Track crossfade progress (0f to 1f)
    var crossfadeProgress by remember(currentPage?.pageId) { mutableStateOf(1f) }

    val scaleBucket = zoomToRenderScaleBucket(viewTransform.zoom, hysteresisBucket)

    // Use derivedStateOf to compute visible tiles with previous bucket fallback
    val visibleTilesWithFallback by remember {
        derivedStateOf {
            val currentBucket = scaleBucket
            val previousBucket = retainedPreviousBucket

            if (previousBucket == null || previousBucket == currentBucket) {
                tiles
                    .filterKeys { it.scaleBucket == currentBucket }
                    .filterValues { it.isValid() }
            } else {
                val currentTiles =
                    tiles
                        .filterKeys {
                            it.scaleBucket == currentBucket
                        }.filterValues { it.isValid() }

                val currentPositions = currentTiles.keys.map { it.tileX to it.tileY }.toSet()

                val previousTiles =
                    tiles
                        .filterKeys {
                            it.scaleBucket == previousBucket &&
                                (it.tileX to it.tileY) !in currentPositions
                        }.filterValues { it.isValid() }

                currentTiles + previousTiles
            }
        }
    }

    // Detect scale bucket changes and update retained bucket for crossfade
    SideEffect {
        if (hysteresisBucket != null && hysteresisBucket != scaleBucket) {
            // Bucket changed - retain the old bucket for crossfade
            retainedPreviousBucket = hysteresisBucket
            bucketChangeTimestamp = System.currentTimeMillis()
            crossfadeProgress = 0f
        }
        hysteresisBucket = scaleBucket
    }

    // Animate crossfade progress and prune previous bucket tiles after crossfade completes
    LaunchedEffect(retainedPreviousBucket, bucketChangeTimestamp) {
        if (retainedPreviousBucket != null) {
            // Animate crossfade progress
            val startTime = bucketChangeTimestamp
            while (System.currentTimeMillis() - startTime < PDF_CROSSFADE_DURATION_MS) {
                val elapsed = System.currentTimeMillis() - startTime
                crossfadeProgress = (elapsed.toFloat() / PDF_CROSSFADE_DURATION_MS).coerceIn(0f, 1f)
                kotlinx.coroutines.delay(PDF_CROSSFADE_FRAME_DELAY_MS) // ~60fps
            }
            crossfadeProgress = 1f
            // Only clear if no new bucket change happened during delay
            if (System.currentTimeMillis() - bucketChangeTimestamp >= PDF_CROSSFADE_DURATION_MS) {
                retainedPreviousBucket = null
            }
        }
    }

    val tileCache = remember(pdfRenderer) { pdfRenderer?.let { createPdfTileCache(appContext) } }
    val pipelineConfig =
        remember {
            PdfPipelineConfig(
                maxInFlightRenders = 4,
                maxQueueSize = 32,
                prefetchRadius = PDF_TILE_PREFETCH_DISTANCE_DEFAULT,
            )
        }
    val pipeline =
        remember(pdfRenderer, tileCache, pipelineConfig) {
            if (pdfRenderer != null && tileCache != null) {
                AsyncPdfPipeline(
                    renderer = pdfRenderer,
                    cache = tileCache,
                    config = pipelineConfig,
                )
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
        retainedPreviousBucket = null
        crossfadeProgress = 1f
    }

    LaunchedEffect(pipeline, currentPage?.pageId) {
        val activePipeline = pipeline ?: return@LaunchedEffect
        val activePageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        activePipeline.tileUpdates.collect { update ->
            if (update.key.pageIndex == activePageIndex && update.tile.isValid()) {
                tiles = tiles + (update.key to update.tile)
            }
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(
        pipeline,
        tileCache,
        isPdfPage,
        currentPage?.pageId,
    ) {
        if (!isPdfPage) return@LaunchedEffect
        val activePipeline = pipeline ?: return@LaunchedEffect
        val activeTileCache = tileCache ?: return@LaunchedEffect
        val pageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect

        snapshotFlow {
            Triple(viewTransform, viewportSize, scaleBucket)
        }.debounce(TILE_REQUEST_FRAME_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { (transform, size, bucket) ->
                if (size.width <= 0 || size.height <= 0) return@collect
                val pageRect =
                    visiblePageRect(
                        viewTransform = transform,
                        viewportSize = size,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                    ) ?: return@collect
                val range =
                    pageRectToTileRange(
                        pageRect = pageRect,
                        scaleBucket = bucket,
                        tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
                    )
                if (!range.isValid) return@collect
                val (maxTileX, maxTileY) =
                    maxTileIndexForPage(
                        pageWidthPoints = pageWidth,
                        pageHeightPoints = pageHeight,
                        scaleBucket = bucket,
                        tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
                    )
                val prefetchDistance = activePipeline.prefetchRadius
                val requestedRange =
                    range
                        .withPrefetch(prefetchDistance)
                        .clampedToPage(pageMaxTileX = maxTileX, pageMaxTileY = maxTileY)
                if (!requestedRange.isValid) return@collect
                tiles =
                    activeTileCache
                        .snapshotForPageWithFallback(
                            pageIndex = pageIndex,
                            currentBucket = bucket,
                            previousBucket = retainedPreviousBucket,
                        )
                activePipeline.requestTiles(
                    tileKeysForRange(
                        pageIndex = pageIndex,
                        scaleBucket = bucket,
                        tileRange = requestedRange,
                    ),
                )
            }
    }

    return PdfTileRenderState(
        tiles = visibleTilesWithFallback,
        scaleBucket = scaleBucket,
        previousScaleBucket = retainedPreviousBucket,
        tileSizePx = DEFAULT_PDF_TILE_SIZE_PX,
        crossfadeProgress = crossfadeProgress,
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
