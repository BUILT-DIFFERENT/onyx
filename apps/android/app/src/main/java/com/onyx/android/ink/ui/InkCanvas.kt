package com.onyx.android.ink.ui

import android.graphics.Color.parseColor
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.ink.brush.Brush as InkBrush

@Composable
fun InkCanvas(
    strokes: List<Stroke>,
    viewTransform: ViewTransform,
    brush: Brush,
    onStrokeFinished: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBrush by rememberUpdatedState(brush)
    val currentTransform by rememberUpdatedState(viewTransform)
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)

    val activeStrokeIds = remember { mutableMapOf<Int, InProgressStrokeId>() }
    val activeStrokePoints = remember { mutableMapOf<Int, MutableList<StrokePoint>>() }
    val activeStrokeStartTimes = remember { mutableMapOf<Int, Long>() }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            strokes.forEach { stroke ->
                drawStroke(stroke, currentTransform)
            }
        }

        AndroidView(
            factory = { context ->
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
                            onStrokeFinished = currentOnStrokeFinished,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
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
    onStrokeFinished: (Stroke) -> Unit,
): Boolean {
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        -> {
            view.requestUnbufferedDispatch(event)
            val startTime = event.eventTime
            activeStrokeStartTimes[pointerId] = startTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            val strokeId = view.startStroke(strokeInput, brush.toInkBrush(viewTransform))
            activeStrokeIds[pointerId] = strokeId
            val points = mutableListOf<StrokePoint>()
            points.add(createStrokePoint(event, actionIndex, viewTransform))
            activeStrokePoints[pointerId] = points
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            val pointerCount = event.pointerCount
            for (index in 0 until pointerCount) {
                val movePointerId = event.getPointerId(index)
                val strokeId = activeStrokeIds[movePointerId] ?: continue
                view.addToStroke(event, movePointerId, strokeId)
                activeStrokePoints[movePointerId]?.add(
                    createStrokePoint(event, index, viewTransform),
                )
            }
            return true
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP,
        -> {
            val strokeId = activeStrokeIds[pointerId] ?: return false
            val startTime = activeStrokeStartTimes[pointerId] ?: event.eventTime
            val strokeInput = createStrokeInput(event, actionIndex, startTime)
            view.finishStroke(strokeInput, strokeId)
            val points = activeStrokePoints[pointerId].orEmpty().toMutableList()
            points.add(createStrokePoint(event, actionIndex, viewTransform))
            val finishedStroke = buildStroke(points, brush)
            onStrokeFinished(finishedStroke)
            activeStrokeIds.remove(pointerId)
            activeStrokePoints.remove(pointerId)
            activeStrokeStartTimes.remove(pointerId)
            return true
        }

        MotionEvent.ACTION_CANCEL -> {
            activeStrokeIds.values.forEach { strokeId ->
                view.cancelStroke(strokeId, event)
            }
            activeStrokeIds.clear()
            activeStrokePoints.clear()
            activeStrokeStartTimes.clear()
            return true
        }
    }

    return false
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

private fun Brush.toInkBrush(viewTransform: ViewTransform): InkBrush {
    val family =
        when (tool) {
            Tool.PEN -> StockBrushes.pressurePenLatest
            Tool.HIGHLIGHTER -> StockBrushes.highlighterLatest
        }
    val size = viewTransform.pageWidthToScreen(baseWidth).coerceAtLeast(0.1f)
    val epsilon = (size * 0.15f).coerceAtLeast(0.1f)
    return InkBrush.createWithColorIntArgb(
        family,
        parseColor(color),
        size,
        epsilon,
    )
}

private fun Int.toInputToolType(): InputToolType =
    when (this) {
        MotionEvent.TOOL_TYPE_STYLUS -> InputToolType.STYLUS
        MotionEvent.TOOL_TYPE_FINGER -> InputToolType.TOUCH
        MotionEvent.TOOL_TYPE_MOUSE -> InputToolType.MOUSE
        else -> InputToolType.UNKNOWN
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
