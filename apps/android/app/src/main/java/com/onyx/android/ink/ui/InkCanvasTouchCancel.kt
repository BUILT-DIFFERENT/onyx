package com.onyx.android.ink.ui

import android.view.MotionEvent
import com.onyx.android.ink.gl.GlInkSurfaceView

internal fun MotionEvent.isCanceledEvent(): Boolean = (flags and MotionEvent.FLAG_CANCELED) != 0

internal fun cancelActiveStroke(
    view: GlInkSurfaceView,
    event: MotionEvent,
    pointerId: Int,
    runtime: InkCanvasRuntime,
) {
    val strokeId = runtime.activeStrokeIds[pointerId] ?: return
    view.cancelStroke(strokeId, event)
    runtime.activeStrokeIds.remove(pointerId)
    runtime.activePointerModes.remove(pointerId)
    runtime.stylusPointerIds.remove(pointerId)
    runtime.activeStrokeBrushes.remove(pointerId)
    runtime.activeStrokePoints.remove(pointerId)
    runtime.activeStrokeStartTimes.remove(pointerId)
    runtime.invalidateActiveStrokeRender()
}

internal fun cancelActiveStrokes(
    view: GlInkSurfaceView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
) {
    runtime.activeStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    runtime.activeStrokeIds.clear()
    runtime.activePointerModes.clear()
    runtime.stylusPointerIds.clear()
    runtime.activeStrokeBrushes.clear()
    runtime.activeStrokePoints.clear()
    runtime.activeStrokeStartTimes.clear()
    runtime.invalidateActiveStrokeRender()
}

internal fun cancelPredictedStroke(
    view: GlInkSurfaceView,
    event: MotionEvent,
    pointerId: Int,
    predictedStrokeIds: MutableMap<Int, Long>,
) {
    val predictedStrokeId = predictedStrokeIds[pointerId] ?: return
    view.cancelStroke(predictedStrokeId, event)
    predictedStrokeIds.remove(pointerId)
}

internal fun cancelPredictedStrokes(
    view: GlInkSurfaceView,
    event: MotionEvent,
    predictedStrokeIds: MutableMap<Int, Long>,
) {
    predictedStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    predictedStrokeIds.clear()
}
