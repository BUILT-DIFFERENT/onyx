package com.onyx.android.data

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OnyxDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun pageDao(): PageDao

    abstract fun strokeDao(): StrokeDao

    abstract fun recognitionDao(): RecognitionDao

    companion object {
        const val DATABASE_NAME = "onyx_notes.db"

        fun build(context: Context): OnyxDatabase =
            Room
                .databaseBuilder(context, OnyxDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}

class Converters {
    @TypeConverter
    fun byteArrayToString(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.DEFAULT)

    @TypeConverter
    fun stringToByteArray(str: String): ByteArray = Base64.decode(str, Base64.DEFAULT)
}
