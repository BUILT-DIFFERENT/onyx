@file:Suppress(
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "NestedBlockDepth",
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package com.onyx.android.ink.ui

import android.content.Context
import android.view.MotionEvent
import com.onyx.android.config.FeatureFlag
import com.onyx.android.config.FeatureFlagStore
import com.onyx.android.ink.algorithm.computeStrokeSplitCandidates
import com.onyx.android.ink.gl.GlHoverOverlay
import com.onyx.android.ink.gl.GlInkSurfaceView
import com.onyx.android.ink.gl.GlOverlayState
import com.onyx.android.ink.gl.GlStrokeInput
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.InputSettings
import com.onyx.android.input.MultiFingerTapAction
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.input.StylusButtonAction
import kotlin.math.hypot

private const val PALM_CONTACT_SIZE_THRESHOLD = 1.0f
private const val PREDICTED_STROKE_ALPHA = 0.2f
private const val IN_PROGRESS_STROKE_ALPHA = 1f
private const val EDGE_TOLERANCE_PX = 12f
private const val MIN_TOLERANCE_ZOOM = 0.001f
private const val SEGMENT_ERASER_RADIUS_PX = 10f
private const val HOVER_ERASER_COLOR = 0xFF6B6B6B.toInt()
private const val HOVER_ERASER_ALPHA = 0.6f
private const val HOVER_PEN_ALPHA = 0.35f
private const val MIN_HOVER_RADIUS = 0.1f
private const val STYLUS_LONG_HOLD_ERASER_DELAY_MS = 350L
private const val DOUBLE_TAP_TIMEOUT_MS = 280L
private const val DOUBLE_TAP_MAX_DISTANCE_PX = 28f
private const val TWO_FINGER_POINTER_COUNT = 2
private const val THREE_FINGER_POINTER_COUNT = 3

internal data class InkCanvasInteraction(
    val brush: Brush,
    val isSegmentEraserEnabled: Boolean = false,
    val lassoSelection: LassoSelection,
    val viewTransform: ViewTransform,
    val strokes: List<Stroke>,
    val pageWidth: Float,
    val pageHeight: Float,
    val allowEditing: Boolean,
    val allowFingerGestures: Boolean,
    val inputSettings: InputSettings,
    val onStrokeFinished: (Stroke) -> Unit,
    val onStrokeErased: (Stroke) -> Unit,
    val onStrokeSplit: (original: Stroke, segments: List<Stroke>) -> Unit = { _, _ -> },
    val onLassoMove: (deltaX: Float, deltaY: Float) -> Unit = { _, _ -> },
    val onLassoResize: (scale: Float, pivotPageX: Float, pivotPageY: Float) -> Unit = { _, _, _ -> },
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
    val onUndoShortcut: () -> Unit = {},
    val onRedoShortcut: () -> Unit = {},
    val onDoubleTapGesture: () -> Unit = {},
    val onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
    val onStrokeRenderFinished: (Long) -> Unit,
)

internal fun handleTouchEvent(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    syncStylusEraserState(event, interaction, runtime)
    if (!interaction.allowFingerGestures && !eventHasStylusStream(event, runtime)) {
        return false
    }
    if (!interaction.allowFingerGestures) {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> {
                handlePointerDown(view, event, interaction, runtime)
            }

            MotionEvent.ACTION_MOVE -> {
                handlePointerMove(view, event, interaction, runtime)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                handlePointerUp(view, event, interaction, runtime)
            }

            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            -> {
                handleHover(view, event, interaction, runtime)
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                runtime.hoverPreviewState.hide()
                updateStylusButtonEraserState(false, interaction, runtime)
                pushOverlayState(view, interaction, runtime)
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                handleCancel(view, event, interaction, runtime)
            }

            else -> {
                false
            }
        }
    }
    return when {
        (runtime.isTransforming || shouldStartTransformGesture(event)) &&
            canStartTransformGesture(interaction) -> {
            handleTransformGesture(view, event, interaction, runtime)
        }

        (runtime.isSingleFingerPanning || shouldStartSingleFingerPanGesture(event, runtime)) &&
            canStartSingleFingerPanGesture(interaction) -> {
            handleSingleFingerPanGesture(view, event, interaction, runtime)
        }

        else -> {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                -> {
                    handlePointerDown(view, event, interaction, runtime)
                }

                MotionEvent.ACTION_MOVE -> {
                    handlePointerMove(view, event, interaction, runtime)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                -> {
                    handlePointerUp(view, event, interaction, runtime)
                }

                MotionEvent.ACTION_HOVER_ENTER,
                MotionEvent.ACTION_HOVER_MOVE,
                -> {
                    handleHover(view, event, interaction, runtime)
                }

                MotionEvent.ACTION_HOVER_EXIT -> {
                    runtime.hoverPreviewState.hide()
                    updateStylusButtonEraserState(false, interaction, runtime)
                    pushOverlayState(view, interaction, runtime)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handleCancel(view, event, interaction, runtime)
                }

                else -> {
                    false
                }
            }
        }
    }
}

