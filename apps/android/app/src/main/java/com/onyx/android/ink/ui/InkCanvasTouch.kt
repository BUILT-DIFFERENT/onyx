@file:Suppress(
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "NestedBlockDepth",
    "ReturnCount",
)

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
private const val PREDICTED_STROKE_ALPHA = 0.2f
private const val IN_PROGRESS_STROKE_ALPHA = 1f
private const val STYLUS_BUTTON_MASK =
    MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
private const val EDGE_TOLERANCE_PX = 12f
private const val MIN_TOLERANCE_ZOOM = 0.001f

// Disabled while pen-up handoff is stabilized; prediction introduces visible divergence.
private const val ENABLE_PREDICTED_STROKES = false

internal data class InkCanvasInteraction(
    val brush: Brush,
    val viewTransform: ViewTransform,
    val strokes: List<Stroke>,
    val pageWidth: Float,
    val pageHeight: Float,
    val allowEditing: Boolean,
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onTransformGesture: (
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        centroidX: Float,
        centroidY: Float,
    ) -> Unit,
    val onPanGestureEnd: (
        velocityX: Float,
        velocityY: Float,
    ) -> Unit,
    val onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
)

internal fun handleTouchEvent(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    syncStylusButtonEraserState(event, interaction, runtime)
    return when {
        runtime.isTransforming || shouldStartTransformGesture(event) -> {
            handleTransformGesture(view, event, interaction, runtime)
        }

        runtime.isSingleFingerPanning || shouldStartSingleFingerPanGesture(event) -> {
            handleSingleFingerPanGesture(view, event, interaction, runtime)
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
                    updateStylusButtonEraserState(false, interaction, runtime)
                    true
                }

                MotionEvent.ACTION_CANCEL -> handleCancel(view, event, interaction, runtime)

                else -> false
            }
    }
}

internal fun handleGenericMotionEvent(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    syncStylusButtonEraserState(event, interaction, runtime)
    return when (event.actionMasked) {
        MotionEvent.ACTION_HOVER_ENTER,
        MotionEvent.ACTION_HOVER_MOVE,
        -> handleHover(event, interaction, runtime)

        MotionEvent.ACTION_HOVER_EXIT -> {
            runtime.hoverPreviewState.hide()
            updateStylusButtonEraserState(false, interaction, runtime)
            true
        }

        MotionEvent.ACTION_BUTTON_PRESS,
        MotionEvent.ACTION_BUTTON_RELEASE,
        -> {
            if (eventHasStylusPointer(event)) {
                handleHover(event, interaction, runtime)
            } else {
                false
            }
        }

        else -> false
    }
}

private fun syncStylusButtonEraserState(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val shouldUpdate =
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_HOVER_EXIT,
            -> true

            else -> eventHasStylusPointer(event)
        }
    if (!shouldUpdate) {
        return
    }
    val isActive =
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_HOVER_EXIT,
            -> false

            else -> isStylusButtonPressed(event)
        }
    updateStylusButtonEraserState(isActive, interaction, runtime)
}

private fun updateStylusButtonEraserState(
    isActive: Boolean,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    if (runtime.isStylusButtonEraserActive == isActive) {
        return
    }
    runtime.isStylusButtonEraserActive = isActive
    interaction.onStylusButtonEraserActiveChanged(isActive)
}

private fun eventHasStylusPointer(event: MotionEvent): Boolean {
    for (index in 0 until event.pointerCount) {
        if (isStylusToolType(event.getToolType(index))) {
            return true
        }
    }
    return false
}

private fun isStylusButtonPressed(event: MotionEvent): Boolean = (event.buttonState and STYLUS_BUTTON_MASK) != 0

