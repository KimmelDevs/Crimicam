package com.example.crimicam.data.service

import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.util.Base64
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*

data class CaptureData(
    // Images (base64 encoded)
    val croppedFaceBase64: String,
    val fullFrameBase64: String,

    // Recognition info
    val isRecognized: Boolean,
    val isCriminal: Boolean,
    val matchedPersonId: String? = null,
    val matchedPersonName: String? = null,
    val confidence: Float,
    val dangerLevel: String? = null, // "LOW", "MEDIUM", "HIGH", "CRITICAL"

    // Location info
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,

    // Metadata
    val timestamp: Timestamp = Timestamp.now(),
    val deviceId: String? = null,
    val description: String = ""
)

class FirestoreCaptureService(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val geocoder = Geocoder(context, Locale.getDefault())

    companion object {
        private const val TAG = "FirestoreCaptureService"
        private const val COLLECTION_NAME = "captured_faces"
        private const val MAX_IMAGE_SIZE = 800 // Max width/height for compression
        private const val COMPRESSION_QUALITY = 85
    }

    /**
     * Save captured face to Firestore
     */
    suspend fun saveCapturedFace(
        croppedFace: Bitmap,
        fullFrame: Bitmap,
        isRecognized: Boolean,
        isCriminal: Boolean,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float,
        dangerLevel: String? = null,
        location: Location? = null,
        deviceId: String? = null
    ): Result<String> {
        return try {
            Log.d(TAG, "Starting to save captured face...")

            // Compress and encode images to base64
            val croppedFaceBase64 = bitmapToBase64(croppedFace)
            val fullFrameBase64 = bitmapToBase64(fullFrame)

            Log.d(TAG, "Images encoded to base64")

            // Get address from location
            val address = location?.let { getAddressFromLocation(it) }

            // Generate description
            val description = generateDescription(
                isRecognized = isRecognized,
                isCriminal = isCriminal,
                personName = matchedPersonName,
                dangerLevel = dangerLevel,
                address = address
            )

            Log.d(TAG, "Generated description: $description")

            // Create capture data
            val captureData = hashMapOf(
                "cropped_face_image_base64" to croppedFaceBase64,
                "full_frame_image_base64" to fullFrameBase64,
                "is_recognized" to isRecognized,
                "is_criminal" to isCriminal,
                "matched_person_id" to matchedPersonId,
                "matched_person_name" to matchedPersonName,
                "confidence" to confidence,
                "danger_level" to dangerLevel,
                "latitude" to location?.latitude,
                "longitude" to location?.longitude,
                "address" to address,
                "timestamp" to Timestamp.now(),
                "device_id" to deviceId,
                "description" to description
            )

            // Save to Firestore
            val docRef = db.collection(COLLECTION_NAME)
                .add(captureData)
                .await()

            Log.d(TAG, "Successfully saved capture with ID: ${docRef.id}")
            Result.success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving captured face", e)
            Result.failure(e)
        }
    }

    /**
     * Convert Bitmap to Base64 string with compression
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize if too large
        val resizedBitmap = if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
            val ratio = Math.min(
                MAX_IMAGE_SIZE.toFloat() / bitmap.width,
                MAX_IMAGE_SIZE.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // Compress to JPEG
        val byteArrayOutputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        // Encode to Base64
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Get address from Location coordinates
     */
    private suspend fun getAddressFromLocation(location: Location): String? {
        return try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                buildString {
                    address.thoroughfare?.let { append(it) }
                    address.subLocality?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.locality?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }
            } else {
                "${location.latitude}, ${location.longitude}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address", e)
            "${location.latitude}, ${location.longitude}"
        }
    }

    /**
     * Generate human-readable description
     */
    private fun generateDescription(
        isRecognized: Boolean,
        isCriminal: Boolean,
        personName: String?,
        dangerLevel: String?,
        address: String?
    ): String {
        return buildString {
            when {
                isCriminal && dangerLevel != null -> {
                    append("🚨 ALERT: ")
                    when (dangerLevel.uppercase()) {
                        "CRITICAL" -> append("CRITICAL THREAT - ")
                        "HIGH" -> append("HIGH DANGER - ")
                        "MEDIUM" -> append("MEDIUM RISK - ")
                        "LOW" -> append("LOW RISK - ")
                    }
                    append("Criminal ${personName ?: "suspect"} detected")
                }
                isRecognized && personName != null -> {
                    append("✅ Identified person: $personName")
                }
                else -> {
                    append("⚠️ Unknown individual detected")
                }
            }

            address?.let {
                append(" at $it")
            }

            append(" on ${java.text.SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(Date())}")
        }
    }

    /**
     * Batch save multiple captures
     */
    suspend fun saveBatchCaptures(captures: List<CaptureData>): Result<List<String>> {
        return try {
            val batch = db.batch()
            val documentIds = mutableListOf<String>()

            captures.forEach { capture ->
                val docRef = db.collection(COLLECTION_NAME).document()
                documentIds.add(docRef.id)

                val captureMap = hashMapOf(
                    "cropped_face_image_base64" to capture.croppedFaceBase64,
                    "full_frame_image_base64" to capture.fullFrameBase64,
                    "is_recognized" to capture.isRecognized,
                    "is_criminal" to capture.isCriminal,
                    "matched_person_id" to capture.matchedPersonId,
                    "matched_person_name" to capture.matchedPersonName,
                    "confidence" to capture.confidence,
                    "danger_level" to capture.dangerLevel,
                    "latitude" to capture.latitude,
                    "longitude" to capture.longitude,
                    "address" to capture.address,
                    "timestamp" to capture.timestamp,
                    "device_id" to capture.deviceId,
                    "description" to capture.description
                )

                batch.set(docRef, captureMap)
            }

            batch.commit().await()
            Log.d(TAG, "Successfully saved ${captures.size} captures in batch")
            Result.success(documentIds)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving batch captures", e)
            Result.failure(e)
        }
    }

    /**
     * Get recent captures
     */
    suspend fun getRecentCaptures(limit: Int = 20): Result<List<Map<String, Any?>>> {
        return try {
            val snapshot = db.collection(COLLECTION_NAME)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val captures = snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }

            Result.success(captures)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent captures", e)
            Result.failure(e)
        }
    }

    /**
     * Delete old captures (older than specified days)
     */
    suspend fun deleteOldCaptures(daysOld: Int = 30): Result<Int> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -daysOld)
            val cutoffDate = Timestamp(calendar.time)

            val snapshot = db.collection(COLLECTION_NAME)
                .whereLessThan("timestamp", cutoffDate)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()
            Log.d(TAG, "Deleted ${snapshot.size()} old captures")
            Result.success(snapshot.size())

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old captures", e)
            Result.failure(e)
        }
    }
}