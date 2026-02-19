@file:Suppress("ktlint:standard:filename")

package com.onyx.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_EDITOR_SETTINGS_TABLE_SQL)
            db.execSQL(INSERT_DEFAULT_EDITOR_SETTINGS_SQL)
        }
    }

private val CREATE_EDITOR_SETTINGS_TABLE_SQL =
    """
    CREATE TABLE IF NOT EXISTS editor_settings (
        settingsId TEXT NOT NULL PRIMARY KEY,
        selectedTool TEXT NOT NULL,
        penTool TEXT NOT NULL,
        penColor TEXT NOT NULL,
        penBaseWidth REAL NOT NULL,
        penMinWidthFactor REAL NOT NULL,
        penMaxWidthFactor REAL NOT NULL,
        penSmoothingLevel REAL NOT NULL,
        penEndTaperStrength REAL NOT NULL,
        highlighterTool TEXT NOT NULL,
        highlighterColor TEXT NOT NULL,
        highlighterBaseWidth REAL NOT NULL,
        highlighterMinWidthFactor REAL NOT NULL,
        highlighterMaxWidthFactor REAL NOT NULL,
        highlighterSmoothingLevel REAL NOT NULL,
        highlighterEndTaperStrength REAL NOT NULL,
        lastNonEraserTool TEXT NOT NULL,
        updatedAt INTEGER NOT NULL
    )
    """.trimIndent()

private val INSERT_DEFAULT_EDITOR_SETTINGS_SQL =
    """
    INSERT OR IGNORE INTO editor_settings (
        settingsId,
        selectedTool,
        penTool,
        penColor,
        penBaseWidth,
        penMinWidthFactor,
        penMaxWidthFactor,
        penSmoothingLevel,
        penEndTaperStrength,
        highlighterTool,
        highlighterColor,
        highlighterBaseWidth,
        highlighterMinWidthFactor,
        highlighterMaxWidthFactor,
        highlighterSmoothingLevel,
        highlighterEndTaperStrength,
        lastNonEraserTool,
        updatedAt
    ) VALUES (
        'default',
        'PEN',
        'PEN',
        '#000000',
        2.0,
        0.85,
        1.15,
        0.35,
        0.35,
        'HIGHLIGHTER',
        '#B31E88E5',
        6.5,
        0.85,
        1.15,
        0.35,
        0.35,
        'PEN',
        0
    )
    """.trimIndent()
