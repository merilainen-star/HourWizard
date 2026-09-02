package com.numbawang.leimaus.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlocked_achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long
)