internal fun handleGenericMotionEvent(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    syncStylusEraserState(event, interaction, runtime)
    return when (event.actionMasked) {
        MotionEvent.ACTION_HOVER_ENTER,
        MotionEvent.ACTION_HOVER_MOVE,
        -> {
            handleHover(view, event, interaction, runtime)
        }

        MotionEvent.ACTION_HOVER_EXIT -> {
            runtime.hoverPreviewState.hide()
            updateStylusButtonEraserState(false, interaction, runtime)
            pushOverlayState(view, interaction, runtime)
            true
        }

        MotionEvent.ACTION_BUTTON_PRESS,
        MotionEvent.ACTION_BUTTON_RELEASE,
        -> {
            if (eventHasStylusStream(event, runtime)) {
                handleHover(view, event, interaction, runtime)
            } else {
                false
            }
        }

        else -> {
            false
        }
    }
}

private fun syncStylusEraserState(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    syncStylusLongHoldState(event, interaction, runtime)
    val isButtonActive =
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_HOVER_EXIT,
            -> false

            else -> isStylusButtonPressed(event, interaction)
        }
    val isActive = isButtonActive || runtime.stylusLongHoldActivePointerIds.isNotEmpty()
    updateStylusButtonEraserState(isActive, interaction, runtime)
}

private fun syncStylusLongHoldState(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        -> {
            val index = event.actionIndex
            if (isPointerStylusStream(event, index, runtime)) {
                val pointerId = event.getPointerId(index)
                runtime.stylusLongHoldStartTimes[pointerId] = event.eventTime
                runtime.stylusLongHoldActivePointerIds.remove(pointerId)
            }
        }

        MotionEvent.ACTION_MOVE,
        MotionEvent.ACTION_HOVER_ENTER,
        MotionEvent.ACTION_HOVER_MOVE,
        -> {
            if (interaction.inputSettings.stylusLongHoldAction == StylusButtonAction.ERASER_HOLD) {
                for (index in 0 until event.pointerCount) {
                    if (!isPointerStylusStream(event, index, runtime)) {
                        continue
                    }
                    val pointerId = event.getPointerId(index)
                    val startTime = runtime.stylusLongHoldStartTimes[pointerId] ?: event.eventTime
                    if (event.eventTime - startTime >= STYLUS_LONG_HOLD_ERASER_DELAY_MS) {
                        runtime.stylusLongHoldActivePointerIds.add(pointerId)
                    }
                }
            } else {
                runtime.stylusLongHoldActivePointerIds.clear()
            }
        }

        MotionEvent.ACTION_POINTER_UP,
        MotionEvent.ACTION_UP,
        -> {
            val pointerId = event.getPointerId(event.actionIndex)
            runtime.stylusLongHoldStartTimes.remove(pointerId)
            runtime.stylusLongHoldActivePointerIds.remove(pointerId)
        }

        MotionEvent.ACTION_CANCEL,
        MotionEvent.ACTION_HOVER_EXIT,
        -> {
            runtime.stylusLongHoldStartTimes.clear()
            runtime.stylusLongHoldActivePointerIds.clear()
        }
    }
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

