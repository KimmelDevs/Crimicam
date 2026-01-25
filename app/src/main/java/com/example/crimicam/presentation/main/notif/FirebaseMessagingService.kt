package com.example.crimicam.presentation.main.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.crimicam.MainActivity
import com.example.crimicam.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class FirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID_ACTIVITY = "crimicam_notifications"
        const val CHANNEL_ID_EMERGENCY = "emergency_reports"
        const val CHANNEL_NAME_ACTIVITY = "Crimicam Alerts"
        const val CHANNEL_NAME_EMERGENCY = "Emergency Reports"
        const val CHANNEL_DESC_ACTIVITY = "Activity and alert notifications"
        const val CHANNEL_DESC_EMERGENCY = "Emergency report notifications from friends"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token: $token")
        // The token will be handled by MainActivity's initializeFCM()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Timber.d("🔥 FCM Message Received!")
        Timber.d("From: ${remoteMessage.from}")
        Timber.d("Data payload: ${remoteMessage.data}")
        Timber.d("Notification payload: ${remoteMessage.notification}")

        // Check if message contains a notification payload
        remoteMessage.notification?.let { notification ->
            Timber.d("Notification Body: ${notification.body}")
            Timber.d("Notification Title: ${notification.title}")
        }

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Data: ${remoteMessage.data}")

            val type = remoteMessage.data["type"] ?: "ACTIVITY_BROADCAST"
            val title = remoteMessage.data["title"] ?: "Crimicam Alert"
            val body = remoteMessage.data["body"] ?: "New notification"

            when (type) {
                "EMERGENCY_REPORT" -> {
                    Timber.d("🚨 Processing EMERGENCY REPORT notification")
                    val reportType = remoteMessage.data["reportType"] ?: "EMERGENCY"
                    val address = remoteMessage.data["address"] ?: "Unknown location"
                    val userName = remoteMessage.data["userName"] ?: "A friend"

                    val fullTitle = when (reportType) {
                        "EMERGENCY" -> "🚨 EMERGENCY: $title"
                        "SUSPICIOUS" -> "⚠️ SUSPICIOUS: $title"
                        "HELP_NEEDED" -> "🆘 HELP NEEDED: $title"
                        else -> "📢 REPORT: $title"
                    }

                    val fullBody = when (reportType) {
                        "EMERGENCY" -> "$userName needs immediate help at $address"
                        "SUSPICIOUS" -> "$userName reported something suspicious at $address"
                        "HELP_NEEDED" -> "$userName needs assistance at $address"
                        else -> "$userName sent a report from $address"
                    }

                    sendEmergencyNotification(fullTitle, fullBody, remoteMessage.data)
                }
                else -> {
                    Timber.d("📡 Processing ACTIVITY BROADCAST notification")
                    sendNotification(title, body, remoteMessage.data)
                }
            }
        } else if (remoteMessage.notification != null) {
            // Handle notification-only messages
            val title = remoteMessage.notification?.title ?: "Crimicam Alert"
            val body = remoteMessage.notification?.body ?: "New notification"
            sendNotification(title, body, emptyMap())
        }
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String>) {
        try {
            Timber.d("📤 Creating notification: $title")

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            data.forEach { (key, value) ->
                intent.putExtra(key, value)
            }

            intent.putExtra("type", data["type"] ?: "ACTIVITY_BROADCAST")
            intent.putExtra("notification", true)

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            createNotificationChannels()

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVITY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // Add notification actions if needed
            val dangerLevel = data["dangerLevel"] ?: "LOW"
            when (dangerLevel.uppercase()) {
                "CRITICAL" -> {
                    notificationBuilder
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(longArrayOf(1000, 1000, 1000, 1000))
                        .setLights(0xFF0000.toInt(), 1000, 1000)
                }
                "HIGH" -> {
                    notificationBuilder
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVibrate(longArrayOf(1000, 500, 1000))
                }
                else -> {
                    notificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                }
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, notificationBuilder.build())

            Timber.d("✅ Notification sent with ID: $notificationId")

        } catch (e: Exception) {
            Timber.e(e, "❌ Error sending notification")
        }
    }

    private fun sendEmergencyNotification(title: String, messageBody: String, data: Map<String, String>) {
        try {
            Timber.d("🚨 Creating emergency notification: $title")

            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            data.forEach { (key, value) ->
                intent.putExtra(key, value)
            }

            intent.putExtra("type", "EMERGENCY_REPORT")
            intent.putExtra("notification", true)
            intent.putExtra("openEmergencyReports", true)

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val emergencySoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            createNotificationChannels()

            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
                .setSmallIcon(R.drawable.ic_emergency)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setSound(emergencySoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(longArrayOf(1000, 500, 1000, 500, 1000))
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            val reportType = data["reportType"] ?: "EMERGENCY"
            when (reportType) {
                "EMERGENCY" -> {
                    notificationBuilder
                        .setColor(0xFFD32F2F.toInt())
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setLights(0xFF0000.toInt(), 1000, 1000)
                }
                "SUSPICIOUS" -> {
                    notificationBuilder
                        .setColor(0xFFF57C00.toInt())
                        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                }
                "HELP_NEEDED" -> {
                    notificationBuilder
                        .setColor(0xFF1976D2.toInt())
                        .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                }
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationId = System.currentTimeMillis().toInt() + 1
            notificationManager.notify(notificationId, notificationBuilder.build())

            Timber.d("✅ Emergency notification sent with ID: $notificationId")

        } catch (e: Exception) {
            Timber.e(e, "❌ Error sending emergency notification")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Activity notifications channel
            val activityChannel = NotificationChannel(
                CHANNEL_ID_ACTIVITY,
                CHANNEL_NAME_ACTIVITY,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_ACTIVITY
                enableVibration(true)
                vibrationPattern = longArrayOf(500, 500)
                enableLights(true)
                lightColor = 0xFF2196F3.toInt()
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Emergency reports channel
            val emergencyChannel = NotificationChannel(
                CHANNEL_ID_EMERGENCY,
                CHANNEL_NAME_EMERGENCY,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_EMERGENCY
                enableVibration(true)
                vibrationPattern = longArrayOf(1000, 500, 1000, 500, 1000)
                enableLights(true)
                lightColor = 0xFFD32F2F.toInt()
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(activityChannel)
            notificationManager.createNotificationChannel(emergencyChannel)

            Timber.d("✅ Notification channels created/updated")
        }
    }
}