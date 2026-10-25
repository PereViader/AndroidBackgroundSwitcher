package com.pereviader.backgroundswitcher.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pereviader.backgroundswitcher.data.local.db.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_rules WHERE screenScope = :screenScope ORDER BY priority ASC")
    fun observeRulesByScope(screenScope: String): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules ORDER BY screenScope ASC, priority ASC")
    fun observeAllRules(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE screenScope = :screenScope ORDER BY priority ASC")
    suspend fun getRulesByScope(screenScope: String): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules ORDER BY screenScope ASC, priority ASC")
    suspend fun getAllRules(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): ScheduleRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRule(rule: ScheduleRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRules(rules: List<ScheduleRuleEntity>)

    @Query("DELETE FROM schedule_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("DELETE FROM schedule_rules WHERE screenScope = :screenScope")
    suspend fun deleteRulesByScope(screenScope: String)

    @Transaction
    suspend fun replaceRulesForScope(screenScope: String, rules: List<ScheduleRuleEntity>) {
        deleteRulesByScope(screenScope)
        if (rules.isNotEmpty()) {
            insertOrUpdateRules(rules)
        }
    }
}
