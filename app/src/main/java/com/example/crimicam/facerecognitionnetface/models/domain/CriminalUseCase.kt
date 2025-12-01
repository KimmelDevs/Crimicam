package com.example.crimicam.facerecognitionnetface.models.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.example.crimicam.data.model.Criminal
import com.example.crimicam.facerecognitionnetface.models.data.CriminalDB
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.FaceImageRecord
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream

class CriminalUseCase(
    private val criminalDB: CriminalDB,
    private val criminalImagesVectorDB: CriminalImagesVectorDB,
    private val faceNet: FaceNet,
    private val mediapipeFaceDetector: MediapipeFaceDetector
) {
    // Cache for count to avoid repeated queries
    private var cachedCount: Long = 0L
    private var lastCountUpdate: Long = 0L
    private val countCacheTimeout = 5000L // 5 seconds

    /**
     * Add a new criminal to the database with face images
     * Returns the auto-generated criminal ID
     *
     * Note: This method processes images directly without Uri conversion.
     * For best results, use addCriminalWithEmbeddings if you have pre-processed embeddings.
     */
    suspend fun addCriminal(
        criminal: Criminal,
        imageBitmaps: List<Bitmap>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Add criminal to database
                val criminalId = criminalDB.addCriminal(criminal)

                // 2. Process and add face images
                var successfulImages = 0

                for (bitmap in imageBitmaps) {
                    try {
                        // Generate embedding directly from bitmap
                        // (skipping MediapipeFaceDetector since it expects Uri)
                        val embedding = faceNet.getFaceEmbedding(bitmap)

                        // Create face image record
                        val faceRecord = FaceImageRecord(
                            recordID = "",
                            personID = criminalId,
                            personName = criminal.fullName,
                            faceEmbedding = embedding.toList(),
                            createdAt = Timestamp.now()
                        )

                        // Add to criminal vector database
                        criminalImagesVectorDB.addFaceImageRecord(faceRecord)
                        successfulImages++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue with next image
                    }
                }

                // 3. Update criminal with actual image count
                if (successfulImages > 0) {
                    criminalDB.updateCriminal(
                        criminalId,
                        mapOf("imageCount" to successfulImages)
                    )
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
     * Add criminal with face detection and cropping (requires Context)
     * This version uses MediapipeFaceDetector properly with Uri
     */
    suspend fun addCriminalWithFaceDetection(
        context: Context,
        criminal: Criminal,
        imageUris: List<Uri>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Add criminal to database
                val criminalId = criminalDB.addCriminal(criminal)

                // 2. Process and add face images with proper face detection
                var successfulImages = 0

                for (uri in imageUris) {
                    try {
                        // Detect and crop face using MediapipeFaceDetector
                        val croppedFaceResult = mediapipeFaceDetector.getCroppedFace(uri)

                        if (croppedFaceResult.isFailure) {
                            continue
                        }

                        val croppedFace = croppedFaceResult.getOrNull() ?: continue

                        // Generate embedding using FaceNet
                        val embedding = faceNet.getFaceEmbedding(croppedFace)

                        // Create face image record
                        val faceRecord = FaceImageRecord(
                            recordID = "",
                            personID = criminalId,
                            personName = criminal.fullName,
                            faceEmbedding = embedding.toList(),
                            createdAt = Timestamp.now()
                        )

                        // Add to criminal vector database
                        criminalImagesVectorDB.addFaceImageRecord(faceRecord)
                        successfulImages++
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Continue with next image
                    }
                }

                // 3. Update criminal with actual image count
                if (successfulImages > 0) {
                    criminalDB.updateCriminal(
                        criminalId,
                        mapOf("imageCount" to successfulImages)
                    )
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
     * Helper: Convert Bitmap to temporary Uri
     * Use this if you need to process Bitmaps with MediapipeFaceDetector
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
     * Add criminal with Bitmap to Uri conversion (slower but uses proper face detection)
     */
    suspend fun addCriminalWithBitmaps(
        context: Context,
        criminal: Criminal,
        imageBitmaps: List<Bitmap>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // Convert bitmaps to temp URIs
                val tempUris = imageBitmaps.map { bitmap ->
                    bitmapToTempUri(context, bitmap)
                }

                // Use the Uri-based method
                val result = addCriminalWithFaceDetection(context, criminal, tempUris)

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
     * Add criminal with direct face embedding processing
     * (When you already have cropped faces and embeddings)
     * This is the fastest method if embeddings are pre-computed
     */
    suspend fun addCriminalWithEmbeddings(
        criminal: Criminal,
        faceEmbeddings: List<FloatArray>
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Add criminal to database
                val criminalId = criminalDB.addCriminal(criminal)

                // 2. Add face embeddings directly
                faceEmbeddings.forEach { embedding ->
                    val faceRecord = FaceImageRecord(
                        recordID = "",
                        personID = criminalId,
                        personName = criminal.fullName,
                        faceEmbedding = embedding.toList(),
                        createdAt = Timestamp.now()
                    )
                    criminalImagesVectorDB.addFaceImageRecord(faceRecord)
                }

                // 3. Update image count
                if (faceEmbeddings.isNotEmpty()) {
                    criminalDB.updateCriminal(
                        criminalId,
                        mapOf("imageCount" to faceEmbeddings.size)
                    )
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
     * Update an existing criminal's data
     */
    suspend fun updateCriminal(
        criminalId: String,
        updates: Map<String, Any>
    ) {
        withContext(Dispatchers.IO) {
            try {
                criminalDB.updateCriminal(criminalId, updates)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Add additional face images to an existing criminal (from Bitmaps)
     */
    suspend fun addCriminalImages(
        criminalId: String,
        imageBitmaps: List<Bitmap>
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val criminal = criminalDB.getCriminal(criminalId)
                    ?: throw Exception("Criminal not found")

                var successfulImages = 0

                for (bitmap in imageBitmaps) {
                    try {
                        // Generate embedding directly
                        val embedding = faceNet.getFaceEmbedding(bitmap)

                        val faceRecord = FaceImageRecord(
                            recordID = "",
                            personID = criminalId,
                            personName = criminal.fullName,
                            faceEmbedding = embedding.toList(),
                            createdAt = Timestamp.now()
                        )

                        criminalImagesVectorDB.addFaceImageRecord(faceRecord)
                        criminalDB.incrementImageCount(criminalId)
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
     * Add additional face images to an existing criminal (from Uris with face detection)
     */
    suspend fun addCriminalImagesFromUris(
        context: Context,
        criminalId: String,
        imageUris: List<Uri>
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val criminal = criminalDB.getCriminal(criminalId)
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

                        val faceRecord = FaceImageRecord(
                            recordID = "",
                            personID = criminalId,
                            personName = criminal.fullName,
                            faceEmbedding = embedding.toList(),
                            createdAt = Timestamp.now()
                        )

                        criminalImagesVectorDB.addFaceImageRecord(faceRecord)
                        criminalDB.incrementImageCount(criminalId)
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
     * Increment the image count for a criminal atomically
     */
    suspend fun incrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                criminalDB.incrementImageCount(criminalId)
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
                criminalDB.decrementImageCount(criminalId)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Get a single criminal by ID
     */
    suspend fun getCriminal(criminalId: String): Criminal? {
        return withContext(Dispatchers.IO) {
            try {
                criminalDB.getCriminal(criminalId)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all criminals as a Flow (reactive)
     */
    fun getAll(): Flow<List<Criminal>> = criminalDB.getAll()

    /**
     * Get all criminals as a list (one-time fetch)
     */
    suspend fun getAllOnce(): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                val criminals = criminalDB.getAllOnce()

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
                val count = criminalDB.getCount()
                cachedCount = count
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
                criminalImagesVectorDB.removeFaceRecordsWithPersonID(criminalId)

                // Remove criminal record
                criminalDB.removeCriminal(criminalId)

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
    suspend fun searchByName(firstName: String, lastName: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                criminalDB.searchByName(firstName, lastName)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by status
     */
    suspend fun searchByStatus(status: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                criminalDB.searchByStatus(status)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by risk level
     */
    suspend fun searchByRiskLevel(riskLevel: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                criminalDB.searchByRiskLevel(riskLevel)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Check if a criminal with the given name exists
     */
    suspend fun existsByName(firstName: String, lastName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                criminalDB.existsByName(firstName, lastName)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Get face images for a criminal
     */
    suspend fun getCriminalFaceImages(criminalId: String): List<FaceImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                criminalImagesVectorDB.getFaceRecordsByPersonID(criminalId)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Clear all criminals and their face images
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                // Get all criminal IDs first
                val criminals = criminalDB.getAllOnce()

                // Remove all face images for each criminal
                criminals.forEach { criminal ->
                    criminalImagesVectorDB.removeFaceRecordsWithPersonID(criminal.id)
                }

                // Clear all criminals
                criminalDB.clearAll()

                invalidateCountCache()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Invalidate the cached count
     */
    private fun invalidateCountCache() {
        lastCountUpdate = 0L
    }
}