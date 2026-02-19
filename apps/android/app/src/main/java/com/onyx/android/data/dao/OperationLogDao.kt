package com.onyx.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onyx.android.data.entity.OperationLogEntity

@Dao
interface OperationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OperationLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<OperationLogEntity>)

    @Query("SELECT * FROM operation_log WHERE noteId = :noteId ORDER BY lamportClock ASC, createdAt ASC")
    suspend fun getOperationsForNote(noteId: String): List<OperationLogEntity>

    @Query(
        "SELECT * FROM operation_log WHERE noteId = :noteId AND lamportClock > :afterLamportClock " +
            "ORDER BY lamportClock ASC, createdAt ASC",
    )
    suspend fun getOperationsForNoteAfterLamport(
        noteId: String,
        afterLamportClock: Long,
    ): List<OperationLogEntity>

    @Query("SELECT MAX(lamportClock) FROM operation_log WHERE deviceId = :deviceId")
    suspend fun getMaxLamportClockForDevice(deviceId: String): Long?

    @Query("DELETE FROM operation_log WHERE opId = :opId")
    suspend fun deleteByOpId(opId: String): Int

    @Query("DELETE FROM operation_log WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String): Int

    @Query("DELETE FROM operation_log")
    suspend fun deleteAll(): Int
}
