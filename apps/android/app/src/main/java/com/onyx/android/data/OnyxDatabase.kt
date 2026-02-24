package com.onyx.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onyx.android.data.dao.EditorSettingsDao
import com.onyx.android.data.dao.FolderDao
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.OperationLogDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.PageObjectDao
import com.onyx.android.data.dao.PageTemplateDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.dao.TagDao
import com.onyx.android.data.dao.ThumbnailDao
import com.onyx.android.data.entity.EditorSettingsEntity
import com.onyx.android.data.entity.FolderEntity
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.NoteTagCrossRef
import com.onyx.android.data.entity.OperationLogEntity
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.PageObjectEntity
import com.onyx.android.data.entity.PageTemplateEntity
import com.onyx.android.data.entity.RecognitionFtsEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.entity.TagEntity
import com.onyx.android.data.entity.ThumbnailEntity
import com.onyx.android.data.migrations.MIGRATION_10_11
import com.onyx.android.data.migrations.MIGRATION_11_12
import com.onyx.android.data.migrations.MIGRATION_12_13
import com.onyx.android.data.migrations.MIGRATION_13_14
import com.onyx.android.data.migrations.MIGRATION_4_5
import com.onyx.android.data.migrations.MIGRATION_5_6
import com.onyx.android.data.migrations.MIGRATION_6_7
import com.onyx.android.data.migrations.MIGRATION_7_8
import com.onyx.android.data.migrations.MIGRATION_8_9
import com.onyx.android.data.migrations.MIGRATION_9_10

