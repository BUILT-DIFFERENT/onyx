package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 11
private const val MIGRATION_TO_VERSION = 12

val MIGRATION_11_12 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN latencyOptimizationMode TEXT NOT NULL DEFAULT 'NORMAL'
                """.trimIndent(),
            )
        }
    }
