@file:Suppress("ktlint:standard:filename")

package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_PAGE_OBJECTS_TABLE_SQL)
            db.execSQL(CREATE_PAGE_OBJECTS_PAGE_INDEX_SQL)
            db.execSQL(CREATE_PAGE_OBJECTS_NOTE_INDEX_SQL)
            db.execSQL(CREATE_PAGE_OBJECTS_KIND_INDEX_SQL)
            db.execSQL(CREATE_PAGE_OBJECTS_PAGE_Z_INDEX_SQL)
            db.execSQL(CREATE_PAGE_OBJECTS_PAGE_DELETE_TRIGGER_SQL)
        }
    }

private val CREATE_PAGE_OBJECTS_TABLE_SQL =
    """
    CREATE TABLE IF NOT EXISTS page_objects (
        objectId TEXT NOT NULL PRIMARY KEY,
        pageId TEXT NOT NULL,
        noteId TEXT NOT NULL,
        kind TEXT NOT NULL,
        zIndex INTEGER NOT NULL,
        x REAL NOT NULL,
        y REAL NOT NULL,
        width REAL NOT NULL,
        height REAL NOT NULL,
        rotationDeg REAL NOT NULL,
        payloadJson TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        deletedAt INTEGER
    )
    """.trimIndent()

private const val CREATE_PAGE_OBJECTS_PAGE_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_page_objects_pageId ON page_objects(pageId)"

private const val CREATE_PAGE_OBJECTS_NOTE_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_page_objects_noteId ON page_objects(noteId)"

private const val CREATE_PAGE_OBJECTS_KIND_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_page_objects_kind ON page_objects(kind)"

private const val CREATE_PAGE_OBJECTS_PAGE_Z_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_page_objects_pageId_zIndex ON page_objects(pageId, zIndex)"

private val CREATE_PAGE_OBJECTS_PAGE_DELETE_TRIGGER_SQL =
    """
    CREATE TRIGGER IF NOT EXISTS pages_delete_page_objects_cleanup
    AFTER DELETE ON pages
    FOR EACH ROW
    BEGIN
        DELETE FROM page_objects WHERE pageId = OLD.pageId;
    END
    """.trimIndent()
