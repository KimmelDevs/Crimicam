package com.example.crimicam.data.repository

import android.graphics.Bitmap
import com.example.crimicam.data.model.SuspiciousActivityRecord
import com.example.crimicam.util.ImageCompressor
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class SuspiciousActivityRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun getUserActivitiesCollection() = auth.currentUser?.uid?.let { userId ->
        firestore.collection("users")
            .document(userId)
            .collection("suspicious_activities")
    }

    suspend fun saveActivity(
        activityType: String,
        displayName: String,
        severity: String,
        confidence: Float,
        duration: Long,
        details: Map<String, Any>,
        frameBitmap: Bitmap?,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String? = null
    ): Result<SuspiciousActivityRecord> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val collection = getUserActivitiesCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            // Compress frame image if provided
            val frameBase64 = frameBitmap?.let { bitmap ->
                val compressed = ImageCompressor.compressBitmap(bitmap, maxWidth = 480, maxHeight = 640)
                ImageCompressor.bitmapToBase64(compressed, quality = 60)
            }

            val docId = collection.document().id

            val record = SuspiciousActivityRecord(
                id = docId,
                userId = userId,
                activityType = activityType,
                displayName = displayName,
                severity = severity,
                confidence = confidence,
                duration = duration,
                details = details,
                timestamp = System.currentTimeMillis(),
                frameImageBase64 = frameBase64,
                latitude = latitude,
                longitude = longitude,
                address = address
            )

            collection.document(docId)
                .set(record)
                .await()

            Result.Success(record)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getActivities(limit: Int = 50): Result<List<SuspiciousActivityRecord>> {
        return try {
            val collection = getUserActivitiesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val activities = snapshot.documents.mapNotNull {
                it.toObject(SuspiciousActivityRecord::class.java)
            }
            Result.Success(activities)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteActivity(activityId: String): Result<Unit> {
        return try {
            val collection = getUserActivitiesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            collection.document(activityId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}