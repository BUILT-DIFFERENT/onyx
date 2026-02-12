package com.onyx.android.data

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnyxDatabaseTest {
    @Test
    fun `migration 1 to 2 is registered with expected versions`() {
        assertEquals(1, OnyxDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, OnyxDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `migration 1 to 2 executes without schema mutations`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        OnyxDatabase.MIGRATION_1_2.migrate(database)

        verify(exactly = 0) { database.execSQL(any()) }
    }
}
