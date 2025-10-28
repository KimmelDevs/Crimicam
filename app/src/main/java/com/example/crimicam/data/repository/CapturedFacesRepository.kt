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

    suspend fun saveCapturedFace(
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap,
        faceFeatures: Map<String, Float>,
        isRecognized: Boolean = false,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float = 0f
    ): Result<CapturedFace> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val collection = getUserCapturedFacesCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            // Compress original image
            val compressedOriginal = ImageCompressor.compressBitmap(originalBitmap, maxWidth = 640, maxHeight = 640)
            val originalBase64 = ImageCompressor.bitmapToBase64(compressedOriginal, quality = 50)

            // Compress face crop
            val compressedFace = ImageCompressor.compressFaceCrop(croppedFaceBitmap)
            val faceBase64 = ImageCompressor.bitmapToBase64(compressedFace, quality = 70)

            // Generate new document ID
            val docId = collection.document().id

            val capturedFace = CapturedFace(
                id = docId,
                userId = userId,
                originalImageBase64 = originalBase64,
                croppedFaceBase64 = faceBase64,
                faceFeatures = faceFeatures,
                timestamp = System.currentTimeMillis(),
                isRecognized = isRecognized,
                matchedPersonId = matchedPersonId,
                matchedPersonName = matchedPersonName,
                confidence = confidence
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
}