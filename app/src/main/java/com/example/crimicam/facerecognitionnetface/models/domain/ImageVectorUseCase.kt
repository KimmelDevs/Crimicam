package com.example.crimicam.facerecognitionnetface.models.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.crimicam.facerecognitionnetface.models.data.FaceImageRecord
import com.example.crimicam.facerecognitionnetface.models.data.ImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.RecognitionMetrics
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class ImageVectorUseCase(
    private val mediapipeFaceDetector: MediapipeFaceDetector,
    private val faceSpoofDetector: FaceSpoofDetector,
    private val imagesVectorDB: ImagesVectorDB,
    private val faceNet: FaceNet,
) {
    data class FaceRecognitionResult(
        val personName: String,
        val boundingBox: Rect,
        val spoofResult: FaceSpoofDetector.FaceSpoofResult? = null,
        val confidence: Float = 0f
    )

    // ✅ Helper method to convert Bitmap to Base64
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        // Compress to JPEG with 80% quality to reduce size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }

    // Add the person's image to the database with URI input
    suspend fun addImage(
        personID: String,
        personName: String,
        imageUri: Uri,
    ): Result<Boolean> {
        return try {
            val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
            if (faceDetectionResult.isSuccess) {
                val croppedFaceBitmap = faceDetectionResult.getOrNull()!!
                val embedding = faceNet.getFaceEmbedding(croppedFaceBitmap)

                // ✅ Convert bitmap to base64
                val base64Image = bitmapToBase64(croppedFaceBitmap)

                // Create FaceImageRecord with embedding as List<Float>
                val faceRecord = FaceImageRecord(
                    personID = personID,
                    personName = personName,
                    imageUri = imageUri.toString(),
                    base64Image = base64Image, // ✅ Store base64
                    faceEmbedding = embedding.toList() // Convert FloatArray to List<Float>
                )

                imagesVectorDB.addFaceImageRecord(faceRecord)
                Result.success(true)
            } else {
                Result.failure(faceDetectionResult.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add image with Bitmap directly (alternative method)
    suspend fun addImage(
        personID: String,
        personName: String,
        faceBitmap: Bitmap,
    ): Result<Boolean> {
        return try {
            val embedding = faceNet.getFaceEmbedding(faceBitmap)

            // ✅ Convert bitmap to base64
            val base64Image = bitmapToBase64(faceBitmap)

            // Create FaceImageRecord with embedding as List<Float>
            val faceRecord = FaceImageRecord(
                personID = personID,
                personName = personName,
                imageUri = "", // No URI for direct bitmap
                base64Image = base64Image, // ✅ Store base64
                faceEmbedding = embedding.toList() // Convert FloatArray to List<Float>
            )

            imagesVectorDB.addFaceImageRecord(faceRecord)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all image base64 strings for a specific person
     * @param personID The person's ID
     * @return List of base64 encoded images
     */
    suspend fun getPersonImages(personID: String): List<String> {
        return try {
            val faceRecords = imagesVectorDB.getFaceRecordsByPersonID(personID)
            // Extract base64Image from each record, filter out empty ones
            faceRecords.mapNotNull { record ->
                record.base64Image?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageVectorUseCase", "Error getting person images: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get all image URIs for a specific person (alternative method)
     * @param personID The person's ID
     * @return List of image URIs
     */
    suspend fun getPersonImageUris(personID: String): List<String> {
        return try {
            val faceRecords = imagesVectorDB.getFaceRecordsByPersonID(personID)
            // Extract imageUri from each record, filter out empty ones
            faceRecords.mapNotNull { record ->
                record.imageUri.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageVectorUseCase", "Error getting person image URIs: ${e.message}")
            emptyList()
        }
    }

    // From the given frame, return the name of the person by performing face recognition
    suspend fun getNearestPersonName(
        frameBitmap: Bitmap,
        flatSearch: Boolean = false,
        confidenceThreshold: Float = 0.4f
    ): Pair<RecognitionMetrics?, List<FaceRecognitionResult>> {
        val (faceDetectionResult, t1) =
            measureTimedValue { mediapipeFaceDetector.getAllCroppedFaces(frameBitmap) }

        val faceRecognitionResults = ArrayList<FaceRecognitionResult>()
        var avgT2 = 0L
        var avgT3 = 0L
        var avgT4 = 0L

        for (result in faceDetectionResult) {
            val (croppedBitmap, boundingBox) = result

            // Get face embedding
            val (embedding, t2) = measureTimedValue {
                faceNet.getFaceEmbedding(croppedBitmap)
            }
            avgT2 += t2.toLong(DurationUnit.MILLISECONDS)

            // Find nearest person in database
            val (recognitionResult, t3) =
                measureTimedValue {
                    imagesVectorDB.getNearestEmbeddingPersonName(embedding, flatSearch)
                }
            avgT3 += t3.toLong(DurationUnit.MILLISECONDS)

            // Spoof detection
            val spoofResult = faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
            avgT4 += spoofResult.timeMillis

            if (recognitionResult == null) {
                faceRecognitionResults.add(
                    FaceRecognitionResult(
                        personName = "Unknown",
                        boundingBox = boundingBox,
                        spoofResult = spoofResult,
                        confidence = 0f
                    )
                )
                continue
            }

            // Calculate confidence score (cosine similarity)
            val storedEmbeddingArray = recognitionResult.faceEmbedding.toFloatArray()
            val confidence = cosineSimilarity(embedding, storedEmbeddingArray)

            val personName = if (confidence > confidenceThreshold) {
                recognitionResult.personName
            } else {
                "Unknown"
            }

            faceRecognitionResults.add(
                FaceRecognitionResult(
                    personName = personName,
                    boundingBox = boundingBox,
                    spoofResult = spoofResult,
                    confidence = confidence
                )
            )
        }

        val metrics =
            if (faceDetectionResult.isNotEmpty()) {
                RecognitionMetrics(
                    timeFaceDetection = t1.toLong(DurationUnit.MILLISECONDS),
                    timeFaceEmbedding = avgT2 / faceDetectionResult.size,
                    timeVectorSearch = avgT3 / faceDetectionResult.size,
                    timeFaceSpoofDetection = avgT4 / faceDetectionResult.size,
                    totalFacesDetected = faceDetectionResult.size
                )
            } else {
                null
            }

        return Pair(metrics, faceRecognitionResults)
    }

    // Get all face records for a specific person
    suspend fun getFaceRecordsForPerson(personID: String): List<FaceImageRecord> {
        return imagesVectorDB.getFaceRecordsByPersonID(personID)
    }

    // Remove all images for a specific person
    suspend fun removeImages(personID: String) {
        imagesVectorDB.removeFaceRecordsWithPersonID(personID)
    }

    // Remove a specific face record
    suspend fun removeFaceRecord(recordId: String) {
        imagesVectorDB.removeFaceRecord(recordId)
    }

    // Get person statistics
    suspend fun getPersonStatistics(personID: String): PersonStatistics {
        val faceRecords = imagesVectorDB.getFaceRecordsByPersonID(personID)
        return PersonStatistics(
            totalImages = faceRecords.size,
            personID = personID,
            personName = faceRecords.firstOrNull()?.personName ?: "Unknown"
        )
    }

    // Clear all face records
    suspend fun clearAllFaceRecords() {
        imagesVectorDB.clearAllFaceRecords()
    }

    // Get all unique persons
    suspend fun getAllPersons(): List<String> {
        return imagesVectorDB.getAllUniquePersonNames()
    }

    // Calculate cosine similarity (returns value between -1 and 1, where 1 is perfect match)
    private fun cosineSimilarity(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        require(x1.size == x2.size) { "Embedding dimensions must match" }

        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in x1.indices) {
            dotProduct += x1[i] * x2[i]
            norm1 += x1[i].pow(2)
            norm2 += x2[i].pow(2)
        }

        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)

        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (norm1 * norm2)
        } else {
            0f
        }
    }

    // Alternative: Euclidean distance (smaller distance = better match)
    private fun euclideanDistance(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        require(x1.size == x2.size) { "Embedding dimensions must match" }

        var sum = 0.0f
        for (i in x1.indices) {
            val diff = x1[i] - x2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    // Convert similarity to distance (for thresholding)
    private fun similarityToDistance(similarity: Float): Float {
        return 1 - similarity
    }

    data class PersonStatistics(
        val totalImages: Int,
        val personID: String,
        val personName: String
    )

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6f // Adjust based on your model
        const val STRICT_CONFIDENCE_THRESHOLD = 0.75f
        const val LENIENT_CONFIDENCE_THRESHOLD = 0.4f
    }
}