private fun shouldTreatStylusPrimaryAsEraser(
    event: MotionEvent,
    interaction: InkCanvasInteraction?,
): Boolean {
    val action = interaction?.inputSettings?.stylusPrimaryAction ?: StylusButtonAction.ERASER_HOLD
    return action == StylusButtonAction.ERASER_HOLD &&
        (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY == MotionEvent.BUTTON_STYLUS_PRIMARY)
}

private fun shouldTreatStylusSecondaryAsEraser(
    event: MotionEvent,
    interaction: InkCanvasInteraction?,
): Boolean {
    val action = interaction?.inputSettings?.stylusSecondaryAction ?: StylusButtonAction.ERASER_HOLD
    return action == StylusButtonAction.ERASER_HOLD &&
        (event.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY == MotionEvent.BUTTON_STYLUS_SECONDARY)
}

private fun resolvePointerMode(
    event: MotionEvent,
    pointerIndex: Int,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
    pointerIsStylusStream: Boolean,
): PointerMode {
    val toolType = event.getToolType(pointerIndex)
    if (toolType == MotionEvent.TOOL_TYPE_ERASER || interaction.brush.tool == Tool.ERASER) {
        return PointerMode.ERASE
    }
    if (
        pointerIsStylusStream &&
        (runtime.isStylusButtonEraserActive || isStylusButtonPressed(event, interaction))
    ) {
        return PointerMode.ERASE
    }
    return PointerMode.DRAW
}

private fun isStylusButtonPressed(
    event: MotionEvent,
    interaction: InkCanvasInteraction,
): Boolean =
    shouldTreatStylusPrimaryAsEraser(event, interaction) ||
        shouldTreatStylusSecondaryAsEraser(event, interaction)

