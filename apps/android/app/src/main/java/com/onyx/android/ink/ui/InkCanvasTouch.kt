package com.onyx.android.ink.ui

import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.StrokeInput
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform

private const val PALM_CONTACT_SIZE_THRESHOLD = 1.0f
private const val PREDICTED_STROKE_ALPHA = 0.35f

internal data class InkCanvasInteraction(
    val brush: Brush,
    val viewTransform: ViewTransform,
    val strokes: List<Stroke>,
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onTransformGesture: (
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        centroidX: Float,
        centroidY: Float,
    ) -> Unit,
)

internal fun handleTouchEvent(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val handled =
        when {
            runtime.isTransforming || shouldStartTransformGesture(event) -> {
                handleTransformGesture(view, event, interaction, runtime)
            }

            runtime.isSingleFingerPanning || shouldStartSingleFingerPanGesture(event) -> {
                handleSingleFingerPanGesture(view, event, interaction, runtime)
            }

            interaction.brush.tool == Tool.ERASER -> {
                handleEraserInput(event, interaction, runtime)
            }

            else ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    -> handlePointerDown(view, event, interaction, runtime)

                    MotionEvent.ACTION_MOVE -> handlePointerMove(view, event, interaction, runtime)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_POINTER_UP,
                    -> handlePointerUp(view, event, interaction, runtime)

                    MotionEvent.ACTION_HOVER_ENTER,
                    MotionEvent.ACTION_HOVER_MOVE,
                    -> handleHover(event, interaction, runtime)

                    MotionEvent.ACTION_HOVER_EXIT -> {
                        runtime.hoverPreviewState.hide()
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> handleCancel(view, event, runtime)

                    else -> false
                }
        }
    return handled
}

private fun handleEraserInput(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_MOVE,
        -> {
            val erasedStroke =
                findStrokeToErase(
                    screenX = event.getX(event.actionIndex),
                    screenY = event.getY(event.actionIndex),
                    strokes = interaction.strokes,
                    viewTransform = interaction.viewTransform,
                )
            if (erasedStroke != null) {
                interaction.onStrokeErased(erasedStroke)
            }
            return true
        }
    }
    runtime.hoverPreviewState.hide()
    return true
}

private fun handlePointerDown(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val actionToolType = event.getToolType(actionIndex)
    val contactSize = event.getSize(actionIndex)
    val isKnownDrawingTool = isSupportedToolType(actionToolType)
    val isUnknownTool = actionToolType == MotionEvent.TOOL_TYPE_UNKNOWN
    val shouldHandle =
        (isKnownDrawingTool || isUnknownTool) && contactSize <= PALM_CONTACT_SIZE_THRESHOLD
    if (!shouldHandle) {
        return false
    }
    if (isStylusToolType(actionToolType)) {
        view.requestUnbufferedDispatch(event)
    }
    runtime.hoverPreviewState.hide()
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.motionPredictionAdapter?.record(event)
    val startTime = event.eventTime
    runtime.activeStrokeStartTimes[pointerId] = startTime
    val strokeInput = createStrokeInput(event, actionIndex, startTime)
    val effectiveBrush = interaction.brush.withToolType(actionToolType)
    val strokeId = view.startStroke(strokeInput, effectiveBrush.toInkBrush(interaction.viewTransform))
    runtime.activeStrokeIds[pointerId] = strokeId
    val points = mutableListOf<StrokePoint>()
    points.add(createStrokePoint(event, actionIndex, interaction.viewTransform))
    runtime.activeStrokePoints[pointerId] = points
    return true
}

private fun handlePointerMove(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    if (event.isCanceledEvent()) {
        cancelActiveStrokes(view, event, runtime)
        cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
        runtime.hoverPreviewState.hide()
        return true
    }
    runtime.motionPredictionAdapter?.record(event)
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    val pointerCount = event.pointerCount
    for (index in 0 until pointerCount) {
        val movePointerId = event.getPointerId(index)
        val strokeId = runtime.activeStrokeIds[movePointerId]
        if (strokeId != null) {
            view.addToStroke(event, movePointerId, strokeId)
            runtime.activeStrokePoints[movePointerId]?.add(
                createStrokePoint(event, index, interaction.viewTransform),
            )
        }
    }
    handlePredictedStrokes(view, event, interaction, runtime)
    return true
}

