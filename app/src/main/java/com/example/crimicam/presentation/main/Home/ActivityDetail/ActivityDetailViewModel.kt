package com.example.crimicam.presentation.main.Home.ActivityDetail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    /**
     * Load details for a specific capture
     */
    fun loadCaptureDetails(captureId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val doc = db.collection("captured_faces")
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
                Log.e("ActivityDetailViewModel", "Error loading capture details: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load capture"
                )
            }
        }
    }

    /**
     * Load all captured faces (most recent first)
     */
    fun loadAllCaptures(limit: Int = 50) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("captured_faces")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e("ActivityDetailViewModel", "Error parsing document ${doc.id}: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d("ActivityDetailViewModel", "Loaded ${captures.size} captured faces")
            } catch (e: Exception) {
                Log.e("ActivityDetailViewModel", "Error loading captures: ${e.message}", e)
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

        // Get cropped face image (base64)
        val croppedFaceBase64 = data["cropped_face_image_base64"] as? String

        // Recognition info
        val isRecognized = data["is_recognized"] as? Boolean ?: false
        val matchedPersonName = data["matched_person_name"] as? String
        val confidence = (data["confidence"] as? Number)?.toFloat() ?: 0f

        // Location info
        val latitude = (data["latitude"] as? Number)?.toDouble()
        val longitude = (data["longitude"] as? Number)?.toDouble()
        val address = data["address"] as? String

        return CapturedFaceData(
            id = id,
            croppedFaceBase64 = croppedFaceBase64,
            isRecognized = isRecognized,
            matchedPersonName = matchedPersonName,
            confidence = confidence,
            timestamp = timestampString,
            latitude = latitude,
            longitude = longitude,
            address = address
        )
    }

    /**
     * Load captures by recognition status
     */
    fun loadCapturesByStatus(isRecognized: Boolean, limit: Int = 50) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("captured_faces")
                    .whereEqualTo("is_recognized", isRecognized)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e("ActivityDetailViewModel", "Error parsing document: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d("ActivityDetailViewModel", "Loaded ${captures.size} ${if (isRecognized) "recognized" else "unknown"} captures")
            } catch (e: Exception) {
                Log.e("ActivityDetailViewModel", "Error loading captures: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load captures"
                )
            }
        }
    }

    /**
     * Load captures for a specific person
     */
    fun loadCapturesForPerson(personId: String, limit: Int = 50) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val querySnapshot = db.collection("captured_faces")
                    .whereEqualTo("matched_person_id", personId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()

                val captures = querySnapshot.documents.mapNotNull { doc ->
                    try {
                        parseCapturedFace(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        Log.e("ActivityDetailViewModel", "Error parsing document: ${e.message}")
                        null
                    }
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    captures = captures,
                    error = null
                )

                Log.d("ActivityDetailViewModel", "Loaded ${captures.size} captures for person $personId")
            } catch (e: Exception) {
                Log.e("ActivityDetailViewModel", "Error loading person captures: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load captures"
                )
            }
        }
    }
}