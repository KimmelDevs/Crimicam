package com.example.crimicam.facerecognitionnetface.models.domain

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImageRecord
import com.example.crimicam.facerecognitionnetface.models.data.CriminalImagesVectorDB
import com.example.crimicam.facerecognitionnetface.models.data.RecognitionMetrics
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.FaceNet
import com.example.crimicam.facerecognitionnetface.models.domain.embeddings.MediapipeFaceDetector
import com.example.crimicam.facerecognitionnetface.models.domain.face_detection.FaceSpoofDetector
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

class CriminalImageVectorUseCase(
    private val mediapipeFaceDetector: MediapipeFaceDetector,
    private val criminalImagesVectorDB: CriminalImagesVectorDB,
    private val faceNet: FaceNet,
    private val faceSpoofDetector: FaceSpoofDetector
) {

    data class CriminalRecognitionResult(
        val criminalName: String,
        val criminalID: String,
        val dangerLevel: String,
        val boundingBox: Rect,
        val spoofResult: FaceSpoofDetector.FaceSpoofResult? = null,
        val confidence: Float = 0f
    )

    /**
     * Add a criminal's image to the database
     */
    suspend fun addImage(
        criminalID: String,
        criminalName: String,
        imageUri: Uri,
        dangerLevel: String = "LOW"
    ): Result<Boolean> {
        return try {
            val faceDetectionResult = mediapipeFaceDetector.getCroppedFace(imageUri)
            if (faceDetectionResult.isSuccess) {
                val croppedFaceBitmap = faceDetectionResult.getOrNull()!!
                val embedding = faceNet.getFaceEmbedding(croppedFaceBitmap)

                val criminalImageRecord = CriminalImageRecord(
                    criminalID = criminalID,
                    criminalName = criminalName,
                    faceEmbedding = embedding.toList(),
                    imageUri = imageUri.toString(),
                    dangerLevel = dangerLevel
                )

                criminalImagesVectorDB.addCriminalImageRecord(criminalImageRecord)
                Result.success(true)
            } else {
                Result.failure(faceDetectionResult.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add image with Bitmap directly
     */
    suspend fun addImage(
        criminalID: String,
        criminalName: String,
        faceBitmap: Bitmap,
        dangerLevel: String = "LOW"
    ): Result<Boolean> {
        return try {
            val embedding = faceNet.getFaceEmbedding(faceBitmap)

            val criminalImageRecord = CriminalImageRecord(
                criminalID = criminalID,
                criminalName = criminalName,
                faceEmbedding = embedding.toList(),
                imageUri = "",
                dangerLevel = dangerLevel
            )

            criminalImagesVectorDB.addCriminalImageRecord(criminalImageRecord)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Find nearest criminal match from frame with spoof detection
     * Returns metrics and list of detected criminals
     */
    suspend fun getNearestCriminalName(
        frameBitmap: Bitmap,
        flatSearch: Boolean = false,
        confidenceThreshold: Float = 0.6f
    ): Pair<RecognitionMetrics?, List<CriminalRecognitionResult>> {
        val (faceDetectionResult, t1) =
            measureTimedValue { mediapipeFaceDetector.getAllCroppedFaces(frameBitmap) }

        val criminalRecognitionResults = ArrayList<CriminalRecognitionResult>()
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

            // Find nearest criminal in database
            val (recognitionResult, t3) =
                measureTimedValue {
                    criminalImagesVectorDB.getNearestEmbeddingCriminalName(embedding, flatSearch)
                }
            avgT3 += t3.toLong(DurationUnit.MILLISECONDS)

            // Spoof detection
            val spoofResult = faceSpoofDetector.detectSpoof(frameBitmap, boundingBox)
            avgT4 += spoofResult.timeMillis

            if (recognitionResult == null) {
                criminalRecognitionResults.add(
                    CriminalRecognitionResult(
                        criminalName = "Unknown",
                        criminalID = "",
                        dangerLevel = "UNKNOWN",
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

            val criminalName = if (confidence > confidenceThreshold) {
                recognitionResult.criminalName
            } else {
                "Unknown"
            }

            criminalRecognitionResults.add(
                CriminalRecognitionResult(
                    criminalName = criminalName,
                    criminalID = if (confidence > confidenceThreshold) recognitionResult.criminalID else "",
                    dangerLevel = if (confidence > confidenceThreshold) recognitionResult.dangerLevel else "UNKNOWN",
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

        return Pair(metrics, criminalRecognitionResults)
    }

    /**
     * Find nearest criminal match from embedding (without frame/spoof detection)
     */
    suspend fun getNearestCriminalNameFromEmbedding(
        embedding: FloatArray,
        flatSearch: Boolean = false,
        confidenceThreshold: Float = 0.6f
    ): Pair<CriminalImageRecord?, Float> {
        val result = criminalImagesVectorDB.getNearestEmbeddingCriminalName(embedding, flatSearch)

        if (result != null) {
            val storedEmbedding = result.faceEmbedding.toFloatArray()
            val confidence = cosineSimilarity(embedding, storedEmbedding)

            return if (confidence > confidenceThreshold) {
                Pair(result, confidence)
            } else {
                Pair(null, confidence)
            }
        }

        return Pair(null, 0f)
    }

    /**
     * Get all face records for a specific criminal
     */
    suspend fun getCriminalImageRecords(criminalID: String): List<CriminalImageRecord> {
        return criminalImagesVectorDB.getCriminalRecordsByCriminalID(criminalID)
    }

    /**
     * Remove all images for a specific criminal
     */
    suspend fun removeImages(criminalID: String) {
        criminalImagesVectorDB.removeCriminalRecordsWithCriminalID(criminalID)
    }

    /**
     * Remove a specific criminal image record
     */
    suspend fun removeCriminalRecord(recordId: String) {
        criminalImagesVectorDB.removeCriminalRecord(recordId)
    }

    /**
     * Get criminal statistics
     */
    suspend fun getCriminalStatistics(criminalID: String): CriminalStatistics {
        val imageRecords = criminalImagesVectorDB.getCriminalRecordsByCriminalID(criminalID)
        return CriminalStatistics(
            totalImages = imageRecords.size,
            criminalID = criminalID,
            criminalName = imageRecords.firstOrNull()?.criminalName ?: "Unknown",
            dangerLevel = imageRecords.firstOrNull()?.dangerLevel ?: "LOW"
        )
    }

    /**
     * Clear all criminal face records
     */
    suspend fun clearAllCriminalRecords() {
        criminalImagesVectorDB.clearAllCriminalRecords()
    }

    /**
     * Get all unique criminal names
     */
    suspend fun getAllCriminals(): List<String> {
        return criminalImagesVectorDB.getAllUniqueCriminalNames()
    }

    /**
     * Calculate cosine similarity (returns value between -1 and 1, where 1 is perfect match)
     */
    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
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

    /**
     * Alternative: Euclidean distance (smaller distance = better match)
     */
    private fun euclideanDistance(x1: FloatArray, x2: FloatArray): Float {
        require(x1.size == x2.size) { "Embedding dimensions must match" }

        var sum = 0.0f
        for (i in x1.indices) {
            val diff = x1[i] - x2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    /**
     * Convert similarity to distance (for thresholding)
     */
    private fun similarityToDistance(similarity: Float): Float {
        return 1 - similarity
    }

    data class CriminalStatistics(
        val totalImages: Int,
        val criminalID: String,
        val criminalName: String,
        val dangerLevel: String
    )

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6f
        const val STRICT_CONFIDENCE_THRESHOLD = 0.75f
        const val LENIENT_CONFIDENCE_THRESHOLD = 0.4f
    }
}