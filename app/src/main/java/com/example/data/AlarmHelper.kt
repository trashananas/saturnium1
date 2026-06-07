package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.receiver.AlarmReceiver
import java.util.Calendar

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
            saveAlarmInPrefs(context, 0, "")
            Log.d(TAG, "No active cycle. Cancelled scheduled alarms.")
            return
        }

        val now = Calendar.getInstance()
        var nextAlarmCal: Calendar? = null
        var matchedConfig: CycleDayConfig? = null

        // Scan upcoming 30 days
        for (i in 0..30) {
            val checkCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
            }
            
            val dayIndex = getDayIndexForDate(cycle.startDateMillis, checkCal.timeInMillis, cycle.cycleLength)
            val config = configs.find { it.dayIndex == dayIndex }
            
            if (config != null && config.alarmEnabled) {
                val alarmCal = Calendar.getInstance().apply {
                    timeInMillis = checkCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, config.alarmHour)
                    set(Calendar.MINUTE, config.alarmMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If scheduled time is in the future, select it!
                if (alarmCal.timeInMillis > now.timeInMillis) {
                    nextAlarmCal = alarmCal
                    matchedConfig = config
                    break
                }
            }
        }

        if (nextAlarmCal != null && matchedConfig != null) {
            // Schedule via AlarmManager
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmCal.timeInMillis,
                    pendingIntent
                )
                
                // Format summary details
                val timeLabel = String.format("%02d:%02d", matchedConfig.alarmHour, matchedConfig.alarmMinute)
                val dayStr = String.format("%02d.%02d.%04d", 
                    nextAlarmCal.get(Calendar.DAY_OF_MONTH), 
                    nextAlarmCal.get(Calendar.MONTH) + 1, 
                    nextAlarmCal.get(Calendar.YEAR)
                )
                val label = "$timeLabel ($dayStr - ${matchedConfig.shiftName})"
                
                saveAlarmInPrefs(context, nextAlarmCal.timeInMillis, label)
                Log.d(TAG, "Scheduled next alarm for: $label")
            } catch (e: SecurityException) {
                // FALLBACK if exact alarms aren't allowed
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextAlarmCal.timeInMillis,
                    pendingIntent
                )
                val timeLabel = String.format("%02d:%02d", matchedConfig.alarmHour, matchedConfig.alarmMinute)
                val dayStr = String.format("%02d.%02d.%04d", 
                    nextAlarmCal.get(Calendar.DAY_OF_MONTH), 
                    nextAlarmCal.get(Calendar.MONTH) + 1, 
                    nextAlarmCal.get(Calendar.YEAR)
                )
                val label = "$timeLabel ($dayStr - ${matchedConfig.shiftName}) [Inexact]"
                saveAlarmInPrefs(context, nextAlarmCal.timeInMillis, label)
                Log.e(TAG, "SecurityException scheduling exact alarm. Fell back to inexact alarm.", e)
            }
        } else {
            // No upcoming alarms, cancel current pending intent
            alarmManager.cancel(pendingIntent)
            saveAlarmInPrefs(context, 0, "No alarms active")
            Log.d(TAG, "No upcoming alarms found in next 30 days.")
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
