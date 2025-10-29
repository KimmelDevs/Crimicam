package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.repository.CapturedFacesRepository
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.ml.FaceDetector
import com.example.crimicam.ml.FaceRecognizer
import com.example.crimicam.util.LocationManager
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
    val detectionCooldown: Long = 3000,
    val statusMessage: String = "Ready",
    val hasLocationPermission: Boolean = false
)

class CameraViewModel(
    private val capturedFacesRepository: CapturedFacesRepository = CapturedFacesRepository(),
    private val knownPeopleRepository: KnownPeopleRepository = KnownPeopleRepository(),
    private val faceDetector: FaceDetector = FaceDetector()
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var locationManager: LocationManager? = null

    init {
        loadKnownPeople()
    }

    fun initLocationManager(context: Context) {
        locationManager = LocationManager(context)
        _state.value = _state.value.copy(
            hasLocationPermission = locationManager?.hasLocationPermission() ?: false
        )
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
                // Get current location
                val locationData = locationManager?.getCurrentLocation()

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
                var statusMessage = if (isRecognized) {
                    "Recognized: ${matchedPerson?.name} (${(confidence * 100).toInt()}%)"
                } else {
                    "Unknown person detected!"
                }

                // Add location info to status
                if (locationData != null) {
                    statusMessage += " 📍"
                }

                // Save to Firestore with location
                when (capturedFacesRepository.saveCapturedFace(
                    originalBitmap = bitmap,
                    croppedFaceBitmap = croppedFace,
                    faceFeatures = faceFeatures,
                    isRecognized = isRecognized,
                    matchedPersonId = matchedPerson?.id,
                    matchedPersonName = matchedPerson?.name,
                    confidence = confidence,
                    latitude = locationData?.latitude,
                    longitude = locationData?.longitude,
                    locationAccuracy = locationData?.accuracy,
                    address = locationData?.address
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