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

@Composable
internal fun rememberPdfBitmap(
    isPdfPage: Boolean,
    currentPage: PageEntity?,
    pdfRenderer: PdfRenderer?,
    viewTransform: ViewTransform,
): android.graphics.Bitmap? {
    var pdfBitmap by remember(currentPage?.pageId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(currentPage?.pageId, viewTransform.zoom, isPdfPage, pdfRenderer) {
        if (!isPdfPage) {
            pdfBitmap = null
            return@LaunchedEffect
        }
        val renderer = pdfRenderer ?: return@LaunchedEffect
        val pageIndex = currentPage?.pdfPageNo ?: return@LaunchedEffect
        val zoom = viewTransform.zoom
        pdfBitmap =
            withContext(Dispatchers.Default) {
                renderer.renderPage(pageIndex, zoom)
            }
    }
    return pdfBitmap
}
