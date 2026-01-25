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
        private const val USERS_COLLECTION = "users"
        private const val NOTIFICATIONS_COLLECTION = "notifications"
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
            val userDoc = firestore.collection(USERS_COLLECTION)
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
            Log.d(TAG, "📢 Will notify ${friendIds.size} friends")

            // Also create a local notification for the user
            createLocalNotificationForUser(
                userId = currentUser.uid,
                reportId = reportId,
                title = "Report Created",
                body = "Your emergency report has been submitted",
                type = "REPORT_CREATED"
            )

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
     * Get unread notifications count
     */
    suspend fun getUnreadNotificationsCount(): Result<Int> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("read", false)
                .get()
                .await()

            val count = querySnapshot.documents.size
            Log.d(TAG, "📊 Unread notifications: $count")
            Result.Success(count)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting unread notifications count", e)
            Result.Error(e)
        }
    }

    /**
     * Get notifications for user
     */
    suspend fun getUserNotifications(): Result<List<NotificationItem>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val notifications = querySnapshot.documents.mapNotNull { doc ->
                try {
                    NotificationItem(
                        id = doc.id,
                        reportId = doc.getString("reportId") ?: "",
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "Unknown",
                        title = doc.getString("title") ?: "",
                        body = doc.getString("body") ?: "",
                        type = doc.getString("type") ?: "",
                        reportType = doc.getString("reportType") ?: "",
                        address = doc.getString("address") ?: "",
                        timestamp = doc.getTimestamp("timestamp"),
                        read = doc.getBoolean("read") ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Log.d(TAG, "✅ Loaded ${notifications.size} notifications")
            Result.Success(notifications)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading notifications", e)
            Result.Error(e)
        }
    }

    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("read", true)
                .await()

            Log.d(TAG, "✅ Notification marked as read: $notificationId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking notification as read", e)
            Result.Error(e)
        }
    }

    /**
     * Mark all notifications as read
     */
    suspend fun markAllNotificationsAsRead(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.Error(Exception("User not authenticated"))

            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("read", false)
                .get()
                .await()

            val batch = firestore.batch()
            querySnapshot.documents.forEach { doc ->
                batch.update(doc.reference, "read", true)
            }

            if (querySnapshot.documents.isNotEmpty()) {
                batch.commit().await()
                Log.d(TAG, "✅ Marked ${querySnapshot.documents.size} notifications as read")
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking all notifications as read", e)
            Result.Error(e)
        }
    }

    /**
     * Create local notification for user
     */
    private suspend fun createLocalNotificationForUser(
        userId: String,
        reportId: String,
        title: String,
        body: String,
        type: String
    ) {
        try {
            val notification = hashMapOf<String, Any>(
                "reportId" to reportId,
                "title" to title,
                "body" to body,
                "type" to type,
                "timestamp" to FieldValue.serverTimestamp(),
                "read" to false
            )

            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(NOTIFICATIONS_COLLECTION)
                .add(notification)
                .await()

            Log.d(TAG, "✅ Local notification created for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating local notification", e)
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
}

data class NotificationItem(
    val id: String = "",
    val reportId: String = "",
    val userId: String = "",
    val userName: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "",
    val reportType: String = "",
    val address: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val read: Boolean = false
)