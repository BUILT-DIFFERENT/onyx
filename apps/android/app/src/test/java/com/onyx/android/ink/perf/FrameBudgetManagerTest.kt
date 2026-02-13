package com.onyx.android.ink.perf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameBudgetManagerTest {
    @Test
    fun `runWithinBudget processes all items when elapsed time stays within budget`() {
        val clock = FakeNanoClock()
        val manager = FrameBudgetManager(targetFps = 60, nanoTimeProvider = clock::now)
        val processed = mutableListOf<Int>()

        val count =
            manager.runWithinBudget(listOf(1, 2, 3, 4)) { item ->
                processed += item
            }

        assertEquals(4, count)
        assertEquals(listOf(1, 2, 3, 4), processed)
    }

    @Test
    fun `runWithinBudget stops once elapsed time exceeds effective frame budget`() {
        val clock = FakeNanoClock()
        val manager = FrameBudgetManager(targetFps = 60, nanoTimeProvider = clock::now)
        val processed = mutableListOf<Int>()

        val count =
            manager.runWithinBudget((1..10).toList()) { item ->
                processed += item
                clock.advanceNanos(5_000_000L) // 5ms per item
            }

        assertEquals(3, count)
        assertEquals(listOf(1, 2, 3), processed)
    }

    @Test
    fun `isOverBudget returns true only after crossing effective budget`() {
        val clock = FakeNanoClock()
        val manager = FrameBudgetManager(targetFps = 120, nanoTimeProvider = clock::now)
        val frameStart = clock.now()

        assertFalse(manager.isOverBudget(frameStart))

        clock.advanceNanos(6_000_000L) // 6ms < 6.6ms effective budget
        assertFalse(manager.isOverBudget(frameStart))

        clock.advanceNanos(800_000L) // 6.8ms > 6.6ms effective budget
        assertTrue(manager.isOverBudget(frameStart))
    }

    @Test
    fun `constructor validates input arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameBudgetManager(targetFps = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameBudgetManager(targetFps = 60, budgetUtilization = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameBudgetManager(targetFps = 60, budgetUtilization = 1.1f)
        }
    }
}

private class FakeNanoClock(
    private var currentNanos: Long = 0L,
) {
    fun now(): Long = currentNanos

    fun advanceNanos(deltaNanos: Long) {
        currentNanos += deltaNanos
    }
}
