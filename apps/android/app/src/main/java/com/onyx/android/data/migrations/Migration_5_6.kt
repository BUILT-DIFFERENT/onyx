@file:Suppress("ktlint:standard:filename")

package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_OPERATION_LOG_TABLE_SQL)
            db.execSQL(CREATE_OPERATION_LOG_NOTE_ID_INDEX_SQL)
            db.execSQL(CREATE_OPERATION_LOG_LAMPORT_INDEX_SQL)
            db.execSQL(CREATE_OPERATION_LOG_NOTE_LAMPORT_INDEX_SQL)
        }
    }

private val CREATE_OPERATION_LOG_TABLE_SQL =
    """
    CREATE TABLE IF NOT EXISTS operation_log (
        opId TEXT NOT NULL PRIMARY KEY,
        noteId TEXT NOT NULL,
        deviceId TEXT NOT NULL,
        lamportClock INTEGER NOT NULL,
        operationType TEXT NOT NULL,
        payload TEXT NOT NULL,
        createdAt INTEGER NOT NULL
    )
    """.trimIndent()

private const val CREATE_OPERATION_LOG_NOTE_ID_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_operation_log_noteId ON operation_log(noteId)"

private const val CREATE_OPERATION_LOG_LAMPORT_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_operation_log_lamportClock ON operation_log(lamportClock)"

private const val CREATE_OPERATION_LOG_NOTE_LAMPORT_INDEX_SQL =
    "CREATE INDEX IF NOT EXISTS index_operation_log_noteId_lamportClock ON operation_log(noteId, lamportClock)"
