package com.pereviader.backgroundswitcher.data.repository

import com.pereviader.backgroundswitcher.core.model.ActiveScheduleResolution
import com.pereviader.backgroundswitcher.core.model.BoundaryTransition
import com.pereviader.backgroundswitcher.core.model.ScheduleRule
import com.pereviader.backgroundswitcher.core.model.ScheduleScreenScope
import com.pereviader.backgroundswitcher.core.model.WallpaperList
import com.pereviader.backgroundswitcher.data.local.datastore.PreferencesManager
import com.pereviader.backgroundswitcher.data.local.db.dao.ScheduleDao
import com.pereviader.backgroundswitcher.data.local.db.dao.WallpaperListDao
import com.pereviader.backgroundswitcher.data.local.db.entity.ScheduleRuleEntity
import com.pereviader.backgroundswitcher.domain.repository.ScheduleRepository
import com.pereviader.backgroundswitcher.domain.scheduler.ScheduleEvaluator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val wallpaperListDao: WallpaperListDao,
    private val preferencesManager: PreferencesManager,
    private val scheduleEvaluator: ScheduleEvaluator
) : ScheduleRepository {

    override fun observeRulesByScope(scope: ScheduleScreenScope): Flow<List<ScheduleRule>> {
        return combine(
            scheduleDao.observeRulesByScope(scope.name),
            wallpaperListDao.observeAllLists(),
            preferencesManager.settingsFlow
        ) { ruleEntities, listEntities, settings ->
            val listNames = listEntities.associate { it.id to it.name }
            val fallbackId = when (scope) {
                ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
                ScheduleScreenScope.HOME -> settings.fallbackListIdHome
                ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
            }
            val domainRules = ruleEntities.map { it.toDomain(listNames[it.listId] ?: WallpaperList.DEFAULT_LIST_NAME) }
            ensureDefaultRuleInList(scope, domainRules, fallbackId, listNames[fallbackId] ?: WallpaperList.DEFAULT_LIST_NAME)
        }
    }

    override fun observeAllRules(): Flow<List<ScheduleRule>> {
        return combine(
            scheduleDao.observeAllRules(),
            wallpaperListDao.observeAllLists(),
            preferencesManager.settingsFlow
        ) { ruleEntities, listEntities, settings ->
            val listNames = listEntities.associate { it.id to it.name }
            val byScope = ruleEntities.groupBy { runCatching { ScheduleScreenScope.valueOf(it.screenScope) }.getOrDefault(ScheduleScreenScope.SHARED) }
            listOf(ScheduleScreenScope.SHARED, ScheduleScreenScope.HOME, ScheduleScreenScope.LOCK).flatMap { scope ->
                val fallbackId = when (scope) {
                    ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
                    ScheduleScreenScope.HOME -> settings.fallbackListIdHome
                    ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
                }
                val domainRules = (byScope[scope] ?: emptyList()).map { it.toDomain(listNames[it.listId] ?: WallpaperList.DEFAULT_LIST_NAME) }
                ensureDefaultRuleInList(scope, domainRules, fallbackId, listNames[fallbackId] ?: WallpaperList.DEFAULT_LIST_NAME)
            }
        }
    }

    override suspend fun getRulesByScope(scope: ScheduleScreenScope): List<ScheduleRule> {
        val listNames = wallpaperListDao.getAllLists().associate { it.id to it.name }
        val settings = preferencesManager.settingsFlow.first()
        val fallbackId = when (scope) {
            ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
            ScheduleScreenScope.HOME -> settings.fallbackListIdHome
            ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
        }
        val domainRules = scheduleDao.getRulesByScope(scope.name)
            .map { it.toDomain(listNames[it.listId] ?: WallpaperList.DEFAULT_LIST_NAME) }
        return ensureDefaultRuleInList(scope, domainRules, fallbackId, listNames[fallbackId] ?: WallpaperList.DEFAULT_LIST_NAME)
    }

    override suspend fun saveRule(rule: ScheduleRule) {
        if (rule.isDefaultRule || rule.id.startsWith("default_rule_")) {
            preferencesManager.updateFallbackListId(rule.screenScope, rule.listId)
            val updated = rule.copy(
                isDefaultRule = true,
                priority = 9999,
                startMinuteOfDay = 0,
                endMinuteOfDay = 0,
                daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
                isEnabled = true
            )
            scheduleDao.insertOrUpdateRule(updated.toEntity())
            normalizePriorities(rule.screenScope)
            return
        }

        val existingRules = scheduleDao.getRulesByScope(rule.screenScope.name)
            .filterNot { it.isDefaultRule || it.id.startsWith("default_rule_") }
        val existing = existingRules.find { it.id == rule.id }
        val priority = existing?.priority ?: ((existingRules.maxOfOrNull { it.priority } ?: -1) + 1)
        scheduleDao.insertOrUpdateRule(rule.copy(priority = priority).toEntity())
        normalizePriorities(rule.screenScope)
    }

    override suspend fun deleteRule(ruleId: String) {
        if (ruleId.startsWith("default_rule_")) return
        val existing = scheduleDao.getRuleById(ruleId) ?: return
        if (existing.isDefaultRule) return

        val scope = runCatching { ScheduleScreenScope.valueOf(existing.screenScope) }
            .getOrDefault(ScheduleScreenScope.SHARED)
        scheduleDao.deleteRuleById(ruleId)
        normalizePriorities(scope)
    }

    override suspend fun toggleRuleEnabled(ruleId: String, isEnabled: Boolean) {
        if (ruleId.startsWith("default_rule_")) return
        val existing = scheduleDao.getRuleById(ruleId) ?: return
        if (existing.isDefaultRule) return

        scheduleDao.insertOrUpdateRule(existing.copy(isEnabled = isEnabled))
    }

    override suspend fun moveRulePriority(scope: ScheduleScreenScope, ruleId: String, moveUp: Boolean) {
        if (ruleId.startsWith("default_rule_")) return
        val rules = scheduleDao.getRulesByScope(scope.name)
            .filterNot { it.isDefaultRule || it.id.startsWith("default_rule_") }
            .sortedBy { it.priority }
            .toMutableList()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index < 0) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in rules.indices) return

        val item = rules.removeAt(index)
        rules.add(targetIndex, item)
        val reindexed = rules.mapIndexed { idx, entity -> entity.copy(priority = idx) }
        scheduleDao.replaceRulesForScope(scope.name, reindexed)
        normalizePriorities(scope)
    }

    override suspend fun reorderRules(scope: ScheduleScreenScope, orderedRuleIds: List<String>) {
        val existingRules = scheduleDao.getRulesByScope(scope.name)
        val customRules = existingRules.filterNot { it.isDefaultRule || it.id.startsWith("default_rule_") }
        val existingById = customRules.associateBy { it.id }
        val ordered = orderedRuleIds.mapNotNull { existingById[it] }
        val remaining = existingById.values.filter { it.id !in orderedRuleIds }.sortedBy { it.priority }
        val reindexed = (ordered + remaining).mapIndexed { idx, entity -> entity.copy(priority = idx) }
        scheduleDao.replaceRulesForScope(scope.name, reindexed)
        normalizePriorities(scope)
    }

    override suspend fun copyScheduleScope(fromScope: ScheduleScreenScope, toScope: ScheduleScreenScope) {
        if (fromScope == toScope) return
        val sourceRules = scheduleDao.getRulesByScope(fromScope.name).sortedBy { it.priority }
        val copiedRules = sourceRules.mapIndexed { index, rule ->
            rule.copy(
                id = "rule_${UUID.randomUUID()}",
                screenScope = toScope.name,
                priority = index
            )
        }
        scheduleDao.replaceRulesForScope(toScope.name, copiedRules)

        val settings = preferencesManager.settingsFlow.first()
        val sourceFallback = when (fromScope) {
            ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
            ScheduleScreenScope.HOME -> settings.fallbackListIdHome
            ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
        }
        preferencesManager.updateFallbackListId(toScope, sourceFallback)
    }

    override suspend fun initializeSeparateSchedulesFromSharedIfNeeded() {
        val homeRules = scheduleDao.getRulesByScope(ScheduleScreenScope.HOME.name)
        val lockRules = scheduleDao.getRulesByScope(ScheduleScreenScope.LOCK.name)
        val sharedRules = scheduleDao.getRulesByScope(ScheduleScreenScope.SHARED.name)
        if (homeRules.isEmpty() && lockRules.isEmpty() && sharedRules.isNotEmpty()) {
            copyScheduleScope(ScheduleScreenScope.SHARED, ScheduleScreenScope.HOME)
            copyScheduleScope(ScheduleScreenScope.SHARED, ScheduleScreenScope.LOCK)
        }
    }

    override suspend fun resolveActiveList(
        scope: ScheduleScreenScope,
        dateTime: LocalDateTime
    ): ActiveScheduleResolution {
        val settings = preferencesManager.settingsFlow.first()
        val effectiveScope = if (!settings.isSeparateScreensMode) {
            ScheduleScreenScope.SHARED
        } else {
            scope
        }
        val fallbackListId = when (effectiveScope) {
            ScheduleScreenScope.SHARED -> settings.fallbackListIdShared
            ScheduleScreenScope.HOME -> settings.fallbackListIdHome
            ScheduleScreenScope.LOCK -> settings.fallbackListIdLock
        }
        val rules = getRulesByScope(effectiveScope)
        val nonEmptyListIds = wallpaperListDao.getNonEmptyListIds().toSet()
        return scheduleEvaluator.evaluateActiveList(
            rules = rules,
            screenScope = effectiveScope,
            fallbackListId = fallbackListId,
            dateTime = dateTime,
            nonEmptyListIds = nonEmptyListIds.ifEmpty { null }
        )
    }

    override suspend fun findNextBoundaryTransition(fromDateTime: LocalDateTime): BoundaryTransition? {
        val settings = preferencesManager.settingsFlow.first()
        val scopes = if (settings.isSeparateScreensMode) {
            listOf(ScheduleScreenScope.HOME, ScheduleScreenScope.LOCK)
        } else {
            listOf(ScheduleScreenScope.SHARED)
        }
        val fallbackByScope = mapOf(
            ScheduleScreenScope.SHARED to settings.fallbackListIdShared,
            ScheduleScreenScope.HOME to settings.fallbackListIdHome,
            ScheduleScreenScope.LOCK to settings.fallbackListIdLock
        )
        val allRules = observeAllRules().first()
        val nonEmptyListIds = wallpaperListDao.getNonEmptyListIds().toSet()
        return scheduleEvaluator.findNextBoundaryTransition(
            rules = allRules,
            scopes = scopes,
            fallbackByScope = fallbackByScope,
            fromDateTime = fromDateTime,
            nonEmptyListIds = nonEmptyListIds.ifEmpty { null }
        )
    }

    private fun ensureDefaultRuleInList(
        scope: ScheduleScreenScope,
        rules: List<ScheduleRule>,
        fallbackListId: String,
        fallbackListName: String
    ): List<ScheduleRule> {
        val defaultRuleId = ScheduleRule.defaultRuleId(scope)
        val existingDefault = rules.find { it.isDefaultRule || it.id == defaultRuleId }
        val customRules = rules
            .filterNot { it.isDefaultRule || it.id == defaultRuleId }
            .sortedBy { it.priority }

        val defaultRule = existingDefault?.copy(
            isDefaultRule = true,
            priority = customRules.size,
            startMinuteOfDay = 0,
            endMinuteOfDay = 0,
            daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
            isEnabled = true
        ) ?: ScheduleRule(
            id = defaultRuleId,
            listId = fallbackListId,
            listName = fallbackListName,
            screenScope = scope,
            priority = customRules.size,
            startMinuteOfDay = 0,
            endMinuteOfDay = 0,
            daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
            isEnabled = true,
            isDefaultRule = true
        )

        return customRules + defaultRule
    }

    private suspend fun normalizePriorities(scope: ScheduleScreenScope) {
        val rules = scheduleDao.getRulesByScope(scope.name)
        val customRules = rules
            .filterNot { it.isDefaultRule || it.id.startsWith("default_rule_") }
            .sortedBy { it.priority }
        val defaultRule = rules
            .find { it.isDefaultRule || it.id.startsWith("default_rule_") }

        val reindexedCustom = customRules.mapIndexed { idx, entity -> entity.copy(priority = idx) }
        val allReindexed = if (defaultRule != null) {
            reindexedCustom + defaultRule.copy(
                priority = reindexedCustom.size,
                startMinuteOfDay = 0,
                endMinuteOfDay = 0,
                daysOfWeekMask = ScheduleRule.ALL_DAYS_MASK,
                isEnabled = true,
                isDefaultRule = true
            )
        } else {
            reindexedCustom
        }
        scheduleDao.replaceRulesForScope(scope.name, allReindexed)
    }

    private fun ScheduleRuleEntity.toDomain(listName: String) = ScheduleRule(
        id = id,
        listId = listId,
        listName = listName,
        screenScope = runCatching { ScheduleScreenScope.valueOf(screenScope) }
            .getOrDefault(ScheduleScreenScope.SHARED),
        priority = priority,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        daysOfWeekMask = daysOfWeekMask,
        isEnabled = isEnabled,
        isDefaultRule = isDefaultRule || id.startsWith("default_rule_")
    )

    private fun ScheduleRule.toEntity() = ScheduleRuleEntity(
        id = id,
        listId = listId,
        screenScope = screenScope.name,
        priority = priority,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay,
        daysOfWeekMask = daysOfWeekMask,
        isEnabled = isEnabled,
        isDefaultRule = isDefaultRule
    )
}