private fun resolvePointerMode(
    event: MotionEvent,
    pointerIndex: Int,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): PointerMode {
    val toolType = event.getToolType(pointerIndex)
    if (toolType == MotionEvent.TOOL_TYPE_ERASER || interaction.brush.tool == Tool.ERASER) {
        return PointerMode.ERASE
    }
    if (isStylusToolType(toolType) && (runtime.isStylusButtonEraserActive || isStylusButtonPressed(event))) {
        return PointerMode.ERASE
    }
    return PointerMode.DRAW
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
    if (!interaction.allowEditing) {
        return true
    }
    val pointerMode = resolvePointerMode(event, actionIndex, interaction, runtime)
    runtime.activePointerModes[pointerId] = pointerMode
    if (isStylusToolType(actionToolType)) {
        view.requestUnbufferedDispatch(event)
    }
    if (pointerMode == PointerMode.ERASE) {
        runtime.hoverPreviewState.hide()
        handleEraserAtPointer(event, actionIndex, interaction, runtime)
        return true
    }
    val downPageX = interaction.viewTransform.screenToPageX(event.getX(actionIndex))
    val downPageY = interaction.viewTransform.screenToPageY(event.getY(actionIndex))
    val tolerance =
        EDGE_TOLERANCE_PX /
            interaction.viewTransform.zoom.coerceAtLeast(MIN_TOLERANCE_ZOOM)
    if (!isInsidePageWithTolerance(downPageX, downPageY, interaction.pageWidth, interaction.pageHeight, tolerance)) {
        runtime.activePointerModes.remove(pointerId)
        return true
    }
    runtime.hoverPreviewState.hide()
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.motionPredictionAdapter?.record(event)
    val startTime = event.eventTime
    runtime.activeStrokeStartTimes[pointerId] = startTime
    val strokeInput = createStrokeInput(event, actionIndex, startTime)
    val effectiveBrush = interaction.brush.withToolType(actionToolType)
    val strokeId =
        view.startStroke(
            strokeInput,
            effectiveBrush.toInkBrush(
                interaction.viewTransform,
                alphaMultiplier = IN_PROGRESS_STROKE_ALPHA,
            ),
        )
    runtime.activeStrokeIds[pointerId] = strokeId
    runtime.activeStrokeBrushes[pointerId] = effectiveBrush
    val points = mutableListOf<StrokePoint>()
    points.add(
        createStrokePoint(
            event = event,
            pointerIndex = actionIndex,
            viewTransform = interaction.viewTransform,
            pageWidth = interaction.pageWidth,
            pageHeight = interaction.pageHeight,
        ),
    )
    runtime.activeStrokePoints[pointerId] = points
    runtime.invalidateActiveStrokeRender()
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
    var handled = false
    for (index in 0 until pointerCount) {
        val movePointerId = event.getPointerId(index)
        when (runtime.activePointerModes[movePointerId]) {
            PointerMode.ERASE -> {
                handleEraserAtPointer(event, index, interaction, runtime)
                handled = true
            }

            PointerMode.DRAW -> {
                val strokeId = runtime.activeStrokeIds[movePointerId]
                if (strokeId != null) {
                    view.addToStroke(event, movePointerId, strokeId)
                    runtime.activeStrokePoints[movePointerId]?.let { points ->
                        appendHistoricalStrokePoints(
                            event = event,
                            pointerIndex = index,
                            viewTransform = interaction.viewTransform,
                            pageWidth = interaction.pageWidth,
                            pageHeight = interaction.pageHeight,
                            outPoints = points,
                        )
                        addPointDeduped(
                            outPoints = points,
                            point =
                                createStrokePoint(
                                    event = event,
                                    pointerIndex = index,
                                    viewTransform = interaction.viewTransform,
                                    pageWidth = interaction.pageWidth,
                                    pageHeight = interaction.pageHeight,
                                ),
                        )
                    }
                    handled = true
                }
            }

            null -> Unit
        }
    }
    if (ENABLE_PREDICTED_STROKES) {
        handlePredictedStrokes(view, event, interaction, runtime)
    }
    return handled
}

private fun handleEraserAtPointer(
    event: MotionEvent,
    pointerIndex: Int,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val allStrokes = interaction.strokes + runtime.pendingCommittedStrokes.values
    val dedupedStrokes =
        LinkedHashMap<String, Stroke>(allStrokes.size).apply {
            allStrokes.forEach { stroke -> put(stroke.id, stroke) }
        }.values.toList()
    val erasedStroke =
        findStrokeToErase(
            screenX = event.getX(pointerIndex),
            screenY = event.getY(pointerIndex),
            strokes = dedupedStrokes,
            viewTransform = interaction.viewTransform,
        )
    if (erasedStroke != null) {
        interaction.onStrokeErased(erasedStroke)
    }
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
            val startIndex = event.findPointerIndex(predictedPointerId)
            if (startIndex < 0) {
                continue
            }
            val startInput =
                createStrokeInput(
                    event = event,
                    pointerIndex = startIndex,
                    startTime = event.eventTime,
                )
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
    val pointerMode = runtime.activePointerModes.remove(pointerId)
    var handled = false
    if (event.isCanceledEvent()) {
        cancelActiveStroke(view, event, pointerId, runtime)
        cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
        runtime.hoverPreviewState.hide()
        handled = true
    } else if (pointerMode == PointerMode.ERASE) {
        runtime.hoverPreviewState.hide()
        handled = true
    } else {
        val strokeId = runtime.activeStrokeIds[pointerId]
        if (strokeId != null) {
            val startTime = runtime.activeStrokeStartTimes[pointerId] ?: event.eventTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            val effectiveBrush =
                runtime.activeStrokeBrushes[pointerId] ?: interaction.brush.withToolType(actionToolType)
            cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
            runtime.motionPredictionAdapter?.record(event)
            view.finishStroke(strokeInput, strokeId)
            val points = runtime.activeStrokePoints[pointerId].orEmpty().toMutableList()
            appendHistoricalStrokePoints(
                event = event,
                pointerIndex = actionIndex,
                viewTransform = interaction.viewTransform,
                pageWidth = interaction.pageWidth,
                pageHeight = interaction.pageHeight,
                outPoints = points,
            )
            addPointDeduped(
                outPoints = points,
                point =
                    createStrokePoint(
                        event = event,
                        pointerIndex = actionIndex,
                        viewTransform = interaction.viewTransform,
                        pageWidth = interaction.pageWidth,
                        pageHeight = interaction.pageHeight,
                    ),
            )
            val finishedStroke = buildStroke(points, effectiveBrush)
            interaction.onStrokeFinished(finishedStroke)
            view.removeFinishedStrokes(setOf(strokeId))
            runtime.activeStrokeIds.remove(pointerId)
            runtime.activeStrokeBrushes.remove(pointerId)
            runtime.activeStrokePoints.remove(pointerId)
            runtime.activeStrokeStartTimes.remove(pointerId)
            runtime.invalidateActiveStrokeRender()
            runtime.hoverPreviewState.hide()
            handled = true
        }
    }
    return handled
}

