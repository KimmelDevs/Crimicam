package com.example.crimicam.presentation.main.Home.Camera

import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.facerecognitionnetface.models.data.RecognitionMetrics
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalImageVectorUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DetectedFace(
    val boundingBox: RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float,
    val distance: Float = 0f,
    // Criminal-specific fields
    val isCriminal: Boolean = false,
    val dangerLevel: String? = null,
    val spoofDetected: Boolean = false
)

// Video recording states
data class RecordingState(
    val isRecording: Boolean = false,
    val recordingTime: String = "00:00",
    val outputUri: Uri? = null,
    val recordingError: String? = null,
    val recordingSaved: Boolean = false
)

data class CameraState(
    val isProcessing: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val scanningMode: ScanningMode = ScanningMode.IDLE,
    val statusMessage: String = "🔍 Scanning for faces...",
    val hasLocationPermission: Boolean = false,
    val knownPeople: List<KnownPerson> = emptyList(),
    val modelInitialized: Boolean = false,
    val peopleCount: Long = 0L,
    val criminalCount: Long = 0L,
    val recordingState: RecordingState = RecordingState()
)

enum class ScanningMode {
    IDLE,
    DETECTING,
    ANALYZING,
    IDENTIFIED,
    UNKNOWN,
    CRIMINAL_DETECTED
}

