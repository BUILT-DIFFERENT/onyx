@file:Suppress("ktlint:standard:filename")

package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE editor_settings ADD COLUMN singleFingerMode TEXT NOT NULL DEFAULT 'PAN'")
            db.execSQL("ALTER TABLE editor_settings ADD COLUMN doubleFingerMode TEXT NOT NULL DEFAULT 'ZOOM_PAN'")
            db.execSQL("ALTER TABLE editor_settings ADD COLUMN stylusPrimaryAction TEXT NOT NULL DEFAULT 'ERASER_HOLD'")
            db.execSQL(
                "ALTER TABLE editor_settings ADD COLUMN stylusSecondaryAction TEXT NOT NULL DEFAULT 'ERASER_HOLD'",
            )
            db.execSQL("ALTER TABLE editor_settings ADD COLUMN stylusLongHoldAction TEXT NOT NULL DEFAULT 'NO_ACTION'")
        }
    }