private fun appendHistoricalStrokePoints(
    event: MotionEvent,
    pointerIndex: Int,
    viewTransform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
    outPoints: MutableList<StrokePoint>,
) {
    val historySize = event.historySize
    if (historySize <= 0) {
        return
    }
    for (historyIndex in 0 until historySize) {
        addPointDeduped(
            outPoints = outPoints,
            point =
                createHistoricalStrokePoint(
                    event = event,
                    pointerIndex = pointerIndex,
                    historyIndex = historyIndex,
                    viewTransform = viewTransform,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                ),
        )
    }
}

private fun addPointDeduped(
    outPoints: MutableList<StrokePoint>,
    point: StrokePoint,
) {
    val lastPoint = outPoints.lastOrNull()
    if (lastPoint != null && lastPoint.x == point.x && lastPoint.y == point.y) {
        return
    }
    outPoints.add(point)
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
        val hoverTool =
            if (runtime.isStylusButtonEraserActive) {
                Tool.ERASER
            } else {
                toolTypeToTool(actionToolType, interaction.brush)
            }
        runtime.hoverPreviewState.show(
            x = event.getX(actionIndex),
            y = event.getY(actionIndex),
            tool = hoverTool,
        )
    } else {
        runtime.hoverPreviewState.hide()
    }
    return true
}

private fun handleCancel(
    view: InProgressStrokesView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
    runtime.activeStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    runtime.activeStrokeIds.clear()
    runtime.activePointerModes.clear()
    runtime.activeStrokeBrushes.clear()
    runtime.activeStrokePoints.clear()
    runtime.activeStrokeStartTimes.clear()
    runtime.panVelocityTracker?.recycle()
    runtime.panVelocityTracker = null
    runtime.invalidateActiveStrokeRender()
    runtime.hoverPreviewState.hide()
    updateStylusButtonEraserState(false, interaction, runtime)
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
    pageWidth: Float,
    pageHeight: Float,
): StrokePoint {
    val rawPageX = viewTransform.screenToPageX(event.getX(pointerIndex))
    val rawPageY = viewTransform.screenToPageY(event.getY(pointerIndex))
    val pageX = clampPageCoordinate(rawPageX, pageWidth)
    val pageY = clampPageCoordinate(rawPageY, pageHeight)
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

private fun createHistoricalStrokePoint(
    event: MotionEvent,
    pointerIndex: Int,
    historyIndex: Int,
    viewTransform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
): StrokePoint {
    val rawPageX =
        viewTransform.screenToPageX(
            event.getHistoricalX(pointerIndex, historyIndex),
        )
    val rawPageY =
        viewTransform.screenToPageY(
            event.getHistoricalY(pointerIndex, historyIndex),
        )
    val pageX = clampPageCoordinate(rawPageX, pageWidth)
    val pageY = clampPageCoordinate(rawPageY, pageHeight)
    val pressure = event.getHistoricalPressure(pointerIndex, historyIndex).coerceIn(0f, 1f)
    val tilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, historyIndex)
    val orientation = event.getHistoricalOrientation(pointerIndex, historyIndex)
    val tiltX = tilt * kotlin.math.cos(orientation)
    val tiltY = tilt * kotlin.math.sin(orientation)
    return StrokePoint(
        x = pageX,
        y = pageY,
        t = event.getHistoricalEventTime(historyIndex),
        p = pressure,
        tx = tiltX,
        ty = tiltY,
        r = orientation,
    )
}

private fun isInsidePageWithTolerance(
    pageX: Float,
    pageY: Float,
    pageWidth: Float,
    pageHeight: Float,
    tolerance: Float,
): Boolean {
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return true
    }
    return pageX in -tolerance..(pageWidth + tolerance) &&
        pageY in -tolerance..(pageHeight + tolerance)
}

private fun clampPageCoordinate(
    coordinate: Float,
    pageDimension: Float,
): Float {
    if (pageDimension <= 0f) {
        return coordinate
    }
    return coordinate.coerceIn(0f, pageDimension)
}
