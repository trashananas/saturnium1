package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_cycles")
data class ShiftCycle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startDateMillis: Long,
    val cycleLength: Int,
    val isActive: Boolean = true
)

@Entity(tableName = "cycle_days")
data class CycleDayConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cycleId: Int,
    val dayIndex: Int, // 1-based index in the cycle: 1..cycleLength
    val isWorkDay: Boolean,
    val shiftName: String,
    val alarmEnabled: Boolean,
    val alarmHour: Int,
    val alarmMinute: Int
)

@Entity(tableName = "cyclic_reminders")
data class CyclicReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val type: String, // "CYCLE_DAY" or "INTERVAL"
    val targetCycleDay: Int? = null,
    val intervalDays: Int? = null,
    val startDateMillis: Long? = null,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)

@Entity(tableName = "date_exclusions")
data class DateExclusion(
    @PrimaryKey val dateStr: String // Format: "YYYY-MM-DD"
)

