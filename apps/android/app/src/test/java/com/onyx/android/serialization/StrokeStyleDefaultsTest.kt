@file:Suppress("MagicNumber")

package com.onyx.android.serialization

import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StrokeStyleDefaultsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `missing smoothingLevel uses default value`() {
        val serialized =
            """
            {
              "tool":"pen",
              "color":"#000000",
              "baseWidth":2.0,
              "minWidthFactor":0.8,
              "maxWidthFactor":1.2,
              "endTaperStrength":0.6,
              "nibRotation":false
            }
            """.trimIndent()

        val style = json.decodeFromString<StrokeStyle>(serialized)

        assertEquals(Tool.PEN, style.tool)
        assertEquals(0.35f, style.smoothingLevel)
    }

    @Test
    fun `missing endTaperStrength uses default value`() {
        val serialized =
            """
            {
              "tool":"pen",
              "color":"#000000",
              "baseWidth":2.0,
              "minWidthFactor":0.8,
              "maxWidthFactor":1.2,
              "smoothingLevel":0.6,
              "nibRotation":false
            }
            """.trimIndent()

        val style = json.decodeFromString<StrokeStyle>(serialized)

        assertEquals(Tool.PEN, style.tool)
        assertEquals(0.35f, style.endTaperStrength)
    }

    @Test
    fun `missing minWidthFactor uses default value`() {
        val serialized =
            """
            {
              "tool":"highlighter",
              "color":"#FDE047",
              "baseWidth":8.0,
              "maxWidthFactor":1.3,
              "smoothingLevel":0.5,
              "endTaperStrength":0.0,
              "nibRotation":false
            }
            """.trimIndent()

        val style = json.decodeFromString<StrokeStyle>(serialized)

        assertEquals(Tool.HIGHLIGHTER, style.tool)
        assertEquals(0.85f, style.minWidthFactor)
    }

    @Test
    fun `missing maxWidthFactor uses default value`() {
        val serialized =
            """
            {
              "tool":"highlighter",
              "color":"#FDE047",
              "baseWidth":8.0,
              "minWidthFactor":0.9,
              "smoothingLevel":0.5,
              "endTaperStrength":0.0,
              "nibRotation":false
            }
            """.trimIndent()

        val style = json.decodeFromString<StrokeStyle>(serialized)

        assertEquals(Tool.HIGHLIGHTER, style.tool)
        assertEquals(1.15f, style.maxWidthFactor)
    }
}
