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
import com.example.crimicam.ml.YOLODetector
import com.example.crimicam.ml.YOLODetectionResult
import com.example.crimicam.ml.ActivityDetectionModel
import com.example.crimicam.ml.DetectionResult as ActivityDetectionResult
import com.example.crimicam.ml.FaceDetector
import com.example.crimicam.ml.FaceRecognizer
import com.example.crimicam.util.LocationManager
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class CameraState(
    val isProcessing: Boolean = false,
    val knownPeople: List<KnownPerson> = emptyList(),
    val lastDetectionTime: Long = 0,
    val detectionCooldown: Long = 2000,
    val statusMessage: String = "Ready",
    val hasLocationPermission: Boolean = false,
    val suspiciousActivityDetected: String? = null,
    val yoloDetections: List<YOLODetectionResult> = emptyList(),
    val securityAlert: YOLODetector.SecurityAlert? = null,
    val alertConfidence: Float = 0f,
    val alertReason: String? = null,
    val detectionStats: YOLODetector.DetectionStats? = null
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
    private var yoloDetector: YOLODetector? = null

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

    fun initDetector(context: Context) {
        try {
            yoloDetector = YOLODetector(context)
            Log.d("CameraViewModel", "YOLO Detector initialized successfully")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to initialize YOLO Detector: ${e.message}", e)
        }
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

                // YOLO OBJECT DETECTION with Enhanced Alert System
                val yoloResults = yoloDetector?.detect(bitmap) ?: emptyList()
                val alertResult = yoloDetector?.analyzeSuspiciousBehavior(yoloResults)
                val stats = yoloDetector?.getDetectionStats()

                Log.d("CameraViewModel", "YOLO Detections: ${yoloResults.size} objects")

                if (alertResult != null) {
                    Log.d("CameraViewModel", "Alert: ${alertResult.alert.displayName}")
                    Log.d("CameraViewModel", "Confidence: ${(alertResult.confidence * 100).toInt()}%")
                    Log.d("CameraViewModel", "Should Trigger: ${alertResult.shouldTrigger}")
                    Log.d("CameraViewModel", "Reason: ${alertResult.reason}")
                }

                yoloResults.forEach { detection ->
                    Log.d("CameraViewModel", " - ${detection.label}: ${(detection.confidence * 100).toInt()}%")
                }

                // Check if alert should trigger
                if (alertResult != null && alertResult.shouldTrigger &&
                    alertResult.alert != YOLODetector.SecurityAlert.NONE) {

                    val alert = alertResult.alert
                    Log.d("CameraViewModel", "🚨 CONFIRMED Security Alert: ${alert.displayName}")

                    // Determine severity
                    val severity = when (alert.severity) {
                        5 -> "CRITICAL"
                        4 -> "HIGH"
                        3, 2 -> "MEDIUM"
                        else -> "LOW"
                    }

                    // Save to Firestore
                    suspiciousActivityRepository.saveActivity(
                        activityType = "YOLO_${alert.name}",
                        displayName = alert.displayName,
                        severity = severity,
                        confidence = alertResult.confidence,
                        duration = 0L,
                        details = mapOf(
                            "objects" to yoloResults.map {
                                mapOf(
                                    "label" to it.label,
                                    "confidence" to (it.confidence * 100).toInt()
                                )
                            },
                            "count" to yoloResults.size,
                            "smoothed_confidence" to (alertResult.confidence * 100).toInt(),
                            "validation_reason" to alertResult.reason,
                            "summary" to "Detected: ${yoloResults.joinToString(", ") {
                                "${it.label} (${(it.confidence * 100).toInt()}%)"
                            }}"
                        ),
                        frameBitmap = bitmap,
                        latitude = locationData?.latitude,
                        longitude = locationData?.longitude,
                        address = locationData?.address
                    )

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        lastDetectionTime = currentTime,
                        yoloDetections = yoloResults,
                        securityAlert = alert,
                        alertConfidence = alertResult.confidence,
                        alertReason = alertResult.reason,
                        detectionStats = stats,
                        statusMessage = "🚨 ${alert.displayName} (${(alertResult.confidence * 100).toInt()}%)",
                        suspiciousActivityDetected = alert.displayName
                    )

                    delay(3000)
                    _state.value = _state.value.copy(
                        suspiciousActivityDetected = null,
                        statusMessage = "Monitoring..."
                    )
                    return@launch
                } else if (alertResult != null && !alertResult.shouldTrigger) {
                    Log.d("CameraViewModel", "⏳ Alert pending: ${alertResult.alert.displayName}")
                    Log.d("CameraViewModel", "   Reason: ${alertResult.reason}")

                    _state.value = _state.value.copy(
                        alertReason = alertResult.reason,
                        detectionStats = stats
                    )
                }

                // ACTIVITY DETECTION
                val hasPerson = yoloResults.any { it.label == "person" }

                if (hasPerson) {
                    val activityResult = activityDetectionModel?.detectSuspiciousActivity(bitmap)

                    when (activityResult) {
                        is ActivityDetectionResult.Detected -> {
                            val activity = activityResult.activities.first()

                            Log.d("CameraViewModel", "🚨 Suspicious Activity: ${activity.type.displayName}")

                            suspiciousActivityRepository.saveActivity(
                                activityType = activity.type.name,
                                displayName = activity.type.displayName,
                                severity = activity.type.severity.name,
                                confidence = activity.confidence,
                                duration = activity.duration,
                                details = mapOf(
                                    "activity" to activity.type.displayName,
                                    "confidence" to (activity.confidence * 100).toInt(),
                                    "duration" to activity.duration,
                                    "details" to activity.details
                                ),
                                frameBitmap = bitmap,
                                latitude = locationData?.latitude,
                                longitude = locationData?.longitude,
                                address = locationData?.address
                            )

                            _state.value = _state.value.copy(
                                isProcessing = false,
                                lastDetectionTime = currentTime,
                                yoloDetections = yoloResults,
                                detectionStats = stats,
                                statusMessage = "🚨 ${activity.type.displayName} detected!",
                                suspiciousActivityDetected = activity.type.displayName
                            )

                            delay(3000)
                            _state.value = _state.value.copy(
                                suspiciousActivityDetected = null,
                                statusMessage = "Monitoring..."
                            )
                            return@launch
                        }
                        else -> {}
                    }
                }

                // FACE DETECTION
                val faces = faceDetector.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    val statusMessage = when {
                        yoloResults.isNotEmpty() -> {
                            val mainObject = yoloResults.maxByOrNull { it.confidence }
                            "📹 ${mainObject?.label?.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                            } ?: "Object"} detected"
                        }
                        else -> "🔍 Monitoring area"
                    }

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        yoloDetections = yoloResults,
                        detectionStats = stats,
                        statusMessage = statusMessage
                    )
                    return@launch
                }

                val face = faces.first()
                val croppedFace = faceDetector.cropFace(bitmap, face)

                if (croppedFace == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        yoloDetections = yoloResults,
                        detectionStats = stats,
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
                    "👤 Known: ${matchedPerson?.name}"
                } else {
                    "👤 Unknown person"
                }

                if (yoloResults.isNotEmpty()) {
                    val objects = yoloResults.take(2).joinToString { it.label }
                    statusMessage += " + $objects"
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
                            yoloDetections = yoloResults,
                            detectionStats = stats,
                            statusMessage = statusMessage
                        )
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            yoloDetections = yoloResults,
                            detectionStats = stats,
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

    fun clearSecurityAlert() {
        _state.value = _state.value.copy(
            securityAlert = null,
            suspiciousActivityDetected = null,
            alertReason = null
        )
    }

    fun updateYoloDetections(detections: List<YOLODetectionResult>) {
        _state.value = _state.value.copy(
            yoloDetections = detections
        )
    }

    fun getDebugInfo(): String {
        val stats = _state.value.detectionStats ?: return "No stats available"
        return buildString {
            appendLine("Detection History: ${stats.historySize} frames")
            stats.currentAlertState?.let { alertState ->
                appendLine("Current Alert: ${alertState.alert.displayName}")
                appendLine("Consecutive Count: ${alertState.consecutiveCount}")
                appendLine("Smoothed Confidence: ${(alertState.smoothedConfidence * 100).toInt()}%")
            } ?: appendLine("Current Alert State: None")
            appendLine("Recent Alerts: ${stats.recentAlerts.joinToString { it.displayName }}")
            appendLine("\nTracking Info:")
            appendLine("Active Tracks: ${stats.activeTracksCount}")
            stats.trackedObjects.forEach { track ->
                val age = (System.currentTimeMillis() - track.firstSeen) / 1000f
                appendLine("  Track #${track.id}: ${track.label} (${track.frameCount} frames, ${age}s)")
            }
        }
    }

    // NEW: Get tracking info for display
    fun getTrackingInfo(): Map<Int, String> {
        val stats = _state.value.detectionStats ?: return emptyMap()
        return stats.trackedObjects.associate { track ->
            track.id to "${track.label} (${track.frameCount} frames)"
        }
    }

    // NEW: Clear all object tracks (useful for debugging or resetting)
    fun clearObjectTracks() {
        yoloDetector?.clearTracks()
        Log.d("CameraViewModel", "Object tracks cleared")
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
        activityDetectionModel?.cleanup()
        yoloDetector?.cleanup()
    }
}