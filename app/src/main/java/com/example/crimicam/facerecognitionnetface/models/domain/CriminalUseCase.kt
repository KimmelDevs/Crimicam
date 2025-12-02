package com.example.crimicam.facerecognitionnetface.models.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImageRecord
import com.example.crimicam.facerecognitionnetface.models.data.CriminalRecord
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream

@Single
class CriminalUseCase(
    private val criminalImagesVectorDB: CriminalImagesVectorDB,
    private val faceNet: FaceNet,
    private val mediapipeFaceDetector: MediapipeFaceDetector
) {
    private val firestore = FirebaseFirestore.getInstance()

    // Top-level criminals collection
    private fun getCriminalsCollection(): CollectionReference {
        return firestore.collection("criminals")
    }

    // Cache for count
    private var cachedCount: Long = 0L
    private var lastCountUpdate: Long = 0L
    private val countCacheTimeout = 5000L // 5 seconds

    /**
     * Add a new criminal with face images
     */
    suspend fun addCriminal(
        criminal: CriminalRecord,
        imageUris: List<Uri>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Create criminal with timestamps
                val criminalWithTimestamps = criminal.copy(
                    criminalID = "", // Will be auto-generated
                    createdAt = Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                // 2. Add to Firestore
                val docRef = getCriminalsCollection().add(criminalWithTimestamps).await()
                val criminalId = docRef.id

                // 3. Process and add face images
                var successfulImages = 0

                for (uri in imageUris) {
                    try {
                        // Detect and crop face
                        val croppedFaceResult = mediapipeFaceDetector.getCroppedFace(uri)
                        if (croppedFaceResult.isFailure) continue

                        val croppedFace = croppedFaceResult.getOrNull() ?: continue

                        // Generate embedding
                        val embedding = faceNet.getFaceEmbedding(croppedFace)

                        // Create criminal image record
                        val imageRecord = CriminalImageRecord(
                            recordID = "",
                            criminalID = criminalId,
                            criminalName = criminal.criminalName,
                            faceEmbedding = embedding.toList(),
                            imageUri = uri.toString(),
                            dangerLevel = criminal.dangerLevel,
                            createdAt = Timestamp.now()
                        )

                        // Add to criminal vector database
                        criminalImagesVectorDB.addCriminalImageRecord(imageRecord)
                        successfulImages++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue with next image
                    }
                }

                // 4. Update criminal with actual image count
                if (successfulImages > 0) {
                    getCriminalsCollection().document(criminalId).update(
                        mapOf(
                            "numImages" to successfulImages.toLong(), // Changed from imageCount to numImages
                            "lastUpdated" to Timestamp.now()
                        )
                    ).await()
                }

                invalidateCountCache()
                criminalId
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Add criminal with Bitmaps instead of Uris
     */
    suspend fun addCriminalWithBitmaps(
        context: Context,
        criminal: CriminalRecord,
        imageBitmaps: List<Bitmap>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Convert bitmaps to temp URIs
                val tempUris = imageBitmaps.map { bitmap ->
                    bitmapToTempUri(context, bitmap)
                }

                // Use the Uri-based method
                val result = addCriminal(criminal, tempUris)

                // Clean up temp files
                tempUris.forEach { uri ->
                    try {
                        val file = File(uri.path ?: "")
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                result
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Add additional images to an existing criminal
     */
    suspend fun addImagesToCriminal(
        context: Context,
        criminalId: String,
        imageUris: List<Uri>
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val criminal = getCriminal(criminalId)
                    ?: throw Exception("Criminal not found")

                var successfulImages = 0

                for (uri in imageUris) {
                    try {
                        // Detect and crop face
                        val croppedFaceResult = mediapipeFaceDetector.getCroppedFace(uri)
                        if (croppedFaceResult.isFailure) continue

                        val croppedFace = croppedFaceResult.getOrNull() ?: continue

                        // Generate embedding
                        val embedding = faceNet.getFaceEmbedding(croppedFace)

                        val imageRecord = CriminalImageRecord(
                            recordID = "",
                            criminalID = criminalId,
                            criminalName = criminal.criminalName,
                            faceEmbedding = embedding.toList(),
                            imageUri = uri.toString(),
                            dangerLevel = criminal.dangerLevel,
                            createdAt = Timestamp.now()
                        )

                        criminalImagesVectorDB.addCriminalImageRecord(imageRecord)

                        // Update image count
                        incrementImageCount(criminalId)

                        successfulImages++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                successfulImages
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Update a criminal's data
     */
    suspend fun updateCriminal(
        criminalId: String,
        updates: Map<String, Any>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val updatesWithTimestamp = updates.toMutableMap()
                updatesWithTimestamp["lastUpdated"] = Timestamp.now()

                getCriminalsCollection()
                    .document(criminalId)
                    .update(updatesWithTimestamp)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Increment the image count for a criminal atomically
     */
    suspend fun incrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .update(
                        mapOf(
                            "numImages" to FieldValue.increment(1), // Changed from imageCount to numImages
                            "lastUpdated" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Decrement the image count for a criminal atomically
     */
    suspend fun decrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .update(
                        mapOf(
                            "numImages" to FieldValue.increment(-1), // Changed from imageCount to numImages
                            "lastUpdated" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Get a single criminal by ID
     */
    suspend fun getCriminal(criminalId: String): CriminalRecord? {
        return withContext(Dispatchers.IO) {
            try {
                val document = getCriminalsCollection()
                    .document(criminalId)
                    .get()
                    .await()

                document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all criminals as a Flow (reactive)
     */
    fun getAll(): Flow<List<CriminalRecord>> = callbackFlow {
        val listenerRegistration = getCriminalsCollection()
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val criminals = snapshot.documents.mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }

                    // Update cached count
                    cachedCount = criminals.size.toLong()
                    lastCountUpdate = System.currentTimeMillis()

                    trySend(criminals)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get all criminals as a list (one-time fetch)
     */
    suspend fun getAllOnce(): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getCriminalsCollection().get().await()
                val criminals = snapshot.documents.mapNotNull { document ->
                    document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                }

                // Update cached count
                cachedCount = criminals.size.toLong()
                lastCountUpdate = System.currentTimeMillis()

                criminals
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get the total count of criminals in the database
     * Uses caching to avoid repeated queries
     */
    fun getCount(): Long {
        val currentTime = System.currentTimeMillis()

        // Return cached count if it's still valid
        if (cachedCount > 0 && (currentTime - lastCountUpdate) < countCacheTimeout) {
            return cachedCount
        }

        // If cache is invalid, return the cached value for now
        return cachedCount
    }

    /**
     * Force refresh the count from Firestore
     */
    suspend fun refreshCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getCriminalsCollection().get().await()
                cachedCount = snapshot.size().toLong()
                lastCountUpdate = System.currentTimeMillis()
                cachedCount
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Remove a criminal and all associated face images
     */
    suspend fun removeCriminal(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                // Remove all face images
                criminalImagesVectorDB.removeCriminalRecordsWithCriminalID(criminalId)

                // Remove criminal record
                getCriminalsCollection()
                    .document(criminalId)
                    .delete()
                    .await()

                invalidateCountCache()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Search for criminals by name
     */
    suspend fun searchByName(criminalName: String): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereEqualTo("criminalName", criminalName)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by danger level
     */
    suspend fun searchByDangerLevel(dangerLevel: String): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereEqualTo("dangerLevel", dangerLevel)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Check if a criminal with the given name exists
     */
    suspend fun existsByName(criminalName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val results = searchByName(criminalName)
                results.isNotEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Get image records for a criminal
     */
    suspend fun getCriminalImageRecords(criminalId: String): List<CriminalImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                criminalImagesVectorDB.getCriminalRecordsByCriminalID(criminalId)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get criminal statistics
     */
    suspend fun getCriminalStatistics(criminalId: String): CriminalStatistics {
        val criminal = getCriminal(criminalId)
        val imageRecords = getCriminalImageRecords(criminalId)

        return CriminalStatistics(
            criminalID = criminalId,
            criminalName = criminal?.criminalName ?: "Unknown",
            dangerLevel = criminal?.dangerLevel ?: "LOW",
            totalImages = imageRecords.size,
            description = criminal?.description ?: "",
            status = if (criminal?.isActive == true) "Active" else "Inactive"
        )
    }

    /**
     * Clear all criminals and their images
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                // Get all criminal IDs first
                val criminals = getAllOnce()

                // Remove all images for each criminal
                criminals.forEach { criminal ->
                    criminalImagesVectorDB.removeCriminalRecordsWithCriminalID(criminal.criminalID)
                }

                // Clear all criminals
                val snapshot = getCriminalsCollection().get().await()
                val batches = snapshot.documents.chunked(500)

                batches.forEach { batch ->
                    val deleteBatch = firestore.batch()
                    batch.forEach { document ->
                        deleteBatch.delete(document.reference)
                    }
                    deleteBatch.commit().await()
                }

                invalidateCountCache()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Helper: Convert Bitmap to temporary Uri
     */
    private suspend fun bitmapToTempUri(context: Context, bitmap: Bitmap): Uri {
        return withContext(Dispatchers.IO) {
            val tempFile = File.createTempFile("temp_criminal_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            tempFile.toUri()
        }
    }

    /**
     * Invalidate the cached count
     */
    private fun invalidateCountCache() {
        lastCountUpdate = 0L
    }

    data class CriminalStatistics(
        val criminalID: String,
        val criminalName: String,
        val dangerLevel: String,
        val totalImages: Int,
        val description: String,
        val status: String
    )
}