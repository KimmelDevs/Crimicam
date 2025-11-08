package com.example.crimicam.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    suspend fun setNotificationStatus(status: Boolean) {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

        val notificationData = mapOf(
            "notification" to status,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .collection("notification")
            .document("status")
            .set(notificationData)
            .await()
    }
}