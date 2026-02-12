package com.onyx.android.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnyxDatabaseTest {
    @Test
    fun `migration 1 to 2 is registered with expected versions`() {
        assertEquals(1, OnyxDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, OnyxDatabase.MIGRATION_1_2.endVersion)
    }
}
