package com.onyx.android.ink.ui

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onyx.android.ink.gl.GlInkSurfaceView
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.input.DoubleFingerMode
import com.onyx.android.input.InputSettings
import com.onyx.android.input.SingleFingerMode
import com.onyx.android.input.StylusButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
class InkCanvasTouchRoutingTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun oneFingerTouch_pansInsteadOfDrawing() {
        val view = GlInkSurfaceView(context)
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
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        var finishedStrokeId: String? = null
        val transformCalls = mutableListOf<TransformCall>()
        val interaction =
            createInteraction(
                onStrokeFinished = { stroke ->
                    strokeFinishedCount += 1
                    finishedStrokeId = stroke.id
                },
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
                source = InputDevice.SOURCE_STYLUS,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 316L,
                action = MotionEvent.ACTION_MOVE,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 332L,
                action = MotionEvent.ACTION_UP,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
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
        assertNotNull(finishedStrokeId)
    }

    @Test
    fun stylusSourceUnknownToolType_routesToDrawingNotPan() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val transformCalls = mutableListOf<TransformCall>()
        val interaction =
            createInteraction(
                allowFingerGestures = false,
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 340L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN,
                source = InputDevice.SOURCE_STYLUS,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 356L,
                action = MotionEvent.ACTION_MOVE,
                x = 42f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 372L,
                action = MotionEvent.ACTION_UP,
                x = 42f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN,
                source = InputDevice.SOURCE_STYLUS,
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

        assertEquals(1, strokeFinishedCount)
        assertTrue(transformCalls.isEmpty())
    }

    @Test
    fun readOnlyMode_blocksStylusEditingButKeepsPanGestures() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val transformCalls = mutableListOf<TransformCall>()
        val readOnlyInteraction =
            createInteraction(
                allowEditing = false,
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 350L
        val stylusDown =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val stylusMove =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 366L,
                action = MotionEvent.ACTION_MOVE,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val stylusUp =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 382L,
                action = MotionEvent.ACTION_UP,
                x = 40f,
                y = 42f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val fingerDownTime = 500L
        val fingerDown =
            singlePointerEvent(
                downTime = fingerDownTime,
                eventTime = fingerDownTime,
                action = MotionEvent.ACTION_DOWN,
                x = 10f,
                y = 20f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val fingerMove =
            singlePointerEvent(
                downTime = fingerDownTime,
                eventTime = 516L,
                action = MotionEvent.ACTION_MOVE,
                x = 22f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val fingerUp =
            singlePointerEvent(
                downTime = fingerDownTime,
                eventTime = 532L,
                action = MotionEvent.ACTION_UP,
                x = 22f,
                y = 44f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )

        try {
            assertTrue(handleTouchEvent(view, stylusDown, readOnlyInteraction, runtime))
            assertFalse(runtime.activeStrokeIds.containsKey(DEFAULT_POINTER_ID))
            assertFalse(handleTouchEvent(view, stylusMove, readOnlyInteraction, runtime))
            assertFalse(handleTouchEvent(view, stylusUp, readOnlyInteraction, runtime))
            assertTrue(handleTouchEvent(view, fingerDown, readOnlyInteraction, runtime))
            assertTrue(handleTouchEvent(view, fingerMove, readOnlyInteraction, runtime))
            assertTrue(handleTouchEvent(view, fingerUp, readOnlyInteraction, runtime))
        } finally {
            stylusDown.recycle()
            stylusMove.recycle()
            stylusUp.recycle()
            fingerDown.recycle()
            fingerMove.recycle()
            fingerUp.recycle()
        }

        assertEquals(0, strokeFinishedCount)
        assertFalse(transformCalls.isEmpty())
    }

    @Test
    fun cancelEvent_clearsFinishedStrokeBridgeState() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val interaction =
            createInteraction(
                onStrokeFinished = { stroke -> runtime.pendingCommittedStrokes[stroke.id] = stroke },
            )

        val downTime = 900L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 16f,
                y = 16f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 916L,
                action = MotionEvent.ACTION_UP,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val cancel =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 932L,
                action = MotionEvent.ACTION_CANCEL,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
            assertTrue(handleTouchEvent(view, cancel, interaction, runtime))
        } finally {
            down.recycle()
            up.recycle()
            cancel.recycle()
        }

        assertTrue(runtime.pendingCommittedStrokes.isNotEmpty())
    }

