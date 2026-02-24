@file:Suppress(
    "TooManyFunctions",
    "LongMethod",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
)

package com.onyx.android.ink.ui

import android.os.Trace
import android.view.MotionEvent
import android.view.VelocityTracker
import com.onyx.android.ink.gl.GlInkSurfaceView
import com.onyx.android.ink.model.Tool
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.SingleFingerMode
import kotlin.math.abs
import kotlin.math.hypot

private const val MIN_ZOOM_CHANGE = 0.85f
private const val MAX_ZOOM_CHANGE = 1.18f
private const val PAN_FLING_MIN_VELOCITY_PX_PER_SECOND = 250f
private const val PAN_VELOCITY_UNITS_PER_SECOND = 1000
private const val LASSO_ZOOM_EPSILON = 0.001f
private const val MIN_ZOOM_FOR_PAGE_DELTA = 0.001f
private const val TRANSFORM_SMOOTHING_ALPHA = 0.22f
private const val PAN_NOISE_THRESHOLD_PX = 0.35f
private const val ZOOM_NOISE_THRESHOLD = 0.0025f
private const val MULTI_FINGER_TAP_TIMEOUT_MS = 260L
private const val MULTI_FINGER_TAP_MAX_MOVEMENT_PX = 24f

private data class TwoPointerTransform(
    val centroidX: Float,
    val centroidY: Float,
    val distance: Float,
)

internal fun shouldStartTransformGesture(event: MotionEvent): Boolean =
    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
        event.pointerCount >= 2 &&
        eventIsFingerOnly(event)

internal fun shouldStartSingleFingerPanGesture(
    event: MotionEvent,
    runtime: InkCanvasRuntime,
): Boolean =
    event.actionMasked == MotionEvent.ACTION_DOWN &&
        event.pointerCount == 1 &&
        isPointerDefinitelyFinger(event, 0) &&
        !eventHasStylusStream(event, runtime)

internal fun canStartTransformGesture(interaction: InkCanvasInteraction): Boolean =
    interaction.inputSettings.doubleFingerMode == DoubleFingerMode.ZOOM_PAN

internal fun canStartSingleFingerPanGesture(interaction: InkCanvasInteraction): Boolean =
    interaction.inputSettings.singleFingerMode == SingleFingerMode.PAN

internal fun handleSingleFingerPanGesture(
    view: GlInkSurfaceView,
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
                if (eventIsFingerOnly(event)) {
                    endSingleFingerPanGesture(view, interaction, runtime, fling = false)
                    startTransformGesture(view, event, runtime)
                    true
                } else {
                    endSingleFingerPanGesture(view, interaction, runtime, fling = false)
                    false
                }
            } else {
                false
            }
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val pointerId = event.getPointerId(event.actionIndex)
            if (runtime.isSingleFingerPanning && pointerId == runtime.singleFingerPanPointerId) {
                endSingleFingerPanGesture(view, interaction, runtime, fling = true)
                true
            } else {
                runtime.isSingleFingerPanning
            }
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
        -> {
            endSingleFingerPanGesture(
                view = view,
                interaction = interaction,
                runtime = runtime,
                fling = event.actionMasked == MotionEvent.ACTION_UP,
            )
            true
        }

        else -> {
            runtime.isSingleFingerPanning
        }
    }

