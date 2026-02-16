@file:Suppress("FunctionName")

package com.onyx.android.ink.ui

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.ViewTransform
import android.graphics.Path as AndroidPath

internal enum class PointerMode {
    DRAW,
    ERASE,
}

@Suppress("LongParameterList")
internal class InkCanvasRuntime(
    val activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val activePointerModes: MutableMap<Int, PointerMode>,
    val activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    val activeStrokeBrushes: MutableMap<Int, Brush>,
    val activeStrokeStartTimes: MutableMap<Int, Long>,
    val predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val pendingCommittedStrokes: MutableMap<String, Stroke>,
    val hoverPreviewState: HoverPreviewState,
    val finishedStrokePathCache: MutableMap<String, StrokePathCacheEntry>,
) {
    var activeStrokeRenderVersion by mutableIntStateOf(0)
    var motionPredictionAdapter: MotionPredictionAdapter? = null
    var isStylusButtonEraserActive = false
    var isTransforming = false
    var previousTransformDistance = 0f
    var previousTransformCentroidX = 0f
    var previousTransformCentroidY = 0f
    var isSingleFingerPanning = false
    var singleFingerPanPointerId = MotionEvent.INVALID_POINTER_ID
    var previousSingleFingerPanX = 0f
    var previousSingleFingerPanY = 0f
    var panVelocityTracker: VelocityTracker? = null

    fun invalidateActiveStrokeRender() {
        activeStrokeRenderVersion += 1
    }
}

private const val ENABLE_MOTION_PREDICTION = true

data class InkCanvasState(
    val strokes: List<Stroke>,
    val viewTransform: ViewTransform,
    val brush: Brush,
    val pageWidth: Float,
    val pageHeight: Float,
    val allowEditing: Boolean = true,
    val allowFingerGestures: Boolean = true,
)

