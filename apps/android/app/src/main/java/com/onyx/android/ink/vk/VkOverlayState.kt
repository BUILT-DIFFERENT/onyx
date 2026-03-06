package com.onyx.android.ink.vk

internal data class VkHoverOverlay(
    val isVisible: Boolean = false,
    val screenX: Float = 0f,
    val screenY: Float = 0f,
    val screenRadius: Float = 0f,
    val argbColor: Int = 0,
    val alpha: Float = 0f,
)

internal data class VkOverlayState(
    val selectedStrokeIds: Set<String> = emptySet(),
    val lassoPath: List<Pair<Float, Float>> = emptyList(),
    val hover: VkHoverOverlay = VkHoverOverlay(),
)
