package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm triggered successfully!")

        // Set state in shared preferences that an alarm is active (for simulated screen)
        context.getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_ringing", true)
            .apply()

        // Send a local standard broadcast to notify a running MainActivity to show the Ringing Screen
        val updateIntent = Intent("com.example.saturnium.ALARM_RING_UPDATE")
        context.sendBroadcast(updateIntent)

        // Show Notification
        showNotification(context)

        // Vibrate temporarily
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(3000)
            }
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "saturnium_alarms_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Saturnium Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggered when your shift pattern alarm fires"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_ringing_screen", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            24680,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🪐 Смена началась! (Saturnium Alarm) 🪐")
            .setContentText("Пора просыпаться согласно вашему графику работы!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 250, 500))

        notificationManager.notify(99, builder.build())
    }
}
