package com.example.crimicam.data.service

import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.util.Base64
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.*

class FirestoreCaptureService(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val geocoder = Geocoder(context, Locale.getDefault())

    companion object {
        private const val TAG = "FirestoreCaptureService"

        // Main collections
        private const val USERS_COLLECTION = "users"
        private const val CRIMINALS_COLLECTION = "criminals"

        // Subcollections
        private const val CAPTURES_SUBCOLLECTION = "captured_faces"
        private const val LOCATIONS_SUBCOLLECTION = "location_history"

        private const val MAX_IMAGE_SIZE = 800
        private const val COMPRESSION_QUALITY = 85
    }

    /**
     * Save captured face to Firestore under user's subcollection
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
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e(TAG, "No authenticated user")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d(TAG, "Starting to save captured face for user: ${currentUser.uid}")

            // Compress and encode images
            val croppedFaceBase64 = bitmapToBase64(croppedFace)
            val fullFrameBase64 = bitmapToBase64(fullFrame)

            Log.d(TAG, "Images encoded to base64")

            // Get address and create GeoPoint
            val address = location?.let { getAddressFromLocation(it) }
            val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }

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
                "location" to geoPoint,
                "latitude" to location?.latitude,
                "longitude" to location?.longitude,
                "address" to address,
                "timestamp" to Timestamp.now(),
                "device_id" to deviceId,
                "description" to description,
                "user_id" to currentUser.uid
            )

            // Save to user's captured_faces subcollection
            val captureRef = db.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(CAPTURES_SUBCOLLECTION)
                .add(captureData)
                .await()

            Log.d(TAG, "Capture saved with ID: ${captureRef.id}")

            // If it's a criminal, update their location tracking
            if (isCriminal && matchedPersonId != null && geoPoint != null) {
                updateCriminalLocation(
                    criminalId = matchedPersonId,
                    criminalName = matchedPersonName ?: "Unknown",
                    location = geoPoint,
                    address = address,
                    dangerLevel = dangerLevel,
                    captureId = captureRef.id
                )
            }

            Result.success(captureRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "Error saving captured face", e)
            Result.failure(e)
        }
    }

    /**
     * Update criminal's location in the criminals collection for map tracking
     */
    private suspend fun updateCriminalLocation(
        criminalId: String,
        criminalName: String,
        location: GeoPoint,
        address: String?,
        dangerLevel: String?,
        captureId: String
    ) {
        try {
            val criminalRef = db.collection(CRIMINALS_COLLECTION).document(criminalId)

            // Create/update criminal document with last location
            val criminalData = hashMapOf(
                "criminal_id" to criminalId,
                "criminal_name" to criminalName,
                "last_location" to location,
                "last_latitude" to location.latitude,
                "last_longitude" to location.longitude,
                "last_address" to address,
                "last_seen" to Timestamp.now(),
                "danger_level" to dangerLevel,
                "last_capture_id" to captureId,
                "total_sightings" to FieldValue.increment(1)
            )

            criminalRef.set(criminalData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Add to location history subcollection
            val locationHistoryData = hashMapOf(
                "location" to location,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to address,
                "timestamp" to Timestamp.now(),
                "capture_id" to captureId
            )

            criminalRef.collection(LOCATIONS_SUBCOLLECTION)
                .add(locationHistoryData)
                .await()

            Log.d(TAG, "Updated criminal location for: $criminalName at $address")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating criminal location", e)
        }
    }

    /**
     * Get all criminal locations for map display
     */
    suspend fun getCriminalLocations(): Result<List<CriminalLocation>> {
        return try {
            val snapshot = db.collection(CRIMINALS_COLLECTION)
                .get()
                .await()

            val locations = snapshot.documents.mapNotNull { doc ->
                try {
                    val location = doc.getGeoPoint("last_location")
                    val latitude = doc.getDouble("last_latitude")
                    val longitude = doc.getDouble("last_longitude")

                    if (location != null || (latitude != null && longitude != null)) {
                        CriminalLocation(
                            criminalId = doc.getString("criminal_id") ?: doc.id,
                            criminalName = doc.getString("criminal_name") ?: "Unknown",
                            latitude = latitude ?: location?.latitude ?: 0.0,
                            longitude = longitude ?: location?.longitude ?: 0.0,
                            address = doc.getString("last_address"),
                            lastSeen = doc.getTimestamp("last_seen"),
                            dangerLevel = doc.getString("danger_level"),
                            totalSightings = doc.getLong("total_sightings")?.toInt() ?: 0
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing criminal location", e)
                    null
                }
            }

            Result.success(locations)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting criminal locations", e)
            Result.failure(e)
        }
    }

    /**
     * Get location history for a specific criminal
     */
    suspend fun getCriminalLocationHistory(criminalId: String): Result<List<LocationHistory>> {
        return try {
            val snapshot = db.collection(CRIMINALS_COLLECTION)
                .document(criminalId)
                .collection(LOCATIONS_SUBCOLLECTION)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val history = snapshot.documents.mapNotNull { doc ->
                try {
                    val location = doc.getGeoPoint("location")
                    val latitude = doc.getDouble("latitude")
                    val longitude = doc.getDouble("longitude")

                    if (location != null || (latitude != null && longitude != null)) {
                        LocationHistory(
                            latitude = latitude ?: location?.latitude ?: 0.0,
                            longitude = longitude ?: location?.longitude ?: 0.0,
                            address = doc.getString("address"),
                            timestamp = doc.getTimestamp("timestamp"),
                            captureId = doc.getString("capture_id")
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing location history", e)
                    null
                }
            }

            Result.success(history)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location history", e)
            Result.failure(e)
        }
    }

    /**
     * Get user's captured faces
     */
    suspend fun getUserCaptures(limit: Int = 50): Result<List<Map<String, Any?>>> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val snapshot = db.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .collection(CAPTURES_SUBCOLLECTION)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val captures = snapshot.documents.map { doc ->
                doc.data?.plus("id" to doc.id) ?: emptyMap()
            }

            Result.success(captures)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user captures", e)
            Result.failure(e)
        }
    }

    /**
     * Convert Bitmap to Base64 string with compression
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
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

        val byteArrayOutputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

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
}

// Data classes for criminal tracking
data class CriminalLocation(
    val criminalId: String,
    val criminalName: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val lastSeen: Timestamp?,
    val dangerLevel: String?,
    val totalSightings: Int
)

data class LocationHistory(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val timestamp: Timestamp?,
    val captureId: String?
)