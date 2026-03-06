@file:Suppress("LongParameterList", "TooManyFunctions")

package com.onyx.android.ink.vk

import android.util.Log

internal object VkNativeBridge {
    private const val TAG = "VkNativeBridge"

    private val isLibraryLoaded: Boolean =
        runCatching {
            System.loadLibrary("onyx_vk_ink")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Failed to load Vulkan JNI library", error)
            false
        }

    fun isSupported(): Boolean = isLibraryLoaded && nativeIsSupported()

    fun createRenderer(): Long {
        if (!isLibraryLoaded) {
            return 0L
        }
        return nativeCreateRenderer()
    }

    fun destroyRenderer(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeDestroyRenderer(handle)
    }

    fun initSurface(
        handle: Long,
        surface: android.view.Surface,
        width: Int,
        height: Int,
    ): Boolean {
        if (!isLibraryLoaded || handle == 0L) {
            return false
        }
        return nativeInitSurface(handle, surface, width, height)
    }

    fun destroySurface(handle: Long) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeDestroySurface(handle)
    }

    fun resize(
        handle: Long,
        width: Int,
        height: Int,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeResize(handle, width, height)
    }

    fun drawFrame(handle: Long): Boolean {
        if (!isLibraryLoaded || handle == 0L) {
            return false
        }
        return nativeDrawFrame(handle)
    }

    fun setPageSize(
        handle: Long,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeSetPageSize(handle, pageWidth, pageHeight)
    }

    fun setViewTransform(
        handle: Long,
        zoom: Float,
        panX: Float,
        panY: Float,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeSetViewTransform(handle, zoom, panX, panY)
    }

    fun setOverlayState(
        handle: Long,
        selectedStrokeCount: Int,
        lassoPointCount: Int,
        hoverVisible: Boolean,
        hoverX: Float,
        hoverY: Float,
        hoverRadius: Float,
        hoverColor: Int,
        hoverAlpha: Float,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeSetOverlayState(
            handle,
            selectedStrokeCount,
            lassoPointCount,
            hoverVisible,
            hoverX,
            hoverY,
            hoverRadius,
            hoverColor,
            hoverAlpha,
        )
    }

    fun syncCommittedStrokes(
        handle: Long,
        strokeCount: Int,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeSyncCommittedStrokes(handle, strokeCount)
    }

    fun startStroke(
        handle: Long,
        strokeId: Long,
        input: VkStrokeInput,
        brush: VkBrush,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeStartStroke(
            handle,
            strokeId,
            input.x,
            input.y,
            input.eventTimeMillis,
            input.pressure,
            input.tiltRadians,
            input.orientationRadians,
            brush.argbColor,
            brush.alphaMultiplier,
            brush.strokeStyle.baseWidth,
            brush.strokeStyle.minWidthFactor,
            brush.strokeStyle.maxWidthFactor,
            brush.strokeStyle.smoothingLevel,
            brush.strokeStyle.endTaperStrength,
            brush.strokeStyle.lineStyle.ordinal,
            brush.strokeStyle.tool.ordinal,
        )
    }

    fun addStrokePoint(
        handle: Long,
        strokeId: Long,
        input: VkStrokeInput,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeAddStrokePoint(
            handle,
            strokeId,
            input.x,
            input.y,
            input.eventTimeMillis,
            input.pressure,
            input.tiltRadians,
            input.orientationRadians,
        )
    }

    fun finishStroke(
        handle: Long,
        strokeId: Long,
        input: VkStrokeInput,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeFinishStroke(
            handle,
            strokeId,
            input.x,
            input.y,
            input.eventTimeMillis,
            input.pressure,
            input.tiltRadians,
            input.orientationRadians,
        )
    }

    fun cancelStroke(
        handle: Long,
        strokeId: Long,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeCancelStroke(handle, strokeId)
    }

    fun removeFinishedStrokes(
        handle: Long,
        strokeIds: LongArray,
    ) {
        if (!isLibraryLoaded || handle == 0L) {
            return
        }
        nativeRemoveFinishedStrokes(handle, strokeIds)
    }

    private external fun nativeIsSupported(): Boolean

    private external fun nativeCreateRenderer(): Long

    private external fun nativeDestroyRenderer(handle: Long)

    private external fun nativeInitSurface(
        handle: Long,
        surface: android.view.Surface,
        width: Int,
        height: Int,
    ): Boolean

    private external fun nativeDestroySurface(handle: Long)

    private external fun nativeResize(
        handle: Long,
        width: Int,
        height: Int,
    )

    private external fun nativeDrawFrame(handle: Long): Boolean

    private external fun nativeSetPageSize(
        handle: Long,
        pageWidth: Float,
        pageHeight: Float,
    )

    private external fun nativeSetViewTransform(
        handle: Long,
        zoom: Float,
        panX: Float,
        panY: Float,
    )

    private external fun nativeSetOverlayState(
        handle: Long,
        selectedStrokeCount: Int,
        lassoPointCount: Int,
        hoverVisible: Boolean,
        hoverX: Float,
        hoverY: Float,
        hoverRadius: Float,
        hoverColor: Int,
        hoverAlpha: Float,
    )

    private external fun nativeSyncCommittedStrokes(
        handle: Long,
        strokeCount: Int,
    )

    private external fun nativeStartStroke(
        handle: Long,
        strokeId: Long,
        x: Float,
        y: Float,
        eventTimeMillis: Long,
        pressure: Float,
        tiltRadians: Float,
        orientationRadians: Float,
        argbColor: Int,
        alphaMultiplier: Float,
        baseWidth: Float,
        minWidthFactor: Float,
        maxWidthFactor: Float,
        smoothingLevel: Float,
        endTaperStrength: Float,
        lineStyleOrdinal: Int,
        toolOrdinal: Int,
    )

    private external fun nativeAddStrokePoint(
        handle: Long,
        strokeId: Long,
        x: Float,
        y: Float,
        eventTimeMillis: Long,
        pressure: Float,
        tiltRadians: Float,
        orientationRadians: Float,
    )

    private external fun nativeFinishStroke(
        handle: Long,
        strokeId: Long,
        x: Float,
        y: Float,
        eventTimeMillis: Long,
        pressure: Float,
        tiltRadians: Float,
        orientationRadians: Float,
    )

    private external fun nativeCancelStroke(
        handle: Long,
        strokeId: Long,
    )

    private external fun nativeRemoveFinishedStrokes(
        handle: Long,
        strokeIds: LongArray,
    )
}
