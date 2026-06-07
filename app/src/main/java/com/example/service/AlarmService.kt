package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AlarmService", "AlarmService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AlarmService", "AlarmService starting command...")

        // 1. Set is_ringing preference to true
        getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_ringing", true)
            .apply()

        // Send local broadcast to update running UI immediately
        sendBroadcast(Intent("com.example.saturnium.ALARM_RING_UPDATE"))

        // 2. Start Service in Foreground with correct type for Android 14+
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(99, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(99, notification)
        }

        // 3. Play alarm sound & gradually increase volume
        playAlarmSound()

        // 4. Start vibration pattern
        startVibration()

        // 5. Wake up Screen and bring Activity to front
        wakeUpAndLaunchMainActivity()

        return START_NOT_STICKY
    }

    private fun playAlarmSound() {
        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            try {
                val prefs = getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
                val storedUriStr = prefs.getString("selected_ringtone_uri", null)
                val soundUri = if (!storedUriStr.isNullOrEmpty()) {
                    android.net.Uri.parse(storedUriStr)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, soundUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    setVolume(0.01f, 0.01f) // Start almost silent
                    start()
                }

                Log.d("AlarmService", "Alarm sound started, fading in...")

                // Gradually increase volume from 0.01 to 1.0 (over 30 seconds)
                var currentVolume = 0.01f
                while (currentVolume < 1.0f && mediaPlayer?.isPlaying == true) {
                    delay(1500) // Increase every 1.5 seconds
                    currentVolume += 0.05f
                    if (currentVolume > 1.0f) currentVolume = 1.0f
                    mediaPlayer?.setVolume(currentVolume, currentVolume)
                    Log.d("AlarmService", "Alarm volume set to: $currentVolume")
                }

            } catch (e: Exception) {
                Log.e("AlarmService", "Error playing alarm sound", e)
            }
        }
    }

    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                it.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 1))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(longArrayOf(0, 800, 400, 800, 400), 1)
            }
        }
    }

    private fun wakeUpAndLaunchMainActivity() {
        // Use PowerManager to wake up screens if possible
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Saturnium:AlarmWakeLock"
        )
        wakeLock?.acquire(10000) // Keep screen bright for 10 seconds

        // Launch MainActivity
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_ringing_screen", true)
        }
        startActivity(activityIntent)
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_ringing_screen", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            24680,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "saturnium_alarms_channel"
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🪐 Будильник SATURNIUM активирован! 🪐")
            .setContentText("Космическое время вставать на вашу смену!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true) // crucial to show on lock screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "saturnium_alarms_channel"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Saturnium Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggered when your shift pattern alarm fires"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true) // alert even in DND mode
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AlarmService", "AlarmService destroyed")
        
        // Cancel fade job
        serviceJob?.cancel()
        serviceScope.cancel()

        // Stop music
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping media player", e)
        }
        mediaPlayer = null

        // Stop vibration
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("AlarmService", "Error stopping vibration", e)
        }
        vibrator = null

        // Write to prefs to close ringing overlay in UI
        getSharedPreferences("saturnium_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_ringing", false)
            .apply()

        // Send local broadcast to refresh UI state
        sendBroadcast(Intent("com.example.saturnium.ALARM_RING_UPDATE"))
    }
}
