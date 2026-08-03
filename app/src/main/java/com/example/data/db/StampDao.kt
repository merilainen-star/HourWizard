package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Query("SELECT * FROM stamp_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<StampEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StampEntity): Long

    @Query("DELETE FROM stamp_logs")
    suspend fun clearAllLogs()
}
