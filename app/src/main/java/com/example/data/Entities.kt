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