class CameraViewModel(
    val personUseCase: PersonUseCase,
    val imageVectorUseCase: ImageVectorUseCase,
    val criminalImageVectorUseCase: CriminalImageVectorUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)

    // Video recording properties
    private var recordingStartTime: Long = 0
    private val timerFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
    private var recordingJob: kotlinx.coroutines.Job? = null

    init {
        refreshPeopleCount()
        refreshCriminalCount()
    }

    // Criminal-specific methods
    fun refreshCriminalCount() {
        viewModelScope.launch {
            try {
                val criminals = criminalImageVectorUseCase.getAllCriminals()
                _state.value = _state.value.copy(criminalCount = criminals.size.toLong())
                Log.d("CameraViewModel", "Refreshed criminal count: ${criminals.size}")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to refresh criminal count", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Process frame for both known people AND criminals
     * Call this from your camera frame processor
     */
    fun processFrameForDetection(frameBitmap: android.graphics.Bitmap) {
        if (_state.value.isProcessing) return

        viewModelScope.launch {
            try {
                setProcessing(true)
                updateScanningMode(ScanningMode.DETECTING)

                // First, check for criminals (higher priority)
                val (criminalMetrics, criminalResults) =
                    criminalImageVectorUseCase.getNearestCriminalName(
                        frameBitmap = frameBitmap,
                        flatSearch = false,
                        confidenceThreshold = CriminalImageVectorUseCase.DEFAULT_CONFIDENCE_THRESHOLD
                    )

                val detectedFaces = mutableListOf<DetectedFace>()
                var hasCriminal = false

                // Process criminal results
                for (criminalResult in criminalResults) {
                    val isCriminal = criminalResult.criminalName != "Unknown"
                    if (isCriminal) hasCriminal = true

                    val boundingBox = RectF(
                        criminalResult.boundingBox.left.toFloat(),
                        criminalResult.boundingBox.top.toFloat(),
                        criminalResult.boundingBox.right.toFloat(),
                        criminalResult.boundingBox.bottom.toFloat()
                    )

                    detectedFaces.add(
                        DetectedFace(
                            boundingBox = boundingBox,
                            personId = criminalResult.criminalID,
                            personName = criminalResult.criminalName,
                            confidence = criminalResult.confidence,
                            isCriminal = isCriminal,
                            dangerLevel = criminalResult.dangerLevel,
                            spoofDetected = criminalResult.spoofResult?.isSpoof ?: false
                        )
                    )
                }

                // If no criminals detected, check for known people
                if (!hasCriminal && detectedFaces.isEmpty()) {
                    val (peopleMetrics, peopleResults) =
                        imageVectorUseCase.getNearestPersonName(
                            frameBitmap = frameBitmap,
                            flatSearch = false,
                            confidenceThreshold = 0.6f
                        )

                    for (personResult in peopleResults) {
                        val boundingBox = RectF(
                            personResult.boundingBox.left.toFloat(),
                            personResult.boundingBox.top.toFloat(),
                            personResult.boundingBox.right.toFloat(),
                            personResult.boundingBox.bottom.toFloat()
                        )

                        detectedFaces.add(
                            DetectedFace(
                                boundingBox = boundingBox,
                                personName = personResult.personName,
                                confidence = personResult.confidence,
                                isCriminal = false,
                                spoofDetected = personResult.spoofResult?.isSpoof ?: false
                            )
                        )
                    }

                    faceDetectionMetricsState.value = peopleMetrics
                } else {
                    faceDetectionMetricsState.value = criminalMetrics
                }

                // Update UI with results
                updateDetectedFacesWithCriminals(detectedFaces, hasCriminal)

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error processing frame", e)
                onRecognitionError(e.message ?: "Unknown error")
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun updateDetectedFacesWithCriminals(faces: List<DetectedFace>, hasCriminal: Boolean) {
        val newMode = when {
            hasCriminal -> ScanningMode.CRIMINAL_DETECTED
            faces.isEmpty() -> ScanningMode.IDLE
            faces.any { it.personName != null && !it.isCriminal } -> ScanningMode.IDENTIFIED
            else -> ScanningMode.UNKNOWN
        }

        val statusMessage = when (newMode) {
            ScanningMode.IDLE -> "🔍 Scanning for faces..."
            ScanningMode.DETECTING -> "👤 Detecting faces..."
            ScanningMode.ANALYZING -> "🔄 Analyzing..."
            ScanningMode.CRIMINAL_DETECTED -> {
                val criminals = faces.filter { it.isCriminal }
                val dangerLevels = criminals.map { it.dangerLevel }.distinct()
                "🚨 CRIMINAL DETECTED! ${criminals.size} criminal(s) - ${dangerLevels.joinToString()}"
            }
            ScanningMode.IDENTIFIED -> "✅ ${faces.count { it.personName != null }} face(s) identified"
            ScanningMode.UNKNOWN -> "❓ ${faces.size} unknown face(s)"
        }

        _state.value = _state.value.copy(
            detectedFaces = faces,
            scanningMode = newMode,
            statusMessage = statusMessage
        )

        Log.d("CameraViewModel", "Updated detected faces: ${faces.size}, criminals: ${faces.count { it.isCriminal }}, mode: $newMode")
    }

    // Video Recording Methods
    fun startRecording() {
        viewModelScope.launch {
            if (_state.value.recordingState.isRecording) {
                Log.w("CameraViewModel", "Already recording, ignoring start request")
                return@launch
            }

            try {
                Log.d("CameraViewModel", "Starting recording")

                _state.value = _state.value.copy(
                    recordingState = RecordingState(
                        isRecording = true,
                        recordingTime = "00:00",
                        outputUri = null,
                        recordingError = null,
                        recordingSaved = false
                    )
                )
                recordingStartTime = System.currentTimeMillis()
                startRecordingTimer()

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to start recording", e)
                _state.value = _state.value.copy(
                    recordingState = RecordingState(
                        isRecording = false,
                        recordingError = "Failed to start recording: ${e.message}"
                    )
                )
            }
        }
    }

    fun stopRecording() {
        if (_state.value.recordingState.isRecording) {
            Log.d("CameraViewModel", "Stopping recording")

            recordingJob?.cancel()
            recordingJob = null

            _state.value = _state.value.copy(
                recordingState = _state.value.recordingState.copy(
                    isRecording = false,
                    recordingTime = "00:00"
                )
            )
            recordingStartTime = 0

        } else {
            Log.w("CameraViewModel", "Stop recording called but not currently recording")
        }
    }

    fun updateRecordingTime() {
        if (_state.value.recordingState.isRecording && recordingStartTime > 0) {
            val elapsedTime = System.currentTimeMillis() - recordingStartTime
            val timeString = timerFormat.format(Date(elapsedTime))
            _state.value = _state.value.copy(
                recordingState = _state.value.recordingState.copy(
                    recordingTime = timeString
                )
            )
        }
    }

    private fun startRecordingTimer() {
        recordingJob?.cancel()

        recordingJob = viewModelScope.launch {
            while (_state.value.recordingState.isRecording && recordingStartTime > 0) {
                updateRecordingTime()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun onRecordingSaved(uri: Uri?) {
        Log.d("CameraViewModel", "Recording saved successfully: $uri")
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(
                outputUri = uri,
                recordingSaved = true
            )
        )
        updateStatusMessage("✅ Recording saved to Gallery")
    }

    fun onRecordingFailed(error: String) {
        Log.e("CameraViewModel", "Recording failed: $error")
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(
                isRecording = false,
                recordingError = error,
                recordingSaved = false
            )
        )
        recordingStartTime = 0
        recordingJob?.cancel()
        recordingJob = null
    }

    fun clearRecordingError() {
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(
                recordingError = null
            )
        )
    }

    fun clearRecordingSaved() {
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(
                recordingSaved = false
            )
        )
    }

    // Existing methods
    fun getNumPeople(): Long = _state.value.peopleCount

    fun refreshPeopleCount() {
        viewModelScope.launch {
            try {
                val count = personUseCase.refreshCount()
                _state.value = _state.value.copy(peopleCount = count)
                Log.d("CameraViewModel", "Refreshed people count: $count")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to refresh people count", e)
                e.printStackTrace()
            }
        }
    }

    fun updateDetectedFaces(faces: List<DetectedFace>) {
        val hasCriminal = faces.any { it.isCriminal }
        updateDetectedFacesWithCriminals(faces, hasCriminal)
    }

    fun updateScanningMode(mode: ScanningMode) {
        _state.value = _state.value.copy(scanningMode = mode)
        Log.d("CameraViewModel", "Scanning mode updated: $mode")
    }

    fun updateStatusMessage(message: String) {
        _state.value = _state.value.copy(statusMessage = message)
        Log.d("CameraViewModel", "Status message updated: $message")
    }

    fun updateKnownPeople(people: List<KnownPerson>) {
        _state.value = _state.value.copy(knownPeople = people)
        Log.d("CameraViewModel", "Known people updated: ${people.size}")
    }

    fun setModelInitialized(initialized: Boolean) {
        _state.value = _state.value.copy(modelInitialized = initialized)
        Log.d("CameraViewModel", "Model initialized: $initialized")
    }

    fun setProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isProcessing = processing)
        Log.d("CameraViewModel", "Processing state: $processing")
    }

    fun setLocationPermission(hasPermission: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = hasPermission)
        Log.d("CameraViewModel", "Location permission: $hasPermission")
    }

    fun clearDetectedFaces() {
        _state.value = _state.value.copy(
            detectedFaces = emptyList(),
            scanningMode = ScanningMode.IDLE,
            statusMessage = "🔍 Scanning for faces..."
        )
        Log.d("CameraViewModel", "Cleared detected faces")
    }

    fun onRecognitionError(error: String) {
        _state.value = _state.value.copy(
            isProcessing = false,
            scanningMode = ScanningMode.IDLE,
            statusMessage = "⚠️ $error"
        )
        Log.e("CameraViewModel", "Recognition error: $error")
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        recordingJob = null
        Log.d("CameraViewModel", "ViewModel cleared")
    }
}