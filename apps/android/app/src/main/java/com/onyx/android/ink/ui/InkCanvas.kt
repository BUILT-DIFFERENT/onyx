@file:Suppress("FunctionName")

package com.onyx.android.ink.ui

import android.os.SystemClock
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

internal data class FinishedStrokeBridgeEntry(
    val inProgressStrokeId: InProgressStrokeId,
    val completedAtUptimeMs: Long,
)

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
    val pendingCommittedAtUptimeMs: MutableMap<String, Long>,
    val finishedInProgressByStrokeId: MutableMap<String, FinishedStrokeBridgeEntry>,
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

// Disabled while pen-up handoff is stabilized; prediction introduces visible divergence.
private const val ENABLE_MOTION_PREDICTION = false
internal const val FINISH_BRIDGE_HOLD_MS = 48L
internal const val FINISH_BRIDGE_STALE_MS = 1_200L

internal fun pressureBridgeStrokeIdsToRemove(
    nowUptimeMs: Long,
    persistedStrokeIds: Set<String>,
    finishedInProgressByStrokeId: Map<String, FinishedStrokeBridgeEntry>,
    holdMs: Long = FINISH_BRIDGE_HOLD_MS,
    staleMs: Long = FINISH_BRIDGE_STALE_MS,
): Set<String> =
    finishedInProgressByStrokeId.entries
        .filter { (strokeId, bridgeEntry) ->
            val ageMs = nowUptimeMs - bridgeEntry.completedAtUptimeMs
            (strokeId in persistedStrokeIds && ageMs >= holdMs) || ageMs >= staleMs
        }
        .mapTo(mutableSetOf()) { it.key }

internal fun bridgedStrokeIds(finishedInProgressByStrokeId: Map<String, FinishedStrokeBridgeEntry>): Set<String> =
    finishedInProgressByStrokeId.keys.toSet()

data class InkCanvasState(
    val strokes: List<Stroke>,
    val viewTransform: ViewTransform,
    val brush: Brush,
    val pageWidth: Float,
    val pageHeight: Float,
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
                pendingCommittedAtUptimeMs = mutableMapOf(),
                finishedInProgressByStrokeId = mutableMapOf(),
                hoverPreviewState = hoverPreviewState,
                finishedStrokePathCache = mutableMapOf(),
            )
        }

    val bridgedStrokeIds = bridgedStrokeIds(runtime.finishedInProgressByStrokeId)
    val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()
    val persistedStrokes = currentStrokes.filterNot { stroke -> stroke.id in bridgedStrokeIds }
    val pendingStrokes =
        runtime.pendingCommittedStrokes.values.filter { pending ->
            pending.id !in persistedStrokeIds && pending.id !in bridgedStrokeIds
        }
    val mergedStrokes = persistedStrokes + pendingStrokes
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
                    setOnTouchListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
                                pageWidth = currentPageWidth,
                                pageHeight = currentPageHeight,
                                onStrokeFinished = { stroke ->
                                    runtime.pendingCommittedStrokes[stroke.id] = stroke
                                    runtime.pendingCommittedAtUptimeMs[stroke.id] = SystemClock.uptimeMillis()
                                    currentCallbacks.onStrokeFinished(stroke)
                                },
                                onStrokeErased = { stroke ->
                                    runtime.pendingCommittedStrokes.remove(stroke.id)
                                    runtime.pendingCommittedAtUptimeMs.remove(stroke.id)
                                    runtime.finishedInProgressByStrokeId.remove(stroke.id)
                                    currentCallbacks.onStrokeErased(stroke)
                                },
                                onTransformGesture = currentCallbacks.onTransformGesture,
                                onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                onStylusButtonEraserActiveChanged = currentCallbacks.onStylusButtonEraserActiveChanged,
                            )
                        handleTouchEvent(
                            view = this@apply,
                            event = event,
                            interaction = interaction,
                            runtime = runtime,
                        )
                    }
                    setOnGenericMotionListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
                                pageWidth = currentPageWidth,
                                pageHeight = currentPageHeight,
                                onStrokeFinished = { stroke ->
                                    runtime.pendingCommittedStrokes[stroke.id] = stroke
                                    runtime.pendingCommittedAtUptimeMs[stroke.id] = SystemClock.uptimeMillis()
                                    currentCallbacks.onStrokeFinished(stroke)
                                },
                                onStrokeErased = { stroke ->
                                    runtime.pendingCommittedStrokes.remove(stroke.id)
                                    runtime.pendingCommittedAtUptimeMs.remove(stroke.id)
                                    runtime.finishedInProgressByStrokeId.remove(stroke.id)
                                    currentCallbacks.onStrokeErased(stroke)
                                },
                                onTransformGesture = currentCallbacks.onTransformGesture,
                                onPanGestureEnd = currentCallbacks.onPanGestureEnd,
                                onStylusButtonEraserActiveChanged = currentCallbacks.onStylusButtonEraserActiveChanged,
                            )
                        handleGenericMotionEvent(event, interaction, runtime)
                    }
                }
            },
            update = { inProgressView ->
                val nowUptimeMs = SystemClock.uptimeMillis()
                val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()
                inProgressView.maskPath =
                    buildPageMaskPath(
                        pageWidth = currentPageWidth,
                        pageHeight = currentPageHeight,
                        transform = currentTransform,
                    )

                if (runtime.pendingCommittedStrokes.isNotEmpty()) {
                    val stalePendingStrokeIds =
                        runtime.pendingCommittedAtUptimeMs
                            .filterValues { committedAt -> nowUptimeMs - committedAt >= FINISH_BRIDGE_STALE_MS }
                            .keys
                    val pendingStrokeIdsToRemove = persistedStrokeIds + stalePendingStrokeIds
                    runtime.pendingCommittedStrokes.keys.removeAll(pendingStrokeIdsToRemove)
                    runtime.pendingCommittedAtUptimeMs.keys.removeAll(pendingStrokeIdsToRemove)
                }

                val bridgeStrokeIdsToRemove =
                    pressureBridgeStrokeIdsToRemove(
                        nowUptimeMs = nowUptimeMs,
                        persistedStrokeIds = persistedStrokeIds,
                        finishedInProgressByStrokeId = runtime.finishedInProgressByStrokeId,
                    )
                if (bridgeStrokeIdsToRemove.isNotEmpty()) {
                    val inProgressIdsToRemove =
                        bridgeStrokeIdsToRemove
                            .mapNotNull { strokeId ->
                                runtime.finishedInProgressByStrokeId[strokeId]?.inProgressStrokeId
                            }
                            .toSet()
                    if (inProgressIdsToRemove.isNotEmpty()) {
                        inProgressView.removeFinishedStrokes(inProgressIdsToRemove)
                    }
                    runtime.finishedInProgressByStrokeId.keys.removeAll(bridgeStrokeIdsToRemove)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHoverPreview(hoverPreviewState, currentBrush, currentTransform)
        }
    }
}

private fun buildPageMaskPath(
    pageWidth: Float,
    pageHeight: Float,
    transform: ViewTransform,
): AndroidPath? {
    if (pageWidth <= 0f || pageHeight <= 0f) {
        return null
    }
    val (left, top) = transform.pageToScreen(0f, 0f)
    val width = transform.pageWidthToScreen(pageWidth)
    val height = transform.pageWidthToScreen(pageHeight)
    val maskPath = AndroidPath()
    maskPath.addRect(left, top, left + width, top + height, AndroidPath.Direction.CW)
    return maskPath
}
