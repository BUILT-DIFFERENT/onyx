@file:Suppress("ktlint:standard:filename")

package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE editor_settings ADD COLUMN doubleTapZoomAction TEXT NOT NULL DEFAULT 'NONE'",
            )
        }
    }
