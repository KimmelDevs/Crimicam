package com.example.crimicam.data.repository

import android.util.Log
import com.example.crimicam.data.model.EmergencyReport
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

class EmergencyReportRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "EmergencyReportRepo"
        private const val REPORTS_COLLECTION = "emergency_reports"
    }

    /**
     * Create a new emergency report
     */
    suspend fun createReport(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        type: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val reportId = UUID.randomUUID().toString()

            // Get user's friends list
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            @Suppress("UNCHECKED_CAST")
            val friendIds = userDoc.get("friends") as? List<String> ?: emptyList()

            // Create report
            val report = hashMapOf<String, Any?>(
                "id" to reportId,
                "userId" to currentUser.uid,
                "userName" to (currentUser.displayName ?: "Unknown User"),
                "userEmail" to (currentUser.email ?: ""),
                "title" to title,
                "description" to description,
                "location" to GeoPoint(latitude, longitude),
                "address" to address,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "ACTIVE",
                "type" to type,
                "isResolved" to false,
                "resolvedAt" to null,
                "friendsNotified" to friendIds
            )

            // Save to Firestore
            firestore.collection(REPORTS_COLLECTION)
                .document(reportId)
                .set(report)
                .await()

            Log.d(TAG, "✅ Emergency report created: $reportId")

            // Send notifications to friends (handled separately)
            notifyFriends(reportId, friendIds, currentUser.displayName ?: "A friend", title, type)

            Result.Success(reportId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating report", e)
            Result.Error(e)
        }
    }

    /**
     * Get reports created by current user
     */
    suspend fun getUserReports(): Result<List<EmergencyReport>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val querySnapshot = firestore.collection(REPORTS_COLLECTION)
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val reports = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(EmergencyReport::class.java)
            }

            Log.d(TAG, "✅ Loaded ${reports.size} user reports")
            Result.Success(reports)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading user reports", e)
            Result.Error(e)
        }
    }

    /**
     * Get reports from friends
     */
    suspend fun getFriendReports(): Result<List<EmergencyReport>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val querySnapshot = firestore.collection(REPORTS_COLLECTION)
                .whereArrayContains("friendsNotified", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val reports = querySnapshot.documents.mapNotNull { doc ->
                doc.toObject(EmergencyReport::class.java)
            }

            Log.d(TAG, "✅ Loaded ${reports.size} friend reports")
            Result.Success(reports)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading friend reports", e)
            Result.Error(e)
        }
    }

    /**
     * Update report status
     */
    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "status" to status,
                "isResolved" to (status == "RESOLVED"),
                "resolvedAt" to if (status == "RESOLVED") FieldValue.serverTimestamp() else null
            )

            firestore.collection(REPORTS_COLLECTION)
                .document(reportId)
                .update(updates)
                .await()

            Log.d(TAG, "✅ Report status updated: $reportId -> $status")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error updating report status", e)
            Result.Error(e)
        }
    }

    /**
     * Delete report
     */
    suspend fun deleteReport(reportId: String): Result<Unit> {
        return try {
            // Delete report document
            firestore.collection(REPORTS_COLLECTION)
                .document(reportId)
                .delete()
                .await()

            Log.d(TAG, "✅ Report deleted: $reportId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deleting report", e)
            Result.Error(e)
        }
    }

    /**
     * Send notifications to friends about the emergency report
     */
    private suspend fun notifyFriends(
        reportId: String,
        friendIds: List<String>,
        userName: String,
        title: String,
        type: String
    ) {
        try {
            // Create notification data
            val notificationData = hashMapOf<String, Any>(
                "reportId" to reportId,
                "type" to "EMERGENCY_REPORT",
                "senderName" to userName,
                "title" to title,
                "reportType" to type,
                "timestamp" to FieldValue.serverTimestamp()
            )

            // Add notification for each friend
            friendIds.forEach { friendId ->
                try {
                    firestore.collection("users")
                        .document(friendId)
                        .collection("notifications")
                        .add(notificationData)
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to notify friend $friendId", e)
                }
            }

            Log.d(TAG, "✅ Notified ${friendIds.size} friends")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error notifying friends", e)
        }
    }
}