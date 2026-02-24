package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MIGRATION_FROM_VERSION = 17
private const val MIGRATION_TO_VERSION = 18

val MIGRATION_17_18 =
    object : Migration(MIGRATION_FROM_VERSION, MIGRATION_TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE page_templates
                ADD COLUMN lineWidth REAL
                """.trimIndent(),
            )
        }
    }