data class InkCanvasCallbacks(
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

@Composable
@Suppress("LongMethod")
fun InkCanvas(
    state: InkCanvasState,
    callbacks: InkCanvasCallbacks,
    modifier: Modifier = Modifier,
) {
    val currentBrush by rememberUpdatedState(state.brush)
    val currentTransform by rememberUpdatedState(state.viewTransform)
    val currentStrokes by rememberUpdatedState(state.strokes)
    val currentPageWidth by rememberUpdatedState(state.pageWidth)
    val currentPageHeight by rememberUpdatedState(state.pageHeight)
    val currentAllowEditing by rememberUpdatedState(state.allowEditing)
    val currentAllowFingerGestures by rememberUpdatedState(state.allowFingerGestures)
    val currentCallbacks by rememberUpdatedState(callbacks)
    val hoverPreviewState = remember { HoverPreviewState() }

    val runtime =
        remember {
            InkCanvasRuntime(
                activeStrokeIds = mutableMapOf(),
                activePointerModes = mutableMapOf(),
                activeStrokePoints = mutableMapOf(),
                activeStrokeBrushes = mutableMapOf(),
                activeStrokeStartTimes = mutableMapOf(),
                predictedStrokeIds = mutableMapOf(),
                pendingCommittedStrokes = mutableMapOf(),
                hoverPreviewState = hoverPreviewState,
                finishedStrokePathCache = mutableMapOf(),
            )
        }

    val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()
    val persistedStrokes = currentStrokes
    val pendingStrokes =
        runtime.pendingCommittedStrokes.values.filter { pending ->
            pending.id !in persistedStrokeIds
        }
    val mergedStrokes =
        LinkedHashMap<String, Stroke>(persistedStrokes.size + pendingStrokes.size).apply {
            persistedStrokes.forEach { stroke -> put(stroke.id, stroke) }
            pendingStrokes.forEach { stroke -> put(stroke.id, stroke) }
        }.values.toList()
    val activeStrokeRenderVersion = runtime.activeStrokeRenderVersion

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activeStrokeRenderVersion < 0) {
                return@Canvas
            }
            runtime.finishedStrokePathCache.keys.retainAll(mergedStrokes.mapTo(mutableSetOf()) { it.id })
            drawStrokesInWorldSpace(
                strokes = mergedStrokes,
                transform = currentTransform,
                pathCache = runtime.finishedStrokePathCache,
            )
        }

        AndroidView(
            factory = { context ->
                runtime.motionPredictionAdapter =
                    if (ENABLE_MOTION_PREDICTION) {
                        MotionPredictionAdapter.create(context)
                    } else {
                        null
                    }
                InProgressStrokesView(context).apply {
                    // Keep this view for low-latency in-progress stroke rendering and touch input handling.
                    // Finished strokes are drawn in Compose using cached world-space paths.
                    alpha = 1f
                    eagerInit()
                    val onFinished = { stroke: Stroke ->
                        runtime.pendingCommittedStrokes[stroke.id] = stroke
                        currentCallbacks.onStrokeFinished(stroke)
                    }
                    val onErased = { stroke: Stroke ->
                        runtime.pendingCommittedStrokes.remove(stroke.id)
                        runtime.finishedStrokePathCache.remove(stroke.id)
                        currentCallbacks.onStrokeErased(stroke)
                    }
                    // Use postOnAnimation to remove finished strokes synchronized with the display refresh.
                    // This prevents ghosting by ensuring the stroke is removed from the
                    // InProgressStrokesView at the same frame it appears in Compose's Canvas.
                    val onStrokeRenderFinished = { _: InProgressStrokeId ->
                        postOnAnimation {
                            val finishedStrokeIds = getFinishedStrokes().keys
                            if (finishedStrokeIds.isNotEmpty()) {
                                removeFinishedStrokes(finishedStrokeIds.toSet())
                            }
                        }
                    }
                    setOnTouchListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
                                pageWidth = currentPageWidth,
                                pageHeight = currentPageHeight,
                                allowEditing = currentAllowEditing,
                                allowFingerGestures = currentAllowFingerGestures,
                                onStrokeFinished = onFinished,
                                onStrokeErased = onErased,
                                onTransformGesture = currentCallbacks.onTransformGesture,
                                onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                onStylusButtonEraserActiveChanged = currentCallbacks.onStylusButtonEraserActiveChanged,
                                onStrokeRenderFinished = onStrokeRenderFinished,
                            )
                        val handled =
                            handleTouchEvent(
                                view = this@apply,
                                event = event,
                                interaction = interaction,
                                runtime = runtime,
                            )
                        // Always consume touch streams for canvas tool types so
                        // InProgressStrokesView does not also process them and
                        // create duplicate/jagged overlay strokes.
                        handled || shouldConsumeCanvasTouchEvent(event, currentAllowFingerGestures)
                    }
                    setOnGenericMotionListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
                                pageWidth = currentPageWidth,
                                pageHeight = currentPageHeight,
                                allowEditing = currentAllowEditing,
                                allowFingerGestures = currentAllowFingerGestures,
                                onStrokeFinished = onFinished,
                                onStrokeErased = onErased,
                                onTransformGesture = currentCallbacks.onTransformGesture,
                                onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                onStylusButtonEraserActiveChanged = currentCallbacks.onStylusButtonEraserActiveChanged,
                                onStrokeRenderFinished = onStrokeRenderFinished,
                            )
                        handleGenericMotionEvent(event, interaction, runtime)
                    }
                }
            },
            update = { inProgressView ->
                val persistedIds = currentStrokes.asSequence().map { it.id }.toHashSet()
                inProgressView.maskPath =
                    buildOutsidePageMaskPath(
                        viewWidth = inProgressView.width.toFloat(),
                        viewHeight = inProgressView.height.toFloat(),
                        pageWidth = currentPageWidth,
                        pageHeight = currentPageHeight,
                        transform = currentTransform,
                    )

                if (runtime.pendingCommittedStrokes.isNotEmpty()) {
                    runtime.pendingCommittedStrokes.keys.removeAll(persistedIds)
                }

                val staleFinishedStrokeIds = inProgressView.getFinishedStrokes().keys
                if (staleFinishedStrokeIds.isNotEmpty()) {
                    // Safety net: if a finished stroke misses the per-stroke removal callback,
                    // clear it here to avoid stuck jagged duplicates in the overlay layer.
                    inProgressView.removeFinishedStrokes(staleFinishedStrokeIds.toSet())
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHoverPreview(hoverPreviewState, currentBrush, currentTransform)
        }
    }
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
    for (index in 0 until event.pointerCount) {
        val toolType = event.getToolType(index)
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            return true
        }
    }
    return false
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
