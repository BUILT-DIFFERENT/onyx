package com.onyx.android.data.serialization

import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeSerializerTest {
    @Test
    fun `serialize 100 points and deserialize returns exact same data`() {
        val points =
            (0..99).map { i ->
                StrokePoint(
                    x = i.toFloat(),
                    y = i * 2f,
                    t = 1000L + i,
                    p = if (i % 2 == 0) 0.5f else null,
                    tx = if (i % 3 == 0) 0.1f else null,
                    ty = if (i % 3 == 0) 0.2f else null,
                    r = if (i % 5 == 0) 45f else null,
                )
            }

        val serialized = StrokeSerializer.serializePoints(points)
        val deserialized = StrokeSerializer.deserializePoints(serialized)

        assertEquals(points, deserialized)
    }

    @Test
    fun `point payload is protobuf bytes and deterministic for same input`() {
        val points =
            listOf(
                StrokePoint(x = 1f, y = 2f, t = 123L, p = 0.5f, tx = null, ty = null, r = null),
            )

        val payloadA = StrokeSerializer.serializePoints(points)
        val payloadB = StrokeSerializer.serializePoints(points)
        val payloadAsText = payloadA.toString(Charsets.UTF_8)

        assertTrue(payloadA.isNotEmpty())
        assertTrue(payloadA.contentEquals(payloadB))
        assertTrue(!payloadAsText.trimStart().startsWith("["))
    }

    @Test
    fun `style JSON contains all fields`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                color = "#0000FF",
                baseWidth = 2f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
                nibRotation = true,
            )

        val json = StrokeSerializer.serializeStyle(style)

        assertTrue(json.contains("\"tool\":"))
        assertTrue(json.contains("\"color\":"))
        assertTrue(json.contains("\"baseWidth\":"))
        assertTrue(json.contains("\"minWidthFactor\":"))
        assertTrue(json.contains("\"maxWidthFactor\":"))
    }

    @Test
    fun `bounds JSON contains x y w h`() {
        val bounds = StrokeBounds(x = 10f, y = 20f, w = 100f, h = 200f)

        val json = StrokeSerializer.serializeBounds(bounds)

        assertTrue(json.contains("\"x\":"))
        assertTrue(json.contains("\"y\":"))
        assertTrue(json.contains("\"w\":"))
        assertTrue(json.contains("\"h\":"))
    }

    @Test
    fun `style round-trip preserves data`() {
        val original =
            StrokeStyle(
                tool = Tool.HIGHLIGHTER,
                color = "#FF00FF",
                baseWidth = 3.5f,
                minWidthFactor = 0.7f,
                maxWidthFactor = 1.9f,
                nibRotation = true,
            )

        val json = StrokeSerializer.serializeStyle(original)
        val deserialized = StrokeSerializer.deserializeStyle(json)

        assertEquals(original, deserialized)
    }
}
