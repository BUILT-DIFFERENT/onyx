package com.onyx.android.ink.model

import com.onyx.android.objects.model.PageObject

sealed class InkAction {
    data class AddStroke(
        val stroke: Stroke,
        val pageId: String,
    ) : InkAction()

    data class RemoveStroke(
        val stroke: Stroke,
        val pageId: String,
    ) : InkAction()

    data class SplitStroke(
        val original: Stroke,
        val segments: List<Stroke>,
        val pageId: String,
        val insertionIndex: Int,
    ) : InkAction()

    data class TransformStrokes(
        val pageId: String,
        val before: List<Stroke>,
        val after: List<Stroke>,
    ) : InkAction()

    data class AddObject(
        val pageId: String,
        val pageObject: PageObject,
    ) : InkAction()

    data class RemoveObject(
        val pageId: String,
        val pageObject: PageObject,
    ) : InkAction()

    data class TransformObject(
        val pageId: String,
        val before: PageObject,
        val after: PageObject,
    ) : InkAction()
}
