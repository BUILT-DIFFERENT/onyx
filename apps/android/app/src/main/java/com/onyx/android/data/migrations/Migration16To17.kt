package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 16
private const val MIGRATION_TO_VERSION = 17

val MIGRATION_16_17 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN keepScreenOnInEditor INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN hideSystemBarsInEditor INTEGER NOT NULL DEFAULT 1
                """.trimIndent(),
            )
        }
    }
