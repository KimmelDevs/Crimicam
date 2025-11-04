package com.example.crimicam.data.repository

import android.graphics.Bitmap
import com.example.crimicam.data.model.CapturedFace
import com.example.crimicam.util.ImageCompressor
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CapturedFacesRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun getUserCapturedFacesCollection() = auth.currentUser?.uid?.let { userId ->
        firestore.collection("users")
            .document(userId)
            .collection("captured_faces")
    }

    /**
     * Enhanced save method that handles multi-modal detection scenarios
     * Now supports saving detections even when face is not available
     */
    suspend fun saveCapturedFace(
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap? = null,
        faceFeatures: Map<String, Any> = emptyMap(),  // Changed from Map<String, Float> to Map<String, Any>
        isRecognized: Boolean = false,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float = 0f,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracy: Float? = null,
        address: String? = null,
        detectionMode: String? = null,
        hasVisibleFace: Boolean = true,
        poseDetected: Boolean = false,
        yoloDetected: Boolean = false
    ): Result<CapturedFace> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            // Compress original image (smaller for storage)
            val compressedOriginal = ImageCompressor.compressBitmap(
                originalBitmap,
                maxWidth = 640,
                maxHeight = 640
            )
            val originalBase64 = ImageCompressor.bitmapToBase64(compressedOriginal, quality = 50)

            // Compress face crop ONLY if available (higher quality for recognition)
            val faceBase64 = croppedFaceBitmap?.let { faceBitmap ->
                val compressedFace = ImageCompressor.compressFaceCrop(faceBitmap)
                ImageCompressor.bitmapToBase64(compressedFace, quality = 70)
            }

            // Generate new document ID
            val docId = collection.document().id

            // Determine detection category
            val detectionCategory = when {
                !hasVisibleFace && poseDetected -> "face_covered"
                !hasVisibleFace && yoloDetected -> "distance_detection"
                hasVisibleFace && poseDetected -> "full_detection"
                hasVisibleFace && !poseDetected -> "face_only"
                else -> "unknown"
            }

            // Convert Map<String, Any> to Map<String, Float> for the data model
            val floatFeatures = faceFeatures.mapValues { (_, value) ->
                when (value) {
                    is Float -> value
                    is Double -> value.toFloat()
                    is Int -> value.toFloat()
                    is Long -> value.toFloat()
                    else -> 0f
                }
            }

            val capturedFace = CapturedFace(
                id = docId,
                userId = userId,
                originalImageBase64 = originalBase64,
                croppedFaceBase64 = faceBase64,
                faceFeatures = floatFeatures,
                timestamp = System.currentTimeMillis(),
                isRecognized = isRecognized,
                matchedPersonId = matchedPersonId,
                matchedPersonName = matchedPersonName,
                confidence = confidence,
                latitude = latitude,
                longitude = longitude,
                locationAccuracy = locationAccuracy,
                address = address,
                detectionMode = detectionMode,
                detectionCategory = detectionCategory,
                hasVisibleFace = hasVisibleFace,
                poseDetected = poseDetected,
                yoloDetected = yoloDetected
            )

            // Save to Firestore under users/{userId}/captured_faces/{docId}
            collection.document(docId)
                .set(capturedFace)
                .await()

            Result.Success(capturedFace)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCapturedFaces(limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUnrecognizedFaces(limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("isRecognized", false)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getRecognizedFaces(limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("isRecognized", true)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCoveredFaces(limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("detectionCategory", "face_covered")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getFullDetections(limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("detectionCategory", "full_detection")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getDetectionsByCategory(
        category: String,
        limit: Int = 50
    ): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("detectionCategory", category)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getFacesByPerson(personId: String, limit: Int = 50): Result<List<CapturedFace>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("matchedPersonId", personId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteCapturedFace(faceId: String): Result<Unit> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            collection.document(faceId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteAllCapturedFaces(): Result<Int> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection.get().await()

            var deletedCount = 0
            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
                deletedCount++
            }

            Result.Success(deletedCount)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteOldCapturedFaces(daysOld: Int = 7): Result<Int> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

            val snapshot = collection
                .whereLessThan("timestamp", cutoffTime)
                .get()
                .await()

            var deletedCount = 0
            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
                deletedCount++
            }

            Result.Success(deletedCount)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCapturedFacesCount(): Result<Int> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection.get().await()
            Result.Success(snapshot.size())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUnrecognizedFacesCount(): Result<Int> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("isRecognized", false)
                .get()
                .await()

            Result.Success(snapshot.size())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCoveredFacesCount(): Result<Int> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("detectionCategory", "face_covered")
                .get()
                .await()

            Result.Success(snapshot.size())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getDetectionStatistics(): Result<DetectionStats> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection.get().await()
            val faces = snapshot.documents.mapNotNull {
                it.toObject(CapturedFace::class.java)
            }

            val stats = DetectionStats(
                totalDetections = faces.size,
                recognizedCount = faces.count { it.isRecognized },
                unrecognizedCount = faces.count { !it.isRecognized },
                coveredFaceCount = faces.count { it.detectionCategory == "face_covered" },
                fullDetectionCount = faces.count { it.detectionCategory == "full_detection" },
                faceOnlyCount = faces.count { it.detectionCategory == "face_only" },
                distanceDetectionCount = faces.count { it.detectionCategory == "distance_detection" },
                poseDetectedCount = faces.count { it.poseDetected == true },
                yoloDetectedCount = faces.count { it.yoloDetected == true }
            )

            Result.Success(stats)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

data class DetectionStats(
    val totalDetections: Int = 0,
    val recognizedCount: Int = 0,
    val unrecognizedCount: Int = 0,
    val coveredFaceCount: Int = 0,
    val fullDetectionCount: Int = 0,
    val faceOnlyCount: Int = 0,
    val distanceDetectionCount: Int = 0,
    val poseDetectedCount: Int = 0,
    val yoloDetectedCount: Int = 0
) {
    val recognitionRate: Float
        get() = if (totalDetections > 0) {
            (recognizedCount.toFloat() / totalDetections) * 100
        } else 0f

    val coveredFaceRate: Float
        get() = if (totalDetections > 0) {
            (coveredFaceCount.toFloat() / totalDetections) * 100
        } else 0f
}