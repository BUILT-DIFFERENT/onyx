package com.onyx.android.recognition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MathRecognitionModeTest {
    @Test
    fun `fromStorageValue returns OFF for unknown values`() {
        assertEquals(MathRecognitionMode.OFF, MathRecognitionMode.fromStorageValue("unknown"))
        assertEquals(MathRecognitionMode.OFF, MathRecognitionMode.fromStorageValue(null))
    }

    @Test
    fun `fromStorageValue resolves known values`() {
        assertEquals(MathRecognitionMode.INLINE_PREVIEW, MathRecognitionMode.fromStorageValue("inline_preview"))
        assertEquals(MathRecognitionMode.LATEX_ONLY, MathRecognitionMode.fromStorageValue("latex_only"))
    }
}
