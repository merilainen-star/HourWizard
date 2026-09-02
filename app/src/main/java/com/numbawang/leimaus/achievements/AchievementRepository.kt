package com.numbawang.leimaus.achievements

import com.numbawang.leimaus.data.db.AchievementDao
import com.numbawang.leimaus.data.db.AchievementEntity
import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.AppSettings
import kotlinx.coroutines.flow.Flow

class AchievementRepository(private val dao: AchievementDao) {

    val unlocked: Flow<List<AchievementEntity>> = dao.getAll()

    /**
     * Evaluates [logs] against [settings], persists any achievement that newly qualifies, and
     * returns just those (empty when nothing new - including the common case where everything
     * that qualifies was already unlocked before).
     */
    suspend fun checkForNewUnlocks(logs: List<StampEntity>, settings: AppSettings): List<Achievement> {
        val qualifying = AchievementEvaluator.evaluate(logs, settings)
        val alreadyUnlocked = dao.getAllUnlockedIds().toSet()
        val newIds = qualifying - alreadyUnlocked
        if (newIds.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        newIds.forEach { id -> dao.insert(AchievementEntity(id = id, unlockedAt = now)) }
        return newIds.mapNotNull { Achievements.byId[it] }
    }
}
