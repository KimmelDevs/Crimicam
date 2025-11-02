package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.repository.CapturedFacesRepository
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.data.repository.SuspiciousActivityRepository
import com.example.crimicam.ml.ActivityDetectionModel
import com.example.crimicam.ml.DetectionResult
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
    val hasLocationPermission: Boolean = false,
    val suspiciousActivityDetected: String? = null
)

class CameraViewModel(
    private val capturedFacesRepository: CapturedFacesRepository = CapturedFacesRepository(),
    private val knownPeopleRepository: KnownPeopleRepository = KnownPeopleRepository(),
    private val suspiciousActivityRepository: SuspiciousActivityRepository = SuspiciousActivityRepository(),
    private val faceDetector: FaceDetector = FaceDetector()
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var locationManager: LocationManager? = null
    private var activityDetectionModel: ActivityDetectionModel? = null

    init {
        loadKnownPeople()
    }

    fun initLocationManager(context: Context) {
        locationManager = LocationManager(context)
        activityDetectionModel = ActivityDetectionModel(context)

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
        val currentTime = System.currentTimeMillis()
        if (currentTime - _state.value.lastDetectionTime < _state.value.detectionCooldown) {
            return
        }

        if (_state.value.isProcessing) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessing = true,
                statusMessage = "Analyzing..."
            )

            try {
                val locationData = locationManager?.getCurrentLocation()

                // ACTIVITY DETECTION (Primary)
                val activityResult = activityDetectionModel?.detectSuspiciousActivity(bitmap)

                when (activityResult) {
                    is DetectionResult.Detected -> {
                        val activity = activityResult.activities.first()

                        Log.d("CameraViewModel", "🚨 Suspicious Activity: ${activity.type.displayName}")
                        Log.d("CameraViewModel", "   Confidence: ${(activity.confidence * 100).toInt()}%")
                        Log.d("CameraViewModel", "   Severity: ${activity.type.severity}")

                        // Save to Firestore
                        suspiciousActivityRepository.saveActivity(
                            activityType = activity.type.name,
                            displayName = activity.type.displayName,
                            severity = activity.type.severity.name,
                            confidence = activity.confidence,
                            duration = activity.duration,
                            details = activity.details,
                            frameBitmap = bitmap,
                            latitude = locationData?.latitude,
                            longitude = locationData?.longitude,
                            address = locationData?.address
                        )

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            lastDetectionTime = currentTime,
                            statusMessage = "🚨 ${activity.type.displayName} detected!",
                            suspiciousActivityDetected = activity.type.displayName
                        )

                        // Clear activity alert after 3 seconds
                        delay(3000)
                        _state.value = _state.value.copy(
                            suspiciousActivityDetected = null,
                            statusMessage = "Monitoring..."
                        )
                        return@launch
                    }
                    else -> {
                        // No suspicious activity, continue with face detection
                    }
                }

                // FACE DETECTION (Secondary)
                val faces = faceDetector.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        statusMessage = "Normal - Monitoring"
                    )
                    return@launch
                }

                val face = faces.first()
                val croppedFace = faceDetector.cropFace(bitmap, face)

                if (croppedFace == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        statusMessage = "Failed to crop face"
                    )
                    return@launch
                }

                val faceFeatures = faceDetector.extractFaceFeatures(face)
                val (matchedPerson, confidence) = FaceRecognizer.findBestMatch(
                    faceFeatures,
                    _state.value.knownPeople
                )

                val isRecognized = matchedPerson != null
                var statusMessage = if (isRecognized) {
                    "Known: ${matchedPerson?.name}"
                } else {
                    "Unknown person"
                }

                if (locationData != null) {
                    statusMessage += " 📍"
                }

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
                            statusMessage = statusMessage
                        )
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            statusMessage = "Error saving"
                        )
                    }
                    is Result.Loading -> {}
                }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error: ${e.message}", e)
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
        activityDetectionModel?.cleanup()
    }
}