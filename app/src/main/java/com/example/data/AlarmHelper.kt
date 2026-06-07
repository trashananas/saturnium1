package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.receiver.AlarmReceiver
import java.util.Calendar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

object AlarmHelper {

    private const val TAG = "AlarmHelper"
    private const val PREFS_NAME = "saturnium_alarm_prefs"
    private const val KEY_NEXT_ALARM_MILLIS = "next_alarm_millis"
    private const val KEY_NEXT_ALARM_LABEL = "next_alarm_label"

    /**
     * Clear-cut day difference calculation. Set both calendars to 12:00 (midday)
     * to eliminate DST differences and accurately count continuous calendar days.
     */
    fun getDaysBetween(startMillis: Long, endMillis: Long): Int {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = endCal.timeInMillis - startCal.timeInMillis
        return (diffMillis / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * Resolves the cycle day index (1-based) for any target date.
     */
    fun getDayIndexForDate(anchorMillis: Long, targetMillis: Long, cycleLength: Int): Int {
        if (cycleLength <= 0) return 1
        val diffDays = getDaysBetween(anchorMillis, targetMillis)
        val indexZeroBased = ((diffDays % cycleLength) + cycleLength) % cycleLength
        return indexZeroBased + 1
    }

    /**
     * Scans forward from today to find the next active alarm, schedules it via AlarmManager,
     * and saves details in SharedPreferences so the UI can display them.
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun scheduleNextAlarm(context: Context, cycle: ShiftCycle?, configs: List<CycleDayConfig>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.saturnium.ALARM_TRIGGER"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            12345, // unique request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (cycle == null || configs.isEmpty()) {
            alarmManager.cancel(pendingIntent)
            saveAlarmInPrefs(context, 0, "No alarms active")
            Log.d(TAG, "No active cycle. Cancelled scheduled alarms.")
            return
        }

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val database = SaturniumDatabase.getDatabase(context)
            val dao = database.saturniumDao()
            
            val exclusions = try {
                dao.getAllDateExclusionsDirect().map { it.dateStr }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
            
            val cyclicReminders = try {
                dao.getAllCyclicRemindersDirect().filter { it.isEnabled }
            } catch (e: Exception) {
                emptyList()
            }

            val now = Calendar.getInstance()
            
            // Scan next 60 days to find absolute next candidate
            var absoluteNextAlarmCal: Calendar? = null
            var absoluteNextLabel = ""
            
            for (i in 0..60) {
                val checkCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, i)
                }
                
                val checkYear = checkCal.get(Calendar.YEAR)
                val checkMonth = checkCal.get(Calendar.MONTH)
                val checkDay = checkCal.get(Calendar.DAY_OF_MONTH)
                val dateStr = String.format("%04d-%02d-%02d", checkYear, checkMonth + 1, checkDay)
                
                val dayIndex = getDayIndexForDate(cycle.startDateMillis, checkCal.timeInMillis, cycle.cycleLength)
                val config = configs.find { it.dayIndex == dayIndex }
                
                // Collect day candidates
                val dayCandidates = mutableListOf<Pair<Calendar, String>>()
                
                // 1. Regular shift
                if (config != null && config.alarmEnabled && !exclusions.contains(dateStr)) {
                    val sCal = Calendar.getInstance().apply {
                        timeInMillis = checkCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, config.alarmHour)
                        set(Calendar.MINUTE, config.alarmMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (sCal.timeInMillis > now.timeInMillis) {
                        val formattedDate = String.format("%02d.%02d.%04d", checkDay, checkMonth + 1, checkYear)
                        dayCandidates.add(sCal to "Смена ${config.dayIndex}: ${config.shiftName} ($formattedDate)")
                    }
                }
                
                // 2. Cyclic reminders
                for (reminder in cyclicReminders) {
                    var isMatched = false
                    if (reminder.type == "CYCLE_DAY") {
                        if (reminder.targetCycleDay == dayIndex) {
                            isMatched = true
                        }
                    } else if (reminder.type == "INTERVAL") {
                        val start = reminder.startDateMillis ?: 0L
                        val daysBetween = getDaysBetween(start, checkCal.timeInMillis)
                        val rInterval = reminder.intervalDays ?: 1
                        if (daysBetween >= 0 && daysBetween % rInterval == 0) {
                            isMatched = true
                        }
                    }
                    
                    if (isMatched) {
                        val rCal = Calendar.getInstance().apply {
                            timeInMillis = checkCal.timeInMillis
                            set(Calendar.HOUR_OF_DAY, reminder.hour)
                            set(Calendar.MINUTE, reminder.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (rCal.timeInMillis > now.timeInMillis) {
                            val formattedDate = String.format("%02d.%02d.%04d", checkDay, checkMonth + 1, checkYear)
                            dayCandidates.add(rCal to "${reminder.label} ($formattedDate)")
                        }
                    }
                }
                
                val nearestOnDay = dayCandidates.minByOrNull { it.first.timeInMillis }
                if (nearestOnDay != null) {
                    if (absoluteNextAlarmCal == null || nearestOnDay.first.timeInMillis < absoluteNextAlarmCal.timeInMillis) {
                        absoluteNextAlarmCal = nearestOnDay.first
                        absoluteNextLabel = nearestOnDay.second
                    }
                }
            }

            if (absoluteNextAlarmCal != null) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        absoluteNextAlarmCal.timeInMillis,
                        pendingIntent
                    )
                    val timeLabel = String.format("%02d:%02d", absoluteNextAlarmCal.get(Calendar.HOUR_OF_DAY), absoluteNextAlarmCal.get(Calendar.MINUTE))
                    val fullLabel = "$timeLabel ($absoluteNextLabel)"
                    saveAlarmInPrefs(context, absoluteNextAlarmCal.timeInMillis, fullLabel)
                    Log.d(TAG, "Scheduled next alarm: $fullLabel")
                } catch (e: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        absoluteNextAlarmCal.timeInMillis,
                        pendingIntent
                    )
                    val timeLabel = String.format("%02d:%02d", absoluteNextAlarmCal.get(Calendar.HOUR_OF_DAY), absoluteNextAlarmCal.get(Calendar.MINUTE))
                    val fullLabel = "$timeLabel ($absoluteNextLabel) [Inexact]"
                    saveAlarmInPrefs(context, absoluteNextAlarmCal.timeInMillis, fullLabel)
                }
            } else {
                alarmManager.cancel(pendingIntent)
                saveAlarmInPrefs(context, 0, "No alarms active")
                Log.d(TAG, "No upcoming alarms.")
            }
        }
    }

    private fun saveAlarmInPrefs(context: Context, millis: Long, label: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_ALARM_MILLIS, millis)
            .putString(KEY_NEXT_ALARM_LABEL, label)
            .apply()
    }

    fun getNextAlarmLabel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val millis = prefs.getLong(KEY_NEXT_ALARM_MILLIS, 0)
        return if (millis > System.currentTimeMillis()) {
            prefs.getString(KEY_NEXT_ALARM_LABEL, "Not set") ?: "Not set"
        } else {
            "No active alarm"
        }
    }
}