private fun handlePredictedStrokes(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val predictedEvent = runtime.motionPredictionAdapter?.predict() ?: return
    val predictedPointerCount = predictedEvent.pointerCount
    for (index in 0 until predictedPointerCount) {
        val toolType = predictedEvent.getToolType(index)
        val predictedPointerId = predictedEvent.getPointerId(index)
        val isActive = runtime.activeStrokeIds.containsKey(predictedPointerId)
        if (isActive) {
            val startTime =
                runtime.activeStrokeStartTimes[predictedPointerId] ?: predictedEvent.eventTime
            val startIndex = event.findPointerIndex(predictedPointerId)
            val startInput =
                if (startIndex >= 0) {
                    createStrokeInput(event, startIndex, startTime)
                } else {
                    createStrokeInput(predictedEvent, index, startTime)
                }
            val effectiveBrush =
                if (toolType == MotionEvent.TOOL_TYPE_UNKNOWN) {
                    interaction.brush
                } else {
                    interaction.brush.withToolType(toolType)
                }
            val predictedStrokeId =
                view.startStroke(
                    startInput,
                    effectiveBrush.toInkBrush(interaction.viewTransform, PREDICTED_STROKE_ALPHA),
                )
            view.addToStroke(predictedEvent, predictedPointerId, predictedStrokeId)
            runtime.predictedStrokeIds[predictedPointerId] = predictedStrokeId
        }
    }
    predictedEvent.recycle()
}

private fun handlePointerUp(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val actionToolType = event.getToolType(actionIndex)
    var handled = false
    if (event.isCanceledEvent()) {
        cancelActiveStroke(view, event, pointerId, runtime)
        cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
        runtime.hoverPreviewState.hide()
        handled = true
    } else {
        val strokeId = runtime.activeStrokeIds[pointerId]
        if (strokeId != null) {
            val startTime = runtime.activeStrokeStartTimes[pointerId] ?: event.eventTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            val effectiveBrush = interaction.brush.withToolType(actionToolType)
            cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
            runtime.motionPredictionAdapter?.record(event)
            view.finishStroke(strokeInput, strokeId)
            val points = runtime.activeStrokePoints[pointerId].orEmpty().toMutableList()
            points.add(createStrokePoint(event, actionIndex, interaction.viewTransform))
            val finishedStroke = buildStroke(points, effectiveBrush)
            interaction.onStrokeFinished(finishedStroke)
            runtime.activeStrokeIds.remove(pointerId)
            runtime.activeStrokePoints.remove(pointerId)
            runtime.activeStrokeStartTimes.remove(pointerId)
            runtime.hoverPreviewState.hide()
            handled = true
        }
    }
    return handled
}

private fun handleHover(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val actionToolType = event.getToolType(actionIndex)
    if (!isStylusToolType(actionToolType)) {
        runtime.hoverPreviewState.hide()
        return false
    }
    val distance = event.getAxisValue(MotionEvent.AXIS_DISTANCE, actionIndex)
    if (distance > 0f) {
        runtime.hoverPreviewState.show(
            x = event.getX(actionIndex),
            y = event.getY(actionIndex),
            tool = toolTypeToTool(actionToolType, interaction.brush),
        )
    } else {
        runtime.hoverPreviewState.hide()
    }
    return true
}

private fun handleCancel(
    view: InProgressStrokesView,
    event: MotionEvent,
    runtime: InkCanvasRuntime,
): Boolean {
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.activeStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    runtime.activeStrokeIds.clear()
    runtime.activeStrokePoints.clear()
    runtime.activeStrokeStartTimes.clear()
    runtime.hoverPreviewState.hide()
    return true
}

private fun createStrokeInput(
    event: MotionEvent,
    pointerIndex: Int,
    startTime: Long,
): StrokeInput {
    val toolType = event.getToolType(pointerIndex).toInputToolType()
    val pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f)
    val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    val orientation = event.getOrientation(pointerIndex)
    return StrokeInput.create(
        x = event.getX(pointerIndex),
        y = event.getY(pointerIndex),
        elapsedTimeMillis = (event.eventTime - startTime).coerceAtLeast(0L),
        toolType = toolType,
        strokeUnitLengthCm = 0.0f,
        pressure = pressure,
        tiltRadians = tilt,
        orientationRadians = orientation,
    )
}

private fun createStrokePoint(
    event: MotionEvent,
    pointerIndex: Int,
    viewTransform: ViewTransform,
): StrokePoint {
    val (pageX, pageY) =
        viewTransform.screenToPage(
            screenX = event.getX(pointerIndex),
            screenY = event.getY(pointerIndex),
        )
    val pressure = event.getPressure(pointerIndex).coerceIn(0f, 1f)
    val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    val orientation = event.getOrientation(pointerIndex)
    val tiltX = tilt * kotlin.math.cos(orientation)
    val tiltY = tilt * kotlin.math.sin(orientation)
    return StrokePoint(
        x = pageX,
        y = pageY,
        t = event.eventTime,
        p = pressure,
        tx = tiltX,
        ty = tiltY,
        r = orientation,
    )
}