private fun handlePointerDown(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val actionToolType = event.getToolType(actionIndex)
    val contactSize = event.getSize(actionIndex)
    val pointerIsStylusStream = isPointerStylusStream(event, actionIndex, runtime)
    val isKnownDrawingTool = isSupportedToolType(actionToolType)
    val isUnknownTool = actionToolType == MotionEvent.TOOL_TYPE_UNKNOWN
    val shouldHandle =
        (isKnownDrawingTool || isUnknownTool || pointerIsStylusStream) && contactSize <= PALM_CONTACT_SIZE_THRESHOLD
    if (!shouldHandle) {
        return false
    }
    if (pointerIsStylusStream) {
        runtime.stylusPointerIds.add(pointerId)
    } else {
        runtime.stylusPointerIds.remove(pointerId)
        if (event.pointerCount > 1 && interaction.inputSettings.doubleFingerMode == DoubleFingerMode.IGNORE) {
            return false
        }
        if (interaction.inputSettings.singleFingerMode != SingleFingerMode.DRAW) {
            return false
        }
    }
    if (!interaction.allowEditing) {
        return true
    }
    if (pointerIsDoubleTapGesture(event, actionIndex, interaction, runtime)) {
        interaction.onDoubleTapGesture()
        resetLastTap(runtime)
        return true
    }
    val pointerMode = resolvePointerMode(event, actionIndex, interaction, runtime, pointerIsStylusStream)
    runtime.activePointerModes[pointerId] = pointerMode
    if (pointerIsStylusStream) {
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
    if (isPredictionEnabled(view.context)) {
        runtime.motionPredictionAdapter?.record(event)
    }
    val startTime = event.eventTime
    runtime.activeStrokeStartTimes[pointerId] = startTime
    val strokeInput =
        createStrokeInput(
            event = event,
            pointerIndex = actionIndex,
            startTime = startTime,
            viewTransform = interaction.viewTransform,
            pageWidth = interaction.pageWidth,
            pageHeight = interaction.pageHeight,
            resolvedToolType =
                if (actionToolType == MotionEvent.TOOL_TYPE_UNKNOWN && pointerIsStylusStream) {
                    MotionEvent.TOOL_TYPE_STYLUS
                } else {
                    actionToolType
                },
        )
    val effectiveBrush = interaction.brush.withToolType(actionToolType)
    val strokeId =
        view.startStroke(
            strokeInput,
            effectiveBrush.toGlBrush(alphaMultiplier = inProgressAlphaForBrush(effectiveBrush)),
        )
    view.setStrokeRenderingActive(true)
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

private fun pointerIsDoubleTapGesture(
    event: MotionEvent,
    pointerIndex: Int,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    if (event.pointerCount != 1) {
        return false
    }
    if (!isPointerDefinitelyFinger(event, pointerIndex)) {
        return false
    }
    if (interaction.inputSettings.singleFingerMode == SingleFingerMode.DRAW) {
        return false
    }
    val previousTapTime = runtime.lastFingerTapTimeMs
    if (previousTapTime <= 0L) {
        return false
    }
    val elapsed = event.eventTime - previousTapTime
    if (elapsed <= 0L || elapsed > DOUBLE_TAP_TIMEOUT_MS) {
        return false
    }
    val tapDistance =
        hypot(
            (event.getX(pointerIndex) - runtime.lastFingerTapX).toDouble(),
            (event.getY(pointerIndex) - runtime.lastFingerTapY).toDouble(),
        )
    return tapDistance <= DOUBLE_TAP_MAX_DISTANCE_PX
}

private fun recordLastFingerTap(
    event: MotionEvent,
    pointerIndex: Int,
    runtime: InkCanvasRuntime,
) {
    runtime.lastFingerTapTimeMs = event.eventTime
    runtime.lastFingerTapX = event.getX(pointerIndex)
    runtime.lastFingerTapY = event.getY(pointerIndex)
}

private fun resetLastTap(runtime: InkCanvasRuntime) {
    runtime.lastFingerTapTimeMs = 0L
    runtime.lastFingerTapX = 0f
    runtime.lastFingerTapY = 0f
}

private fun handlePointerMove(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    if (event.isCanceledEvent()) {
        cancelActiveStrokes(view, event, runtime)
        cancelPredictedStrokes(view, event, runtime.predictedStrokeIds)
        view.setStrokeRenderingActive(false)
        runtime.hoverPreviewState.hide()
        return true
    }
    val predictedStrokesEnabled = isPredictionEnabled(view.context)
    if (predictedStrokesEnabled) {
        runtime.motionPredictionAdapter?.record(event)
    }
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
                val shouldPromoteStylusToEraser =
                    runtime.isStylusButtonEraserActive &&
                        runtime.stylusPointerIds.contains(movePointerId) &&
                        interaction.brush.tool != Tool.ERASER
                if (shouldPromoteStylusToEraser) {
                    cancelActiveStroke(view, event, movePointerId, runtime)
                    runtime.activePointerModes[movePointerId] = PointerMode.ERASE
                    handleEraserAtPointer(event, index, interaction, runtime)
                    handled = true
                    continue
                }
                val strokeId = runtime.activeStrokeIds[movePointerId]
                if (strokeId != null) {
                    val startTime = runtime.activeStrokeStartTimes[movePointerId] ?: event.eventTime
                    val strokeInput =
                        createStrokeInput(
                            event = event,
                            pointerIndex = index,
                            startTime = startTime,
                            viewTransform = interaction.viewTransform,
                            pageWidth = interaction.pageWidth,
                            pageHeight = interaction.pageHeight,
                        )
                    view.addToStroke(strokeInput, strokeId)
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

            null -> {
                Unit
            }
        }
    }
    if (predictedStrokesEnabled) {
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
    val pointerId = event.getPointerId(pointerIndex)
    val currentPagePoint =
        Pair(
            interaction.viewTransform.screenToPageX(event.getX(pointerIndex)),
            interaction.viewTransform.screenToPageY(event.getY(pointerIndex)),
        )
    val previousPagePoint = runtime.eraserLastPagePoints[pointerId]
    runtime.eraserLastPagePoints[pointerId] = currentPagePoint

    val allStrokes = interaction.strokes + runtime.pendingCommittedStrokes.values
    val dedupedStrokes =
        LinkedHashMap<String, Stroke>(allStrokes.size)
            .apply {
                allStrokes.forEach { stroke -> put(stroke.id, stroke) }
            }.values
            .toList()

    if (interaction.isSegmentEraserEnabled) {
        val eraserPathPoints =
            if (previousPagePoint != null) {
                listOf(previousPagePoint, currentPagePoint)
            } else {
                listOf(currentPagePoint)
            }
        val eraserRadius =
            SEGMENT_ERASER_RADIUS_PX /
                interaction.viewTransform.zoom.coerceAtLeast(MIN_TOLERANCE_ZOOM)
        val splitCandidates =
            computeStrokeSplitCandidates(
                strokes = dedupedStrokes,
                eraserPathPoints = eraserPathPoints,
                eraserRadius = eraserRadius,
            )
        splitCandidates.forEach { candidate ->
            interaction.onStrokeSplit(candidate.original, candidate.segments)
        }
        return true
    }

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
    view: GlInkSurfaceView,
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
            val predictedPointerIsStylus =
                runtime.stylusPointerIds.contains(predictedPointerId) ||
                    isPointerStylusStream(predictedEvent, index, runtime)
            val startInput =
                createStrokeInput(
                    event = event,
                    pointerIndex = startIndex,
                    startTime = event.eventTime,
                    viewTransform = interaction.viewTransform,
                    pageWidth = interaction.pageWidth,
                    pageHeight = interaction.pageHeight,
                    resolvedToolType =
                        if (toolType == MotionEvent.TOOL_TYPE_UNKNOWN && predictedPointerIsStylus) {
                            MotionEvent.TOOL_TYPE_STYLUS
                        } else {
                            toolType
                        },
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
                    effectiveBrush.toGlBrush(alphaMultiplier = predictedAlphaForBrush(effectiveBrush)),
                )
            val predictedInput =
                createStrokeInput(
                    event = predictedEvent,
                    pointerIndex = index,
                    startTime = event.eventTime,
                    viewTransform = interaction.viewTransform,
                    pageWidth = interaction.pageWidth,
                    pageHeight = interaction.pageHeight,
                    resolvedToolType =
                        if (toolType == MotionEvent.TOOL_TYPE_UNKNOWN && predictedPointerIsStylus) {
                            MotionEvent.TOOL_TYPE_STYLUS
                        } else {
                            toolType
                        },
                )
            view.addToStroke(predictedInput, predictedStrokeId)
            runtime.predictedStrokeIds[predictedPointerId] = predictedStrokeId
        }
    }
    predictedEvent.recycle()
}

