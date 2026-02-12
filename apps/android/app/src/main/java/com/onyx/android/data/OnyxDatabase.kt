package com.onyx.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.onyx.android.data.dao.NoteDao
import com.onyx.android.data.dao.PageDao
import com.onyx.android.data.dao.RecognitionDao
import com.onyx.android.data.dao.StrokeDao
import com.onyx.android.data.entity.NoteEntity
import com.onyx.android.data.entity.PageEntity
import com.onyx.android.data.entity.RecognitionFtsEntity
import com.onyx.android.data.entity.RecognitionIndexEntity
import com.onyx.android.data.entity.StrokeEntity

@Database(
    entities = [
        NoteEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        RecognitionIndexEntity::class,
        RecognitionFtsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OnyxDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun pageDao(): PageDao

    abstract fun strokeDao(): StrokeDao

    abstract fun recognitionDao(): RecognitionDao

    companion object {
        const val DATABASE_NAME = "onyx_notes.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // No schema change in v2. We switched stroke payload encoding to protobuf.
                    // Existing dev data compatibility is intentionally not guaranteed in this phase.
                }
            }

        fun build(context: Context): OnyxDatabase =
            Room
                .databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
