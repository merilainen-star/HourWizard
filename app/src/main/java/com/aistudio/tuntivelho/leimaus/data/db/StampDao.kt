package com.aistudio.tuntivelho.leimaus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Query("SELECT * FROM stamp_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamp_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsList(): List<StampEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StampEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<StampEntity>)

    @Query("DELETE FROM stamp_logs")
    suspend fun clearAllLogs()
}
