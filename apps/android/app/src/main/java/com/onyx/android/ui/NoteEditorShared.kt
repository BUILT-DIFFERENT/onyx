@file:Suppress("FunctionName")

package com.onyx.android.ui

import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.pdf.PdfRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

private const val PDF_RENDER_BUCKET_BASE = 1f
private const val PDF_RENDER_BUCKET_MID = 1.5f
private const val PDF_RENDER_BUCKET_HIGH = 2f
private const val PDF_RENDER_BUCKET_HIGHER = 3f
private const val PDF_RENDER_BUCKET_MAX = 4f
private val PDF_RENDER_SCALE_BUCKETS =
    floatArrayOf(
        PDF_RENDER_BUCKET_BASE,
        PDF_RENDER_BUCKET_MID,
        PDF_RENDER_BUCKET_HIGH,
        PDF_RENDER_BUCKET_HIGHER,
        PDF_RENDER_BUCKET_MAX,
    )
private const val PDF_RENDER_MAX_PIXELS = 16_000_000f
private const val PDF_RENDER_MIN_SCALE = 0.5f

@Composable
internal fun DisposePdfRenderer(pdfRenderer: PdfRenderer?) {
    DisposableEffect(pdfRenderer) {
        onDispose {
            pdfRenderer?.close()
        }
    }
}

@Composable
internal fun rememberTransformState(
    viewTransform: ViewTransform,
    onTransformChange: (ViewTransform) -> Unit,
): TransformableState =
    rememberTransformableState { zoomChange, panChange, _ ->
        onTransformChange(
            viewTransform.copy(
                zoom =
                    (viewTransform.zoom * zoomChange)
                        .coerceIn(ViewTransform.MIN_ZOOM, ViewTransform.MAX_ZOOM),
                panX = viewTransform.panX + panChange.x,
                panY = viewTransform.panY + panChange.y,
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
): ViewTransform {
    val currentZoom = current.zoom
    val targetZoom =
        (currentZoom * gesture.zoomChange).coerceIn(
            ViewTransform.MIN_ZOOM,
            ViewTransform.MAX_ZOOM,
        )
    val appliedZoomChange =
        if (currentZoom > 0f) {
            targetZoom / currentZoom
        } else {
            1f
        }
    val anchoredPanX = gesture.centroidX - (gesture.centroidX - current.panX) * appliedZoomChange
    val anchoredPanY = gesture.centroidY - (gesture.centroidY - current.panY) * appliedZoomChange
    return current.copy(
        zoom = targetZoom,
        panX = anchoredPanX + gesture.panChangeX,
        panY = anchoredPanY + gesture.panChangeY,
    )
}

internal fun fitTransformToViewport(
    pageWidth: Float,
    pageHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ViewTransform {
    if (!hasValidDimensions(pageWidth, pageHeight, viewportWidth, viewportHeight)) {
        return ViewTransform.DEFAULT
    }
    val fitZoom =
        min(viewportWidth / pageWidth, viewportHeight / pageHeight)
            .coerceIn(ViewTransform.MIN_ZOOM, ViewTransform.MAX_ZOOM)
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
    pdfRenderer: PdfRenderer?,
    viewZoom: Float,
): android.graphics.Bitmap? {
    var pdfBitmap by remember(currentPage?.pageId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val pageWidth = currentPage?.width ?: 0f
    val pageHeight = currentPage?.height ?: 0f
    val renderScale = resolvePdfRenderScale(viewZoom, pageWidth, pageHeight)
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

internal fun resolvePdfRenderScale(
    viewZoom: Float,
    pageWidth: Float,
    pageHeight: Float,
): Float {
    val bucketedScale = zoomToRenderScaleBucket(viewZoom)
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return bucketedScale
    }
    val maxScaleForPage =
        sqrt(PDF_RENDER_MAX_PIXELS / (pageWidth * pageHeight))
            .coerceAtLeast(PDF_RENDER_MIN_SCALE)
    return bucketedScale.coerceAtMost(maxScaleForPage)
}

internal fun zoomToRenderScaleBucket(zoom: Float): Float {
    val clampedZoom = zoom.coerceIn(ViewTransform.MIN_ZOOM, ViewTransform.MAX_ZOOM)
    return PDF_RENDER_SCALE_BUCKETS.firstOrNull { bucket -> clampedZoom <= bucket }
        ?: PDF_RENDER_SCALE_BUCKETS.last()
}