private fun handlePointerUp(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val actionToolType = event.getToolType(actionIndex)
    val pointerWasStylusStream =
        runtime.stylusPointerIds.contains(pointerId) ||
            isPointerStylusStream(event, actionIndex, runtime)
    runtime.stylusPointerIds.remove(pointerId)
    val pointerMode = runtime.activePointerModes.remove(pointerId)
    var handled = false
    if (event.isCanceledEvent()) {
        cancelActiveStroke(view, event, pointerId, runtime)
        cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
        view.setStrokeRenderingActive(runtime.activeStrokeIds.isNotEmpty())
        runtime.hoverPreviewState.hide()
        handled = true
    } else if (pointerMode == PointerMode.ERASE) {
        runtime.eraserLastPagePoints.remove(pointerId)
        runtime.hoverPreviewState.hide()
        if (isPointerDefinitelyFinger(event, actionIndex)) {
            recordLastFingerTap(event, actionIndex, runtime)
        }
        handled = true
    } else {
        val strokeId = runtime.activeStrokeIds[pointerId]
        if (strokeId != null) {
            val startTime = runtime.activeStrokeStartTimes[pointerId] ?: event.eventTime
            val strokeInput =
                createStrokeInput(
                    event = event,
                    pointerIndex = actionIndex,
                    startTime = startTime,
                    viewTransform = interaction.viewTransform,
                    pageWidth = interaction.pageWidth,
                    pageHeight = interaction.pageHeight,
                    resolvedToolType =
                        if (actionToolType == MotionEvent.TOOL_TYPE_UNKNOWN && pointerWasStylusStream) {
                            MotionEvent.TOOL_TYPE_STYLUS
                        } else {
                            actionToolType
                        },
                )
            val effectiveBrush =
                runtime.activeStrokeBrushes[pointerId] ?: interaction.brush.withToolType(actionToolType)
            cancelPredictedStroke(view, event, pointerId, runtime.predictedStrokeIds)
            if (isPredictionEnabled(view.context)) {
                runtime.motionPredictionAdapter?.record(event)
            }
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
            // Defer removal to callback synchronized with view rendering to prevent ghosting
            interaction.onStrokeRenderFinished(strokeId)
            runtime.activeStrokeIds.remove(pointerId)
            runtime.activeStrokeBrushes.remove(pointerId)
            runtime.activeStrokePoints.remove(pointerId)
            runtime.activeStrokeStartTimes.remove(pointerId)
            view.setStrokeRenderingActive(runtime.activeStrokeIds.isNotEmpty())
            runtime.invalidateActiveStrokeRender()
            runtime.hoverPreviewState.hide()
            if (isPointerDefinitelyFinger(event, actionIndex)) {
                recordLastFingerTap(event, actionIndex, runtime)
            } else {
                resetLastTap(runtime)
            }
            handled = true
        }
    }
    runtime.eraserLastPagePoints.remove(pointerId)
    view.setStrokeRenderingActive(runtime.activeStrokeIds.isNotEmpty())
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
        // Position unchanged but pressure may have changed — update last point
        // to preserve pressure variation (e.g., pressing harder without moving)
        if (point.p != null && point.p != lastPoint.p) {
            outPoints[outPoints.lastIndex] = lastPoint.copy(p = point.p, t = point.t)
        }
        return
    }
    outPoints.add(point)
}

