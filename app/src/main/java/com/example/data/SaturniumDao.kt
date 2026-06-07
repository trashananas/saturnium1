package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SaturniumDao {

    @Query("SELECT * FROM shift_cycles WHERE isActive = 1 LIMIT 1")
    fun getActiveCycleFlow(): Flow<ShiftCycle?>

    @Query("SELECT * FROM shift_cycles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveCycle(): ShiftCycle?

    @Query("SELECT * FROM shift_cycles ORDER BY id DESC")
    fun getAllCyclesFlow(): Flow<List<ShiftCycle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: ShiftCycle): Long

    @Update
    suspend fun updateCycle(cycle: ShiftCycle)

    @Query("SELECT * FROM cycle_days WHERE cycleId = :cycleId ORDER BY dayIndex ASC")
    fun getDayConfigsFlow(cycleId: Int): Flow<List<CycleDayConfig>>

    @Query("SELECT * FROM cycle_days WHERE cycleId = :cycleId ORDER BY dayIndex ASC")
    suspend fun getDayConfigs(cycleId: Int): List<CycleDayConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayConfigs(configs: List<CycleDayConfig>)

    @Query("DELETE FROM cycle_days WHERE cycleId = :cycleId")
    suspend fun deleteDayConfigsForCycle(cycleId: Int)

    @Update
    suspend fun updateDayConfig(config: CycleDayConfig)

    @Transaction
    suspend fun replaceCycleAndDays(cycle: ShiftCycle, configs: List<CycleDayConfig>): Int {
        // Deactivate other cycles
        deactivateAllCycles()
        
        // Insert new/updated cycle
        val cycleId = insertCycle(cycle.copy(isActive = true)).toInt()
        
        // Delete old day configs for this cycle
        deleteDayConfigsForCycle(cycleId)
        
        // Map configs with the assigned cycle ID and insert them
        val updatedConfigs = configs.map { it.copy(cycleId = cycleId) }
        insertDayConfigs(updatedConfigs)
        
        return cycleId
    }

    @Query("UPDATE shift_cycles SET isActive = 0")
    suspend fun deactivateAllCycles()

    // Cyclic Reminders
    @Query("SELECT * FROM cyclic_reminders ORDER BY id DESC")
    fun getAllCyclicRemindersFlow(): Flow<List<CyclicReminder>>

    @Query("SELECT * FROM cyclic_reminders ORDER BY id DESC")
    suspend fun getAllCyclicRemindersDirect(): List<CyclicReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCyclicReminder(reminder: CyclicReminder): Long

    @Delete
    suspend fun deleteCyclicReminder(reminder: CyclicReminder)

    // Date Exclusions
    @Query("SELECT * FROM date_exclusions")
    fun getAllDateExclusionsFlow(): Flow<List<DateExclusion>>

    @Query("SELECT * FROM date_exclusions")
    suspend fun getAllDateExclusionsDirect(): List<DateExclusion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDateExclusion(exclusion: DateExclusion)

    @Query("DELETE FROM date_exclusions WHERE dateStr = :dateStr")
    suspend fun deleteDateExclusionByDate(dateStr: String)
}

