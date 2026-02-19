package com.onyx.android.data.sync

import com.onyx.android.data.entity.OperationLogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationLogEntityTest {
    @Test
    fun `entity has correct default values`() {
        val op =
            OperationLogEntity(
                opId = "op-123",
                noteId = "note-123",
                deviceId = "device-1",
                lamportClock = 1L,
                operationType = "stroke_add",
                payload = "{}",
                createdAt = 1000L,
            )

        assertEquals("op-123", op.opId)
        assertEquals("note-123", op.noteId)
        assertEquals(1L, op.lamportClock)
    }

    @Test
    fun `entity stores all fields correctly`() {
        val op =
            OperationLogEntity(
                opId = "op-456",
                noteId = "note-456",
                payload = """{"title":"New Title"}""",
                deviceId = "device-2",
                lamportClock = 42L,
                operationType = "page_update",
                createdAt = 2000L,
            )

        assertEquals("op-456", op.opId)
        assertEquals("note-456", op.noteId)
        assertEquals("page_update", op.operationType)
        assertEquals("""{"title":"New Title"}""", op.payload)
        assertEquals("device-2", op.deviceId)
        assertEquals(42L, op.lamportClock)
        assertEquals(2000L, op.createdAt)
    }

    @Test
    fun `lamport sequence is monotonic in sorted operation list`() {
        val operations =
            listOf(
                OperationLogEntity("op-2", "note-1", "device", 2L, "stroke_add", "{}", 200L),
                OperationLogEntity("op-1", "note-1", "device", 1L, "stroke_add", "{}", 100L),
                OperationLogEntity("op-3", "note-1", "device", 3L, "stroke_add", "{}", 300L),
            ).sortedBy { it.lamportClock }

        for (i in 1 until operations.size) {
            assertTrue(operations[i].lamportClock > operations[i - 1].lamportClock)
        }
    }
}