internal fun handleTransformGesture(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean =
    when (event.actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (event.pointerCount >= 2 && eventIsFingerOnly(event)) {
                startTransformGesture(view, event, runtime)
                true
            } else {
                false
            }
        }

        MotionEvent.ACTION_MOVE -> {
            if (runtime.isTransforming && !eventIsFingerOnly(event)) {
                endTransformGesture(view, interaction, runtime, fling = false)
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
                maybeDispatchMultiFingerTapShortcut(event, interaction, runtime)
                endTransformGesture(view, interaction, runtime, fling = true)
            } else {
                setTransformBaseline(event, runtime, excludeActionIndex = true)
            }
            true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
        -> {
            endTransformGesture(
                view = view,
                interaction = interaction,
                runtime = runtime,
                fling = event.actionMasked == MotionEvent.ACTION_UP,
            )
            true
        }

        else -> {
            runtime.isTransforming
        }
    }

private fun startTransformGesture(
    view: GlInkSurfaceView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    endSingleFingerPanGesture(view, interaction = null, runtime = runtime, fling = false)
    cancelActiveStrokes(view, event, runtime)
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.hoverPreviewState.hide()
    runtime.isTransforming = true
    view.setGestureRenderingActive(true)
    setTransformBaseline(event, runtime)
    runtime.multiFingerTapStartTimeMs = event.eventTime
    runtime.multiFingerTapStartCentroidX = runtime.previousTransformCentroidX
    runtime.multiFingerTapStartCentroidY = runtime.previousTransformCentroidY
    runtime.multiFingerTapMaxPointerCount = event.pointerCount
    runtime.multiFingerTapMaxMovementPx = 0f
    startPanVelocityTracking(event, runtime)
}

private fun updateTransformGesture(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val rawTransform = readTwoPointerTransform(event, runtime) ?: return

    val smoothedCentroidX =
        if (runtime.smoothedTransformCentroidX == 0f) {
            rawTransform.centroidX
        } else {
            lerp(runtime.smoothedTransformCentroidX, rawTransform.centroidX, TRANSFORM_SMOOTHING_ALPHA)
        }
    val smoothedCentroidY =
        if (runtime.smoothedTransformCentroidY == 0f) {
            rawTransform.centroidY
        } else {
            lerp(runtime.smoothedTransformCentroidY, rawTransform.centroidY, TRANSFORM_SMOOTHING_ALPHA)
        }
    val smoothedDistance =
        if (runtime.smoothedTransformDistance == 0f) {
            rawTransform.distance
        } else {
            lerp(runtime.smoothedTransformDistance, rawTransform.distance, TRANSFORM_SMOOTHING_ALPHA)
        }

    runtime.smoothedTransformCentroidX = smoothedCentroidX
    runtime.smoothedTransformCentroidY = smoothedCentroidY
    runtime.smoothedTransformDistance = smoothedDistance

    val previousDistance = runtime.previousTransformDistance
    if (previousDistance <= 0f) {
        runtime.previousTransformDistance = smoothedDistance
        runtime.previousTransformCentroidX = smoothedCentroidX
        runtime.previousTransformCentroidY = smoothedCentroidY
        return
    }

    val rawZoomChange = (smoothedDistance / previousDistance).coerceIn(MIN_ZOOM_CHANGE, MAX_ZOOM_CHANGE)
    runtime.multiFingerTapMaxPointerCount = maxOf(runtime.multiFingerTapMaxPointerCount, event.pointerCount)
    val movementSinceStart =
        hypot(
            (smoothedCentroidX - runtime.multiFingerTapStartCentroidX).toDouble(),
            (smoothedCentroidY - runtime.multiFingerTapStartCentroidY).toDouble(),
        ).toFloat()
    runtime.multiFingerTapMaxMovementPx =
        maxOf(runtime.multiFingerTapMaxMovementPx, movementSinceStart)
    val zoomChange = if (abs(rawZoomChange - 1f) < ZOOM_NOISE_THRESHOLD) 1f else rawZoomChange

    val rawPanChangeX = smoothedCentroidX - runtime.previousTransformCentroidX
    val rawPanChangeY = smoothedCentroidY - runtime.previousTransformCentroidY
    val panChangeX = if (abs(rawPanChangeX) < PAN_NOISE_THRESHOLD_PX) 0f else rawPanChangeX
    val panChangeY = if (abs(rawPanChangeY) < PAN_NOISE_THRESHOLD_PX) 0f else rawPanChangeY

    if (zoomChange != 1f || panChangeX != 0f || panChangeY != 0f) {
        Trace.beginSection("InkCanvasTransformTouch#dispatchTransform")
        try {
            if (interaction.brush.tool == Tool.LASSO && interaction.lassoSelection.hasSelection) {
                val zoom = interaction.viewTransform.zoom.coerceAtLeast(MIN_ZOOM_FOR_PAGE_DELTA)
                if (panChangeX != 0f || panChangeY != 0f) {
                    interaction.onLassoMove(panChangeX / zoom, panChangeY / zoom)
                }
                if (abs(zoomChange - 1f) > LASSO_ZOOM_EPSILON) {
                    val pivot = interaction.lassoSelection.transformCenter
                    interaction.onLassoResize(zoomChange, pivot.first, pivot.second)
                }
            } else {
                interaction.onTransformGesture(
                    zoomChange,
                    panChangeX,
                    panChangeY,
                    smoothedCentroidX,
                    smoothedCentroidY,
                )
            }
        } finally {
            Trace.endSection()
        }
    }

    addCentroidMovementToVelocityTracker(
        transform = TwoPointerTransform(smoothedCentroidX, smoothedCentroidY, smoothedDistance),
        eventTime = event.eventTime,
        runtime = runtime,
    )
    runtime.previousTransformDistance = smoothedDistance
    runtime.previousTransformCentroidX = smoothedCentroidX
    runtime.previousTransformCentroidY = smoothedCentroidY
}

private fun setTransformBaseline(
    event: MotionEvent,
    runtime: InkCanvasRuntime,
    excludeActionIndex: Boolean = false,
) {
    val pointerIds = selectTransformPointerIds(event, excludeActionIndex) ?: return
    runtime.transformPointerIdA = pointerIds.first
    runtime.transformPointerIdB = pointerIds.second

    val transform = readTwoPointerTransform(event, runtime) ?: return
    runtime.previousTransformDistance = transform.distance
    runtime.previousTransformCentroidX = transform.centroidX
    runtime.previousTransformCentroidY = transform.centroidY
    runtime.smoothedTransformDistance = transform.distance
    runtime.smoothedTransformCentroidX = transform.centroidX
    runtime.smoothedTransformCentroidY = transform.centroidY
}

private fun selectTransformPointerIds(
    event: MotionEvent,
    excludeActionIndex: Boolean,
): Pair<Int, Int>? {
    val ids = ArrayList<Int>(2)
    for (index in 0 until event.pointerCount) {
        if (excludeActionIndex && index == event.actionIndex) {
            continue
        }
        if (!isPointerDefinitelyFinger(event, index)) {
            continue
        }
        ids += event.getPointerId(index)
        if (ids.size == 2) {
            return Pair(ids[0], ids[1])
        }
    }
    return null
}

private fun startSingleFingerPanGesture(
    view: GlInkSurfaceView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    endTransformGesture(
        view = view,
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
    view.setGestureRenderingActive(true)
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
        return false
    }
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val panChangeX = x - runtime.previousSingleFingerPanX
    val panChangeY = y - runtime.previousSingleFingerPanY
    if (interaction.brush.tool == Tool.LASSO && interaction.lassoSelection.hasSelection) {
        val zoom = interaction.viewTransform.zoom.coerceAtLeast(MIN_ZOOM_FOR_PAGE_DELTA)
        interaction.onLassoMove(panChangeX / zoom, panChangeY / zoom)
    } else {
        interaction.onTransformGesture(
            1f,
            panChangeX,
            panChangeY,
            x,
            y,
        )
    }
    runtime.previousSingleFingerPanX = x
    runtime.previousSingleFingerPanY = y
    runtime.panVelocityTracker?.addMovement(event)
    return true
}

private fun endSingleFingerPanGesture(
    view: GlInkSurfaceView,
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
    view.setGestureRenderingActive(runtime.isTransforming)
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
    view: GlInkSurfaceView,
    interaction: InkCanvasInteraction?,
    runtime: InkCanvasRuntime,
    fling: Boolean,
) {
    runtime.isTransforming = false
    runtime.previousTransformDistance = 0f
    runtime.previousTransformCentroidX = 0f
    runtime.previousTransformCentroidY = 0f
    runtime.transformPointerIdA = MotionEvent.INVALID_POINTER_ID
    runtime.transformPointerIdB = MotionEvent.INVALID_POINTER_ID
    runtime.smoothedTransformDistance = 0f
    runtime.smoothedTransformCentroidX = 0f
    runtime.smoothedTransformCentroidY = 0f
    if (interaction != null) {
        endPanVelocityTracking(interaction, runtime, fling)
    } else {
        runtime.panVelocityTracker?.recycle()
        runtime.panVelocityTracker = null
    }
    resetMultiFingerTap(runtime)
    view.setGestureRenderingActive(runtime.isSingleFingerPanning)
}

private fun maybeDispatchMultiFingerTapShortcut(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    if (runtime.multiFingerTapStartTimeMs <= 0L) {
        return
    }
    val elapsed = event.eventTime - runtime.multiFingerTapStartTimeMs
    if (elapsed <= 0L || elapsed > MULTI_FINGER_TAP_TIMEOUT_MS) {
        return
    }
    if (runtime.multiFingerTapMaxMovementPx > MULTI_FINGER_TAP_MAX_MOVEMENT_PX) {
        return
    }
    dispatchMultiFingerTapShortcut(interaction, runtime)
}

private fun readTwoPointerTransform(
    event: MotionEvent,
    runtime: InkCanvasRuntime,
): TwoPointerTransform? {
    if (event.pointerCount < 2) {
        return null
    }

    val pointerIdA = runtime.transformPointerIdA
    val pointerIdB = runtime.transformPointerIdB

    if (pointerIdA == MotionEvent.INVALID_POINTER_ID || pointerIdB == MotionEvent.INVALID_POINTER_ID) {
        val fallback = selectTransformPointerIds(event, excludeActionIndex = false) ?: return null
        runtime.transformPointerIdA = fallback.first
        runtime.transformPointerIdB = fallback.second
    }

    val indexA = event.findPointerIndex(runtime.transformPointerIdA)
    val indexB = event.findPointerIndex(runtime.transformPointerIdB)
    if (indexA < 0 || indexB < 0) {
        val fallback = selectTransformPointerIds(event, excludeActionIndex = false) ?: return null
        runtime.transformPointerIdA = fallback.first
        runtime.transformPointerIdB = fallback.second
    }

    val resolvedIndexA = event.findPointerIndex(runtime.transformPointerIdA)
    val resolvedIndexB = event.findPointerIndex(runtime.transformPointerIdB)
    if (resolvedIndexA < 0 || resolvedIndexB < 0) {
        return null
    }

    val x0 = event.getX(resolvedIndexA)
    val y0 = event.getY(resolvedIndexA)
    val x1 = event.getX(resolvedIndexB)
    val y1 = event.getY(resolvedIndexB)
    val centroidX = (x0 + x1) / 2f
    val centroidY = (y0 + y1) / 2f
    val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
    return TwoPointerTransform(centroidX, centroidY, distance)
}

private fun lerp(
    previous: Float,
    current: Float,
    alpha: Float,
): Float = previous + (current - previous) * alpha
