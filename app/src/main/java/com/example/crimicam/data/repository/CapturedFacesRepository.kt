package com.example.crimicam.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.crimicam.util.ImageCompressor
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.*

class CapturedFacesRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun getUserCapturedFacesCollection() = auth.currentUser?.uid?.let { userId ->
        firestore.collection("users")
            .document(userId)
            .collection("captured_faces")
    }

    /**
     * Save captured face to Firestore with proper field names
     */
    suspend fun saveCapturedFace(
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap? = null,
        faceFeatures: Map<String, Float> = emptyMap(),
        isRecognized: Boolean = false,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float = 0f,
        latitude: Double? = null,
        longitude: Double? = null,
        locationAccuracy: Float? = null,
        address: String? = null
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val collection = firestore.collection("users")
                .document(userId)
                .collection("captured_faces")

            Log.d("CapturedFacesRepository", "🔄 Starting to save face for user: $userId")

            // Compress and convert images to base64
            val compressedOriginal = ImageCompressor.compressBitmap(
                originalBitmap,
                maxWidth = 640,
                maxHeight = 640
            )
            val originalBase64 = ImageCompressor.bitmapToBase64(compressedOriginal, quality = 50)
            Log.d("CapturedFacesRepository", "✅ Original image compressed: ${originalBase64?.take(50)}...")

            val croppedFaceBase64 = croppedFaceBitmap?.let { faceBitmap ->
                val compressedFace = ImageCompressor.compressFaceCrop(faceBitmap)
                val base64 = ImageCompressor.bitmapToBase64(compressedFace, quality = 70)
                Log.d("CapturedFacesRepository", "✅ Face crop compressed: ${base64?.take(50)}...")
                base64
            }

            // Create data map with field names that match what HomeViewModel expects
            val faceData = hashMapOf<String, Any>(
                "timestamp" to com.google.firebase.Timestamp.now(),
                "is_recognized" to isRecognized,
                "confidence" to confidence,
                "face_features" to faceFeatures,
                "user_id" to userId,
                "original_image_base64" to (originalBase64 ?: "")
            )

            // Add optional fields only if they have values
            matchedPersonId?.let { faceData["matched_person_id"] = it }
            matchedPersonName?.let { faceData["matched_person_name"] = it }
            latitude?.let { faceData["latitude"] = it }
            longitude?.let { faceData["longitude"] = it }
            locationAccuracy?.let { faceData["location_accuracy"] = it }
            address?.let { faceData["address"] = it }
            croppedFaceBase64?.let { faceData["cropped_face_base64"] = it }

            Log.d("CapturedFacesRepository", "📊 Face data keys: ${faceData.keys}")
            Log.d("CapturedFacesRepository", "🔍 Face features size: ${faceFeatures.size}")

            // Add the document and get the ID
            val docRef = collection.add(faceData).await()

            Log.d("CapturedFacesRepository", "✅ Face saved successfully with ID: ${docRef.id}")
            Result.Success(docRef.id)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Failed to save face: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun getCapturedFaces(limit: Int = 50): Result<List<Map<String, Any>>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                data.toMutableMap().apply {
                    put("id", doc.id) // Add document ID to the data
                }
            }
            Log.d("CapturedFacesRepository", "✅ Loaded ${faces.size} captured faces")
            Result.Success(faces)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Error getting captured faces: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun getCapturedFaceById(faceId: String): Result<Map<String, Any>?> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val document = collection.document(faceId).get().await()
            if (document.exists()) {
                val data = document.data?.toMutableMap()?.apply {
                    put("id", document.id)
                }
                Result.Success(data)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Error getting face by ID: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun getUnrecognizedFaces(limit: Int = 50): Result<List<Map<String, Any>>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("is_recognized", false)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                data.toMutableMap().apply {
                    put("id", doc.id)
                }
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Error getting unrecognized faces: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun getRecognizedFaces(limit: Int = 50): Result<List<Map<String, Any>>> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .whereEqualTo("is_recognized", true)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val faces = snapshot.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                data.toMutableMap().apply {
                    put("id", doc.id)
                }
            }
            Result.Success(faces)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Error getting recognized faces: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun deleteCapturedFace(faceId: String): Result<Unit> {
        return try {
            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("User not logged in"))

            collection.document(faceId).delete().await()
            Log.d("CapturedFacesRepository", "✅ Deleted face: $faceId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ Error deleting face: ${e.message}", e)
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
            Log.e("CapturedFacesRepository", "❌ Error getting faces count: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Debug function to check what's in Firestore
     */
    suspend fun debugCheckFirestoreData(): Result<List<Map<String, Any>>> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            Log.d("CapturedFacesRepository", "🔍 DEBUG: Checking Firestore for user: $userId")

            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("captured_faces")
                .get()
                .await()

            Log.d("CapturedFacesRepository", "📊 DEBUG: Found ${snapshot.documents.size} documents")

            val documents = snapshot.documents.map { doc ->
                Log.d("CapturedFacesRepository", "📄 DEBUG Document ${doc.id}:")
                doc.data?.forEach { (key, value) ->
                    Log.d("CapturedFacesRepository", "   $key: ${value.toString().take(100)}")
                }
                doc.data?.toMutableMap()?.apply {
                    put("id", doc.id)
                } ?: emptyMap()
            }

            Result.Success(documents)
        } catch (e: Exception) {
            Log.e("CapturedFacesRepository", "❌ DEBUG Error: ${e.message}", e)
            Result.Error(e)
        }
    }
}