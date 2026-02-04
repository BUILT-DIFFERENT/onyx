package com.onyx.android.ink.model

sealed class InkAction {
    data class AddStroke(
        val stroke: Stroke,
    ) : InkAction()

    data class RemoveStroke(
        val stroke: Stroke,
    ) : InkAction()
}
