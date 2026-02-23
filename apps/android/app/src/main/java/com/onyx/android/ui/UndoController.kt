package com.onyx.android.ui

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.ink.model.InkAction
import com.onyx.android.ink.model.Stroke
import com.onyx.android.objects.model.PageObject

@Suppress("LongMethod", "CyclomaticComplexMethod", "TooManyFunctions")
internal class UndoController(
    private val viewModel: NoteEditorViewModel,
    private val maxUndoActions: Int,
    private val useMultiPage: Boolean = false,
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
            is InkAction.AddStroke -> {
                if (useMultiPage) {
                    viewModel.removeStrokeForPage(action.stroke, action.pageId, persist = true)
                } else {
                    viewModel.removeStroke(action.stroke, persist = true)
                }
            }

            is InkAction.RemoveStroke -> {
                if (useMultiPage) {
                    viewModel.addStrokeForPage(action.stroke, action.pageId, persist = true)
                } else {
                    viewModel.addStroke(action.stroke, persist = true)
                }
            }

            is InkAction.SplitStroke -> {
                if (useMultiPage) {
                    viewModel.restoreSplitStrokeForPage(
                        original = action.original,
                        segments = action.segments,
                        pageId = action.pageId,
                        insertionIndex = action.insertionIndex,
                        persist = true,
                    )
                } else {
                    viewModel.restoreSplitStroke(
                        original = action.original,
                        segments = action.segments,
                        pageId = action.pageId,
                        insertionIndex = action.insertionIndex,
                        persist = true,
                    )
                }
            }

            is InkAction.TransformStrokes -> {
                if (useMultiPage) {
                    viewModel.replaceStrokesForPage(
                        pageId = action.pageId,
                        replacement = action.before,
                        persist = true,
                    )
                } else {
                    viewModel.replaceStrokes(
                        pageId = action.pageId,
                        replacement = action.before,
                        persist = true,
                    )
                }
            }

            is InkAction.AddObject -> {
                if (useMultiPage) {
                    viewModel.removePageObjectForPage(
                        pageId = action.pageId,
                        pageObject = action.pageObject,
                        persist = true,
                    )
                } else {
                    viewModel.removePageObject(
                        pageObject = action.pageObject,
                        persist = true,
                    )
                }
            }

            is InkAction.RemoveObject -> {
                if (useMultiPage) {
                    viewModel.addPageObjectForPage(
                        pageId = action.pageId,
                        pageObject = action.pageObject,
                        persist = true,
                    )
                } else {
                    viewModel.addPageObject(
                        pageObject = action.pageObject,
                        persist = true,
                    )
                }
            }

            is InkAction.TransformObject -> {
                if (useMultiPage) {
                    viewModel.transformPageObjectForPage(
                        pageId = action.pageId,
                        before = action.after,
                        after = action.before,
                        persist = true,
                    )
                } else {
                    viewModel.transformPageObject(
                        before = action.after,
                        after = action.before,
                        persist = true,
                    )
                }
            }
        }
        redoStack.add(action)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is InkAction.AddStroke -> {
                if (useMultiPage) {
                    viewModel.addStrokeForPage(action.stroke, action.pageId, persist = true)
                } else {
                    viewModel.addStroke(action.stroke, persist = true)
                }
            }

            is InkAction.RemoveStroke -> {
                if (useMultiPage) {
                    viewModel.removeStrokeForPage(action.stroke, action.pageId, persist = true)
                } else {
                    viewModel.removeStroke(action.stroke, persist = true)
                }
            }

            is InkAction.SplitStroke -> {
                if (useMultiPage) {
                    viewModel.splitStrokeForPage(
                        original = action.original,
                        segments = action.segments,
                        pageId = action.pageId,
                        persist = true,
                    )
                } else {
                    viewModel.splitStroke(
                        original = action.original,
                        segments = action.segments,
                        pageId = action.pageId,
                        persist = true,
                    )
                }
            }

            is InkAction.TransformStrokes -> {
                if (useMultiPage) {
                    viewModel.replaceStrokesForPage(
                        pageId = action.pageId,
                        replacement = action.after,
                        persist = true,
                    )
                } else {
                    viewModel.replaceStrokes(
                        pageId = action.pageId,
                        replacement = action.after,
                        persist = true,
                    )
                }
            }

            is InkAction.AddObject -> {
                if (useMultiPage) {
                    viewModel.addPageObjectForPage(
                        pageId = action.pageId,
                        pageObject = action.pageObject,
                        persist = true,
                    )
                } else {
                    viewModel.addPageObject(
                        pageObject = action.pageObject,
                        persist = true,
                    )
                }
            }

            is InkAction.RemoveObject -> {
                if (useMultiPage) {
                    viewModel.removePageObjectForPage(
                        pageId = action.pageId,
                        pageObject = action.pageObject,
                        persist = true,
                    )
                } else {
                    viewModel.removePageObject(
                        pageObject = action.pageObject,
                        persist = true,
                    )
                }
            }

            is InkAction.TransformObject -> {
                if (useMultiPage) {
                    viewModel.transformPageObjectForPage(
                        pageId = action.pageId,
                        before = action.before,
                        after = action.after,
                        persist = true,
                    )
                } else {
                    viewModel.transformPageObject(
                        before = action.before,
                        after = action.after,
                        persist = true,
                    )
                }
            }
        }
        undoStack.add(action)
        trimUndoStack()
    }

    fun onStrokeFinished(
        stroke: Stroke,
        currentPage: PageEntity?,
    ) {
        val pageId = currentPage?.pageId
        if (pageId == null) {
            return
        }
        if (useMultiPage) {
            viewModel.addStrokeForPage(stroke, pageId, persist = true)
        } else {
            viewModel.addStroke(stroke, persist = true)
        }
        undoStack.add(InkAction.AddStroke(stroke, pageId))
        trimUndoStack()
        redoStack.clear()
        if (currentPage.kind == "pdf") {
            viewModel.upgradePageToMixed(pageId)
        }
        logStrokePoints(stroke)
    }

    fun onStrokeErased(
        stroke: Stroke,
        pageId: String,
    ) {
        if (useMultiPage) {
            viewModel.removeStrokeForPage(stroke, pageId, persist = true)
        } else {
            viewModel.removeStroke(stroke, persist = true)
        }
        undoStack.add(InkAction.RemoveStroke(stroke, pageId))
        trimUndoStack()
        redoStack.clear()
    }

    fun onStrokeSplit(
        original: Stroke,
        segments: List<Stroke>,
        pageId: String,
    ) {
        val insertionIndex =
            if (useMultiPage) {
                viewModel.splitStrokeForPage(original, segments, pageId, persist = true)
            } else {
                viewModel.splitStroke(original, segments, pageId, persist = true)
            }
        if (insertionIndex == null) {
            return
        }
        undoStack.add(
            InkAction.SplitStroke(
                original = original,
                segments = segments,
                pageId = pageId,
                insertionIndex = insertionIndex,
            ),
        )
        trimUndoStack()
        redoStack.clear()
    }

    fun onStrokesTransformed(
        pageId: String,
        before: List<Stroke>,
        after: List<Stroke>,
    ) {
        if (before.isEmpty() || after.isEmpty() || before.size != after.size) {
            return
        }
        if (useMultiPage) {
            viewModel.replaceStrokesForPage(
                pageId = pageId,
                replacement = after,
                persist = true,
            )
        } else {
            viewModel.replaceStrokes(
                pageId = pageId,
                replacement = after,
                persist = true,
            )
        }
        undoStack.add(
            InkAction.TransformStrokes(
                pageId = pageId,
                before = before,
                after = after,
            ),
        )
        trimUndoStack()
        redoStack.clear()
    }

    fun onObjectAdded(
        pageId: String,
        pageObject: PageObject,
    ) {
        if (useMultiPage) {
            viewModel.addPageObjectForPage(
                pageId = pageId,
                pageObject = pageObject,
                persist = true,
            )
        } else {
            viewModel.addPageObject(
                pageObject = pageObject,
                persist = true,
            )
        }
        undoStack.add(InkAction.AddObject(pageId = pageId, pageObject = pageObject))
        trimUndoStack()
        redoStack.clear()
    }

    fun onObjectRemoved(
        pageId: String,
        pageObject: PageObject,
    ) {
        if (useMultiPage) {
            viewModel.removePageObjectForPage(
                pageId = pageId,
                pageObject = pageObject,
                persist = true,
            )
        } else {
            viewModel.removePageObject(
                pageObject = pageObject,
                persist = true,
            )
        }
        undoStack.add(InkAction.RemoveObject(pageId = pageId, pageObject = pageObject))
        trimUndoStack()
        redoStack.clear()
    }

    fun onObjectTransformed(
        pageId: String,
        before: PageObject,
        after: PageObject,
    ) {
        if (useMultiPage) {
            viewModel.transformPageObjectForPage(
                pageId = pageId,
                before = before,
                after = after,
                persist = true,
            )
        } else {
            viewModel.transformPageObject(
                before = before,
                after = after,
                persist = true,
            )
        }
        undoStack.add(InkAction.TransformObject(pageId = pageId, before = before, after = after))
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
