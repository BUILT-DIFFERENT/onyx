package com.onyx.android.input

enum class SingleFingerMode {
    DRAW,
    PAN,
    IGNORE,
}

enum class DoubleFingerMode {
    ZOOM_PAN,
    IGNORE,
}

enum class StylusButtonAction {
    ERASER_HOLD,
    NO_ACTION,
}

data class InputSettings(
    val singleFingerMode: SingleFingerMode = SingleFingerMode.PAN,
    val doubleFingerMode: DoubleFingerMode = DoubleFingerMode.ZOOM_PAN,
    val stylusPrimaryAction: StylusButtonAction = StylusButtonAction.ERASER_HOLD,
    val stylusSecondaryAction: StylusButtonAction = StylusButtonAction.ERASER_HOLD,
    val stylusLongHoldAction: StylusButtonAction = StylusButtonAction.NO_ACTION,
)
