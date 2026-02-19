package com.onyx.android.data.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LamportClockTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var lamportClock: LamportClock

    @BeforeEach
    fun setup() {
        context = mockk()
        prefs = mockk()
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("onyx_lamport_clock", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getLong("lamport_counter", 0L) } returns 0L

        lamportClock = LamportClock(context)
    }

    @Test
    fun `next returns 1 when starting from 0`() {
        every { prefs.getLong("lamport_counter", 0L) } returns 0L

        val result = lamportClock.next()

        assertEquals(1L, result)
        verify { editor.putLong("lamport_counter", 1L) }
    }

    @Test
    fun `next increments counter by 1`() {
        every { prefs.getLong("lamport_counter", 0L) } returns 5L

        val result = lamportClock.next()

        assertEquals(6L, result)
        verify { editor.putLong("lamport_counter", 6L) }
    }

    @Test
    fun `peek returns current value without incrementing`() {
        every { prefs.getLong("lamport_counter", 0L) } returns 42L

        val result = lamportClock.peek()

        assertEquals(42L, result)
        verify(exactly = 0) { editor.putLong(any(), any()) }
    }

    @Test
    fun `updateIfGreater does nothing when received is smaller`() {
        every { prefs.getLong("lamport_counter", 0L) } returns 10L

        lamportClock.updateIfGreater(5L)

        verify(exactly = 0) { editor.putLong(any(), any()) }
        verify(exactly = 0) { editor.commit() }
    }

    @Test
    fun `updateIfGreater does nothing when received equals current`() {
        every { prefs.getLong("lamport_counter", 0L) } returns 10L

        lamportClock.updateIfGreater(10L)

        verify(exactly = 0) { editor.putLong(any(), any()) }
        verify(exactly = 0) { editor.commit() }
    }

    @Test
    fun `next is monotonic across calls`() {
        var currentValue = 0L
        every { prefs.getLong("lamport_counter", 0L) } answers { currentValue }
        every { editor.putLong("lamport_counter", any()) } answers {
            currentValue = secondArg()
            editor
        }

        val results = mutableListOf<Long>()
        repeat(5) {
            results.add(lamportClock.next())
        }

        for (i in 1 until results.size) {
            assertTrue(results[i] > results[i - 1], "Values should be monotonic: $results")
        }
    }

    @Test
    fun `updateIfGreater seeds clock and next stays monotonic`() {
        var currentValue = 7L
        every { prefs.getLong("lamport_counter", 0L) } answers { currentValue }
        every { editor.putLong("lamport_counter", any()) } answers {
            currentValue = secondArg()
            editor
        }
        every { editor.commit() } answers { true }

        lamportClock.updateIfGreater(99L)
        val nextValue = lamportClock.next()

        assertEquals(100L, nextValue)
        assertEquals(100L, currentValue)
    }
}
