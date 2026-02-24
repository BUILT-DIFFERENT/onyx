package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 13
private const val MIGRATION_TO_VERSION = 14

val MIGRATION_13_14 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN penLineStyle TEXT NOT NULL DEFAULT 'SOLID'
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN highlighterLineStyle TEXT NOT NULL DEFAULT 'SOLID'
                """.trimIndent(),
            )
        }
    }
