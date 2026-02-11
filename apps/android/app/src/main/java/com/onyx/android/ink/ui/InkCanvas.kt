@file:Suppress("FunctionName")

package com.onyx.android.ink.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

internal class InkCanvasRuntime(
    val activeStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val activeStrokePoints: MutableMap<Int, MutableList<StrokePoint>>,
    val activeStrokeStartTimes: MutableMap<Int, Long>,
    val predictedStrokeIds: MutableMap<Int, InProgressStrokeId>,
    val hoverPreviewState: HoverPreviewState,
) {
    var motionPredictionAdapter: MotionPredictionAdapter? = null
    var isTransforming = false
    var previousTransformDistance = 0f
    var previousTransformCentroidX = 0f
    var previousTransformCentroidY = 0f
    var isSingleFingerPanning = false
    var singleFingerPanPointerId = MotionEvent.INVALID_POINTER_ID
    var previousSingleFingerPanX = 0f
    var previousSingleFingerPanY = 0f
}

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
                activeStrokeStartTimes = mutableMapOf(),
                predictedStrokeIds = mutableMapOf(),
                hoverPreviewState = hoverPreviewState,
            )
        }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            currentStrokes.forEach { stroke ->
                drawStroke(stroke, currentTransform)
            }
        }

        AndroidView(
            factory = { context ->
                runtime.motionPredictionAdapter = MotionPredictionAdapter.create(context)
                InProgressStrokesView(context).apply {
                    eagerInit()
                    addFinishedStrokesListener(
                        object : InProgressStrokesFinishedListener {
                            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
                                if (strokes.isNotEmpty()) {
                                    removeFinishedStrokes(strokes.keys)
                                }
                            }
                        },
                    )
                    setOnTouchListener { _, event ->
                        val interaction =
                            InkCanvasInteraction(
                                brush = currentBrush,
                                viewTransform = currentTransform,
                                strokes = currentStrokes,
                                onStrokeFinished = currentCallbacks.onStrokeFinished,
                                onStrokeErased = currentCallbacks.onStrokeErased,
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
                val finishedStrokeIds = inProgressView.getFinishedStrokes().keys.toSet()
                if (finishedStrokeIds.isNotEmpty()) {
                    inProgressView.removeFinishedStrokes(finishedStrokeIds)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawHoverPreview(hoverPreviewState, currentBrush, currentTransform)
        }
    }
}
