package com.example.crimicam.util

import android.content.Context
import android.content.Intent
import com.example.crimicam.data.remote.FirestoreService
import com.example.crimicam.service.SoundService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class NotificationManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var listenerJob: Job? = null
    private var wasNotificationActive = false

    private val firestoreService = FirestoreService(
        FirebaseFirestore.getInstance(),
        FirebaseAuth.getInstance()
    )

    fun startListening() {
        // Start the sound service
        val serviceIntent = Intent(context, SoundService::class.java)
        context.startService(serviceIntent)

        listenerJob = firestoreService.listenForNotifications()
            .onEach { isNotificationActive ->
                // Only trigger sound when notification changes from false to true
                if (isNotificationActive && !wasNotificationActive) {
                    triggerAlarm()
                }
                wasNotificationActive = isNotificationActive
            }
            .launchIn(scope)
    }

    fun stopListening() {
        listenerJob?.cancel()

        // Stop the sound service
        val stopIntent = Intent(context, SoundService::class.java).apply {
            action = SoundService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    private fun triggerAlarm() {
        val alarmIntent = Intent(context, SoundService::class.java).apply {
            action = "PLAY_ALARM"
        }
        context.startService(alarmIntent)
    }
}