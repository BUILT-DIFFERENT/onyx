@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:argument-list-wrapping",
    "ktlint:standard:discouraged-comment-location",
)

package com.onyx.android.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.onyx.android.data.serialization.StrokeSerializer
import com.onyx.android.ink.model.StrokePoint
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for OnyxDatabase v2→v3.
 *
 * This test verifies that the migration from v2 to v3 preserves data integrity
 * and correctly adds new tables (folders, tags, NoteTagCrossRef, thumbnails)
 * and the folderId column to notes.
 *
 * Seed data:
 * - 2 notes
 * - 3 pages (2 for note1, 1 for note2)
 * - 5 strokes (3 for page1, 2 for page2)
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalSerializationApi::class)
class OnyxDatabaseMigrationTest {
    private val testDatabaseName = "onyx_notes_migration_test.db"

    @get:Rule
    val migrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            OnyxDatabase::class.java,
            // No auto-migrations
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    // Test data constants
    private val note1Id = "note-001"
    private val note2Id = "note-002"
    private val page1Id = "page-001"
    private val page2Id = "page-002"
    private val page3Id = "page-003"
    private val stroke1Id = "stroke-001"
    private val stroke2Id = "stroke-002"
    private val stroke3Id = "stroke-003"
    private val stroke4Id = "stroke-004"
    private val stroke5Id = "stroke-005"

    private val testTimestamp = 1700000000000L

    /**
     * Creates protobuf-encoded stroke data for testing.
     */
    private fun createTestStrokeData(): ByteArray {
        val points =
            listOf(
                StrokePoint(x = 100f, y = 100f, t = testTimestamp, p = 0.5f),
                StrokePoint(x = 150f, y = 120f, t = testTimestamp + 10, p = 0.6f),
                StrokePoint(x = 200f, y = 140f, t = testTimestamp + 20, p = 0.7f),
            )
        return StrokeSerializer.serializePoints(points)
    }

