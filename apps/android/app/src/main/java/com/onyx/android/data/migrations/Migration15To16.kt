package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val FROM_VERSION = 15
private const val TO_VERSION = 16

val MIGRATION_15_16 =
    object : Migration(FROM_VERSION, TO_VERSION) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN lockUpdatedAt INTEGER")
        }
    }
