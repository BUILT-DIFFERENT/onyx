@file:Suppress("FunctionName")

package com.onyx.android.ink.ui

import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.ViewTransform
import androidx.ink.strokes.Stroke as InkStroke

internal data class FinishedStrokeBridgeEntry(
    val inProgressStrokeId: InProgressStrokeId,
    val completedAtUptimeMs: Long,
)

@Suppress("LongParameterList")
internal class InkCanvasRuntime(
    val activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    val activeStrokeBrushes: MutableMap<Int, Brush>,
    val activeStrokeStartTimes: MutableMap<Int, Long>,
    val predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val pendingCommittedStrokes: MutableMap<String, Stroke>,
    val pendingCommittedAtUptimeMs: MutableMap<String, Long>,
    val finishedInProgressByStrokeId: MutableMap<String, FinishedStrokeBridgeEntry>,
    val hoverPreviewState: HoverPreviewState,
) {
    var activeStrokeRenderVersion by mutableIntStateOf(0)
    var motionPredictionAdapter: MotionPredictionAdapter? = null
    var isTransforming = false
    var previousTransformDistance = 0f
    var previousTransformCentroidX = 0f
    var previousTransformCentroidY = 0f
    var isSingleFingerPanning = false
    var singleFingerPanPointerId = MotionEvent.INVALID_POINTER_ID
    var previousSingleFingerPanX = 0f
    var previousSingleFingerPanY = 0f

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

data class InkCanvasState(
    val strokes: List<Stroke>,
    val viewTransform: ViewTransform,
    val brush: Brush,
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
    val currentCallbacks by rememberUpdatedState(callbacks)
    val hoverPreviewState = remember { HoverPreviewState() }

    val runtime =
        remember {
            InkCanvasRuntime(
                activeStrokeIds = mutableMapOf(),
                activeStrokePoints = mutableMapOf(),
                activeStrokeBrushes = mutableMapOf(),
                activeStrokeStartTimes = mutableMapOf(),
                predictedStrokeIds = mutableMapOf(),
                pendingCommittedStrokes = mutableMapOf(),
                pendingCommittedAtUptimeMs = mutableMapOf(),
                finishedInProgressByStrokeId = mutableMapOf(),
                hoverPreviewState = hoverPreviewState,
            )
        }

    val mergedStrokes =
        if (runtime.pendingCommittedStrokes.isEmpty()) {
            currentStrokes
        } else {
            val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()
            val pendingStrokes =
                runtime.pendingCommittedStrokes.values.filter { pending ->
                    pending.id !in persistedStrokeIds
                }
            currentStrokes + pendingStrokes
        }
    val activeStrokeRenderVersion = runtime.activeStrokeRenderVersion

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activeStrokeRenderVersion < 0) {
                return@Canvas
            }
            mergedStrokes.forEach { stroke ->
                drawStroke(stroke, currentTransform)
            }
            runtime.activeStrokePoints.forEach { (pointerId, points) ->
                val brush = runtime.activeStrokeBrushes[pointerId] ?: return@forEach
                drawStrokePoints(
                    points = points,
                    style = brush.toStrokeStyle(),
                    transform = currentTransform,
                )
            }
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
                    // Keep this view for input event handling, but render strokes via Compose
                    // so in-progress and committed strokes share the exact same draw pipeline.
                    alpha = 0f
                    eagerInit()
                    addFinishedStrokesListener(
                        object : InProgressStrokesFinishedListener {
                            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
                                // Keep finished in-progress strokes briefly to bridge pen-up handoff.
                            }
                        },
                    )
                    setOnTouchListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
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
                            )
                        handleTouchEvent(
                            view = this@apply,
                            event = event,
                            interaction = interaction,
                            runtime = runtime,
                        )
                    }
                }
            },
            update = { inProgressView ->
                val nowUptimeMs = SystemClock.uptimeMillis()
                val persistedStrokeIds = currentStrokes.asSequence().map { it.id }.toHashSet()

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
