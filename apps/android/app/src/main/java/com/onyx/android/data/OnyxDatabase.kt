package com.onyx.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onyx.android.data.dao.FolderDao
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.dao.TagDao
import com.onyx.android.data.dao.ThumbnailDao
import com.onyx.android.data.entity.FolderEntity
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.NoteTagCrossRef
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.RecognitionFtsEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity
import com.onyx.android.data.entity.TagEntity
import com.onyx.android.data.entity.ThumbnailEntity

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
    ],
    version = 3,
    exportSchema = true,
)
abstract class OnyxDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun pageDao(): PageDao

    abstract fun strokeDao(): StrokeDao

    abstract fun recognitionDao(): RecognitionDao

    abstract fun folderDao(): FolderDao

    abstract fun tagDao(): TagDao

    abstract fun thumbnailDao(): ThumbnailDao

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

        fun build(context: Context): OnyxDatabase =
            Room
                .databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