private fun handleHover(
    view: GlInkSurfaceView,
    event: MotionEvent,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
): Boolean {
    val actionIndex = event.actionIndex
    val actionToolType = event.getToolType(actionIndex)
    if (!isStylusToolType(actionToolType) && !isPointerStylusStream(event, actionIndex, runtime)) {
        runtime.hoverPreviewState.hide()
        pushOverlayState(view, interaction, runtime)
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
    pushOverlayState(view, interaction, runtime)
    return true
}

private fun handleCancel(
    view: GlInkSurfaceView,
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
    runtime.eraserLastPagePoints.clear()
    runtime.activeStrokeBrushes.clear()
    runtime.activeStrokePoints.clear()
    runtime.activeStrokeStartTimes.clear()
    runtime.stylusPointerIds.clear()
    runtime.stylusLongHoldStartTimes.clear()
    runtime.stylusLongHoldActivePointerIds.clear()
    resetLastTap(runtime)
    resetMultiFingerTap(runtime)
    runtime.panVelocityTracker?.recycle()
    runtime.panVelocityTracker = null
    view.setStrokeRenderingActive(false)
    view.setGestureRenderingActive(false)
    runtime.invalidateActiveStrokeRender()
    runtime.hoverPreviewState.hide()
    pushOverlayState(view, interaction, runtime)
    updateStylusButtonEraserState(false, interaction, runtime)
    return true
}

internal fun dispatchMultiFingerTapShortcut(
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val action =
        when (runtime.multiFingerTapMaxPointerCount) {
            TWO_FINGER_POINTER_COUNT -> interaction.inputSettings.twoFingerTapAction
            THREE_FINGER_POINTER_COUNT -> interaction.inputSettings.threeFingerTapAction
            else -> MultiFingerTapAction.NONE
        }
    when (action) {
        MultiFingerTapAction.NONE -> Unit
        MultiFingerTapAction.UNDO -> interaction.onUndoShortcut()
        MultiFingerTapAction.REDO -> interaction.onRedoShortcut()
    }
}

internal fun resetMultiFingerTap(runtime: InkCanvasRuntime) {
    runtime.multiFingerTapStartTimeMs = 0L
    runtime.multiFingerTapStartCentroidX = 0f
    runtime.multiFingerTapStartCentroidY = 0f
    runtime.multiFingerTapMaxPointerCount = 0
    runtime.multiFingerTapMaxMovementPx = 0f
}

private fun createStrokeInput(
    event: MotionEvent,
    pointerIndex: Int,
    startTime: Long,
    viewTransform: ViewTransform,
    pageWidth: Float,
    pageHeight: Float,
    resolvedToolType: Int = event.getToolType(pointerIndex),
): GlStrokeInput {
    val pressure = applyPressureGamma(event.getPressure(pointerIndex))
    val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    val orientation = event.getOrientation(pointerIndex)
    val pageX = clampPageCoordinate(viewTransform.screenToPageX(event.getX(pointerIndex)), pageWidth)
    val pageY = clampPageCoordinate(viewTransform.screenToPageY(event.getY(pointerIndex)), pageHeight)
    return GlStrokeInput(
        x = pageX,
        y = pageY,
        eventTimeMillis = (event.eventTime - startTime).coerceAtLeast(0L),
        toolType = resolvedToolType,
        pressure = pressure,
        tiltRadians = tilt,
        orientationRadians = orientation,
    )
}

private fun inProgressAlphaForBrush(brush: Brush): Float =
    if (brush.tool == Tool.HIGHLIGHTER) {
        HIGHLIGHTER_STROKE_ALPHA
    } else {
        IN_PROGRESS_STROKE_ALPHA
    }

private fun predictedAlphaForBrush(brush: Brush): Float =
    if (brush.tool == Tool.HIGHLIGHTER) {
        HIGHLIGHTER_STROKE_ALPHA * PREDICTED_STROKE_ALPHA
    } else {
        PREDICTED_STROKE_ALPHA
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

private fun isPredictionEnabled(context: Context): Boolean =
    FeatureFlagStore
        .getInstance(context)
        .get(FeatureFlag.INK_PREDICTION_ENABLED)

private fun pushOverlayState(
    view: GlInkSurfaceView,
    interaction: InkCanvasInteraction,
    runtime: InkCanvasRuntime,
) {
    val hover = runtime.hoverPreviewState
    val hoverOverlay =
        if (hover.isVisible) {
            val isEraser = hover.tool == Tool.ERASER
            val color = if (isEraser) HOVER_ERASER_COLOR else ColorCache.resolve(interaction.brush.color)
            val alpha = if (isEraser) HOVER_ERASER_ALPHA else HOVER_PEN_ALPHA
            GlHoverOverlay(
                isVisible = true,
                screenX = hover.x,
                screenY = hover.y,
                screenRadius =
                    (
                        interaction.viewTransform
                            .pageWidthToScreen(interaction.brush.baseWidth)
                            .coerceAtLeast(MIN_HOVER_RADIUS)
                    ) / 2f,
                argbColor = color,
                alpha = alpha,
            )
        } else {
            GlHoverOverlay()
        }
    view.setOverlayState(
        GlOverlayState(
            selectedStrokeIds = interaction.lassoSelection.selectedStrokeIds,
            lassoPath = interaction.lassoSelection.lassoPath,
            hover = hoverOverlay,
        ),
    )
}
