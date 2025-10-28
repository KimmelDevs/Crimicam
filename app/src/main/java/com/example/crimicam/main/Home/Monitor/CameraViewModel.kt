package com.example.crimicam.main.Home.Monitor


import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.repository.CapturedFacesRepository
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.ml.FaceDetector
import com.example.crimicam.ml.FaceRecognizer
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraState(
    val isProcessing: Boolean = false,
    val knownPeople: List<KnownPerson> = emptyList(),
    val lastDetectionTime: Long = 0,
    val detectionCooldown: Long = 3000, // 3 seconds between captures
    val statusMessage: String = "Ready"
)

class CameraViewModel(
    private val capturedFacesRepository: CapturedFacesRepository = CapturedFacesRepository(),
    private val knownPeopleRepository: KnownPeopleRepository = KnownPeopleRepository(),
    private val faceDetector: FaceDetector = FaceDetector()
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    init {
        loadKnownPeople()
    }

    private fun loadKnownPeople() {
        viewModelScope.launch {
            when (val result = knownPeopleRepository.getKnownPeople()) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        knownPeople = result.data,
                        statusMessage = "Loaded ${result.data.size} known people"
                    )
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        statusMessage = "Error loading known people"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun processFrame(bitmap: Bitmap) {
        // Check cooldown
        val currentTime = System.currentTimeMillis()
        if (currentTime - _state.value.lastDetectionTime < _state.value.detectionCooldown) {
            return
        }

        if (_state.value.isProcessing) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                statusMessage = "Detecting faces..."
            )

            try {
                // Detect faces
                val faces = faceDetector.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        statusMessage = "No face detected"
                    )
                    return@launch
                }

                // Process first detected face
                val face = faces.first()
                val croppedFace = faceDetector.cropFace(bitmap, face)

                if (croppedFace == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        statusMessage = "Failed to crop face"
                    )
                    return@launch
                }

                // Extract features
                val faceFeatures = faceDetector.extractFaceFeatures(face)

                // Try to match with known people
                val (matchedPerson, confidence) = FaceRecognizer.findBestMatch(
                    faceFeatures,
                    _state.value.knownPeople
                )

                val isRecognized = matchedPerson != null
                val statusMessage = if (isRecognized) {
                    "Recognized: ${matchedPerson?.name} (${(confidence * 100).toInt()}%)"
                } else {
                    "Unknown person detected!"
                }

                // Save to Firestore
                when (capturedFacesRepository.saveCapturedFace(
                    originalBitmap = bitmap,
                    croppedFaceBitmap = croppedFace,
                    faceFeatures = faceFeatures,
                    isRecognized = isRecognized,
                    matchedPersonId = matchedPerson?.id,
                    matchedPersonName = matchedPerson?.name,
                    confidence = confidence
                )) {
                    is Result.Success -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            lastDetectionTime = currentTime,
                            statusMessage = "$statusMessage - Saved!"
                        )
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            statusMessage = "Error saving face"
                        )
                    }
                    is Result.Loading -> {}
                }

                // Reset status message after 2 seconds
                delay(2000)
                if (_state.value.statusMessage.contains("Saved!")) {
                    _state.value = _state.value.copy(statusMessage = "Monitoring...")
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    statusMessage = "Error: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
    }
}