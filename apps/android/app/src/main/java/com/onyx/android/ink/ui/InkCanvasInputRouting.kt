@file:Suppress("ReturnCount")

package com.onyx.android.ink.ui

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs

private const val STYLUS_BUTTON_MASK =
    MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY
private const val UNKNOWN_STYLUS_SIZE_MAX = 0.08f
private const val STYLUS_AXIS_EPSILON = 0.001f

internal fun eventHasStylusStream(
    event: MotionEvent,
    runtime: InkCanvasRuntime? = null,
): Boolean {
    for (index in 0 until event.pointerCount) {
        if (isPointerStylusStream(event, index, runtime)) {
            return true
        }
    }
    if (runtime == null) {
        return false
    }
    if (runtime.stylusPointerIds.isNotEmpty()) {
        return true
    }
    for (index in 0 until event.pointerCount) {
        val pointerId = event.getPointerId(index)
        if (
            runtime.stylusPointerIds.contains(pointerId) ||
            runtime.activeStrokeIds.containsKey(pointerId)
        ) {
            return true
        }
    }
    return false
}

internal fun isPointerStylusStream(
    event: MotionEvent,
    pointerIndex: Int,
    runtime: InkCanvasRuntime? = null,
): Boolean {
    if (pointerIndex < 0 || pointerIndex >= event.pointerCount) {
        return false
    }
    val toolType = event.getToolType(pointerIndex)
    if (isStylusToolType(toolType) || isUnknownStylusLikePointer(event, pointerIndex)) {
        return true
    }
    val pointerId = event.getPointerId(pointerIndex)
    if (runtime?.stylusPointerIds?.contains(pointerId) == true) {
        return true
    }
    val pointerIsDefinitelyFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
    if (pointerIsDefinitelyFinger) {
        return false
    }
    if (eventIsFromStylusSource(event) || (event.buttonState and STYLUS_BUTTON_MASK) != 0) {
        return true
    }
    return false
}

internal fun isPointerDefinitelyFinger(
    event: MotionEvent,
    pointerIndex: Int,
): Boolean = event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_FINGER

internal fun eventIsFingerOnly(event: MotionEvent): Boolean {
    if (event.pointerCount == 0) {
        return false
    }
    for (index in 0 until event.pointerCount) {
        if (!isPointerDefinitelyFinger(event, index)) {
            return false
        }
    }
    return true
}

private fun eventIsFromStylusSource(event: MotionEvent): Boolean =
    event.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS ||
        event.source and InputDevice.SOURCE_BLUETOOTH_STYLUS == InputDevice.SOURCE_BLUETOOTH_STYLUS

private fun isUnknownStylusLikePointer(
    event: MotionEvent,
    pointerIndex: Int,
): Boolean {
    if (event.getToolType(pointerIndex) != MotionEvent.TOOL_TYPE_UNKNOWN) {
        return false
    }
    val distance = event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex)
    val tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
    val orientation = event.getOrientation(pointerIndex)
    if (distance > STYLUS_AXIS_EPSILON) {
        return true
    }
    if (abs(tilt) > STYLUS_AXIS_EPSILON) {
        return true
    }
    if (abs(orientation) > STYLUS_AXIS_EPSILON && event.getSize(pointerIndex) <= UNKNOWN_STYLUS_SIZE_MAX) {
        return true
    }
    return false
}
