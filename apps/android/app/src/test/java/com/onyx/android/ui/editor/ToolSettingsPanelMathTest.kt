package com.onyx.android.ui.editor

import com.onyx.android.ink.model.Brush
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSettingsPanelMathTest {
    @Test
    fun `applyPressureSensitivity keeps width factors bounded and ordered`() {
        val brush = Brush()

        val low = applyPressureSensitivity(brush, 0f)
        val high = applyPressureSensitivity(brush, 1f)

        assertTrue(low.minWidthFactor < low.maxWidthFactor)
        assertTrue(high.minWidthFactor < high.maxWidthFactor)
        assertTrue(low.minWidthFactor >= 0.1f)
        assertTrue(high.maxWidthFactor <= 2.2f)
        assertTrue(high.maxWidthFactor - high.minWidthFactor > low.maxWidthFactor - low.minWidthFactor)
    }

    @Test
    fun `resolvePressureSensitivity round trips default brush spread`() {
        val brush = Brush(minWidthFactor = 0.85f, maxWidthFactor = 1.15f)

        val resolved = resolvePressureSensitivity(brush)
        val reapplied = applyPressureSensitivity(brush, resolved)

        assertEquals(
            brush.maxWidthFactor - brush.minWidthFactor,
            reapplied.maxWidthFactor - reapplied.minWidthFactor,
            0.0001f,
        )
    }

    @Test
    fun `applySmoothingLevel and resolveSmoothingLevel stay clamped`() {
        val brush = Brush(smoothingLevel = 3f)
        assertEquals(1f, resolveSmoothingLevel(brush))

        val lowered = applySmoothingLevel(brush, -1f)
        val raised = applySmoothingLevel(brush, 2f)

        assertEquals(0f, lowered.smoothingLevel)
        assertEquals(1f, raised.smoothingLevel)
    }
}
