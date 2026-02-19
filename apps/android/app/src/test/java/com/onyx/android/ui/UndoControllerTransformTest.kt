package com.onyx.android.ui

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class UndoControllerTransformTest {
    @Test
    fun `transform action is undoable and redoable`() {
        val viewModel = mockk<NoteEditorViewModel>(relaxed = true)
        val controller = UndoController(viewModel = viewModel, maxUndoActions = 10, useMultiPage = true)
        val pageId = "page-1"
        val before = listOf(createStroke("stroke-1", 10f, 20f), createStroke("stroke-2", 30f, 40f))
        val after = listOf(createStroke("stroke-1", 20f, 25f), createStroke("stroke-2", 40f, 45f))

        controller.onStrokesTransformed(pageId = pageId, before = before, after = after)

        verify(exactly = 1) {
            viewModel.replaceStrokesForPage(pageId = pageId, replacement = after, persist = true)
        }

        controller.undo()
        verify(exactly = 1) {
            viewModel.replaceStrokesForPage(pageId = pageId, replacement = before, persist = true)
        }

        controller.redo()
        verify(exactly = 2) {
            viewModel.replaceStrokesForPage(pageId = pageId, replacement = after, persist = true)
        }
    }

    private fun createStroke(
        id: String,
        x: Float,
        y: Float,
    ): Stroke =
        Stroke(
            id = id,
            points =
                listOf(
                    StrokePoint(x = x, y = y, t = 0L),
                    StrokePoint(x = x + 10f, y = y + 5f, t = 1L),
                ),
            style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
            bounds = StrokeBounds(x = x, y = y, w = 10f, h = 5f),
            createdAt = 0L,
        )
}
