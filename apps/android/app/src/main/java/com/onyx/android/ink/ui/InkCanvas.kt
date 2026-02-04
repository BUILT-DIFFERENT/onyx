package com.onyx.android.ink.ui

import android.content.Context
import android.graphics.Color.parseColor
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.StrokeInput
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import java.lang.reflect.Method
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.ink.brush.Brush as InkBrush

@Composable
fun InkCanvas(
    strokes: List<Stroke>,
    viewTransform: ViewTransform,
    brush: Brush,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokeErased: (Stroke) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentBrush by rememberUpdatedState(brush)
    val currentTransform by rememberUpdatedState(viewTransform)
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val currentOnStrokeErased by rememberUpdatedState(onStrokeErased)
    val currentStrokes by rememberUpdatedState(strokes)
    val hoverPreviewState = remember { HoverPreviewState() }

    val activeStrokeIds = remember { mutableMapOf<Int, InProgressStrokeId>() }
    val activeStrokePoints = remember { mutableMapOf<Int, MutableList<StrokePoint>>() }
    val activeStrokeStartTimes = remember { mutableMapOf<Int, Long>() }
    val predictedStrokeIds = remember { mutableMapOf<Int, InProgressStrokeId>() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { stroke ->
                drawStroke(stroke, currentTransform)
            }
        }

        AndroidView(
            factory = { context ->
                val motionPredictionAdapter = MotionPredictionAdapter.create(context)
                InProgressStrokesView(context).apply {
                    setOnTouchListener { _, event ->
                        handleTouchEvent(
                            view = this@apply,
                            event = event,
                            brush = currentBrush,
                            viewTransform = currentTransform,
                            activeStrokeIds = activeStrokeIds,
                            activeStrokePoints = activeStrokePoints,
                            activeStrokeStartTimes = activeStrokeStartTimes,
                            predictedStrokeIds = predictedStrokeIds,
                            motionPredictionAdapter = motionPredictionAdapter,
                            hoverPreviewState = hoverPreviewState,
                            onStrokeFinished = currentOnStrokeFinished,
                            onStrokeErased = currentOnStrokeErased,
                            strokes = currentStrokes,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHoverPreview(hoverPreviewState, currentBrush, currentTransform)
        }
    }
}

private fun handleTouchEvent(
    view: InProgressStrokesView,
    event: MotionEvent,
    brush: Brush,
    viewTransform: ViewTransform,
    activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    activeStrokeStartTimes: MutableMap<Int, Long>,
    predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
    motionPredictionAdapter: MotionPredictionAdapter?,
    hoverPreviewState: HoverPreviewState,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokeErased: (Stroke) -> Unit,
    strokes: List<Stroke>,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val actionToolType = event.getToolType(actionIndex)

    if (brush.tool == Tool.ERASER) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            -> {
                val erasedStroke =
                    findStrokeToErase(
                        screenX = event.getX(actionIndex),
                        screenY = event.getY(actionIndex),
                        strokes = strokes,
                        viewTransform = viewTransform,
                    )
                if (erasedStroke != null) {
                    onStrokeErased(erasedStroke)
                }
                return true
            }
        }
        return true
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        -> {
            if (!isSupportedToolType(actionToolType)) {
                return false
            }
            val contactSize = event.getSize(actionIndex)
            if (contactSize > PALM_CONTACT_SIZE_THRESHOLD) {
                return false
            }
            if (isStylusToolType(actionToolType)) {
                view.requestUnbufferedDispatch(event)
            }
            hoverPreviewState.hide()
            cancelPredictedStrokes(view, event, predictedStrokeIds)
            motionPredictionAdapter?.record(event)
            val startTime = event.eventTime
            activeStrokeStartTimes[pointerId] = startTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            val effectiveBrush = brush.withToolType(actionToolType)
            val strokeId = view.startStroke(strokeInput, effectiveBrush.toInkBrush(viewTransform))
            activeStrokeIds[pointerId] = strokeId
            val points = mutableListOf<StrokePoint>()
            points.add(createStrokePoint(event, actionIndex, viewTransform))
            activeStrokePoints[pointerId] = points
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            if (event.isCanceledEvent()) {
                cancelActiveStrokes(view, event, activeStrokeIds, activeStrokePoints, activeStrokeStartTimes)
                cancelPredictedStrokes(view, event, predictedStrokeIds)
                hoverPreviewState.hide()
                return true
            }
            motionPredictionAdapter?.record(event)
            cancelPredictedStrokes(view, event, predictedStrokeIds)
            val pointerCount = event.pointerCount
            for (index in 0 until pointerCount) {
                if (!isSupportedToolType(event.getToolType(index))) {
                    continue
                }
                val movePointerId = event.getPointerId(index)
                val strokeId = activeStrokeIds[movePointerId] ?: continue
                view.addToStroke(event, movePointerId, strokeId)
                activeStrokePoints[movePointerId]?.add(
                    createStrokePoint(event, index, viewTransform),
                )
            }
            val predictedEvent = motionPredictionAdapter?.predict()
            if (predictedEvent != null) {
                try {
                    val predictedPointerCount = predictedEvent.pointerCount
                    for (index in 0 until predictedPointerCount) {
                        if (!isSupportedToolType(predictedEvent.getToolType(index))) {
                            continue
                        }
                        val predictedPointerId = predictedEvent.getPointerId(index)
                        if (!activeStrokeIds.containsKey(predictedPointerId)) {
                            continue
                        }
                        val startTime = activeStrokeStartTimes[predictedPointerId] ?: predictedEvent.eventTime
                        val startIndex = event.findPointerIndex(predictedPointerId)
                        val startInput =
                            if (startIndex >= 0) {
                                createStrokeInput(event, startIndex, startTime)
                            } else {
                                createStrokeInput(predictedEvent, index, startTime)
                            }
                        val effectiveBrush = brush.withToolType(predictedEvent.getToolType(index))
                        val predictedStrokeId =
                            view.startStroke(
                                startInput,
                                effectiveBrush.toInkBrush(viewTransform, PREDICTED_STROKE_ALPHA),
                            )
                        view.addToStroke(predictedEvent, predictedPointerId, predictedStrokeId)
                        predictedStrokeIds[predictedPointerId] = predictedStrokeId
                    }
                } finally {
                    predictedEvent.recycle()
                }
            }
            return true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP,
        -> {
            if (event.isCanceledEvent()) {
                cancelActiveStroke(
                    view = view,
                    event = event,
                    pointerId = pointerId,
                    activeStrokeIds = activeStrokeIds,
                    activeStrokePoints = activeStrokePoints,
                    activeStrokeStartTimes = activeStrokeStartTimes,
                )
                cancelPredictedStroke(view, event, pointerId, predictedStrokeIds)
                hoverPreviewState.hide()
                return true
            }
            val strokeId = activeStrokeIds[pointerId] ?: return false
            val startTime = activeStrokeStartTimes[pointerId] ?: event.eventTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            val effectiveBrush = brush.withToolType(actionToolType)
            cancelPredictedStroke(view, event, pointerId, predictedStrokeIds)
            motionPredictionAdapter?.record(event)
            view.finishStroke(strokeInput, strokeId)
            val points = activeStrokePoints[pointerId].orEmpty().toMutableList()
            points.add(createStrokePoint(event, actionIndex, viewTransform))
            val finishedStroke = buildStroke(points, effectiveBrush)
            onStrokeFinished(finishedStroke)
            activeStrokeIds.remove(pointerId)
            activeStrokePoints.remove(pointerId)
            activeStrokeStartTimes.remove(pointerId)
            hoverPreviewState.hide()
            return true
        }

        MotionEvent.ACTION_HOVER_ENTER,
        MotionEvent.ACTION_HOVER_MOVE,
        -> {
            if (!isStylusToolType(actionToolType)) {
                hoverPreviewState.hide()
                return false
            }
            val distance = event.getAxisValue(MotionEvent.AXIS_DISTANCE, actionIndex)
            if (distance > 0f) {
                hoverPreviewState.show(
                    x = event.getX(actionIndex),
                    y = event.getY(actionIndex),
                    tool = toolTypeToTool(actionToolType, brush),
                )
            } else {
                hoverPreviewState.hide()
            }
            return true
        }

        MotionEvent.ACTION_HOVER_EXIT -> {
            hoverPreviewState.hide()
            return true
        }

        MotionEvent.ACTION_CANCEL -> {
            cancelPredictedStrokes(view, event, predictedStrokeIds)
            activeStrokeIds.values.forEach { strokeId ->
                view.cancelStroke(strokeId, event)
            }
            activeStrokeIds.clear()
            activeStrokePoints.clear()
            activeStrokeStartTimes.clear()
            hoverPreviewState.hide()
            return true
        }
    }

    return false
}

// MotionEvent.getSize returns a normalized touch size [0..1] where larger values
// indicate a broader contact. Values above 0.5f are typically larger than a
// finger pad and align with palm contacts, so we reject them early.
private const val PALM_CONTACT_SIZE_THRESHOLD = 0.5f
private const val PREDICTED_STROKE_ALPHA = 0.35f

private fun MotionEvent.isCanceledEvent(): Boolean = (flags and MotionEvent.FLAG_CANCELED) != 0

private fun cancelActiveStroke(
    view: InProgressStrokesView,
    event: MotionEvent,
    pointerId: Int,
    activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    activeStrokeStartTimes: MutableMap<Int, Long>,
) {
    val strokeId = activeStrokeIds[pointerId] ?: return
    view.cancelStroke(strokeId, event)
    activeStrokeIds.remove(pointerId)
    activeStrokePoints.remove(pointerId)
    activeStrokeStartTimes.remove(pointerId)
}

private fun cancelActiveStrokes(
    view: InProgressStrokesView,
    event: MotionEvent,
    activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    activeStrokeStartTimes: MutableMap<Int, Long>,
) {
    activeStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    activeStrokeIds.clear()
    activeStrokePoints.clear()
    activeStrokeStartTimes.clear()
}

private fun cancelPredictedStroke(
    view: InProgressStrokesView,
    event: MotionEvent,
    pointerId: Int,
    predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
) {
    val predictedStrokeId = predictedStrokeIds[pointerId] ?: return
    view.cancelStroke(predictedStrokeId, event)
    predictedStrokeIds.remove(pointerId)
}

private fun cancelPredictedStrokes(
    view: InProgressStrokesView,
    event: MotionEvent,
    predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
) {
    predictedStrokeIds.values.forEach { strokeId ->
        view.cancelStroke(strokeId, event)
    }
    predictedStrokeIds.clear()
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
    val tiltX = tilt * cos(orientation)
    val tiltY = tilt * sin(orientation)
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

private fun buildStroke(
    points: List<StrokePoint>,
    brush: Brush,
): Stroke {
    val bounds = calculateBounds(points)
    return Stroke(
        id = UUID.randomUUID().toString(),
        points = points,
        style = brush.toStrokeStyle(),
        bounds = bounds,
        createdAt = points.minOfOrNull { it.t } ?: System.currentTimeMillis(),
    )
}

private fun calculateBounds(points: List<StrokePoint>): StrokeBounds {
    if (points.isEmpty()) {
        return StrokeBounds(x = 0f, y = 0f, w = 0f, h = 0f)
    }
    var minX = points.first().x
    var minY = points.first().y
    var maxX = points.first().x
    var maxY = points.first().y
    for (point in points) {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
    }
    return StrokeBounds(
        x = minX,
        y = minY,
        w = maxX - minX,
        h = maxY - minY,
    )
}

private fun findStrokeToErase(
    screenX: Float,
    screenY: Float,
    strokes: List<Stroke>,
    viewTransform: ViewTransform,
): Stroke? {
    val (pageX, pageY) = viewTransform.screenToPage(screenX, screenY)
    val hitRadius = 10f / viewTransform.zoom
    for (stroke in strokes) {
        val expandedBounds =
            stroke.bounds.run {
                StrokeBounds(
                    x = x - hitRadius,
                    y = y - hitRadius,
                    w = w + 2 * hitRadius,
                    h = h + 2 * hitRadius,
                )
            }
        if (
            pageX < expandedBounds.x ||
            pageX > expandedBounds.x + expandedBounds.w ||
            pageY < expandedBounds.y ||
            pageY > expandedBounds.y + expandedBounds.h
        ) {
            continue
        }
        val points = stroke.points
        for (index in 0 until points.size - 1) {
            val p1 = points[index]
            val p2 = points[index + 1]
            val distance = pointToSegmentDistance(pageX, pageY, p1.x, p1.y, p2.x, p2.y)
            if (distance <= hitRadius) {
                return stroke
            }
        }
    }
    return null
}

private fun pointToSegmentDistance(
    px: Float,
    py: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) {
        return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
    }
    val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
    val clampedT = t.coerceIn(0f, 1f)
    val nearestX = x1 + clampedT * dx
    val nearestY = y1 + clampedT * dy
    return sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
}

private fun Brush.toInkBrush(
    viewTransform: ViewTransform,
    alphaMultiplier: Float = 1f,
): InkBrush {
    val family =
        when (tool) {
            Tool.PEN -> StockBrushes.pressurePenLatest
            Tool.HIGHLIGHTER -> StockBrushes.highlighterLatest
            Tool.ERASER -> StockBrushes.markerLatest
        }
    val size = viewTransform.pageWidthToScreen(baseWidth).coerceAtLeast(0.1f)
    val epsilon = (size * 0.15f).coerceAtLeast(0.1f)
    val baseColor = parseColor(color)
    val adjustedColor = applyAlpha(baseColor, alphaMultiplier)
    return InkBrush.createWithColorIntArgb(
        family,
        adjustedColor,
        size,
        epsilon,
    )
}

private fun applyAlpha(
    colorInt: Int,
    alphaMultiplier: Float,
): Int {
    val clampedMultiplier = alphaMultiplier.coerceIn(0f, 1f)
    val baseAlpha = (colorInt ushr 24) and 0xFF
    val adjustedAlpha = (baseAlpha * clampedMultiplier).toInt().coerceIn(0, 255)
    return (adjustedAlpha shl 24) or (colorInt and 0x00FFFFFF)
}

private class MotionPredictionAdapter private constructor(
    private val predictor: Any,
    private val recordMethod: Method,
    private val predictMethod: Method,
) {
    fun record(event: MotionEvent) {
        recordMethod.invoke(predictor, event)
    }

    fun predict(): MotionEvent? = predictMethod.invoke(predictor) as? MotionEvent

    companion object {
        fun create(context: Context): MotionPredictionAdapter? {
            val classNames =
                listOf(
                    "android.view.MotionEventPredictor",
                    "androidx.input.motionprediction.MotionEventPredictor",
                )
            for (className in classNames) {
                try {
                    val clazz = Class.forName(className)
                    val createMethod = clazz.getMethod("create", Context::class.java)
                    val predictor = createMethod.invoke(null, context) ?: continue
                    val recordMethod = clazz.getMethod("record", MotionEvent::class.java)
                    val predictMethod = clazz.getMethod("predict")
                    return MotionPredictionAdapter(predictor, recordMethod, predictMethod)
                } catch (_: Exception) {
                    continue
                }
            }
            return null
        }
    }
}

private fun Int.toInputToolType(): InputToolType =
    when (this) {
        MotionEvent.TOOL_TYPE_STYLUS -> InputToolType.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> InputToolType.STYLUS
        MotionEvent.TOOL_TYPE_FINGER -> InputToolType.TOUCH
        MotionEvent.TOOL_TYPE_MOUSE -> InputToolType.MOUSE
        else -> InputToolType.UNKNOWN
    }

private fun isSupportedToolType(toolType: Int): Boolean =
    toolType == MotionEvent.TOOL_TYPE_STYLUS ||
        toolType == MotionEvent.TOOL_TYPE_ERASER ||
        toolType == MotionEvent.TOOL_TYPE_FINGER

private fun isStylusToolType(toolType: Int): Boolean =
    toolType == MotionEvent.TOOL_TYPE_STYLUS ||
        toolType == MotionEvent.TOOL_TYPE_ERASER

private fun toolTypeToTool(
    toolType: Int,
    brush: Brush,
): Tool =
    if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
        Tool.ERASER
    } else {
        brush.tool
    }

private fun Brush.withToolType(toolType: Int): Brush =
    if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
        copy(tool = Tool.ERASER)
    } else {
        this
    }

