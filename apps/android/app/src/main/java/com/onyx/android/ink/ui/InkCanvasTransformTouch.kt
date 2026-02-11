package com.onyx.android.ink.ui

import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView
import kotlin.math.hypot

private const val MIN_ZOOM_CHANGE = 0.5f
private const val MAX_ZOOM_CHANGE = 2.0f

private data class TwoPointerTransform(
    val centroidX: Float,
    val centroidY: Float,
    val distance: Float,
)

internal fun shouldStartTransformGesture(event: MotionEvent): Boolean =
    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount >= 2

internal fun handleTransformGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean =
    when (event.actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount >= 2) {
                startTransformGesture(view, event, runtime)
                true
            } else {
                false
            }
        }

        MotionEvent.ACTION_MOVE -> {
            if (runtime.isTransforming && event.pointerCount >= 2) {
                updateTransformGesture(event, interaction, runtime)
                true
            } else {
                false
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val remainingPointers = event.pointerCount - 1
            if (remainingPointers < 2) {
                endTransformGesture(runtime)
            } else {
                setTransformBaseline(event, runtime)
            }
            true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
        -> {
            endTransformGesture(runtime)
            true
        }

        else -> runtime.isTransforming
    }

private fun startTransformGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    cancelActiveStrokes(view, event, runtime)
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.hoverPreviewState.hide()
    runtime.isTransforming = true
    setTransformBaseline(event, runtime)
}

private fun updateTransformGesture(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val transform = readTwoPointerTransform(event) ?: return
    val previousDistance = runtime.previousTransformDistance
    if (previousDistance > 0f) {
        val zoomChange =
            (transform.distance / previousDistance).coerceIn(
                MIN_ZOOM_CHANGE,
                MAX_ZOOM_CHANGE,
            )
        val panChangeX = transform.centroidX - runtime.previousTransformCentroidX
        val panChangeY = transform.centroidY - runtime.previousTransformCentroidY
        interaction.onTransformGesture(
            zoomChange,
            panChangeX,
            panChangeY,
            transform.centroidX,
            transform.centroidY,
        )
    }
    runtime.previousTransformDistance = transform.distance
    runtime.previousTransformCentroidX = transform.centroidX
    runtime.previousTransformCentroidY = transform.centroidY
}

private fun setTransformBaseline(
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    val transform = readTwoPointerTransform(event) ?: return
    runtime.previousTransformDistance = transform.distance
    runtime.previousTransformCentroidX = transform.centroidX
    runtime.previousTransformCentroidY = transform.centroidY
}

private fun endTransformGesture(runtime: InkCanvasRuntime) {
    runtime.isTransforming = false
    runtime.previousTransformDistance = 0f
    runtime.previousTransformCentroidX = 0f
    runtime.previousTransformCentroidY = 0f
}

private fun readTwoPointerTransform(event: MotionEvent): TwoPointerTransform? {
    if (event.pointerCount < 2) {
        return null
    }
    val x0 = event.getX(0)
    val y0 = event.getY(0)
    val x1 = event.getX(1)
    val y1 = event.getY(1)
    val centroidX = (x0 + x1) / 2f
    val centroidY = (y0 + y1) / 2f
    val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
    return TwoPointerTransform(centroidX, centroidY, distance)
}