    @Test
    fun rapidPenUpDown_sequenceMaintainsConsistency() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val renderFinishedStrokeIds = mutableListOf<Long>()
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
                onStrokeRenderFinished = { strokeId -> renderFinishedStrokeIds += strokeId },
            )

        var eventTime = 2600L
        repeat(5) { cycle ->
            val downTime = eventTime
            val baseX = 40f + (cycle * 6f)
            val baseY = 50f + (cycle * 4f)
            val down =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_DOWN,
                    x = baseX,
                    y = baseY,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 8L
            val move =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_MOVE,
                    x = baseX + 6f,
                    y = baseY + 3f,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 8L
            val up =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_UP,
                    x = baseX + 8f,
                    y = baseY + 5f,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 8L

            try {
                assertTrue(handleTouchEvent(view, down, interaction, runtime))
                assertTrue(handleTouchEvent(view, move, interaction, runtime))
                assertTrue(handleTouchEvent(view, up, interaction, runtime))
            } finally {
                down.recycle()
                move.recycle()
                up.recycle()
            }
        }

        assertEquals(5, strokeFinishedCount)
        assertEquals(5, renderFinishedStrokeIds.size)
        assertTrue(runtime.activeStrokeIds.isEmpty())
        assertTrue(runtime.activePointerModes.isEmpty())
        assertTrue(runtime.activeStrokePoints.isEmpty())
        assertTrue(runtime.activeStrokeBrushes.isEmpty())
        assertTrue(runtime.activeStrokeStartTimes.isEmpty())
    }

    @Test
    fun rapidStrokeSequence_noActiveStrokesLeaked() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
            )

        var eventTime = 3000L
        repeat(10) { cycle ->
            val downTime = eventTime
            val baseX = 25f + (cycle * 3f)
            val baseY = 35f + (cycle * 2f)
            val down =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_DOWN,
                    x = baseX,
                    y = baseY,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 6L
            val move =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_MOVE,
                    x = baseX + 4f,
                    y = baseY + 4f,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 6L
            val up =
                singlePointerEvent(
                    downTime = downTime,
                    eventTime = eventTime,
                    action = MotionEvent.ACTION_UP,
                    x = baseX + 7f,
                    y = baseY + 6f,
                    toolType = MotionEvent.TOOL_TYPE_STYLUS,
                    source = InputDevice.SOURCE_STYLUS,
                )
            eventTime += 6L

            try {
                assertTrue(handleTouchEvent(view, down, interaction, runtime))
                assertTrue(handleTouchEvent(view, move, interaction, runtime))
                assertTrue(handleTouchEvent(view, up, interaction, runtime))
            } finally {
                down.recycle()
                move.recycle()
                up.recycle()
            }
        }

        assertEquals(10, strokeFinishedCount)
        assertTrue(runtime.activeStrokeIds.isEmpty())
        assertTrue(runtime.activePointerModes.isEmpty())
        assertTrue(runtime.stylusPointerIds.isEmpty())
        assertTrue(runtime.activeStrokePoints.isEmpty())
        assertTrue(runtime.activeStrokeBrushes.isEmpty())
        assertTrue(runtime.activeStrokeStartTimes.isEmpty())
        assertFalse(runtime.hasActiveStrokeInputs())
    }

    @Test
    fun strokeAfterCancel_clearsStateAndStartsFresh() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                onStrokeFinished = { strokeFinishedCount += 1 },
            )

        val downTime = 3600L
        val firstDown =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 60f,
                y = 60f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val firstMove =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime + 12L,
                action = MotionEvent.ACTION_MOVE,
                x = 68f,
                y = 67f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val cancel =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime + 24L,
                action = MotionEvent.ACTION_CANCEL,
                x = 68f,
                y = 67f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        val secondDownTime = downTime + 40L
        val secondDown =
            singlePointerEvent(
                downTime = secondDownTime,
                eventTime = secondDownTime,
                action = MotionEvent.ACTION_DOWN,
                x = 75f,
                y = 75f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val secondMove =
            singlePointerEvent(
                downTime = secondDownTime,
                eventTime = secondDownTime + 12L,
                action = MotionEvent.ACTION_MOVE,
                x = 84f,
                y = 82f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val secondUp =
            singlePointerEvent(
                downTime = secondDownTime,
                eventTime = secondDownTime + 24L,
                action = MotionEvent.ACTION_UP,
                x = 89f,
                y = 86f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, firstDown, interaction, runtime))
            assertTrue(handleTouchEvent(view, firstMove, interaction, runtime))
            assertTrue(handleTouchEvent(view, cancel, interaction, runtime))
            assertTrue(runtime.activeStrokeIds.isEmpty())
            assertTrue(runtime.activePointerModes.isEmpty())
            assertTrue(runtime.activeStrokePoints.isEmpty())
            assertTrue(runtime.activeStrokeBrushes.isEmpty())
            assertTrue(runtime.activeStrokeStartTimes.isEmpty())

            assertTrue(handleTouchEvent(view, secondDown, interaction, runtime))
            assertTrue(handleTouchEvent(view, secondMove, interaction, runtime))
            assertTrue(handleTouchEvent(view, secondUp, interaction, runtime))
        } finally {
            firstDown.recycle()
            firstMove.recycle()
            cancel.recycle()
            secondDown.recycle()
            secondMove.recycle()
            secondUp.recycle()
        }

        assertEquals(1, strokeFinishedCount)
        assertTrue(runtime.activeStrokeIds.isEmpty())
        assertTrue(runtime.activePointerModes.isEmpty())
        assertTrue(runtime.stylusPointerIds.isEmpty())
        assertTrue(runtime.activeStrokePoints.isEmpty())
        assertTrue(runtime.activeStrokeBrushes.isEmpty())
        assertTrue(runtime.activeStrokeStartTimes.isEmpty())
        assertFalse(runtime.hasActiveStrokeInputs())
    }

    @Test
    fun fingerPan_transitionsToPinchZoom_whenSecondFingerAppears() {
        val view = GlInkSurfaceView(context)
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

    @Test
    fun singleFingerIgnore_doesNotDrawOrPan() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val transformCalls = mutableListOf<TransformCall>()
        val interaction =
            createInteraction(
                inputSettings =
                    InputSettings(
                        singleFingerMode = SingleFingerMode.IGNORE,
                        doubleFingerMode = DoubleFingerMode.ZOOM_PAN,
                    ),
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 1400L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 40f,
                y = 40f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1420L,
                action = MotionEvent.ACTION_MOVE,
                x = 72f,
                y = 70f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1440L,
                action = MotionEvent.ACTION_UP,
                x = 72f,
                y = 70f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )

        try {
            assertFalse(handleTouchEvent(view, down, interaction, runtime))
            assertFalse(handleTouchEvent(view, move, interaction, runtime))
            assertFalse(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertEquals(0, strokeFinishedCount)
        assertTrue(transformCalls.isEmpty())
        assertFalse(runtime.isSingleFingerPanning)
        assertFalse(runtime.isTransforming)
    }

    @Test
    fun doubleFingerIgnore_blocksPinchAndSecondFingerDrawing() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val transformCalls = mutableListOf<TransformCall>()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                inputSettings =
                    InputSettings(
                        singleFingerMode = SingleFingerMode.DRAW,
                        doubleFingerMode = DoubleFingerMode.IGNORE,
                    ),
                onStrokeFinished = { strokeFinishedCount += 1 },
                onTransformGesture = { zoom, panX, panY, centroidX, centroidY ->
                    transformCalls += TransformCall(zoom, panX, panY, centroidX, centroidY)
                },
            )

        val downTime = 1600L
        val firstDown =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 20f,
                y = 20f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )
        val secondDown =
            twoPointerEvent(
                downTime = downTime,
                eventTime = 1616L,
                action = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                firstPointerId = 0,
                firstToolType = MotionEvent.TOOL_TYPE_FINGER,
                firstX = 20f,
                firstY = 20f,
                secondPointerId = 1,
                secondToolType = MotionEvent.TOOL_TYPE_FINGER,
                secondX = 70f,
                secondY = 20f,
            )
        val move =
            twoPointerEvent(
                downTime = downTime,
                eventTime = 1632L,
                action = MotionEvent.ACTION_MOVE,
                firstPointerId = 0,
                firstToolType = MotionEvent.TOOL_TYPE_FINGER,
                firstX = 28f,
                firstY = 24f,
                secondPointerId = 1,
                secondToolType = MotionEvent.TOOL_TYPE_FINGER,
                secondX = 76f,
                secondY = 26f,
            )
        val secondUp =
            twoPointerEvent(
                downTime = downTime,
                eventTime = 1648L,
                action = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                firstPointerId = 0,
                firstToolType = MotionEvent.TOOL_TYPE_FINGER,
                firstX = 28f,
                firstY = 24f,
                secondPointerId = 1,
                secondToolType = MotionEvent.TOOL_TYPE_FINGER,
                secondX = 76f,
                secondY = 26f,
            )
        val firstUp =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1660L,
                action = MotionEvent.ACTION_UP,
                x = 28f,
                y = 24f,
                toolType = MotionEvent.TOOL_TYPE_FINGER,
            )

        try {
            assertTrue(handleTouchEvent(view, firstDown, interaction, runtime))
            assertFalse(handleTouchEvent(view, secondDown, interaction, runtime))
            assertFalse(handleTouchEvent(view, move, interaction, runtime))
            assertFalse(runtime.isTransforming)
            assertFalse(runtime.isSingleFingerPanning)
            assertFalse(handleTouchEvent(view, secondUp, interaction, runtime))
            assertTrue(handleTouchEvent(view, firstUp, interaction, runtime))
        } finally {
            firstDown.recycle()
            secondDown.recycle()
            move.recycle()
            secondUp.recycle()
            firstUp.recycle()
        }

        assertEquals(1, strokeFinishedCount)
        assertTrue(transformCalls.isEmpty())
    }

    @Test
    fun stylusLongHoldEraser_activatesAfterDelay() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val stylusStates = mutableListOf<Boolean>()
        val interaction =
            createInteraction(
                inputSettings =
                    InputSettings(
                        stylusLongHoldAction = StylusButtonAction.ERASER_HOLD,
                    ),
                onStylusButtonEraserActiveChanged = { stylusStates += it },
            )

        val downTime = 1800L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 100f,
                y = 100f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val preHoldMove =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime + 200L,
                action = MotionEvent.ACTION_MOVE,
                x = 103f,
                y = 102f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val postHoldMove =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime + 420L,
                action = MotionEvent.ACTION_MOVE,
                x = 107f,
                y = 106f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime + 460L,
                action = MotionEvent.ACTION_UP,
                x = 107f,
                y = 106f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, preHoldMove, interaction, runtime))
            assertFalse(runtime.isStylusButtonEraserActive)
            assertTrue(handleTouchEvent(view, postHoldMove, interaction, runtime))
            assertTrue(runtime.isStylusButtonEraserActive)
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            preHoldMove.recycle()
            postHoldMove.recycle()
            up.recycle()
        }

        assertTrue(stylusStates.contains(true))
        assertTrue(stylusStates.contains(false))
    }

    @Test
    fun stylusButtonEraser_latchesForCurrentPointerUntilLift() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val erased = mutableListOf<Stroke>()
        val stylusButtonStates = mutableListOf<Boolean>()
        val interaction =
            createInteraction(
                strokes = listOf(sampleStroke()),
                onStrokeErased = { stroke -> erased += stroke },
                onStylusButtonEraserActiveChanged = { isActive -> stylusButtonStates += isActive },
            )

        val downTime = 1200L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 50f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                source = InputDevice.SOURCE_STYLUS,
            )
        val moveButtonReleased =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1216L,
                action = MotionEvent.ACTION_MOVE,
                x = 52f,
                y = 52f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = 0,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1232L,
                action = MotionEvent.ACTION_UP,
                x = 52f,
                y = 52f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = 0,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertEquals(PointerMode.ERASE, runtime.activePointerModes[DEFAULT_POINTER_ID])
            assertTrue(handleTouchEvent(view, moveButtonReleased, interaction, runtime))
            assertEquals(PointerMode.ERASE, runtime.activePointerModes[DEFAULT_POINTER_ID])
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            moveButtonReleased.recycle()
            up.recycle()
        }

        assertTrue(erased.isNotEmpty())
        assertTrue(stylusButtonStates.contains(true))
        assertTrue(stylusButtonStates.contains(false))
        assertTrue(runtime.activePointerModes.isEmpty())
        assertTrue(runtime.activeStrokeIds.isEmpty())
    }

    @Test
    fun eraserErasesOnDown() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val erased = mutableListOf<Stroke>()
        val interaction =
            createInteraction(
                strokes = listOf(sampleStroke()),
                onStrokeErased = { stroke -> erased += stroke },
            )

        val downTime = 1350L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 50f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                source = InputDevice.SOURCE_STYLUS,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1366L,
                action = MotionEvent.ACTION_MOVE,
                x = 52f,
                y = 52f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, move, interaction, runtime))
        } finally {
            down.recycle()
            move.recycle()
        }

        assertTrue(erased.isNotEmpty())
    }

    @Test
    fun offPageStrokeStart_isIgnored() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                pageWidth = 100f,
                pageHeight = 100f,
                onStrokeFinished = { strokeFinishedCount += 1 },
            )

        val downTime = 1600L
        val downOutside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 150f,
                y = 150f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val moveInside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1616L,
                action = MotionEvent.ACTION_MOVE,
                x = 50f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val upInside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1632L,
                action = MotionEvent.ACTION_UP,
                x = 50f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, downOutside, interaction, runtime))
            assertFalse(runtime.activeStrokeIds.containsKey(DEFAULT_POINTER_ID))
            assertFalse(runtime.activePointerModes.containsKey(DEFAULT_POINTER_ID))
            assertFalse(handleTouchEvent(view, moveInside, interaction, runtime))
            assertFalse(handleTouchEvent(view, upInside, interaction, runtime))
        } finally {
            downOutside.recycle()
            moveInside.recycle()
            upInside.recycle()
        }

        assertEquals(0, strokeFinishedCount)
    }

    @Test
    fun nearEdgeStrokeStart_isAccepted() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        var strokeFinishedCount = 0
        val interaction =
            createInteraction(
                pageWidth = 100f,
                pageHeight = 100f,
                onStrokeFinished = { strokeFinishedCount += 1 },
            )

        val downTime = 1750L
        val downNearEdge =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 101f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val moveInside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1766L,
                action = MotionEvent.ACTION_MOVE,
                x = 98f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val upInside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1782L,
                action = MotionEvent.ACTION_UP,
                x = 98f,
                y = 50f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, downNearEdge, interaction, runtime))
            assertTrue(handleTouchEvent(view, moveInside, interaction, runtime))
            assertTrue(handleTouchEvent(view, upInside, interaction, runtime))
        } finally {
            downNearEdge.recycle()
            moveInside.recycle()
            upInside.recycle()
        }

        assertEquals(1, strokeFinishedCount)
    }

    @Test
    fun strokePoints_areClampedToPageBounds() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val finishedStrokes = mutableListOf<Stroke>()
        val interaction =
            createInteraction(
                pageWidth = 100f,
                pageHeight = 100f,
                onStrokeFinished = { stroke -> finishedStrokes += stroke },
            )

        val downTime = 1900L
        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 95f,
                y = 95f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val moveOutside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1916L,
                action = MotionEvent.ACTION_MOVE,
                x = 140f,
                y = 160f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val upOutside =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 1932L,
                action = MotionEvent.ACTION_UP,
                x = 160f,
                y = 180f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, moveOutside, interaction, runtime))
            assertTrue(handleTouchEvent(view, upOutside, interaction, runtime))
        } finally {
            down.recycle()
            moveOutside.recycle()
            upOutside.recycle()
        }

        val stroke = finishedStrokes.single()
        assertTrue(stroke.points.all { point -> point.x in 0f..100f })
        assertTrue(stroke.points.all { point -> point.y in 0f..100f })
        val lastPoint = stroke.points.last()
        assertEquals(100f, lastPoint.x, DELTA)
        assertEquals(100f, lastPoint.y, DELTA)
    }

    @Test
    fun stylusHoverButton_updatesTransientEraserState() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val stylusButtonStates = mutableListOf<Boolean>()
        val interaction =
            createInteraction(
                onStylusButtonEraserActiveChanged = { isActive ->
                    stylusButtonStates += isActive
                },
            )
        val hoverDown =
            singlePointerEvent(
                downTime = 2100L,
                eventTime = 2100L,
                action = MotionEvent.ACTION_HOVER_MOVE,
                x = 32f,
                y = 32f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY,
                source = InputDevice.SOURCE_STYLUS,
                distance = 1f,
            )
        val hoverUp =
            singlePointerEvent(
                downTime = 2100L,
                eventTime = 2116L,
                action = MotionEvent.ACTION_HOVER_MOVE,
                x = 34f,
                y = 34f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                buttonState = 0,
                source = InputDevice.SOURCE_STYLUS,
                distance = 1f,
            )

        try {
            assertTrue(handleGenericMotionEvent(view, hoverDown, interaction, runtime))
            assertTrue(handleGenericMotionEvent(view, hoverUp, interaction, runtime))
        } finally {
            hoverDown.recycle()
            hoverUp.recycle()
        }

        assertTrue(stylusButtonStates.contains(true))
        assertTrue(stylusButtonStates.contains(false))
    }

    @Test
    fun predictedPoints_doNotLeakIntoCommittedStroke() {
        val view = GlInkSurfaceView(context)
        val runtime = createRuntime()
        val finishedStrokes = mutableListOf<Stroke>()
        val interaction =
            createInteraction(
                onStrokeFinished = { stroke -> finishedStrokes += stroke },
            )
        val downTime = 2400L
        val predictedMoveEvent =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 2416L,
                action = MotionEvent.ACTION_MOVE,
                x = 320f,
                y = 340f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        runtime.motionPredictionAdapter = createMotionPredictionAdapter(predictedMoveEvent)

        val down =
            singlePointerEvent(
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = 30f,
                y = 30f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val move =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 2416L,
                action = MotionEvent.ACTION_MOVE,
                x = 40f,
                y = 45f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )
        val up =
            singlePointerEvent(
                downTime = downTime,
                eventTime = 2432L,
                action = MotionEvent.ACTION_UP,
                x = 42f,
                y = 48f,
                toolType = MotionEvent.TOOL_TYPE_STYLUS,
                source = InputDevice.SOURCE_STYLUS,
            )

        try {
            assertTrue(handleTouchEvent(view, down, interaction, runtime))
            assertTrue(handleTouchEvent(view, move, interaction, runtime))
            assertTrue(runtime.predictedStrokeIds.containsKey(DEFAULT_POINTER_ID))
            assertTrue(handleTouchEvent(view, up, interaction, runtime))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        assertTrue(runtime.predictedStrokeIds.isEmpty())
        val stroke = finishedStrokes.single()
        assertTrue(stroke.points.isNotEmpty())
        // Predicted coordinates (320, 340) must never be committed to final stroke points.
        assertTrue(stroke.points.none { point -> point.x > 200f || point.y > 200f })
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
        activePointerModes = mutableMapOf(),
        stylusPointerIds = mutableSetOf(),
        activeStrokePoints = mutableMapOf(),
        activeStrokeBrushes = mutableMapOf(),
        activeStrokeStartTimes = mutableMapOf(),
        predictedStrokeIds = mutableMapOf(),
        eraserLastPagePoints = mutableMapOf(),
        pendingCommittedStrokes = mutableMapOf(),
        hoverPreviewState = HoverPreviewState(),
        finishedStrokePathCache = mutableMapOf(),
        stylusLongHoldStartTimes = mutableMapOf(),
        stylusLongHoldActivePointerIds = mutableSetOf(),
    )

private fun createInteraction(
    strokes: List<Stroke> = emptyList(),
    pageWidth: Float = 1000f,
    pageHeight: Float = 1000f,
    allowEditing: Boolean = true,
    allowFingerGestures: Boolean = true,
    inputSettings: InputSettings = InputSettings(),
    onStrokeFinished: (Stroke) -> Unit = {},
    onStrokeErased: (Stroke) -> Unit = {},
    onTransformGesture: (Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _ -> },
    onPanGestureEnd: (Float, Float) -> Unit = { _, _ -> },
    onStylusButtonEraserActiveChanged: (Boolean) -> Unit = {},
    onStrokeRenderFinished: (Long) -> Unit = {},
): InkCanvasInteraction =
    InkCanvasInteraction(
        brush = Brush(tool = Tool.PEN),
        lassoSelection = LassoSelection(),
        viewTransform = ViewTransform.DEFAULT,
        strokes = strokes,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        allowEditing = allowEditing,
        allowFingerGestures = allowFingerGestures,
        inputSettings = inputSettings,
        onStrokeFinished = onStrokeFinished,
        onStrokeErased = onStrokeErased,
        onTransformGesture = onTransformGesture,
        onPanGestureEnd = onPanGestureEnd,
        onStylusButtonEraserActiveChanged = onStylusButtonEraserActiveChanged,
        onStrokeRenderFinished = onStrokeRenderFinished,
    )

private fun singlePointerEvent(
    downTime: Long,
    eventTime: Long,
    action: Int,
    x: Float,
    y: Float,
    toolType: Int,
    buttonState: Int = DEFAULT_BUTTON_STATE,
    source: Int = InputDevice.SOURCE_TOUCHSCREEN,
    distance: Float = 0f,
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
            setAxisValue(MotionEvent.AXIS_DISTANCE, distance)
        }
    return MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        1,
        arrayOf(properties),
        arrayOf(coordinates),
        DEFAULT_META_STATE,
        buttonState,
        DEFAULT_X_PRECISION,
        DEFAULT_Y_PRECISION,
        DEFAULT_DEVICE_ID,
        DEFAULT_EDGE_FLAGS,
        source,
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

private fun sampleStroke(): Stroke =
    Stroke(
        id = "sample-stroke",
        points =
            listOf(
                StrokePoint(x = 48f, y = 48f, t = 1L),
                StrokePoint(x = 52f, y = 52f, t = 2L),
            ),
        style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
        bounds = StrokeBounds(x = 48f, y = 48f, w = 4f, h = 4f),
        createdAt = 2L,
    )

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

private class FakeMotionPredictor(
    private var nextPrediction: MotionEvent?,
) {
    fun record(
        @Suppress("UNUSED_PARAMETER") event: MotionEvent,
    ) {
    }

    fun predict(): MotionEvent? = nextPrediction.also { nextPrediction = null }
}

private fun createMotionPredictionAdapter(predictedEvent: MotionEvent): MotionPredictionAdapter {
    val predictor = FakeMotionPredictor(predictedEvent)
    val constructor =
        MotionPredictionAdapter::class.java.getDeclaredConstructor(
            Any::class.java,
            Method::class.java,
            Method::class.java,
        )
    constructor.isAccessible = true
    return constructor.newInstance(
        predictor,
        FakeMotionPredictor::class.java.getDeclaredMethod("record", MotionEvent::class.java),
        FakeMotionPredictor::class.java.getDeclaredMethod("predict"),
    ) as MotionPredictionAdapter
}
