package com.numbawang.leimaus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Query("SELECT * FROM unlocked_achievements ORDER BY unlockedAt DESC")
    fun getAll(): Flow<List<AchievementEntity>>

    /** onConflict = IGNORE so an already-unlocked achievement keeps its original unlock date. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AchievementEntity)

    @Query("SELECT id FROM unlocked_achievements")
    suspend fun getAllUnlockedIds(): List<String>
}
