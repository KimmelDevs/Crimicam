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
import com.example.crimicam.ml.*
import com.example.crimicam.ml.DetectionResult as ActivityDetectionResult
import com.example.crimicam.util.LocationManager
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.crimicam.util.LocationData

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
    val detectionStats: YOLODetector.DetectionStats? = null,
    val personDetectionMode: PersonDetectionMode = PersonDetectionMode.NONE,
    val motionDetected: Boolean = false,
    val presenceConfidence: Float = 0f,
    val processingStage: ProcessingStage = ProcessingStage.IDLE,
    val isMotionDetectionEnabled: Boolean = true // NEW: Control motion detection
)

enum class PersonDetectionMode {
    NONE,
    FACE_ONLY,
    POSE_ONLY,
    FACE_AND_POSE,
    YOLO_PERSON
}

enum class ProcessingStage {
    IDLE,
    CHECKING_MOTION,
    CHECKING_PRESENCE,
    DETECTING_OBJECTS,
    RECOGNIZING_FACES,
    COMPLETE
}

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
    private var presenceDetector: PresenceDetector? = null
    private var motionDetector: MotionDetector = MotionDetector()

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
            presenceDetector = PresenceDetector(context)
            Log.d("CameraViewModel", "✅ All detectors initialized successfully")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "❌ Failed to initialize detectors: ${e.message}", e)
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
            try {
                val locationData = locationManager?.getCurrentLocation()

                // ═══════════════════════════════════════════════════════════
                // STEP 0: MOTION DETECTION (Only if enabled)
                // ═══════════════════════════════════════════════════════════
                if (_state.value.isMotionDetectionEnabled) {
                    _state.value = _state.value.copy(
                        isProcessing = true,
                        processingStage = ProcessingStage.CHECKING_MOTION,
                        statusMessage = "Checking motion..."
                    )

                    val motionResult = motionDetector.detectMotion(bitmap)

                    if (!motionResult.hasMotion) {
                        Log.d("CameraViewModel", "⏸️ No motion - skipping heavy processing")
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingStage = ProcessingStage.IDLE,
                            statusMessage = "🔍 Monitoring (no motion)",
                            motionDetected = false,
                            personDetectionMode = PersonDetectionMode.NONE
                        )
                        return@launch
                    }

                    // MOTION DETECTED! Disable motion detection and continue
                    Log.d("CameraViewModel", "🏃 Motion detected: ${(motionResult.changePercentage * 100).toInt()}% - DISABLING motion detector")
                    _state.value = _state.value.copy(
                        motionDetected = true,
                        isMotionDetectionEnabled = false, // 🔥 DISABLE motion detection
                        processingStage = ProcessingStage.CHECKING_PRESENCE,
                        statusMessage = "Motion detected - checking presence..."
                    )
                } else {
                    // Motion detection disabled - continue with presence check
                    _state.value = _state.value.copy(
                        isProcessing = true,
                        processingStage = ProcessingStage.CHECKING_PRESENCE,
                        statusMessage = "Checking presence..."
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // STEP 1: PRESENCE DETECTION (Fast TFLite model)
                // ═══════════════════════════════════════════════════════════
                val presenceConfidence = presenceDetector?.detectPresence(bitmap) ?: 0f

                if (presenceConfidence < 0.5f) {
                    Log.d("CameraViewModel", "👻 No human presence (${(presenceConfidence * 100).toInt()}%) - RE-ENABLING motion detector")

                    // No person found - re-enable motion detection
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingStage = ProcessingStage.IDLE,
                        statusMessage = "🔍 Motion detected (no human)",
                        presenceConfidence = presenceConfidence,
                        personDetectionMode = PersonDetectionMode.NONE,
                        isMotionDetectionEnabled = true, // 🔥 RE-ENABLE motion detection
                        motionDetected = false
                    )
                    return@launch
                }

                Log.d("CameraViewModel", "👤 Human presence confirmed: ${(presenceConfidence * 100).toInt()}%")
                _state.value = _state.value.copy(
                    presenceConfidence = presenceConfidence,
                    processingStage = ProcessingStage.DETECTING_OBJECTS,
                    statusMessage = "Human detected - analyzing..."
                )

                // ═══════════════════════════════════════════════════════════
                // STEP 2: YOLO OBJECT DETECTION
                // ═══════════════════════════════════════════════════════════
                val yoloResults = yoloDetector?.detect(bitmap) ?: emptyList()
                val alertResult = yoloDetector?.analyzeSuspiciousBehavior(yoloResults)
                val stats = yoloDetector?.getDetectionStats()

                Log.d("CameraViewModel", "YOLO Detections: ${yoloResults.size} objects")

                val yoloPeople = yoloResults.filter { it.label == "person" }
                val hasYoloPerson = yoloPeople.isNotEmpty()

                // Handle security alerts
                if (alertResult != null && alertResult.shouldTrigger &&
                    alertResult.alert != YOLODetector.SecurityAlert.NONE) {

                    val alert = alertResult.alert
                    Log.d("CameraViewModel", "🚨 CONFIRMED Security Alert: ${alert.displayName}")

                    val severity = when (alert.severity) {
                        5 -> "CRITICAL"
                        4 -> "HIGH"
                        3, 2 -> "MEDIUM"
                        else -> "LOW"
                    }

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
                            "presence_confidence" to (presenceConfidence * 100).toInt(),
                            "motion_detected" to true
                        ),
                        frameBitmap = bitmap,
                        latitude = locationData?.latitude,
                        longitude = locationData?.longitude,
                        address = locationData?.address
                    )

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        lastDetectionTime = currentTime,
                        processingStage = ProcessingStage.COMPLETE,
                        yoloDetections = yoloResults,
                        securityAlert = alert,
                        alertConfidence = alertResult.confidence,
                        alertReason = alertResult.reason,
                        detectionStats = stats,
                        statusMessage = "🚨 ${alert.displayName} (${(alertResult.confidence * 100).toInt()}%)",
                        suspiciousActivityDetected = alert.displayName,
                        isMotionDetectionEnabled = true // Re-enable after processing
                    )

                    delay(3000)
                    _state.value = _state.value.copy(
                        suspiciousActivityDetected = null,
                        statusMessage = "Monitoring..."
                    )
                    return@launch
                }

                // ═══════════════════════════════════════════════════════════
                // STEP 3: MULTI-MODAL PERSON DETECTION
                // ═══════════════════════════════════════════════════════════
                _state.value = _state.value.copy(
                    processingStage = ProcessingStage.RECOGNIZING_FACES,
                    statusMessage = "Recognizing..."
                )

                var detectionMode = PersonDetectionMode.NONE
                var hasFace = false
                var hasPose = false

                val faces = faceDetector.detectFaces(bitmap)
                hasFace = faces.isNotEmpty()

                if (hasYoloPerson) {
                    val activityResult = activityDetectionModel?.detectSuspiciousActivity(bitmap)
                    hasPose = when (activityResult) {
                        is ActivityDetectionResult.Detected -> true
                        is ActivityDetectionResult.Normal -> true
                        else -> false
                    }

                    if (activityResult is ActivityDetectionResult.Detected) {
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
                                "details" to activity.details,
                                "detection_mode" to "pose_based",
                                "presence_confidence" to (presenceConfidence * 100).toInt()
                            ),
                            frameBitmap = bitmap,
                            latitude = locationData?.latitude,
                            longitude = locationData?.longitude,
                            address = locationData?.address
                        )

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            lastDetectionTime = currentTime,
                            processingStage = ProcessingStage.COMPLETE,
                            yoloDetections = yoloResults,
                            detectionStats = stats,
                            statusMessage = "🚨 ${activity.type.displayName} detected!",
                            suspiciousActivityDetected = activity.type.displayName,
                            personDetectionMode = PersonDetectionMode.POSE_ONLY,
                            isMotionDetectionEnabled = true // Re-enable
                        )

                        delay(3000)
                        _state.value = _state.value.copy(
                            suspiciousActivityDetected = null,
                            statusMessage = "Monitoring..."
                        )
                        return@launch
                    }
                }

                detectionMode = when {
                    hasFace && hasPose -> PersonDetectionMode.FACE_AND_POSE
                    hasFace && !hasPose -> PersonDetectionMode.FACE_ONLY
                    !hasFace && hasPose -> PersonDetectionMode.POSE_ONLY
                    hasYoloPerson -> PersonDetectionMode.YOLO_PERSON
                    else -> PersonDetectionMode.NONE
                }

                Log.d("CameraViewModel", "Detection Mode: $detectionMode (Face: $hasFace, Pose: $hasPose, YOLO: $hasYoloPerson)")

                // ═══════════════════════════════════════════════════════════
                // STEP 4: PROCESS BASED ON DETECTION MODE
                // ═══════════════════════════════════════════════════════════
                when (detectionMode) {
                    PersonDetectionMode.FACE_AND_POSE,
                    PersonDetectionMode.FACE_ONLY -> {
                        processFaceDetection(
                            bitmap, faces.first(), yoloResults, stats,
                            locationData, currentTime, detectionMode
                        )
                    }

                    PersonDetectionMode.POSE_ONLY -> {
                        Log.d("CameraViewModel", "👤 Person detected (pose only - face covered/hidden)")

                        capturedFacesRepository.saveCapturedFace(
                            originalBitmap = bitmap,
                            croppedFaceBitmap = null,
                            faceFeatures = emptyMap(),
                            isRecognized = false,
                            matchedPersonId = null,
                            matchedPersonName = null,
                            confidence = 0f,
                            latitude = locationData?.latitude,
                            longitude = locationData?.longitude,
                            locationAccuracy = locationData?.accuracy,
                            address = locationData?.address
                        )

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            lastDetectionTime = currentTime,
                            processingStage = ProcessingStage.COMPLETE,
                            yoloDetections = yoloResults,
                            detectionStats = stats,
                            statusMessage = "👤 Person (face hidden/covered)",
                            personDetectionMode = detectionMode,
                            isMotionDetectionEnabled = true // Re-enable
                        )
                    }

                    PersonDetectionMode.YOLO_PERSON -> {
                        Log.d("CameraViewModel", "👤 Person detected (YOLO only)")

                        capturedFacesRepository.saveCapturedFace(
                            originalBitmap = bitmap,
                            croppedFaceBitmap = null,
                            faceFeatures = emptyMap(),
                            isRecognized = false,
                            matchedPersonId = null,
                            matchedPersonName = null,
                            confidence = yoloPeople.maxOfOrNull { it.confidence } ?: 0f,
                            latitude = locationData?.latitude,
                            longitude = locationData?.longitude,
                            locationAccuracy = locationData?.accuracy,
                            address = locationData?.address
                        )

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            lastDetectionTime = currentTime,
                            processingStage = ProcessingStage.COMPLETE,
                            yoloDetections = yoloResults,
                            detectionStats = stats,
                            statusMessage = "👤 Person detected (${yoloPeople.size})",
                            personDetectionMode = detectionMode,
                            isMotionDetectionEnabled = true // Re-enable
                        )
                    }

                    PersonDetectionMode.NONE -> {
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
                            processingStage = ProcessingStage.COMPLETE,
                            yoloDetections = yoloResults,
                            detectionStats = stats,
                            statusMessage = statusMessage,
                            personDetectionMode = detectionMode,
                            isMotionDetectionEnabled = true // Re-enable
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingStage = ProcessingStage.IDLE,
                    statusMessage = "Error: ${e.message}",
                    isMotionDetectionEnabled = true // Re-enable even on error
                )
            }
        }
    }

    private suspend fun processFaceDetection(
        bitmap: Bitmap,
        face: com.google.mlkit.vision.face.Face,
        yoloResults: List<YOLODetectionResult>,
        stats: YOLODetector.DetectionStats?,
        locationData: LocationData?,
        currentTime: Long,
        detectionMode: PersonDetectionMode
    ) {
        val croppedFace = faceDetector.cropFace(bitmap, face)

        if (croppedFace == null) {
            _state.value = _state.value.copy(
                isProcessing = false,
                processingStage = ProcessingStage.COMPLETE,
                yoloDetections = yoloResults,
                detectionStats = stats,
                statusMessage = "Face detected but couldn't crop",
                isMotionDetectionEnabled = true // Re-enable
            )
            return
        }

        val faceFeatures = faceDetector.extractFaceFeatures(face)
        val (matchedPerson, confidence) = FaceRecognizer.findBestMatch(
            faceFeatures,
            _state.value.knownPeople
        )

        val isRecognized = matchedPerson != null
        val modeStr = when (detectionMode) {
            PersonDetectionMode.FACE_AND_POSE -> " (face + pose)"
            PersonDetectionMode.FACE_ONLY -> " (face only)"
            else -> ""
        }

        var statusMessage = if (isRecognized) {
            "👤 Known: ${matchedPerson?.name}$modeStr"
        } else {
            "👤 Unknown person$modeStr"
        }

        if (yoloResults.isNotEmpty()) {
            val objects = yoloResults.take(2).joinToString { it.label }
            statusMessage += " + $objects"
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
                    processingStage = ProcessingStage.COMPLETE,
                    yoloDetections = yoloResults,
                    detectionStats = stats,
                    statusMessage = statusMessage,
                    personDetectionMode = detectionMode,
                    isMotionDetectionEnabled = true // Re-enable
                )
            }
            is Result.Error -> {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    processingStage = ProcessingStage.COMPLETE,
                    yoloDetections = yoloResults,
                    detectionStats = stats,
                    statusMessage = "Error saving",
                    isMotionDetectionEnabled = true // Re-enable
                )
            }
            is Result.Loading -> {}
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

    fun resetMotionDetector() {
        motionDetector.reset()
        _state.value = _state.value.copy(
            isMotionDetectionEnabled = true,
            motionDetected = false
        )
        Log.d("CameraViewModel", "Motion detector reset and re-enabled")
    }

    fun getDebugInfo(): String {
        val stats = _state.value.detectionStats ?: return "No stats available"
        return buildString {
            appendLine("Processing Stage: ${_state.value.processingStage}")
            appendLine("Motion Detection: ${if (_state.value.isMotionDetectionEnabled) "ENABLED ✅" else "DISABLED ⏸️"}")
            appendLine("Motion Detected: ${_state.value.motionDetected}")
            appendLine("Presence Confidence: ${(_state.value.presenceConfidence * 100).toInt()}%")
            appendLine("Detection Mode: ${_state.value.personDetectionMode}")
            appendLine("Detection History: ${stats.historySize} frames")
            stats.currentAlertState?.let { alertState ->
                appendLine("Current Alert: ${alertState.alert.displayName}")
                appendLine("Consecutive Count: ${alertState.consecutiveCount}")
                appendLine("Smoothed Confidence: ${(alertState.smoothedConfidence * 100).toInt()}%")
            } ?: appendLine("Current Alert State: None")
            appendLine("Active Tracks: ${stats.activeTracksCount}")
        }
    }

    fun getTrackingInfo(): Map<Int, String> {
        val stats = _state.value.detectionStats ?: return emptyMap()
        return stats.trackedObjects.associate { track ->
            track.id to "${track.label} (${track.frameCount} frames)"
        }
    }

    fun clearObjectTracks() {
        yoloDetector?.clearTracks()
        Log.d("CameraViewModel", "Object tracks cleared")
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
        activityDetectionModel?.cleanup()
        yoloDetector?.cleanup()
        presenceDetector?.cleanup()
    }
}