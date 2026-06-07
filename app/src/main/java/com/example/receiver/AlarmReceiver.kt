package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AlarmHelper
import com.example.data.SaturniumDatabase
import com.example.data.SaturniumRepository
import com.example.service.AlarmService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AlarmReceiver", "Received broadcast with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("AlarmReceiver", "Rescheduling alarms on BOOT_COMPLETED...")
            val pendingResult = goAsync()
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val database = SaturniumDatabase.getDatabase(context)
                    val repository = SaturniumRepository(database.saturniumDao())
                    val activeCycle = repository.getActiveCycleDirect()
                    if (activeCycle != null) {
                        val configs = repository.getDayConfigsDirect(activeCycle.id)
                        AlarmHelper.scheduleNextAlarm(context, activeCycle, configs)
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Failed to reschedule alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        Log.d("AlarmReceiver", "Alarm triggered! Routing to AlarmService...")
        val serviceIntent = Intent(context, AlarmService::class.java)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed starting AlarmService, attempting fallback standard start", e)
            try {
                context.startService(serviceIntent)
            } catch (ex: Exception) {
                Log.e("AlarmReceiver", "All attempts to start AlarmService failed", ex)
            }
        }
    }
}
