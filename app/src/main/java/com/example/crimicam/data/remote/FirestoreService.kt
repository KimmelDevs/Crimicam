package com.example.crimicam.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun listenForNotifications(): Flow<Boolean> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(Exception("User not authenticated"))
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("notification")
            .document("status")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val isNotificationActive = snapshot.getBoolean("notification") ?: false
                    trySend(isNotificationActive)
                }
            }

        awaitClose {
            listener.remove()
        }
    }
}