private class HoverPreviewState {
    var isVisible by mutableStateOf(false)
    var x by mutableStateOf(0f)
    var y by mutableStateOf(0f)
    var tool by mutableStateOf(Tool.PEN)

    fun show(
        x: Float,
        y: Float,
        tool: Tool,
    ) {
        this.x = x
        this.y = y
        this.tool = tool
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}

private fun DrawScope.drawHoverPreview(
    hoverPreviewState: HoverPreviewState,
    brush: Brush,
    viewTransform: ViewTransform,
) {
    if (!hoverPreviewState.isVisible) return
    val size = viewTransform.pageWidthToScreen(brush.baseWidth).coerceAtLeast(0.1f)
    val radius = size / 2f
    val color =
        if (hoverPreviewState.tool == Tool.ERASER) {
            Color(0xFF6B6B6B)
        } else {
            Color(parseColor(brush.color))
        }
    val alpha = if (hoverPreviewState.tool == Tool.ERASER) 0.6f else 0.35f
    val strokeWidth = (size * 0.12f).coerceAtLeast(1f)
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = Offset(hoverPreviewState.x, hoverPreviewState.y),
        style = ComposeStroke(width = strokeWidth),
    )
}

fun DrawScope.drawStroke(
    stroke: Stroke,
    transform: ViewTransform,
) {
    if (stroke.points.size < 2) return
    val path = Path()
    val firstPoint = stroke.points.first()
    val (startX, startY) = transform.pageToScreen(firstPoint.x, firstPoint.y)
    path.moveTo(startX, startY)
    for (index in 1 until stroke.points.size) {
        val point = stroke.points[index]
        val (x, y) = transform.pageToScreen(point.x, point.y)
        path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = Color(parseColor(stroke.style.color)),
        style =
            ComposeStroke(
                width = transform.pageWidthToScreen(stroke.style.baseWidth),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
    )
}