@Database(
    entities = [
        NoteEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        RecognitionIndexEntity::class,
        RecognitionFtsEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        ThumbnailEntity::class,
        PageTemplateEntity::class,
        EditorSettingsEntity::class,
        OperationLogEntity::class,
        PageObjectEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@Suppress("TooManyFunctions")
abstract class OnyxDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun pageDao(): PageDao

    abstract fun strokeDao(): StrokeDao

    abstract fun recognitionDao(): RecognitionDao

    abstract fun folderDao(): FolderDao

    abstract fun tagDao(): TagDao

    abstract fun thumbnailDao(): ThumbnailDao

    abstract fun pageTemplateDao(): PageTemplateDao

    abstract fun editorSettingsDao(): EditorSettingsDao

    abstract fun operationLogDao(): OperationLogDao

    abstract fun pageObjectDao(): PageObjectDao

    companion object {
        const val DATABASE_NAME = "onyx_notes.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // No schema change in v2. We switched stroke payload encoding to protobuf.
                    // Existing dev data compatibility is intentionally not guaranteed in this phase.
                }
            }

        @Suppress("MagicNumber")
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Add folderId column to notes table
                    db.execSQL("ALTER TABLE notes ADD COLUMN folderId TEXT")

                    // Create folders table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS folders (
                            folderId TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            parentId TEXT,
                            createdAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    // Create tags table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS tags (
                            tagId TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            color TEXT NOT NULL,
                            createdAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    // Create note-tag cross reference table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS NoteTagCrossRef (
                            noteId TEXT NOT NULL,
                            tagId TEXT NOT NULL,
                            PRIMARY KEY(noteId, tagId)
                        )
                        """.trimIndent(),
                    )

                    // Create thumbnails table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS thumbnails (
                            noteId TEXT NOT NULL PRIMARY KEY,
                            filePath TEXT NOT NULL,
                            contentHash TEXT NOT NULL,
                            generatedAt INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )

                    // Create indexes for foreign key relationships
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_folderId ON notes(folderId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders(parentId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_NoteTagCrossRef_noteId ON NoteTagCrossRef(noteId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_NoteTagCrossRef_tagId ON NoteTagCrossRef(tagId)")
                }
            }

        @Suppress("MagicNumber")
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    addFolderAndTemplateColumns(db)
                    addTemplateTableAndIndexes(db)
                    normalizeReferences(db)
                    addFolderIntegrityTriggers(db)
                    addTemplateIntegrityTriggers(db)
                }
            }

        private fun addFolderAndTemplateColumns(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE folders SET updatedAt = createdAt WHERE updatedAt = 0")
            db.execSQL("ALTER TABLE pages ADD COLUMN templateId TEXT")
        }

        private fun addTemplateTableAndIndexes(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS page_templates (
                    templateId TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    backgroundKind TEXT NOT NULL,
                    spacing REAL,
                    color TEXT,
                    isBuiltIn INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_noteId ON pages(noteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_templateId ON pages(templateId)")
        }

        private fun normalizeReferences(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE folders SET parentId = NULL WHERE parentId = folderId")
            db.execSQL(
                """
                UPDATE notes
                SET folderId = NULL
                WHERE folderId IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM folders WHERE folders.folderId = notes.folderId
                  )
                """.trimIndent(),
            )
        }

        private fun addFolderIntegrityTriggers(db: SupportSQLiteDatabase) {
            addFolderParentTriggers(db)
            addNoteFolderTriggers(db)
            addFolderDeleteCleanupTrigger(db)
        }

        private fun addFolderParentTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS folders_parent_insert_check
                BEFORE INSERT ON folders
                FOR EACH ROW
                WHEN NEW.parentId IS NOT NULL
                     AND (
                         NEW.parentId = NEW.folderId
                         OR NOT EXISTS (
                             SELECT 1 FROM folders WHERE folderId = NEW.parentId
                         )
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'folders.parentId must reference an existing folder');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS folders_parent_update_check
                BEFORE UPDATE OF parentId ON folders
                FOR EACH ROW
                WHEN NEW.parentId IS NOT NULL
                     AND (
                         NEW.parentId = NEW.folderId
                         OR NOT EXISTS (
                             SELECT 1 FROM folders WHERE folderId = NEW.parentId
                         )
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'folders.parentId must reference an existing folder');
                END
                """.trimIndent(),
            )
        }

        private fun addNoteFolderTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS notes_folder_insert_check
                BEFORE INSERT ON notes
                FOR EACH ROW
                WHEN NEW.folderId IS NOT NULL
                     AND NOT EXISTS (
                         SELECT 1 FROM folders WHERE folderId = NEW.folderId
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'notes.folderId must reference an existing folder');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS notes_folder_update_check
                BEFORE UPDATE OF folderId ON notes
                FOR EACH ROW
                WHEN NEW.folderId IS NOT NULL
                     AND NOT EXISTS (
                         SELECT 1 FROM folders WHERE folderId = NEW.folderId
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'notes.folderId must reference an existing folder');
                END
                """.trimIndent(),
            )
        }

        private fun addFolderDeleteCleanupTrigger(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS folders_delete_cleanup
                AFTER DELETE ON folders
                FOR EACH ROW
                BEGIN
                    UPDATE notes SET folderId = NULL WHERE folderId = OLD.folderId;
                    UPDATE folders SET parentId = NULL WHERE parentId = OLD.folderId;
                END
                """.trimIndent(),
            )
        }

        private fun addTemplateIntegrityTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS pages_template_insert_check
                BEFORE INSERT ON pages
                FOR EACH ROW
                WHEN NEW.templateId IS NOT NULL
                     AND NOT EXISTS (
                         SELECT 1 FROM page_templates WHERE templateId = NEW.templateId
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'pages.templateId must reference an existing template');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS pages_template_update_check
                BEFORE UPDATE OF templateId ON pages
                FOR EACH ROW
                WHEN NEW.templateId IS NOT NULL
                     AND NOT EXISTS (
                         SELECT 1 FROM page_templates WHERE templateId = NEW.templateId
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'pages.templateId must reference an existing template');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS page_templates_delete_cleanup
                AFTER DELETE ON page_templates
                FOR EACH ROW
                BEGIN
                    UPDATE pages SET templateId = NULL WHERE templateId = OLD.templateId;
                END
                """.trimIndent(),
            )
        }

        fun build(context: Context): OnyxDatabase =
            Room
                .databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                ).build()
    }
}
