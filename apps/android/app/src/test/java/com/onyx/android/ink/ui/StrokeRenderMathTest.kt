package com.onyx.android.ink.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrokeRenderMathTest {
    @Test
    fun `pressure gamma clamps and preserves endpoints`() {
        assertEquals(0f, applyPressureGamma(-1f), 0.0001f)
        assertEquals(0f, applyPressureGamma(0f), 0.0001f)
        assertEquals(1f, applyPressureGamma(1f), 0.0001f)
        assertEquals(1f, applyPressureGamma(2f), 0.0001f)
    }

    @Test
    fun `pressure gamma boosts mid-range pressure`() {
        val gammaMid = applyPressureGamma(0.5f)
        assertEquals(0.6597539f, gammaMid, 0.0001f)
    }

    @Test
    fun `pressure width uses min factor at zero pressure`() {
        val width =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = 0f,
            )

        assertEquals(2f, width, 0.0001f)
    }

    @Test
    fun `pressure width uses midpoint at half pressure`() {
        val width =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = 0.5f,
            )

        assertEquals(4f, width, 0.0001f)
    }

    @Test
    fun `pressure width uses max factor at full pressure`() {
        val width =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = 1f,
            )

        assertEquals(6f, width, 0.0001f)
    }

    @Test
    fun `pressure width uses fallback when pressure is null`() {
        val width =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = null,
                pressureFallback = 0.5f,
            )

        assertEquals(4f, width, 0.0001f)
    }

    @Test
    fun `pressure width clamps out of range values`() {
        val belowRange =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = -1f,
            )
        val aboveRange =
            pressureWidth(
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                pressure = 2f,
            )

        assertEquals(2f, belowRange, 0.0001f)
        assertEquals(6f, aboveRange, 0.0001f)
    }
}
