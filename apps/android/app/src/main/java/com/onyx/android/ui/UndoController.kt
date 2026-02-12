package com.onyx.android.ui

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.InkAction
import com.onyx.android.ink.model.Stroke

internal class UndoController(
    private val viewModel: NoteEditorViewModel,
    private val maxUndoActions: Int,
) {
    val undoStack = mutableStateListOf<InkAction>()
    val redoStack = mutableStateListOf<InkAction>()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> viewModel.removeStroke(action.stroke, persist = true)
            is InkAction.RemoveStroke -> viewModel.addStroke(action.stroke, persist = true)
        }
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> viewModel.addStroke(action.stroke, persist = true)
            is InkAction.RemoveStroke -> viewModel.removeStroke(action.stroke, persist = true)
        }
        undoStack.add(action)
        trimUndoStack()
    }

    fun onStrokeFinished(
        stroke: Stroke,
        currentPage: PageEntity?,
    ) {
        viewModel.addStroke(stroke, persist = true)
        undoStack.add(InkAction.AddStroke(stroke))
        trimUndoStack()
        redoStack.clear()
        val pageId = currentPage?.pageId
        if (pageId != null && currentPage.kind == "pdf") {
            viewModel.upgradePageToMixed(pageId)
        }
        logStrokePoints(stroke)
    }

    fun onStrokeErased(stroke: Stroke) {
        viewModel.removeStroke(stroke, persist = true)
        undoStack.add(InkAction.RemoveStroke(stroke))
        trimUndoStack()
        redoStack.clear()
    }

    private fun trimUndoStack() {
        if (undoStack.size > maxUndoActions) {
            undoStack.removeAt(0)
        }
    }

    private fun logStrokePoints(stroke: Stroke) {
        val firstPoint = stroke.points.firstOrNull()
        val lastPoint = stroke.points.lastOrNull()
        if (firstPoint != null && lastPoint != null) {
            val message =
                "Saved stroke points in pt: " +
                    "start=(${firstPoint.x}, ${firstPoint.y}) " +
                    "end=(${lastPoint.x}, ${lastPoint.y})"
            Log.d("InkStroke", message)
        }
    }
}
