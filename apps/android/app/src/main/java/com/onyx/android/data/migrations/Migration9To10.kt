package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 9
private const val MIGRATION_TO_VERSION = 10

val MIGRATION_9_10 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN twoFingerTapAction TEXT NOT NULL DEFAULT 'UNDO'
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN threeFingerTapAction TEXT NOT NULL DEFAULT 'REDO'
                """.trimIndent(),
            )
        }
    }