    /**
     * Test: Verify v2→v3 migration preserves all existing data.
     *
     * Steps:
     * 1. Create database at version 2
     * 2. Insert seed data (2 notes, 3 pages, 5 strokes)
     * 3. Run migration to version 3
     * 4. Verify all data is preserved
     */
    @Test
    fun migration_2_to_3_preservesExistingData() {
        runBlocking {
            // Step 1: Create v2 database with seed data
            migrationTestHelper.createDatabase(testDatabaseName, 2).use { db ->
                // Insert 2 notes (v2 schema - no folderId column)
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(note1Id, "user-001", "Test Note 1", testTimestamp, testTimestamp + 1000, null),
                )
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(note2Id, "user-001", "Test Note 2", testTimestamp + 2000, testTimestamp + 3000, null),
                )

                // Insert 3 pages
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, pdfAssetId, pdfPageNo, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(page1Id, note1Id, "CANVAS", "A4", 0, 595.0, 842.0, "pt", null, null, testTimestamp, 0L),
                )
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, pdfAssetId, pdfPageNo, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(page2Id, note1Id, "CANVAS", "A4", 1, 595.0, 842.0, "pt", null, null, testTimestamp + 1000, 0L),
                )
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, pdfAssetId, pdfPageNo, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(page3Id, note2Id, "CANVAS", "A4", 0, 595.0, 842.0, "pt", null, null, testTimestamp + 2000, 0L),
                )

                // Insert 5 strokes with protobuf data
                val strokeData = createTestStrokeData()
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        stroke1Id,
                        page1Id,
                        strokeData,
                        "{\"color\":\"#000000\",\"width\":2.0}",
                        "{\"left\":100.0,\"top\":100.0,\"right\":200.0,\"bottom\":140.0}",
                        testTimestamp,
                        0L,
                    ),
                )
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        stroke2Id,
                        page1Id,
                        strokeData,
                        "{\"color\":\"#FF0000\",\"width\":1.5}",
                        "{\"left\":50.0,\"top\":50.0,\"right\":150.0,\"bottom\":100.0}",
                        testTimestamp + 100,
                        0L,
                    ),
                )
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        stroke3Id,
                        page1Id,
                        strokeData,
                        "{\"color\":\"#00FF00\",\"width\":3.0}",
                        "{\"left\":200.0,\"top\":200.0,\"right\":300.0,\"bottom\":250.0}",
                        testTimestamp + 200,
                        0L,
                    ),
                )
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        stroke4Id,
                        page2Id,
                        strokeData,
                        "{\"color\":\"#0000FF\",\"width\":2.5}",
                        "{\"left\":10.0,\"top\":10.0,\"right\":100.0,\"bottom\":50.0}",
                        testTimestamp + 300,
                        0L,
                    ),
                )
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        stroke5Id,
                        page3Id,
                        strokeData,
                        "{\"color\":\"#000000\",\"width\":1.0}",
                        "{\"left\":0.0,\"top\":0.0,\"right\":50.0,\"bottom\":50.0}",
                        testTimestamp + 400,
                        0L,
                    ),
                )
            }

            // Step 2: Run migration to v3
            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).use { db ->
                    // Step 3: Verify all notes preserved
                    val noteCursor = db.query("SELECT * FROM notes ORDER BY noteId")
                    noteCursor.use { cursor ->
                        assertEquals("All notes should be preserved", 2, cursor.count)

                        assertTrue(cursor.moveToFirst())
                        assertEquals(note1Id, cursor.getString(cursor.getColumnIndexOrThrow("noteId")))
                        assertEquals("Test Note 1", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                        assertNull("folderId should be null for migrated notes", cursor.getString(cursor.getColumnIndexOrThrow("folderId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(note2Id, cursor.getString(cursor.getColumnIndexOrThrow("noteId")))
                        assertEquals("Test Note 2", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                    }

                    // Verify all pages preserved
                    val pageCursor = db.query("SELECT * FROM pages ORDER BY pageId")
                    pageCursor.use { cursor ->
                        assertEquals("All pages should be preserved", 3, cursor.count)

                        assertTrue(cursor.moveToFirst())
                        assertEquals(page1Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))
                        assertEquals(note1Id, cursor.getString(cursor.getColumnIndexOrThrow("noteId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(page2Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))
                        assertEquals(note1Id, cursor.getString(cursor.getColumnIndexOrThrow("noteId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(page3Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))
                        assertEquals(note2Id, cursor.getString(cursor.getColumnIndexOrThrow("noteId")))
                    }

                    // Verify all strokes preserved
                    val strokeCursor = db.query("SELECT * FROM strokes ORDER BY strokeId")
                    strokeCursor.use { cursor ->
                        assertEquals("All strokes should be preserved", 5, cursor.count)

                        // Verify stroke data is intact
                        assertTrue(cursor.moveToFirst())
                        assertEquals(stroke1Id, cursor.getString(cursor.getColumnIndexOrThrow("strokeId")))
                        assertEquals(page1Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))
                        assertNotNull(cursor.getBlob(cursor.getColumnIndexOrThrow("strokeData")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(stroke2Id, cursor.getString(cursor.getColumnIndexOrThrow("strokeId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(stroke3Id, cursor.getString(cursor.getColumnIndexOrThrow("strokeId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(stroke4Id, cursor.getString(cursor.getColumnIndexOrThrow("strokeId")))
                        assertEquals(page2Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))

                        assertTrue(cursor.moveToNext())
                        assertEquals(stroke5Id, cursor.getString(cursor.getColumnIndexOrThrow("strokeId")))
                        assertEquals(page3Id, cursor.getString(cursor.getColumnIndexOrThrow("pageId")))
                    }
                }
        }
    }

    /**
     * Test: Verify v2→v3 migration creates new tables correctly.
     */
    @Test
    fun migration_2_to_3_createsNewTables() {
        runBlocking {
            migrationTestHelper.createDatabase(testDatabaseName, 2).close()

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).use { db ->
                    // Verify folders table exists with correct schema
                    val foldersCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='folders'")
                    foldersCursor.use { cursor ->
                        assertTrue("folders table should exist", cursor.moveToFirst())
                    }

                    // Verify tags table exists
                    val tagsCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='tags'")
                    tagsCursor.use { cursor ->
                        assertTrue("tags table should exist", cursor.moveToFirst())
                    }

                    // Verify NoteTagCrossRef table exists
                    val crossRefCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='NoteTagCrossRef'")
                    crossRefCursor.use { cursor ->
                        assertTrue("NoteTagCrossRef table should exist", cursor.moveToFirst())
                    }

                    // Verify thumbnails table exists
                    val thumbnailsCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='thumbnails'")
                    thumbnailsCursor.use { cursor ->
                        assertTrue("thumbnails table should exist", cursor.moveToFirst())
                    }
                }
        }
    }

    /**
     * Test: Verify v2→v3 migration creates indexes correctly.
     */
    @Test
    fun migration_2_to_3_createsIndexes() {
        runBlocking {
            migrationTestHelper.createDatabase(testDatabaseName, 2).close()

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).use { db ->
                    // Verify index on notes.folderId
                    val notesIndexCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_notes_folderId'")
                    notesIndexCursor.use { cursor ->
                        assertTrue("index_notes_folderId should exist", cursor.moveToFirst())
                    }

                    // Verify index on folders.parentId
                    val foldersIndexCursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_folders_parentId'")
                    foldersIndexCursor.use { cursor ->
                        assertTrue("index_folders_parentId should exist", cursor.moveToFirst())
                    }

                    // Verify indexes on NoteTagCrossRef
                    val noteTagNoteIndexCursor =
                        db.query(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_NoteTagCrossRef_noteId'",
                        )
                    noteTagNoteIndexCursor.use { cursor ->
                        assertTrue("index_NoteTagCrossRef_noteId should exist", cursor.moveToFirst())
                    }

                    val noteTagTagIndexCursor =
                        db.query(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_NoteTagCrossRef_tagId'",
                        )
                    noteTagTagIndexCursor.use { cursor ->
                        assertTrue("index_NoteTagCrossRef_tagId should exist", cursor.moveToFirst())
                    }
                }
        }
    }

    /**
     * Test: Verify protobuf stroke data can be deserialized after migration.
     */
    @Test
    fun migration_2_to_3_preservesProtobufStrokeData() {
        runBlocking {
            val originalPoints =
                listOf(
                    StrokePoint(x = 100f, y = 100f, t = testTimestamp, p = 0.5f),
                    StrokePoint(x = 150f, y = 120f, t = testTimestamp + 10, p = 0.6f),
                    StrokePoint(x = 200f, y = 140f, t = testTimestamp + 20, p = 0.7f),
                )
            val strokeData = StrokeSerializer.serializePoints(originalPoints)

            migrationTestHelper.createDatabase(testDatabaseName, 2).use { db ->
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(stroke1Id, page1Id, strokeData, "{\"color\":\"#000000\"}", "{}", testTimestamp, 0L),
                )
            }

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).use { db ->
                    val cursor = db.query("SELECT strokeData FROM strokes WHERE strokeId = ?", arrayOf(stroke1Id))
                    cursor.use { c ->
                        assertTrue("Stroke should exist", c.moveToFirst())
                        val retrievedData = c.getBlob(0)

                        // Deserialize and verify
                        val deserializedPoints = StrokeSerializer.deserializePoints(retrievedData)
                        assertEquals("All points should be preserved", originalPoints.size, deserializedPoints.size)

                        for (i in originalPoints.indices) {
                            assertEquals(originalPoints[i].x, deserializedPoints[i].x, 0.001f)
                            assertEquals(originalPoints[i].y, deserializedPoints[i].y, 0.001f)
                            assertEquals(originalPoints[i].t, deserializedPoints[i].t)
                            assertEquals(originalPoints[i].p!!, deserializedPoints[i].p!!, 0.001f)
                        }
                    }
                }
        }
    }

    /**
     * Test: Verify folderId column is added to notes table with NULL default.
     */
    @Test
    fun migration_2_to_3_addsFolderIdColumnWithNullDefault() {
        runBlocking {
            migrationTestHelper.createDatabase(testDatabaseName, 2).use { db ->
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(note1Id, "user-001", "Test Note", testTimestamp, testTimestamp, null),
                )
            }

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).use { db ->
                    val cursor = db.query("SELECT folderId FROM notes WHERE noteId = ?", arrayOf(note1Id))
                    cursor.use { c ->
                        assertTrue("Note should exist", c.moveToFirst())
                        assertNull("folderId should be NULL for existing notes", c.getString(0))
                    }
                }
        }
    }

    /**
     * Test: Verify full database works correctly after migration using Room.
     */
    @Test
    fun migration_2_to_3_fullDatabaseIntegration() {
        runBlocking {
            // Create and populate v2 database
            migrationTestHelper.createDatabase(testDatabaseName, 2).use { db ->
                // Insert notes
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(note1Id, "user-001", "Integration Test Note 1", testTimestamp, testTimestamp, null),
                )
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf(note2Id, "user-001", "Integration Test Note 2", testTimestamp + 1000, testTimestamp + 1000, null),
                )

                // Insert pages
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(page1Id, note1Id, "CANVAS", "A4", 0, 595.0, 842.0, "pt", testTimestamp, 0L),
                )
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(page2Id, note2Id, "CANVAS", "A4", 0, 595.0, 842.0, "pt", testTimestamp, 0L),
                )

                // Insert strokes
                val strokeData = createTestStrokeData()
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(stroke1Id, page1Id, strokeData, "{}", "{}", testTimestamp, 0L),
                )
                db.execSQL(
                    "INSERT INTO strokes (strokeId, pageId, strokeData, style, bounds, createdAt, createdLamport) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(stroke2Id, page2Id, strokeData, "{}", "{}", testTimestamp, 0L),
                )
            }

            // Run migration
            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    3,
                    true,
                    OnyxDatabase.MIGRATION_2_3,
                ).close()

            // Open with Room and verify data integrity
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database =
                Room
                    .databaseBuilder(context, OnyxDatabase::class.java, testDatabaseName)
                    .addMigrations(OnyxDatabase.MIGRATION_2_3)
                    .build()

            try {
                // Verify notes
                val noteDao = database.noteDao()
                val notes = noteDao.getAllNotesForTesting()
                assertEquals("All notes should be accessible via Room", 2, notes.size)

                // Verify pages
                val pageDao = database.pageDao()
                val pages1 = pageDao.getPagesForNoteSync(note1Id)
                assertEquals("Note 1 should have 1 page", 1, pages1.size)

                val pages2 = pageDao.getPagesForNoteSync(note2Id)
                assertEquals("Note 2 should have 1 page", 1, pages2.size)

                // Verify strokes
                val strokeDao = database.strokeDao()
                val strokes1 = strokeDao.getByPageId(page1Id)
                assertEquals("Page 1 should have 1 stroke", 1, strokes1.size)

                val strokes2 = strokeDao.getByPageId(page2Id)
                assertEquals("Page 2 should have 1 stroke", 1, strokes2.size)

                // Verify new tables are empty but accessible
                val folders = database.folderDao().getAllFoldersForTesting()
                assertEquals("Folders table should be empty", 0, folders.size)

                val tags = database.tagDao().getAllTagsForTesting()
                assertEquals("Tags table should be empty", 0, tags.size)

                val thumbnails = database.thumbnailDao().getAllThumbnailsForTesting()
                assertEquals("Thumbnails table should be empty", 0, thumbnails.size)
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun migration_3_to_4_adds_template_schema_and_folder_timestamps() {
        runBlocking {
            val folderId = "folder-001"
            val noteId = "note-001"
            val pageId = "page-001"

            migrationTestHelper.createDatabase(testDatabaseName, 3).use { db ->
                db.execSQL(
                    "INSERT INTO folders (folderId, name, parentId, createdAt) VALUES (?, ?, ?, ?)",
                    arrayOf(folderId, "Root", null, testTimestamp),
                )
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt, folderId) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(noteId, "user-001", "Test Note", testTimestamp, testTimestamp, null, folderId),
                )
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, pdfAssetId, pdfPageNo, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(pageId, noteId, "CANVAS", "A4", 0, 595.0, 842.0, "pt", null, null, testTimestamp, 0L),
                )
            }

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    4,
                    true,
                    OnyxDatabase.MIGRATION_3_4,
                ).use { db ->
                    val folderCursor = db.query("SELECT createdAt, updatedAt FROM folders WHERE folderId = ?", arrayOf(folderId))
                    folderCursor.use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(
                            cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")),
                            cursor
                                .getLong(cursor.getColumnIndexOrThrow("updatedAt")),
                        )
                    }

                    val templateTableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='page_templates'")
                    templateTableCursor.use { cursor ->
                        assertTrue("page_templates table should exist", cursor.moveToFirst())
                    }

                    val templateColumnCursor = db.query("PRAGMA table_info(pages)")
                    templateColumnCursor.use { cursor ->
                        var hasTemplateId = false
                        while (cursor.moveToNext()) {
                            if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "templateId") {
                                hasTemplateId = true
                            }
                        }
                        assertTrue("pages.templateId column should exist", hasTemplateId)
                    }
                }
        }
    }

    @Test
    fun migration_3_to_4_enforces_folder_and_template_referential_integrity() {
        runBlocking {
            val folderId = "folder-001"
            val noteId = "note-001"
            val pageId = "page-001"
            val templateId = "template-001"

            migrationTestHelper.createDatabase(testDatabaseName, 3).use { db ->
                db.execSQL(
                    "INSERT INTO folders (folderId, name, parentId, createdAt) VALUES (?, ?, ?, ?)",
                    arrayOf(folderId, "Root", null, testTimestamp),
                )
                db.execSQL(
                    "INSERT INTO notes (noteId, ownerUserId, title, createdAt, updatedAt, deletedAt, folderId) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(noteId, "user-001", "Test Note", testTimestamp, testTimestamp, null, folderId),
                )
                db.execSQL(
                    "INSERT INTO pages (pageId, noteId, kind, geometryKind, indexInNote, width, height, unit, pdfAssetId, pdfPageNo, updatedAt, contentLamportMax) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(pageId, noteId, "CANVAS", "A4", 0, 595.0, 842.0, "pt", null, null, testTimestamp, 0L),
                )
            }

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    4,
                    true,
                    OnyxDatabase.MIGRATION_3_4,
                ).use { db ->
                    db.execSQL(
                        "INSERT INTO page_templates (templateId, name, backgroundKind, spacing, color, isBuiltIn, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(templateId, "Grid", "grid", 24f, "#E0E0E0", 1, testTimestamp),
                    )
                    db.execSQL("UPDATE pages SET templateId = ? WHERE pageId = ?", arrayOf(templateId, pageId))

                    db.execSQL("DELETE FROM folders WHERE folderId = ?", arrayOf(folderId))
                    val noteCursor = db.query("SELECT folderId FROM notes WHERE noteId = ?", arrayOf(noteId))
                    noteCursor.use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertNull("note.folderId should be nulled when folder is deleted", cursor.getString(0))
                    }

                    db.execSQL("DELETE FROM page_templates WHERE templateId = ?", arrayOf(templateId))
                    val pageCursor = db.query("SELECT templateId FROM pages WHERE pageId = ?", arrayOf(pageId))
                    pageCursor.use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertNull("page.templateId should be nulled when template is deleted", cursor.getString(0))
                    }

                    try {
                        db.execSQL("UPDATE notes SET folderId = ? WHERE noteId = ?", arrayOf("missing-folder", noteId))
                        fail("Expected folder integrity trigger to reject invalid folderId")
                    } catch (_: Exception) {
                        // Expected: trigger aborts invalid reference writes
                    }

                    try {
                        db.execSQL("UPDATE pages SET templateId = ? WHERE pageId = ?", arrayOf("missing-template", pageId))
                        fail("Expected template integrity trigger to reject invalid templateId")
                    } catch (_: Exception) {
                        // Expected: trigger aborts invalid reference writes
                    }
                }
        }
    }

    @Test
    fun migration_4_to_5_creates_editor_settings_with_default_row() {
        runBlocking {
            migrationTestHelper.createDatabase(testDatabaseName, 4).close()

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    5,
                    true,
                    com.onyx.android.data.migrations.MIGRATION_4_5,
                ).use { db ->
                    val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='editor_settings'")
                    tableCursor.use { cursor ->
                        assertTrue("editor_settings table should exist", cursor.moveToFirst())
                    }

                    val settingsCursor =
                        db.query(
                            "SELECT settingsId, selectedTool, penColor, highlighterColor FROM editor_settings WHERE settingsId = ?",
                            arrayOf("default"),
                        )
                    settingsCursor.use { cursor ->
                        assertTrue("default editor settings row should exist", cursor.moveToFirst())
                        assertEquals("default", cursor.getString(cursor.getColumnIndexOrThrow("settingsId")))
                        assertEquals("PEN", cursor.getString(cursor.getColumnIndexOrThrow("selectedTool")))
                        assertEquals("#000000", cursor.getString(cursor.getColumnIndexOrThrow("penColor")))
                        assertEquals("#B31E88E5", cursor.getString(cursor.getColumnIndexOrThrow("highlighterColor")))
                    }
                }
        }
    }

    @Test
    fun migration_5_to_6_creates_operation_log_table_and_indexes() {
        runBlocking {
            migrationTestHelper.createDatabase(testDatabaseName, 5).close()

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    6,
                    true,
                    com.onyx.android.data.migrations.MIGRATION_5_6,
                ).use { db ->
                    val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='operation_log'")
                    tableCursor.use { cursor ->
                        assertTrue("operation_log table should exist", cursor.moveToFirst())
                    }

                    val noteIdIndexCursor =
                        db
                            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_operation_log_noteId'")
                    noteIdIndexCursor.use { cursor ->
                        assertTrue("index_operation_log_noteId should exist", cursor.moveToFirst())
                    }

                    val lamportIndexCursor =
                        db
                            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_operation_log_lamportClock'")
                    lamportIndexCursor.use { cursor ->
                        assertTrue("index_operation_log_lamportClock should exist", cursor.moveToFirst())
                    }

                    val noteLamportIndexCursor =
                        db.query(
                            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_operation_log_noteId_lamportClock'",
                        )
                    noteLamportIndexCursor.use { cursor ->
                        assertTrue("index_operation_log_noteId_lamportClock should exist", cursor.moveToFirst())
                    }
                }
        }
    }

    @Test
    fun migration_5_to_6_operation_log_orders_by_lamport_monotonically() {
        runBlocking {
            val noteId = "note-lamport"
            migrationTestHelper.createDatabase(testDatabaseName, 5).close()

            migrationTestHelper
                .runMigrationsAndValidate(
                    testDatabaseName,
                    6,
                    true,
                    com.onyx.android.data.migrations.MIGRATION_5_6,
                ).use { db ->
                    db.execSQL(
                        "INSERT INTO operation_log (opId, noteId, deviceId, lamportClock, operationType, payload, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf("op-3", noteId, "device-a", 3L, "stroke_add", "{}", testTimestamp + 30),
                    )
                    db.execSQL(
                        "INSERT INTO operation_log (opId, noteId, deviceId, lamportClock, operationType, payload, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf("op-1", noteId, "device-a", 1L, "stroke_add", "{}", testTimestamp + 10),
                    )
                    db.execSQL(
                        "INSERT INTO operation_log (opId, noteId, deviceId, lamportClock, operationType, payload, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf("op-2", noteId, "device-a", 2L, "stroke_add", "{}", testTimestamp + 20),
                    )

                    val cursor =
                        db
                            .query("SELECT lamportClock FROM operation_log WHERE noteId = ? ORDER BY lamportClock ASC", arrayOf(noteId))
                    cursor.use {
                        assertTrue(it.moveToFirst())
                        val first = it.getLong(0)
                        assertTrue(it.moveToNext())
                        val second = it.getLong(0)
                        assertTrue(it.moveToNext())
                        val third = it.getLong(0)
                        assertEquals(1L, first)
                        assertEquals(2L, second)
                        assertEquals(3L, third)
                    }
                }
        }
    }
}
