package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 14
private const val MIGRATION_TO_VERSION = 15

val MIGRATION_14_15 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE notes
                ADD COLUMN lastOpenedPageId TEXT
                """.trimIndent(),
            )
        }
    }
