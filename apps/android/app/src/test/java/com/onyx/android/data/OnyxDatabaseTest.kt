package com.onyx.android.data

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
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

    @Test
    fun `migration 2 to 3 is registered with expected versions`() {
        assertEquals(2, OnyxDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, OnyxDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun `migration 2 to 3 creates new tables and columns`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)
        val executedSql = mutableListOf<String>()
        every { database.execSQL(capture(executedSql)) } returns Unit

        OnyxDatabase.MIGRATION_2_3.migrate(database)

        // Verify folderId column added to notes
        assert(executedSql.any { it.contains("ALTER TABLE notes ADD COLUMN folderId") })

        // Verify folders table created
        assert(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS folders") })

        // Verify tags table created
        assert(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS tags") })

        // Verify NoteTagCrossRef table created
        assert(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS NoteTagCrossRef") })

        // Verify thumbnails table created
        assert(executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS thumbnails") })

        // Verify indexes created
        assert(executedSql.any { it.contains("index_notes_folderId") })
        assert(executedSql.any { it.contains("index_folders_parentId") })
        assert(executedSql.any { it.contains("index_NoteTagCrossRef_noteId") })
        assert(executedSql.any { it.contains("index_NoteTagCrossRef_tagId") })
    }
}
