package com.pereviader.backgroundswitcher.domain.repository

import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.BoundaryTransition
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface ScheduleRepository {
    fun observeRulesByScope(scope: ScheduleScreenScope): Flow<List<ScheduleRule>>
    fun observeAllRules(): Flow<List<ScheduleRule>>
    suspend fun getRulesByScope(scope: ScheduleScreenScope): List<ScheduleRule>
    suspend fun saveRule(rule: ScheduleRule)
    suspend fun deleteRule(ruleId: String)
    suspend fun toggleRuleEnabled(ruleId: String, isEnabled: Boolean)
    suspend fun moveRulePriority(scope: ScheduleScreenScope, ruleId: String, moveUp: Boolean)
    suspend fun reorderRules(scope: ScheduleScreenScope, orderedRuleIds: List<String>)
    suspend fun copyScheduleScope(fromScope: ScheduleScreenScope, toScope: ScheduleScreenScope)
    suspend fun initializeSeparateSchedulesFromSharedIfNeeded()
    suspend fun resolveActiveList(
        scope: ScheduleScreenScope,
        dateTime: LocalDateTime = LocalDateTime.now()
    ): ActiveScheduleResolution
    suspend fun findNextBoundaryTransition(
        fromDateTime: LocalDateTime = LocalDateTime.now()
    ): BoundaryTransition?
}
