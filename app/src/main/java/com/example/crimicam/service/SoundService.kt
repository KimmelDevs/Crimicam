package com.example.crimicam.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.crimicam.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var notificationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val NOTIFICATION_ID = 1234
        const val ACTION_STOP = "STOP_ACTION"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                stopSelf()
            }
            "PLAY_ALARM" -> {
                playAlarm()
            }
            else -> {
                // Service started to listen for notifications
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "crimicam_channel")
            .setContentTitle("Crimicam")
            .setContentText("Listening for alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, use the mediaPlayback foreground service type
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            // For older versions, just start as foreground service
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun playAlarm() {
        stopAlarm() // Stop any existing alarm

        notificationJob = serviceScope.launch {
            try {
                // Try different alarm sounds
                val alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                if (alarmUri != null) {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(this@SoundService, alarmUri)
                        setOnPreparedListener { start() }
                        setOnCompletionListener { stopAlarm() }
                        setOnErrorListener { mp, what, extra ->
                            stopAlarm()
                            true
                        }
                        prepareAsync()
                    }

                    // Stop automatically after 10 seconds
                    delay(10000L)
                    stopAlarm()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopAlarm()
            }
        }
    }

    fun stopAlarm() {
        notificationJob?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}