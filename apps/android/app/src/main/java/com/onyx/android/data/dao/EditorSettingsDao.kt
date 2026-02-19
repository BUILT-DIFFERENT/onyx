package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.EditorSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EditorSettingsDao {
    @Query("SELECT * FROM editor_settings WHERE settingsId = 'default' LIMIT 1")
    fun getSettings(): Flow<EditorSettingsEntity?>

    @Query("SELECT * FROM editor_settings WHERE settingsId = 'default' LIMIT 1")
    suspend fun getSettingsOnce(): EditorSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: EditorSettingsEntity)
}
