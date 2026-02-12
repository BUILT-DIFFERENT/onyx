@file:Suppress("TooManyFunctions")

package com.onyx.android.ink.ui

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.ink.authoring.InProgressStrokesView
import kotlin.math.hypot

private const val MIN_ZOOM_CHANGE = 0.5f
private const val MAX_ZOOM_CHANGE = 2.0f
private const val PAN_FLING_MIN_VELOCITY_PX_PER_SECOND = 250f
private const val PAN_VELOCITY_UNITS_PER_SECOND = 1000

private data class TwoPointerTransform(
    val centroidX: Float,
    val centroidY: Float,
    val distance: Float,
)

internal fun shouldStartTransformGesture(event: MotionEvent): Boolean =
    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
        event.pointerCount >= 2 &&
        isFingerOnlyGesture(event)

internal fun shouldStartSingleFingerPanGesture(event: MotionEvent): Boolean =
    event.actionMasked == MotionEvent.ACTION_DOWN &&
        event.pointerCount == 1 &&
        event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER

internal fun handleSingleFingerPanGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean =
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            startSingleFingerPanGesture(view, event, runtime)
            true
        }

        MotionEvent.ACTION_MOVE -> {
            if (runtime.isSingleFingerPanning) {
                updateSingleFingerPanGesture(event, interaction, runtime)
            } else {
                false
            }
        }

        MotionEvent.ACTION_POINTER_DOWN -> {
            if (runtime.isSingleFingerPanning && event.pointerCount >= 2) {
                if (isFingerOnlyGesture(event)) {
                    endSingleFingerPanGesture(interaction, runtime, fling = false)
                    startTransformGesture(view, event, runtime)
                    true
                } else {
                    endSingleFingerPanGesture(interaction, runtime, fling = false)
                    false
                }
            } else {
                false
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val pointerId = event.getPointerId(event.actionIndex)
            if (runtime.isSingleFingerPanning && pointerId == runtime.singleFingerPanPointerId) {
                endSingleFingerPanGesture(interaction, runtime, fling = true)
                true
            } else {
                runtime.isSingleFingerPanning
            }
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
        -> {
            endSingleFingerPanGesture(
                interaction = interaction,
                runtime = runtime,
                fling = event.actionMasked == MotionEvent.ACTION_UP,
            )
            true
        }

        else -> runtime.isSingleFingerPanning
    }

internal fun handleTransformGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean =
    when (event.actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount >= 2 && isFingerOnlyGesture(event)) {
                startTransformGesture(view, event, runtime)
                true
            } else {
                false
            }
        }

        MotionEvent.ACTION_MOVE -> {
            if (runtime.isTransforming && !isFingerOnlyGesture(event)) {
                endTransformGesture(interaction, runtime, fling = false)
                false
            } else if (runtime.isTransforming && event.pointerCount >= 2) {
                updateTransformGesture(event, interaction, runtime)
                true
            } else {
                false
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val remainingPointers = event.pointerCount - 1
            if (remainingPointers < 2) {
                endTransformGesture(interaction, runtime, fling = true)
            } else {
                setTransformBaseline(event, runtime)
            }
            true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
        -> {
            endTransformGesture(
                interaction = interaction,
                runtime = runtime,
                fling = event.actionMasked == MotionEvent.ACTION_UP,
            )
            true
        }

        else -> runtime.isTransforming
    }

private fun startTransformGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    endSingleFingerPanGesture(interaction = null, runtime = runtime, fling = false)
    cancelActiveStrokes(view, event, runtime)
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.hoverPreviewState.hide()
    runtime.isTransforming = true
    setTransformBaseline(event, runtime)
    startPanVelocityTracking(event, runtime)
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
    addCentroidMovementToVelocityTracker(transform, event.eventTime, runtime)
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

private fun startSingleFingerPanGesture(
    view: InProgressStrokesView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    endTransformGesture(
        interaction = null,
        runtime = runtime,
        fling = false,
    )
    cancelActiveStrokes(view, event, runtime)
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.hoverPreviewState.hide()
    val actionIndex = event.actionIndex
    runtime.isSingleFingerPanning = true
    runtime.singleFingerPanPointerId = event.getPointerId(actionIndex)
    runtime.previousSingleFingerPanX = event.getX(actionIndex)
    runtime.previousSingleFingerPanY = event.getY(actionIndex)
    startPanVelocityTracking(event, runtime)
}

private fun updateSingleFingerPanGesture(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val panPointerId = runtime.singleFingerPanPointerId
    val pointerIndex = event.findPointerIndex(panPointerId)
    if (pointerIndex < 0) {
        endSingleFingerPanGesture(interaction, runtime, fling = false)
        return false
    }
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val panChangeX = x - runtime.previousSingleFingerPanX
    val panChangeY = y - runtime.previousSingleFingerPanY
    interaction.onTransformGesture(
        1f,
        panChangeX,
        panChangeY,
        x,
        y,
    )
    runtime.previousSingleFingerPanX = x
    runtime.previousSingleFingerPanY = y
    runtime.panVelocityTracker?.addMovement(event)
    return true
}

private fun endSingleFingerPanGesture(
    interaction: InkCanvasInteraction?,
    runtime: InkCanvasRuntime,
    fling: Boolean,
) {
    runtime.isSingleFingerPanning = false
    runtime.singleFingerPanPointerId = MotionEvent.INVALID_POINTER_ID
    runtime.previousSingleFingerPanX = 0f
    runtime.previousSingleFingerPanY = 0f
    if (interaction != null) {
        endPanVelocityTracking(interaction, runtime, fling)
    } else {
        runtime.panVelocityTracker?.recycle()
        runtime.panVelocityTracker = null
    }
}

private fun startPanVelocityTracking(
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    runtime.panVelocityTracker?.recycle()
    runtime.panVelocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
}

private fun addCentroidMovementToVelocityTracker(
    transform: TwoPointerTransform,
    eventTime: Long,
    runtime: InkCanvasRuntime,
) {
    val tracker = runtime.panVelocityTracker ?: return
    val syntheticEvent =
        MotionEvent.obtain(
            eventTime,
            eventTime,
            MotionEvent.ACTION_MOVE,
            transform.centroidX,
            transform.centroidY,
            0,
        )
    tracker.addMovement(syntheticEvent)
    syntheticEvent.recycle()
}

private fun endPanVelocityTracking(
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
    fling: Boolean,
) {
    val tracker = runtime.panVelocityTracker
    if (tracker != null && fling) {
        tracker.computeCurrentVelocity(PAN_VELOCITY_UNITS_PER_SECOND)
        val velocityX = tracker.xVelocity
        val velocityY = tracker.yVelocity
        if (hypot(velocityX.toDouble(), velocityY.toDouble()) >= PAN_FLING_MIN_VELOCITY_PX_PER_SECOND) {
            interaction.onPanGestureEnd(velocityX, velocityY)
        }
    }
    tracker?.recycle()
    runtime.panVelocityTracker = null
}

private fun endTransformGesture(
    interaction: InkCanvasInteraction?,
    runtime: InkCanvasRuntime,
    fling: Boolean,
) {
    runtime.isTransforming = false
    runtime.previousTransformDistance = 0f
    runtime.previousTransformCentroidX = 0f
    runtime.previousTransformCentroidY = 0f
    if (interaction != null) {
        endPanVelocityTracking(interaction, runtime, fling)
    } else {
        runtime.panVelocityTracker?.recycle()
        runtime.panVelocityTracker = null
    }
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

private fun isFingerOnlyGesture(event: MotionEvent): Boolean {
    for (index in 0 until event.pointerCount) {
        if (event.getToolType(index) != MotionEvent.TOOL_TYPE_FINGER) {
            return false
        }
    }
    return true
}
