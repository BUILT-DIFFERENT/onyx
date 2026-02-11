package com.onyx.android.ink.ui

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokesView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasTouchRoutingTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun oneFingerTouch_pansInsteadOfDrawing() {
        val view = InProgressStrokesView(context)
        val runtime = createRuntime()
        val transformCalls = mutableListOf<TransformCall>()
        var strokeFinishedCount = 0
        var strokeErasedCount = 0
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
                onStrokeErased = { strokeErasedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 100L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 10f,
                y = 20f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 116L,
                action = MotionEvent.ACTION_MOVE,
                x = 22f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 132L,
                action = MotionEvent.ACTION_UP,
                x = 22f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, move, interaction, runtime))
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertEquals(1, transformCalls.size)
        val transform = transformCalls.single()
        assertEquals(1f, transform.zoomChange, DELTA)
        assertEquals(12f, transform.panChangeX, DELTA)
        assertEquals(24f, transform.panChangeY, DELTA)
        assertEquals(22f, transform.centroidX, DELTA)
        assertEquals(44f, transform.centroidY, DELTA)
        assertEquals(0, strokeFinishedCount)
        assertEquals(0, strokeErasedCount)
        assertTrue(runtime.activeStrokeIds.isEmpty())
        assertFalse(runtime.isSingleFingerPanning)
    }

    @Test
    fun stylusTouch_stillDrawsStroke() {
        val view = InProgressStrokesView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val transformCalls = mutableListOf<TransformCall>()
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 300L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 316L,
                action = MotionEvent.ACTION_MOVE,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 332L,
                action = MotionEvent.ACTION_UP,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(runtime.activeStrokeIds.containsKey(DEFAULT_POINTER_ID))
            assertTrue(handleTouchEvent(view, move, interaction, runtime))
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertEquals(1, strokeFinishedCount)
        assertTrue(transformCalls.isEmpty())
        assertFalse(runtime.isSingleFingerPanning)
        assertFalse(runtime.activeStrokeIds.containsKey(DEFAULT_POINTER_ID))
    }

    @Test
    fun fingerPan_transitionsToPinchZoom_whenSecondFingerAppears() {
        val view = InProgressStrokesView(context)
        val runtime = createRuntime()
        val transformCalls = mutableListOf<TransformCall>()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 500L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 10f,
                y = 10f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val panMove =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 516L,
                action = MotionEvent.ACTION_MOVE,
                x = 20f,
                y = 20f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val pointerDown =
            twoPointerEvent(
                downTime = downTime,
                eventTime = 532L,
                action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                firstPointerId = 0,
                firstToolType = MotionEvent.TOOL_TYPE_FINGER,
                firstX = 20f,
                firstY = 20f,
                secondPointerId = 1,
                secondToolType = MotionEvent.TOOL_TYPE_FINGER,
                secondX = 40f,
                secondY = 20f,
            )
        val pinchMove =
            twoPointerEvent(
                downTime = downTime,
                eventTime = 548L,
                action = MotionEvent.ACTION_MOVE,
                firstPointerId = 0,
                firstToolType = MotionEvent.TOOL_TYPE_FINGER,
                firstX = 22f,
                firstY = 20f,
                secondPointerId = 1,
                secondToolType = MotionEvent.TOOL_TYPE_FINGER,
                secondX = 50f,
                secondY = 20f,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, panMove, interaction, runtime))
            assertTrue(handleTouchEvent(view, pointerDown, interaction, runtime))
            assertTrue(runtime.isTransforming)
            assertFalse(runtime.isSingleFingerPanning)
            assertTrue(handleTouchEvent(view, pinchMove, interaction, runtime))
        } finally {
            down.recycle()
            panMove.recycle()
            pointerDown.recycle()
            pinchMove.recycle()
        }

        assertEquals(0, strokeFinishedCount)
        assertTrue(transformCalls.size >= 2)
        assertTrue(transformCalls.any { it.zoomChange > 1.0f + DELTA })
    }
}

