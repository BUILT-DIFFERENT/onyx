package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 10
private const val MIGRATION_TO_VERSION = 11

val MIGRATION_10_11 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE editor_settings
                ADD COLUMN doubleTapZoomPointerMode TEXT NOT NULL DEFAULT 'FINGER_ONLY'
                """.trimIndent(),
            )
        }
    }
