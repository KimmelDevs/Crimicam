package com.example.crimicam.data.repository

import com.example.crimicam.data.remote.FirestoreService
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay

class NotificationRepository(
    private val firestoreService: FirestoreService
) {

    suspend fun triggerNotification(): Result<Unit> {
        return try {
            // Set notification to true
            firestoreService.setNotificationStatus(true)

            // Wait for 1 second
            delay(1000L)

            // Set notification back to false
            firestoreService.setNotificationStatus(false)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}