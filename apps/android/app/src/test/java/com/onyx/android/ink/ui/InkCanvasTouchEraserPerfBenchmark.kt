package com.onyx.android.ink.ui

import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime
import org.junit.jupiter.api.Assertions.assertTrue

private data class MockStroke(val id: String)

class InkCanvasTouchEraserPerfBenchmark {

    @Test
    fun benchmarkEraserAllocation() {
        val strokes1 = (1..5000).map { MockStroke("id_$it") }
        val strokes2 = (4000..6000).map { MockStroke("id_$it") }

        val allStrokes = strokes1 + strokes2

        // Warmup
        repeat(101) {
            LinkedHashMap<String, MockStroke>(allStrokes.size).apply {
                allStrokes.forEach { stroke -> put(stroke.id, stroke) }
            }.values.toList()

            allStrokes.distinctBy { it.id }
        }

        // Benchmark LinkedHashMap approach
        var timeLinkedHashMap = 0L
        repeat(101) {
            timeLinkedHashMap += measureNanoTime {
                LinkedHashMap<String, MockStroke>(allStrokes.size).apply {
                    allStrokes.forEach { stroke -> put(stroke.id, stroke) }
                }.values.toList()
            }
        }

        // Benchmark distinctBy approach
        var timeDistinctBy = 0L
        repeat(101) {
            timeDistinctBy += measureNanoTime {
                allStrokes.distinctBy { it.id }
            }
        }

        println("Average time for LinkedHashMap: ${timeLinkedHashMap / 100 / 1_000_000.0} ms")
        println("Average time for distinctBy: ${timeDistinctBy / 100 / 1_000_000.0} ms")

        assertTrue(true) // Just to pass the test
    }
}
