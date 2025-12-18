package com.example.crimicam.presentation.main.Home.ActivityDetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class ActivityDetailState(
    val isLoading: Boolean = false,
    val captures: List<CapturedFaceData> = emptyList(),
    val selectedCapture: CapturedFaceData? = null,
    val error: String? = null
)

class ActivityDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(ActivityDetailState())
    val state: StateFlow<ActivityDetailState> = _state.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "ActivityDetailViewModel"
    }

    /**
     * Load details for a specific capture from current user's subcollection
     */
    fun loadCaptureDetails(captureId: String) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please sign in to view capture details"
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val doc = db.collection("users")
                    .document(currentUser.uid)
                    .collection("captured_faces")
                    .document(captureId)
                    .get()
                    .await()

                if (doc.exists()) {
                    val capture = parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    _state.value = _state.value.copy(
                        isLoading = false,
                        captures = listOf(capture),
                        selectedCapture = capture,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Capture not found"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading capture details: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load capture"
                )
            }
        }
    }

    /**
     * Load all captured faces from current user's subcollection (most recent first)
     */
    fun loadAllCaptures(limit: Int = 50) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please sign in to view captures"
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("captured_faces")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing document ${doc.id}: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d(TAG, "Loaded ${captures.size} captured faces for user ${currentUser.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading captures: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load captures"
                )
            }
        }
    }

    /**
     * Parse Firestore document into CapturedFaceData
     */
    private fun parseCapturedFace(id: String, data: Map<String, Any>): CapturedFaceData {
        // Parse timestamp
        val timestamp = data["timestamp"] as? com.google.firebase.Timestamp
        val timestampString = timestamp?.toDate()?.let { date ->
            SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(date)
        } ?: "Unknown time"

        // Get images (base64)
        val croppedFaceBase64 = data["cropped_face_image_base64"] as? String
        val fullFrameBase64 = data["full_frame_image_base64"] as? String

        // Recognition info
        val isRecognized = data["is_recognized"] as? Boolean ?: false
        val isCriminal = data["is_criminal"] as? Boolean ?: false
        val matchedPersonId = data["matched_person_id"] as? String
        val matchedPersonName = data["matched_person_name"] as? String
        val confidence = (data["confidence"] as? Number)?.toFloat() ?: 0f
        val dangerLevel = data["danger_level"] as? String

        // Location info
        val latitude = (data["latitude"] as? Number)?.toDouble()
        val longitude = (data["longitude"] as? Number)?.toDouble()
        val address = data["address"] as? String

        // Device info
        val deviceId = data["device_id"] as? String
        val deviceModel = data["device_model"] as? String

        // Additional metadata
        val detectionTime = (data["detection_time_ms"] as? Number)?.toLong()

        return CapturedFaceData(
            id = id,
            croppedFaceBase64 = croppedFaceBase64,
            fullFrameBase64 = fullFrameBase64,
            isRecognized = isRecognized,
            isCriminal = isCriminal,
            matchedPersonId = matchedPersonId,
            matchedPersonName = matchedPersonName,
            confidence = confidence,
            dangerLevel = dangerLevel,
            timestamp = timestampString,
            latitude = latitude,
            longitude = longitude,
            address = address,
            deviceId = deviceId,
            deviceModel = deviceModel,
            detectionTimeMs = detectionTime
        )
    }

    /**
     * Load captures by recognition status
     */
    fun loadCapturesByStatus(isRecognized: Boolean, limit: Int = 50) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please sign in to view captures"
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("captured_faces")
                    .whereEqualTo("is_recognized", isRecognized)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing document: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d(TAG, "Loaded ${captures.size} ${if (isRecognized) "recognized" else "unknown"} captures")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading captures: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load captures"
                )
            }
        }
    }

    /**
     * Load criminal captures only
     */
    fun loadCriminalCaptures(limit: Int = 50) {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Please sign in to view captures"
                )
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("users")
                    .document(currentUser.uid)
                    .collection("captured_faces")
                    .whereEqualTo("is_criminal", true)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing document: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d(TAG, "Loaded ${captures.size} criminal captures")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading captures: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load captures"
                )
            }
        }
    }
}

data class CapturedFaceData(
    val id: String = "",
    val croppedFaceBase64: String? = null,
    val fullFrameBase64: String? = null,
    val isRecognized: Boolean = false,
    val isCriminal: Boolean = false,
    val matchedPersonId: String? = null,
    val matchedPersonName: String? = null,
    val confidence: Float = 0f,
    val dangerLevel: String? = null,
    val timestamp: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val deviceId: String? = null,
    val deviceModel: String? = null,
    val detectionTimeMs: Long? = null
)