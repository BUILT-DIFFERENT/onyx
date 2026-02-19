@file:Suppress("MagicNumber")

package com.onyx.android.config

import com.onyx.android.ink.model.BrushPreset
import com.onyx.android.ink.model.Tool
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrushPresetTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun `valid preset data is accepted`() {
        val preset =
            BrushPreset(
                id = "custom_pen",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "#112233",
                baseWidth = 2.4f,
                smoothingLevel = 0.5f,
                endTaperStrength = 0.4f,
                minWidthFactor = 0.8f,
                maxWidthFactor = 1.2f,
                nibRotation = false,
            )

        assertEquals("custom_pen", preset.id)
        assertEquals(Tool.PEN, preset.tool)
    }

    @Test
    fun `blank preset id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrushPreset(
                id = "",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "#112233",
                baseWidth = 2f,
            )
        }
    }

    @Test
    fun `invalid color format is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrushPreset(
                id = "custom_pen",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "112233",
                baseWidth = 2f,
            )
        }
    }

    @Test
    fun `non-positive base width is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrushPreset(
                id = "custom_pen",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "#112233",
                baseWidth = 0f,
            )
        }
    }

    @Test
    fun `smoothing and taper out of range are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrushPreset(
                id = "custom_pen",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "#112233",
                baseWidth = 2f,
                smoothingLevel = 1.1f,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            BrushPreset(
                id = "custom_pen",
                name = "Custom Pen",
                tool = Tool.PEN,
                color = "#112233",
                baseWidth = 2f,
                endTaperStrength = -0.1f,
            )
        }
    }

    @Test
    fun `json round trip preserves preset values`() {
        val preset = BrushPreset.FOUNTAIN

        val encoded = json.encodeToString(preset)
        val decoded = json.decodeFromString<BrushPreset>(encoded)

        assertEquals(preset, decoded)
    }

    @Test
    fun `default presets include expected five valid entries`() {
        val defaults = BrushPreset.DEFAULT_PRESETS

        assertEquals(5, defaults.size)
        assertEquals(5, defaults.map { it.id }.toSet().size)
        assertTrue(defaults.any { it.name == "Ballpoint" && it.tool == Tool.PEN })
        assertTrue(defaults.any { it.name == "Fountain" && it.tool == Tool.PEN })
        assertTrue(defaults.any { it.name == "Pencil" && it.tool == Tool.PEN })
        assertTrue(defaults.any { it.name == "Standard" && it.tool == Tool.HIGHLIGHTER })
        assertTrue(defaults.any { it.name == "Wide" && it.tool == Tool.HIGHLIGHTER })
        assertTrue(defaults.filter { it.tool == Tool.HIGHLIGHTER }.all { it.endTaperStrength == 0f })
    }
}