private data class TransformCall(
    val zoomChange: Float,
    val panChangeX: Float,
    val panChangeY: Float,
    val centroidX: Float,
    val centroidY: Float,
)

private fun createRuntime(): InkCanvasRuntime =
    InkCanvasRuntime(
        activeStrokeIds = mutableMapOf(),
        activeStrokePoints = mutableMapOf(),
        activeStrokeStartTimes = mutableMapOf(),
        predictedStrokeIds = mutableMapOf(),
        hoverPreviewState = HoverPreviewState(),
    )

private fun createInteraction(
    onStrokeFinished: (com.onyx.android.ink.model.Stroke) -> Unit = {},
    onStrokeErased: (com.onyx.android.ink.model.Stroke) -> Unit = {},
    onTransformGesture: (Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
): InkCanvasInteraction =
    InkCanvasInteraction(
        brush = Brush(tool = Tool.PEN),
        viewTransform = ViewTransform.DEFAULT,
        strokes = emptyList(),
        onStrokeFinished = onStrokeFinished,
        onStrokeErased = onStrokeErased,
        onTransformGesture = onTransformGesture,
    )

private fun singlePointerEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
    toolType: Int,
): MotionEvent {
    val properties =
        MotionEvent.PointerProperties().apply {
            id = DEFAULT_POINTER_ID
            this.toolType = toolType
        }
    val coordinates =
        MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = DEFAULT_PRESSURE
            size = DEFAULT_SIZE
        }
    return MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        1,
        arrayOf(properties),
        arrayOf(coordinates),
        DEFAULT_META_STATE,
        DEFAULT_BUTTON_STATE,
        DEFAULT_X_PRECISION,
        DEFAULT_Y_PRECISION,
        DEFAULT_DEVICE_ID,
        DEFAULT_EDGE_FLAGS,
        InputDevice.SOURCE_TOUCHSCREEN,
        DEFAULT_FLAGS,
    )
}

private fun twoPointerEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    firstPointerId: Int,
    firstToolType: Int,
    firstX: Float,
    firstY: Float,
    secondPointerId: Int,
    secondToolType: Int,
    secondX: Float,
    secondY: Float,
): MotionEvent {
    val firstProperties =
        MotionEvent.PointerProperties().apply {
            id = firstPointerId
            toolType = firstToolType
        }
    val secondProperties =
        MotionEvent.PointerProperties().apply {
            id = secondPointerId
            toolType = secondToolType
        }
    val firstCoordinates =
        MotionEvent.PointerCoords().apply {
            x = firstX
            y = firstY
            pressure = DEFAULT_PRESSURE
            size = DEFAULT_SIZE
        }
    val secondCoordinates =
        MotionEvent.PointerCoords().apply {
            x = secondX
            y = secondY
            pressure = DEFAULT_PRESSURE
            size = DEFAULT_SIZE
        }
    return MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        2,
        arrayOf(firstProperties, secondProperties),
        arrayOf(firstCoordinates, secondCoordinates),
        DEFAULT_META_STATE,
        DEFAULT_BUTTON_STATE,
        DEFAULT_X_PRECISION,
        DEFAULT_Y_PRECISION,
        DEFAULT_DEVICE_ID,
        DEFAULT_EDGE_FLAGS,
        InputDevice.SOURCE_TOUCHSCREEN,
        DEFAULT_FLAGS,
    )
}

private const val DEFAULT_POINTER_ID = 0
private const val DEFAULT_META_STATE = 0
private const val DEFAULT_BUTTON_STATE = 0
private const val DEFAULT_DEVICE_ID = 0
private const val DEFAULT_EDGE_FLAGS = 0
private const val DEFAULT_FLAGS = 0
private const val DEFAULT_X_PRECISION = 1f
private const val DEFAULT_Y_PRECISION = 1f
private const val DEFAULT_PRESSURE = 0.5f
private const val DEFAULT_SIZE = 0.1f
private const val DELTA = 0.0001f
