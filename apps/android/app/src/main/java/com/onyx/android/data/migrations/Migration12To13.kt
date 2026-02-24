package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 12
private const val MIGRATION_TO_VERSION = 13

val MIGRATION_12_13 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN eraserBaseWidth REAL NOT NULL DEFAULT 12.0
                """.trimIndent(),
            )
        }
    }
