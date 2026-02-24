@file:Suppress("FunctionName")

package com.onyx.android.ink.ui

import android.content.Context
import android.os.Trace
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.onyx.android.config.FeatureFlag
import com.onyx.android.config.FeatureFlagStore
import com.onyx.android.ink.gl.GlHoverOverlay
import com.onyx.android.ink.gl.GlInkSurfaceView
import com.onyx.android.ink.gl.GlOverlayState
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.ViewTransform
import com.onyx.android.input.InputSettings
import com.onyx.android.input.LatencyOptimizationMode
import android.graphics.Path as AndroidPath

internal enum class PointerMode {
    DRAW,
    ERASE,
}

private const val HANDOFF_FRAME_DELAY = 2
private const val GL_HOVER_ERASER_COLOR = 0xFF6B6B6B.toInt()
private const val GL_HOVER_ERASER_ALPHA = 0.6f
private const val GL_HOVER_PEN_ALPHA = 0.35f
private const val GL_MIN_HOVER_SCREEN_RADIUS = 0.1f
private const val FAST_MODE_SMOOTHING_MULTIPLIER = 0.6f

@Suppress("LongParameterList")
internal class InkCanvasRuntime(
    val activeStrokeIds: MutableMap<Int, Long>,
    val activePointerModes: MutableMap<Int, PointerMode>,
    val stylusPointerIds: MutableSet<Int>,
    val activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    val activeStrokeBrushes: MutableMap<Int, Brush>,
    val activeStrokeStartTimes: MutableMap<Int, Long>,
    val predictedStrokeIds: MutableMap<Int, Long>,
    val eraserLastPagePoints: MutableMap<Int, Pair<Float, Float>>,
    val pendingCommittedStrokes: MutableMap<String, Stroke>,
    val hoverPreviewState: HoverPreviewState,
    val finishedStrokePathCache: MutableMap<String, StrokePathCacheEntry>,
    val stylusLongHoldStartTimes: MutableMap<Int, Long>,
    val stylusLongHoldActivePointerIds: MutableSet<Int>,
) {
    var activeStrokeRenderVersion by mutableIntStateOf(0)
    var motionPredictionAdapter: MotionPredictionAdapter? = null
    var isStylusButtonEraserActive = false
    var isTransforming = false
    var previousTransformDistance = 0f
    var previousTransformCentroidX = 0f
    var previousTransformCentroidY = 0f
    var transformPointerIdA = MotionEvent.INVALID_POINTER_ID
    var transformPointerIdB = MotionEvent.INVALID_POINTER_ID
    var smoothedTransformDistance = 0f
    var smoothedTransformCentroidX = 0f
    var smoothedTransformCentroidY = 0f
    var isSingleFingerPanning = false
    var singleFingerPanPointerId = MotionEvent.INVALID_POINTER_ID
    var previousSingleFingerPanX = 0f
    var previousSingleFingerPanY = 0f
    var panVelocityTracker: VelocityTracker? = null
    var lastFingerTapTimeMs = 0L
    var lastFingerTapX = 0f
    var lastFingerTapY = 0f
    var lastStylusTapTimeMs = 0L
    var lastStylusTapX = 0f
    var lastStylusTapY = 0f
    var isStylusToggleEraserEnabled = false
    var isStylusToggleButtonPressed = false
    var multiFingerTapStartTimeMs = 0L
    var multiFingerTapStartCentroidX = 0f
    var multiFingerTapStartCentroidY = 0f
    var multiFingerTapMaxPointerCount = 0
    var multiFingerTapMaxMovementPx = 0f

    fun invalidateActiveStrokeRender() {
        activeStrokeRenderVersion += 1
    }

    fun hasActiveStrokeInputs(): Boolean =
        activeStrokeIds.isNotEmpty() ||
            activeStrokePoints.isNotEmpty() ||
            activeStrokeBrushes.isNotEmpty() ||
            activeStrokeStartTimes.isNotEmpty()
}

data class InkCanvasState(
    val strokes: List<Stroke>,
    val viewTransform: ViewTransform,
    val brush: Brush,
    val lassoSelection: LassoSelection = LassoSelection(),
    val isSegmentEraserEnabled: Boolean = false,
    val eraseStrokePredicate: (Stroke) -> Boolean = { true },
    val pageWidth: Float,
    val pageHeight: Float,
    val allowEditing: Boolean = true,
    val allowFingerGestures: Boolean = true,
    val inputSettings: InputSettings = InputSettings(),
)

data class InkCanvasCallbacks(
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
    val onSwitchToPenShortcut: () -> Unit = {},
    val onSwitchToEraserShortcut: () -> Unit = {},
    val onSwitchToLastToolShortcut: () -> Unit = {},
    val onDoubleTapGesture: () -> Unit = {},
    val onStylusButtonEraserActiveChanged: (Boolean) -> Unit,
)

