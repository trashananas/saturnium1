package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

class SaturniumRepository(private val dao: SaturniumDao) {

    val activeCycle: Flow<ShiftCycle?> = dao.getActiveCycleFlow()

    val activeDayConfigs: Flow<List<CycleDayConfig>> = activeCycle.flatMapLatest { cycle ->
        if (cycle != null) {
            dao.getDayConfigsFlow(cycle.id)
        } else {
            emptyFlow()
        }
    }

    suspend fun getActiveCycleDirect(): ShiftCycle? = dao.getActiveCycle()

    suspend fun getDayConfigsDirect(cycleId: Int): List<CycleDayConfig> = dao.getDayConfigs(cycleId)

    suspend fun replaceCycleAndDays(cycle: ShiftCycle, configs: List<CycleDayConfig>): Int {
        return dao.replaceCycleAndDays(cycle, configs)
    }

    suspend fun updateDayConfig(config: CycleDayConfig) {
        dao.updateDayConfig(config)
    }

    suspend fun updateCycle(cycle: ShiftCycle) {
        dao.updateCycle(cycle)
    }

    // Cyclic Reminders
    val allCyclicReminders: Flow<List<CyclicReminder>> = dao.getAllCyclicRemindersFlow()

    suspend fun getAllCyclicRemindersDirect(): List<CyclicReminder> {
        return dao.getAllCyclicRemindersDirect()
    }

    suspend fun insertCyclicReminder(reminder: CyclicReminder): Long {
        return dao.insertCyclicReminder(reminder)
    }

    suspend fun deleteCyclicReminder(reminder: CyclicReminder) {
        dao.deleteCyclicReminder(reminder)
    }

    // Date Exclusions
    val allDateExclusions: Flow<List<DateExclusion>> = dao.getAllDateExclusionsFlow()

    suspend fun getAllDateExclusionsDirect(): List<DateExclusion> {
        return dao.getAllDateExclusionsDirect()
    }

    suspend fun insertDateExclusion(exclusion: DateExclusion) {
        dao.insertDateExclusion(exclusion)
    }

    suspend fun deleteDateExclusionByDate(dateStr: String) {
        dao.deleteDateExclusionByDate(dateStr)
    }
}

