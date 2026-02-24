package com.onyx.android.input

enum class SingleFingerMode {
    DRAW,
    PAN,
    IGNORE,
}

enum class DoubleFingerMode {
    ZOOM_PAN,
    PAN_ONLY,
    IGNORE,
}

enum class StylusButtonAction {
    ERASER_HOLD,
    ERASER_TOGGLE,
    NO_ACTION,
}

enum class DoubleTapZoomAction {
    NONE,
    CYCLE_PRESET,
    FIT_TO_PAGE,
}

enum class DoubleTapZoomPointerMode {
    FINGER_ONLY,
    FINGER_AND_STYLUS,
}

enum class MultiFingerTapAction {
    NONE,
    UNDO,
    REDO,
}

data class InputSettings(
    val singleFingerMode: SingleFingerMode = SingleFingerMode.PAN,
    val doubleFingerMode: DoubleFingerMode = DoubleFingerMode.ZOOM_PAN,
    val stylusPrimaryAction: StylusButtonAction = StylusButtonAction.ERASER_HOLD,
    val stylusSecondaryAction: StylusButtonAction = StylusButtonAction.ERASER_HOLD,
    val stylusLongHoldAction: StylusButtonAction = StylusButtonAction.NO_ACTION,
    val doubleTapZoomAction: DoubleTapZoomAction = DoubleTapZoomAction.NONE,
    val doubleTapZoomPointerMode: DoubleTapZoomPointerMode = DoubleTapZoomPointerMode.FINGER_ONLY,
    val twoFingerTapAction: MultiFingerTapAction = MultiFingerTapAction.UNDO,
    val threeFingerTapAction: MultiFingerTapAction = MultiFingerTapAction.REDO,
)
