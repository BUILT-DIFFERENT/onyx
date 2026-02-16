package com.onyx.android.ink.ui

import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView

internal fun MotionEvent.isCanceledEvent(): Boolean = (flags and MotionEvent.FLAG_CANCELED) != 0

internal fun cancelActiveStroke(
    view: InProgressStrokesView,
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
    view: InProgressStrokesView,
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
    view: InProgressStrokesView,
    event: MotionEvent,
    pointerId: Int,
    predictedStrokeIds: MutableMap<Int, androidx.ink.authoring.InProgressStrokeId>,
) {
    val predictedStrokeId = predictedStrokeIds[pointerId] ?: return
    view.cancelStroke(predictedStrokeId, event)
    predictedStrokeIds.remove(pointerId)
}

internal fun cancelPredictedStrokes(
    view: InProgressStrokesView,
    event: MotionEvent,
    predictedStrokeIds: MutableMap<Int, androidx.ink.authoring.InProgressStrokeId>,
) {
    predictedStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    predictedStrokeIds.clear()
}
