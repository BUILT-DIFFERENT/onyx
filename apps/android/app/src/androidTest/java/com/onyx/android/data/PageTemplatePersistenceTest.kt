package com.onyx.android.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTemplatePersistenceTest {
    @Test
    fun template_association_survives_database_reopen() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dbName = "page-template-persistence-test.db"
            context.deleteDatabase(dbName)

            val firstOpen =
                Room
                    .databaseBuilder(context, OnyxDatabase::class.java, dbName)
                    .addMigrations(
                        OnyxDatabase.MIGRATION_1_2,
                        OnyxDatabase.MIGRATION_2_3,
                        OnyxDatabase.MIGRATION_3_4,
                        com.onyx.android.data.migrations.MIGRATION_4_5,
                        com.onyx.android.data.migrations.MIGRATION_5_6,
                    ).build()

            val pageId = "page-template-test"
            val noteId = "note-template-test"
            val templateId = "template-template-test"
            val timestamp = 1700000000000L

            try {
                firstOpen.noteDao().insert(
                    com.onyx.android.data.entity.NoteEntity(
                        noteId = noteId,
                        ownerUserId = "tester",
                        title = "Template test",
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        deletedAt = null,
                    ),
                )
                firstOpen.pageTemplateDao().insert(
                    com.onyx.android.data.entity.PageTemplateEntity(
                        templateId = templateId,
                        name = "Grid 24",
                        backgroundKind = "grid",
                        spacing = 24f,
                        color = "#E0E0E0",
                        isBuiltIn = false,
                        createdAt = timestamp,
                    ),
                )
                firstOpen.pageDao().insert(
                    com.onyx.android.data.entity.PageEntity(
                        pageId = pageId,
                        noteId = noteId,
                        kind = "ink",
                        geometryKind = "fixed",
                        indexInNote = 0,
                        width = 612f,
                        height = 792f,
                        templateId = templateId,
                        updatedAt = timestamp,
                        contentLamportMax = 0,
                    ),
                )
            } finally {
                firstOpen.close()
            }

            val secondOpen =
                Room
                    .databaseBuilder(context, OnyxDatabase::class.java, dbName)
                    .addMigrations(
                        OnyxDatabase.MIGRATION_1_2,
                        OnyxDatabase.MIGRATION_2_3,
                        OnyxDatabase.MIGRATION_3_4,
                        com.onyx.android.data.migrations.MIGRATION_4_5,
                        com.onyx.android.data.migrations.MIGRATION_5_6,
                    ).build()

            try {
                val reopenedPage = secondOpen.pageDao().getById(pageId)
                assertNotNull(reopenedPage)
                assertEquals(templateId, reopenedPage?.templateId)

                val reopenedTemplate = secondOpen.pageTemplateDao().getById(templateId)
                assertNotNull(reopenedTemplate)
                assertEquals("grid", reopenedTemplate?.backgroundKind)
                assertEquals(24f, reopenedTemplate?.spacing ?: 0f, 0.0001f)

                secondOpen.pageTemplateDao().delete(templateId)
                val pageAfterTemplateDelete = secondOpen.pageDao().getById(pageId)
                assertNull(pageAfterTemplateDelete?.templateId)
            } finally {
                secondOpen.close()
                context.deleteDatabase(dbName)
            }
        }
    }
}
