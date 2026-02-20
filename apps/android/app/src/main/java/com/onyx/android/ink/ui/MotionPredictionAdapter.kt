package com.onyx.android.ink.ui

import android.content.Context
import android.view.MotionEvent
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import java.lang.reflect.Method

internal class MotionPredictionAdapter private constructor(
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
                val adapter = createAdapter(className, context)
                if (adapter != null) {
                    return adapter
                }
            }
            return null
        }

        private fun createAdapter(
            className: String,
            context: Context,
        ): MotionPredictionAdapter? {
            return runCatching {
                val clazz = Class.forName(className)
                val createMethod = clazz.getMethod("create", Context::class.java)
                val predictor = createMethod.invoke(null, context) ?: return@runCatching null
                val recordMethod = clazz.getMethod("record", MotionEvent::class.java)
                val predictMethod = clazz.getMethod("predict")
                MotionPredictionAdapter(predictor, recordMethod, predictMethod)
            }.getOrNull()
        }
    }
}

internal fun isSupportedToolType(toolType: Int): Boolean =
    toolType == MotionEvent.TOOL_TYPE_STYLUS ||
        toolType == MotionEvent.TOOL_TYPE_ERASER

internal fun isStylusToolType(toolType: Int): Boolean =
    toolType == MotionEvent.TOOL_TYPE_STYLUS ||
        toolType == MotionEvent.TOOL_TYPE_ERASER

internal fun toolTypeToTool(
    toolType: Int,
    brush: Brush,
): Tool =
    if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
        Tool.ERASER
    } else {
        brush.tool
    }

internal fun Brush.withToolType(toolType: Int): Brush =
    if (toolType == MotionEvent.TOOL_TYPE_ERASER) {
        copy(tool = Tool.ERASER)
    } else {
        this
    }
