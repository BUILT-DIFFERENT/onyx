package com.onyx.android.ink

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.Tool

interface InkSurface {
    fun setBrush(brush: Brush)

    fun setTool(tool: Tool)

    fun getStrokes(): List<Stroke>

    fun addStroke(stroke: Stroke)

    fun removeStroke(strokeId: String)

    fun clear()

    fun undo(): Boolean

    fun redo(): Boolean

    fun setOnStrokeAddedListener(listener: (Stroke) -> Unit)
}
