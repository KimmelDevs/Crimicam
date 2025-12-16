package com.example.crimicam.presentation.main.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.crimicam.MainActivity
import com.example.crimicam.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class FirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "crimicam_notifications"
        const val CHANNEL_NAME = "Crimicam Alerts"
        const val CHANNEL_DESCRIPTION = "Activity and alert notifications"

    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        Timber.d("FCM Token to save: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("From: ${remoteMessage.from}")

        remoteMessage.notification?.let { notification ->
            Timber.d("Notification Body: ${notification.body}")
            sendNotification(
                notification.title ?: "Crimicam Alert",
                notification.body ?: "New activity detected",
                remoteMessage.data
            )
        }

        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: ${remoteMessage.data}")

            val title = remoteMessage.data["title"] ?: "Crimicam Alert"
            val body = remoteMessage.data["body"] ?: "New activity detected"
            sendNotification(title, body, remoteMessage.data)
        }
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        data.forEach { (key, value) ->
            intent.putExtra(key, value)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        data["dangerLevel"]?.let { dangerLevel ->
            when (dangerLevel.uppercase()) {
                "CRITICAL" -> {
                    notificationBuilder
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                }
                "HIGH" -> {
                    notificationBuilder
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVibrate(longArrayOf(1000, 500, 1000))
                }
                "MEDIUM" -> {
                    notificationBuilder
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVibrate(longArrayOf(500, 500))
                }
                else -> {
                    notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW)
                }
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                vibrationPattern = longArrayOf(500, 500)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}