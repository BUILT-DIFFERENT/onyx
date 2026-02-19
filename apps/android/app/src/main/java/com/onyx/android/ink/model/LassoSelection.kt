package com.onyx.android.ink.model

data class LassoSelection(
    val selectedStrokeIds: Set<String> = emptySet(),
    val lassoPath: List<Pair<Float, Float>> = emptyList(),
    val selectionBounds: StrokeBounds? = null,
    val isActive: Boolean = false,
    val transformCenter: Pair<Float, Float> = Pair(0f, 0f),
) {
    val hasSelection: Boolean get() = selectedStrokeIds.isNotEmpty()

    fun withSelection(
        strokeIds: Set<String>,
        bounds: StrokeBounds?,
    ): LassoSelection =
        copy(
            selectedStrokeIds = strokeIds,
            selectionBounds = bounds,
            transformCenter = bounds?.let { Pair(it.x + it.w / 2, it.y + it.h / 2) } ?: Pair(0f, 0f),
        )

    fun withLassoPath(path: List<Pair<Float, Float>>): LassoSelection =
        copy(
            lassoPath = path,
        )

    fun clear(): LassoSelection = LassoSelection()

    fun beginLasso(): LassoSelection =
        copy(
            isActive = true,
            selectedStrokeIds = emptySet(),
            lassoPath = emptyList(),
            selectionBounds = null,
        )

    fun endLasso(): LassoSelection =
        copy(
            isActive = false,
            lassoPath = emptyList(),
        )
}