@Composable
@Suppress("LongMethod")
fun InkCanvas(
    state: InkCanvasState,
    callbacks: InkCanvasCallbacks,
    modifier: Modifier = Modifier,
) {
    val currentBrush by rememberUpdatedState(state.brush)
    val currentSegmentEraserEnabled by rememberUpdatedState(state.isSegmentEraserEnabled)
    val currentEraseStrokePredicate by rememberUpdatedState(state.eraseStrokePredicate)
    val currentLassoSelection by rememberUpdatedState(state.lassoSelection)
    val currentTransform by rememberUpdatedState(state.viewTransform)
    val currentStrokes by rememberUpdatedState(state.strokes)
    val currentPageWidth by rememberUpdatedState(state.pageWidth)
    val currentPageHeight by rememberUpdatedState(state.pageHeight)
    val currentAllowEditing by rememberUpdatedState(state.allowEditing)
    val currentAllowFingerGestures by rememberUpdatedState(state.allowFingerGestures)
    val currentInputSettings by rememberUpdatedState(state.inputSettings)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val currentLatencyMode by rememberUpdatedState(state.inputSettings.latencyOptimizationMode)
    val context = LocalContext.current
    val flagStore = remember(context) { FeatureFlagStore.getInstance(context) }
    val runtimeBrush by rememberUpdatedState(adjustBrushForLatencyMode(currentBrush, currentLatencyMode))
    val predictionEnabledForMode by rememberUpdatedState(resolvePredictionEnabled(flagStore, currentLatencyMode))
    val hoverPreviewState = remember { HoverPreviewState() }

    val runtime =
        remember {
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
                hoverPreviewState = hoverPreviewState,
                finishedStrokePathCache = mutableMapOf(),
                stylusLongHoldStartTimes = mutableMapOf(),
                stylusLongHoldActivePointerIds = mutableSetOf(),
            )
        }

    val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()
    val persistedStrokes = currentStrokes
    val pendingStrokes =
        runtime.pendingCommittedStrokes.values.filter { pending ->
            pending.id !in persistedStrokeIds
        }
    val mergedStrokes =
        LinkedHashMap<String, Stroke>(persistedStrokes.size + pendingStrokes.size)
            .apply {
                persistedStrokes.forEach { stroke -> put(stroke.id, stroke) }
                pendingStrokes.forEach { stroke -> put(stroke.id, stroke) }
            }.values
            .toList()

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                syncMotionPredictionAdapter(
                    runtime = runtime,
                    context = context,
                    enabled = predictionEnabledForMode,
                )
                GlInkSurfaceView(context).apply {
                    // GL view handles all stroke rendering and touch input with low-latency updates.
                    alpha = 1f
                    eagerInit()
                    val onFinished = { stroke: Stroke ->
                        runtime.pendingCommittedStrokes[stroke.id] = stroke
                        currentCallbacks.onStrokeFinished(stroke)
                    }
                    val onErased = { stroke: Stroke ->
                        runtime.eraserLastPagePoints.clear()
                        runtime.pendingCommittedStrokes.remove(stroke.id)
                        currentCallbacks.onStrokeErased(stroke)
                    }
                    val onSplit = { original: Stroke, segments: List<Stroke> ->
                        runtime.pendingCommittedStrokes.remove(original.id)
                        segments.forEach { segment ->
                            runtime.pendingCommittedStrokes[segment.id] = segment
                        }
                        currentCallbacks.onStrokeSplit(original, segments)
                    }
                    val onStrokeRenderFinished = { strokeId: Long ->
                        scheduleFrameWaitedRemoval(
                            view = this,
                            strokeIds = setOf(strokeId),
                            framesRemaining = HANDOFF_FRAME_DELAY,
                            runtime = runtime,
                        )
                    }
                    setOnTouchListener { _, event ->
                        Trace.beginSection("InkCanvas#handleTouchEvent")
                        try {
                            val interaction =
                                InkCanvasInteraction(
                                    brush = runtimeBrush,
                                    isSegmentEraserEnabled = currentSegmentEraserEnabled,
                                    eraseStrokePredicate = currentEraseStrokePredicate,
                                    lassoSelection = currentLassoSelection,
                                    viewTransform = currentTransform,
                                    strokes = currentStrokes,
                                    pageWidth = currentPageWidth,
                                    pageHeight = currentPageHeight,
                                    allowEditing = currentAllowEditing,
                                    allowFingerGestures = currentAllowFingerGestures,
                                    inputSettings = currentInputSettings,
                                    onStrokeFinished = onFinished,
                                    onStrokeErased = onErased,
                                    onStrokeSplit = onSplit,
                                    onLassoMove = currentCallbacks.onLassoMove,
                                    onLassoResize = currentCallbacks.onLassoResize,
                                    onTransformGesture = currentCallbacks.onTransformGesture,
                                    onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                    onUndoShortcut = currentCallbacks.onUndoShortcut,
                                    onRedoShortcut = currentCallbacks.onRedoShortcut,
                                    onSwitchToPenShortcut = currentCallbacks.onSwitchToPenShortcut,
                                    onSwitchToEraserShortcut = currentCallbacks.onSwitchToEraserShortcut,
                                    onSwitchToLastToolShortcut = currentCallbacks.onSwitchToLastToolShortcut,
                                    onDoubleTapGesture = currentCallbacks.onDoubleTapGesture,
                                    onStylusButtonEraserActiveChanged =
                                        currentCallbacks.onStylusButtonEraserActiveChanged,
                                    onStrokeRenderFinished = onStrokeRenderFinished,
                                )
                            val handled =
                                handleTouchEvent(
                                    view = this@apply,
                                    event = event,
                                    interaction = interaction,
                                    runtime = runtime,
                                )
                            // Consume canvas tool streams to avoid parent handlers
                            // from double-processing draw/erase gestures.
                            handled || shouldConsumeCanvasTouchEvent(event, currentAllowFingerGestures)
                        } finally {
                            Trace.endSection()
                        }
                    }
                    setOnGenericMotionListener { _, event ->
                        Trace.beginSection("InkCanvas#handleGenericMotionEvent")
                        try {
                            val interaction =
                                InkCanvasInteraction(
                                    brush = runtimeBrush,
                                    isSegmentEraserEnabled = currentSegmentEraserEnabled,
                                    eraseStrokePredicate = currentEraseStrokePredicate,
                                    lassoSelection = currentLassoSelection,
                                    viewTransform = currentTransform,
                                    strokes = currentStrokes,
                                    pageWidth = currentPageWidth,
                                    pageHeight = currentPageHeight,
                                    allowEditing = currentAllowEditing,
                                    allowFingerGestures = currentAllowFingerGestures,
                                    inputSettings = currentInputSettings,
                                    onStrokeFinished = onFinished,
                                    onStrokeErased = onErased,
                                    onStrokeSplit = onSplit,
                                    onLassoMove = currentCallbacks.onLassoMove,
                                    onLassoResize = currentCallbacks.onLassoResize,
                                    onTransformGesture = currentCallbacks.onTransformGesture,
                                    onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                    onUndoShortcut = currentCallbacks.onUndoShortcut,
                                    onRedoShortcut = currentCallbacks.onRedoShortcut,
                                    onSwitchToPenShortcut = currentCallbacks.onSwitchToPenShortcut,
                                    onSwitchToEraserShortcut = currentCallbacks.onSwitchToEraserShortcut,
                                    onSwitchToLastToolShortcut = currentCallbacks.onSwitchToLastToolShortcut,
                                    onDoubleTapGesture = currentCallbacks.onDoubleTapGesture,
                                    onStylusButtonEraserActiveChanged =
                                        currentCallbacks.onStylusButtonEraserActiveChanged,
                                    onStrokeRenderFinished = onStrokeRenderFinished,
                                )
                            handleGenericMotionEvent(
                                view = this@apply,
                                event = event,
                                interaction = interaction,
                                runtime = runtime,
                            )
                        } finally {
                            Trace.endSection()
                        }
                    }
                }
            },
            update = { inProgressView ->
                Trace.beginSection("InkCanvas#androidViewUpdate")
                try {
                    syncMotionPredictionAdapter(
                        runtime = runtime,
                        context = inProgressView.context,
                        enabled = predictionEnabledForMode,
                    )
                    val persistedIds = currentStrokes.asSequence().map { it.id }.toHashSet()
                    inProgressView.maskPath =
                        buildOutsidePageMaskPath(
                            viewWidth = inProgressView.width.toFloat(),
                            viewHeight = inProgressView.height.toFloat(),
                            pageWidth = currentPageWidth,
                            pageHeight = currentPageHeight,
                            transform = currentTransform,
                        )
                    inProgressView.setCommittedStrokes(
                        strokes = mergedStrokes,
                        pageWidth = currentPageWidth,
                        pageHeight = currentPageHeight,
                    )
                    inProgressView.setViewTransform(currentTransform)
                    inProgressView.setOverlayState(
                        GlOverlayState(
                            selectedStrokeIds = currentLassoSelection.selectedStrokeIds,
                            lassoPath = currentLassoSelection.lassoPath,
                            hover =
                                runtime.hoverPreviewState.toGlHoverOverlay(
                                    brush = currentBrush,
                                    transform = currentTransform,
                                ),
                        ),
                    )

                    if (runtime.pendingCommittedStrokes.isNotEmpty()) {
                        runtime.pendingCommittedStrokes.keys.removeAll(persistedIds)
                    }

                    if (!runtime.hasActiveStrokeInputs()) {
                        val staleFinishedStrokeIds = inProgressView.getFinishedStrokes().keys
                        if (staleFinishedStrokeIds.isNotEmpty()) {
                            // Guarded fallback cleanup to prevent ghost overlays without dropping active segments.
                            scheduleFrameWaitedRemoval(
                                view = inProgressView,
                                strokeIds = staleFinishedStrokeIds.toSet(),
                                framesRemaining = HANDOFF_FRAME_DELAY,
                                runtime = runtime,
                            )
                        }
                    }
                } finally {
                    Trace.endSection()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun adjustBrushForLatencyMode(
    brush: Brush,
    mode: LatencyOptimizationMode,
): Brush =
    when (mode) {
        LatencyOptimizationMode.NORMAL -> brush
        LatencyOptimizationMode.FAST_EXPERIMENTAL ->
            brush.copy(
                smoothingLevel = (brush.smoothingLevel * FAST_MODE_SMOOTHING_MULTIPLIER).coerceAtLeast(0f),
            )
    }

private fun resolvePredictionEnabled(
    featureFlagStore: FeatureFlagStore,
    mode: LatencyOptimizationMode,
): Boolean =
    when (mode) {
        LatencyOptimizationMode.NORMAL -> featureFlagStore.get(FeatureFlag.INK_PREDICTION_ENABLED)
        LatencyOptimizationMode.FAST_EXPERIMENTAL -> true
    }

@Suppress("ReturnCount")
private fun shouldConsumeCanvasTouchEvent(
    event: MotionEvent,
    allowFingerGestures: Boolean,
): Boolean {
    val shouldConsumeAction =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            -> true

            else -> false
        }
    if (!shouldConsumeAction) {
        return false
    }
    if (allowFingerGestures) {
        return true
    }
    return eventHasStylusStream(event)
}

private fun buildOutsidePageMaskPath(
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    transform: ViewTransform,
): AndroidPath? {
    if (!hasValidMaskDimensions(viewWidth, viewHeight, pageWidth, pageHeight)) {
        return null
    }
    val left = transform.pageToScreenX(0f)
    val top = transform.pageToScreenY(0f)
    val width = transform.pageWidthToScreen(pageWidth)
    val height = transform.pageWidthToScreen(pageHeight)
    val maskPath = AndroidPath().apply { fillType = AndroidPath.FillType.EVEN_ODD }
    maskPath.addRect(0f, 0f, viewWidth, viewHeight, AndroidPath.Direction.CW)
    maskPath.addRect(left, top, left + width, top + height, AndroidPath.Direction.CW)
    return maskPath
}

private fun hasValidMaskDimensions(
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
): Boolean = pageWidth > 0f && pageHeight > 0f && viewWidth > 0f && viewHeight > 0f

private fun syncMotionPredictionAdapter(
    runtime: InkCanvasRuntime,
    context: Context,
    enabled: Boolean,
) {
    if (enabled) {
        if (runtime.motionPredictionAdapter == null) {
            runtime.motionPredictionAdapter = MotionPredictionAdapter.create(context)
        }
        return
    }
    runtime.motionPredictionAdapter = null
}

private fun scheduleFrameWaitedRemoval(
    view: GlInkSurfaceView,
    strokeIds: Set<Long>,
    framesRemaining: Int,
    runtime: InkCanvasRuntime,
) {
    if (framesRemaining <= 0) {
        view.removeFinishedStrokes(strokeIds)
        if (!runtime.hasActiveStrokeInputs()) {
            val residualFinishedIds = view.getFinishedStrokes().keys
            if (residualFinishedIds.isNotEmpty()) {
                view.removeFinishedStrokes(residualFinishedIds.toSet())
            }
        }
        return
    }
    view.postOnAnimation {
        scheduleFrameWaitedRemoval(view, strokeIds, framesRemaining - 1, runtime)
    }
}

private fun HoverPreviewState.toGlHoverOverlay(
    brush: Brush,
    transform: ViewTransform,
): GlHoverOverlay {
    if (!isVisible) {
        return GlHoverOverlay()
    }
    val isEraser = tool == com.onyx.android.ink.model.Tool.ERASER
    val color = if (isEraser) GL_HOVER_ERASER_COLOR else ColorCache.resolve(brush.color)
    val alpha = if (isEraser) GL_HOVER_ERASER_ALPHA else GL_HOVER_PEN_ALPHA
    val radius =
        (transform.pageWidthToScreen(brush.baseWidth).coerceAtLeast(GL_MIN_HOVER_SCREEN_RADIUS)) /
            2f
    return GlHoverOverlay(
        isVisible = true,
        screenX = x,
        screenY = y,
        screenRadius = radius,
        argbColor = color,
        alpha = alpha,
    )
}
