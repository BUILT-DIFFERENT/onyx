package com.onyx.android.ink.model

sealed class InkAction {
    data class AddStroke(
        val stroke: Stroke,
        val pageId: String,
    ) : InkAction()

    data class RemoveStroke(
        val stroke: Stroke,
        val pageId: String,
    ) : InkAction()
}
