package com.example.crimicam.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val CHANNEL_ID = "crimicam_channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crimicam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for crime alert notifications"
            }

            val notificationManager = ContextCompat.getSystemService(
                context,
                NotificationManager::class.java
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }
}