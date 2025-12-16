package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.data.service.FirestoreCaptureService
import com.example.crimicam.facerecognitionnetface.models.data.RecognitionMetrics
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalImageVectorUseCase
import com.example.crimicam.webrtc.WebRTCManager
import com.example.crimicam.webrtc.WebRTCSignalingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class DetectedFace(
    val boundingBox: RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float,
    val distance: Float = 0f,
    val isCriminal: Boolean = false,
    val dangerLevel: String? = null,
    val spoofDetected: Boolean = false,
    val croppedBitmap: Bitmap? = null
)

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
    val recordingState: RecordingState = RecordingState(),
    val currentLocation: Location? = null,
    val lastSavedCaptureId: String? = null,
    // WebRTC streaming state
    val isStreaming: Boolean = false,
    val streamingSessionId: String? = null,
    val webRtcConnectionState: WebRTCManager.RTCConnectionState = WebRTCManager.RTCConnectionState.DISCONNECTED,
    val viewerCount: Int = 0
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
    val criminalImageVectorUseCase: CriminalImageVectorUseCase,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)

    private val firestoreCaptureService = FirestoreCaptureService(context)
    private val auth = FirebaseAuth.getInstance()
    private val webRtcManager = WebRTCManager(context)
    private val signalingService = WebRTCSignalingService()
    private val sessionRepository = WebRTCSignalingRepository()

    private var recordingStartTime: Long = 0
    private val timerFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var lastFullFrameBitmap: Bitmap? = null
    private var currentSessionId: String? = null
    private val viewerConnections = mutableMapOf<String, org.webrtc.PeerConnection>()

    // Per-person cooldown management
    private val personCooldowns = ConcurrentHashMap<String, Long>()
    private val PERSON_COOLDOWN_MS = 10000L
    private var cooldownCleanupJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "CameraViewModel"
    }

    init {
        webRtcManager.initialize()
        observeConnectionState()
        refreshPeopleCount()
        refreshCriminalCount()
        ensureAuthentication()
        startCooldownCleanup()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            webRtcManager.connectionState.collect { state ->
                _state.value = _state.value.copy(webRtcConnectionState = state)
            }
        }
    }

    // ========== WebRTC Streaming Methods ==========

    fun startWebRtcStreaming() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: run {
                    updateStatusMessage("⚠️ Please sign in to start streaming")
                    return@launch
                }

                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                val deviceName = android.os.Build.MODEL

                // Create session in Firestore
                val session = WebRTCSession(
                    id = "",
                    userId = userId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    isStreaming = true,
                    streamStartedAt = System.currentTimeMillis(),
                    lastHeartbeat = System.currentTimeMillis(),
                    latitude = _state.value.currentLocation?.latitude,
                    longitude = _state.value.currentLocation?.longitude
                )

                val result = sessionRepository.createSession(session)
                if (result is com.example.crimicam.util.Result.Success) {
                    currentSessionId = result.data

                    // Start WebRTC
                    webRtcManager.startStreaming(
                        sessionId = result.data,
                        onIceCandidate = { candidate ->
                            handleIceCandidate(candidate, result.data, userId, null)
                        },
                        onOfferCreated = { offer ->
                            handleOfferCreated(offer, result.data, userId)
                        }
                    )

                    // Listen for viewer answers
                    observeViewerAnswers(result.data, userId)

                    // Start heartbeat
                    startHeartbeat(result.data)

                    _state.value = _state.value.copy(
                        isStreaming = true,
                        streamingSessionId = result.data
                    )

                    updateStatusMessage("🔴 Live streaming started")
                    Log.d(TAG, "Streaming started: ${result.data}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
                updateStatusMessage("⚠️ Failed to start streaming")
            }
        }
    }

    fun stopWebRtcStreaming() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                try {
                    // Close all viewer connections
                    viewerConnections.values.forEach { it.close() }
                    viewerConnections.clear()

                    // Update session status
                    sessionRepository.updateSessionStatus(sessionId, false)

                    // Cleanup signaling
                    signalingService.cleanupSession(sessionId)

                    webRtcManager.release()

                    _state.value = _state.value.copy(
                        isStreaming = false,
                        streamingSessionId = null,
                        viewerCount = 0
                    )

                    currentSessionId = null
                    updateStatusMessage("⚫ Streaming stopped")
                    Log.d(TAG, "Streaming stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping stream", e)
                }
            }
        }
    }

    private fun handleOfferCreated(offer: SessionDescription, sessionId: String, userId: String) {
        viewModelScope.launch {
            try {
                signalingService.sendOffer(sessionId, userId, offer)
                Log.d(TAG, "Offer sent to signaling server")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send offer", e)
            }
        }
    }

    private fun handleIceCandidate(
        candidate: IceCandidate,
        sessionId: String,
        senderId: String,
        receiverId: String?
    ) {
        viewModelScope.launch {
            try {
                signalingService.sendIceCandidate(sessionId, senderId, receiverId, candidate)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send ICE candidate", e)
            }
        }
    }

    private fun observeViewerAnswers(sessionId: String, broadcasterId: String) {
        viewModelScope.launch {
            signalingService.observeAnswers(sessionId, broadcasterId).collect { (viewerId, answer) ->
                Log.d(TAG, "Received answer from viewer: $viewerId")
                webRtcManager.setRemoteDescription(answer) {
                    _state.value = _state.value.copy(
                        viewerCount = _state.value.viewerCount + 1
                    )
                }

                // Observe ICE candidates from this viewer
                observeViewerIceCandidates(sessionId, broadcasterId)
            }
        }
    }

    private fun observeViewerIceCandidates(sessionId: String, receiverId: String) {
        viewModelScope.launch {
            signalingService.observeIceCandidates(sessionId, receiverId).collect { candidate ->
                webRtcManager.addIceCandidate(candidate)
            }
        }
    }

    private fun startHeartbeat(sessionId: String) {
        viewModelScope.launch {
            while (_state.value.isStreaming) {
                sessionRepository.updateHeartbeat(sessionId, System.currentTimeMillis())
                kotlinx.coroutines.delay(5000) // Update every 5 seconds
            }
        }
    }

    fun switchCamera() {
        webRtcManager.switchCamera()
    }

    // ========== Face Recognition Methods ==========

    private fun ensureAuthentication() {
        viewModelScope.launch {
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Signed in anonymously")
                    }
                }
            }
        }
    }

    private fun startCooldownCleanup() {
        cooldownCleanupJob?.cancel()
        cooldownCleanupJob = viewModelScope.launch {
            while (true) {
                cleanupOldCooldowns()
                kotlinx.coroutines.delay(60000L) // Cleanup every minute
            }
        }
    }

    private fun cleanupOldCooldowns() {
        val currentTime = System.currentTimeMillis()
        val fiveMinutesAgo = currentTime - (5 * 60 * 1000L)

        personCooldowns.entries.removeIf { entry ->
            entry.value < fiveMinutesAgo
        }

        if (personCooldowns.isNotEmpty()) {
            Log.d(TAG, "Cooldown cleanup: ${personCooldowns.size} active cooldowns")
        }
    }

    fun refreshCriminalCount() {
        viewModelScope.launch {
            try {
                val criminals = criminalImageVectorUseCase.getAllCriminals()
                _state.value = _state.value.copy(criminalCount = criminals.size.toLong())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh criminal count", e)
            }
        }
    }

    fun processFrameForDetection(frameBitmap: Bitmap) {
        if (_state.value.isProcessing) return

        lastFullFrameBitmap = frameBitmap

        viewModelScope.launch {
            try {
                setProcessing(true)
                updateScanningMode(ScanningMode.DETECTING)

                val (criminalMetrics, criminalResults) =
                    criminalImageVectorUseCase.getNearestCriminalName(
                        frameBitmap = frameBitmap,
                        flatSearch = false,
                        confidenceThreshold = CriminalImageVectorUseCase.DEFAULT_CONFIDENCE_THRESHOLD
                    )

                val detectedFaces = mutableListOf<DetectedFace>()
                var hasCriminal = false

                for (criminalResult in criminalResults) {
                    val isCriminal = criminalResult.criminalName != "Unknown"
                    if (isCriminal) hasCriminal = true

                    val boundingBox = RectF(
                        criminalResult.boundingBox.left.toFloat(),
                        criminalResult.boundingBox.top.toFloat(),
                        criminalResult.boundingBox.right.toFloat(),
                        criminalResult.boundingBox.bottom.toFloat()
                    )

                    val croppedFace = cropBitmapFromBoundingBox(frameBitmap, boundingBox)

                    detectedFaces.add(
                        DetectedFace(
                            boundingBox = boundingBox,
                            personId = criminalResult.criminalID,
                            personName = criminalResult.criminalName,
                            confidence = criminalResult.confidence,
                            isCriminal = isCriminal,
                            dangerLevel = criminalResult.dangerLevel,
                            spoofDetected = criminalResult.spoofResult?.isSpoof ?: false,
                            croppedBitmap = croppedFace
                        )
                    )

                    if (isCriminal && croppedFace != null) {
                        val personId = criminalResult.criminalID
                        if (personId != null && isPersonInCooldown(personId)) {
                            Log.d(TAG, "Skipping criminal $personId due to cooldown")
                        } else {
                            saveCaptureToFirestore(
                                croppedFace = croppedFace,
                                fullFrame = frameBitmap,
                                isRecognized = true,
                                isCriminal = true,
                                personId = criminalResult.criminalID,
                                personName = criminalResult.criminalName,
                                confidence = criminalResult.confidence,
                                dangerLevel = criminalResult.dangerLevel
                            )
                            personId?.let { startCooldownForPerson(it) }
                        }
                    }
                }

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

                        val croppedFace = cropBitmapFromBoundingBox(frameBitmap, boundingBox)

                        val personId = if (personResult.personName != "Unknown") {
                            personResult.personName?.hashCode()?.toString()
                        } else {
                            "${boundingBox.left.toInt()}_${boundingBox.top.toInt()}_${boundingBox.width().toInt()}_${boundingBox.height().toInt()}".hashCode().toString()
                        }

                        detectedFaces.add(
                            DetectedFace(
                                boundingBox = boundingBox,
                                personName = personResult.personName,
                                confidence = personResult.confidence,
                                isCriminal = false,
                                spoofDetected = personResult.spoofResult?.isSpoof ?: false,
                                croppedBitmap = croppedFace
                            )
                        )

                        if (personResult.personName != "Unknown" && croppedFace != null && personId != null) {
                            if (isPersonInCooldown(personId)) {
                                Log.d(TAG, "Skipping recognized person ${personResult.personName} due to cooldown")
                            } else {
                                saveCaptureToFirestore(
                                    croppedFace = croppedFace,
                                    fullFrame = frameBitmap,
                                    isRecognized = true,
                                    isCriminal = false,
                                    personId = personId,
                                    personName = personResult.personName,
                                    confidence = personResult.confidence,
                                    dangerLevel = null
                                )
                                startCooldownForPerson(personId)
                            }
                        }
                    }

                    faceDetectionMetricsState.value = peopleMetrics
                } else {
                    faceDetectionMetricsState.value = criminalMetrics
                }

                updateDetectedFacesWithCriminals(detectedFaces, hasCriminal)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
                onRecognitionError(e.message ?: "Unknown error")
            } finally {
                setProcessing(false)
            }
        }
    }

    private fun isPersonInCooldown(personId: String): Boolean {
        val lastDetectionTime = personCooldowns[personId] ?: return false
        val currentTime = System.currentTimeMillis()
        return currentTime - lastDetectionTime < PERSON_COOLDOWN_MS
    }

    private fun getCooldownRemaining(personId: String): Long {
        val lastDetectionTime = personCooldowns[personId] ?: return 0L
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastDetectionTime
        return if (elapsed < PERSON_COOLDOWN_MS) {
            PERSON_COOLDOWN_MS - elapsed
        } else {
            0L
        }
    }

    private fun startCooldownForPerson(personId: String) {
        personCooldowns[personId] = System.currentTimeMillis()
        Log.d(TAG, "Started cooldown for person: $personId")
    }

    private fun cropBitmapFromBoundingBox(bitmap: Bitmap, boundingBox: RectF): Bitmap? {
        return try {
            val left = boundingBox.left.toInt().coerceIn(0, bitmap.width)
            val top = boundingBox.top.toInt().coerceIn(0, bitmap.height)
            val right = boundingBox.right.toInt().coerceIn(0, bitmap.width)
            val bottom = boundingBox.bottom.toInt().coerceIn(0, bitmap.height)

            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)

            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping bitmap", e)
            null
        }
    }

    private fun saveCaptureToFirestore(
        croppedFace: Bitmap,
        fullFrame: Bitmap,
        isRecognized: Boolean,
        isCriminal: Boolean,
        personId: String? = null,
        personName: String? = null,
        confidence: Float,
        dangerLevel: String? = null
    ) {
        viewModelScope.launch {
            try {
                val result = firestoreCaptureService.saveCapturedFace(
                    croppedFace = croppedFace,
                    fullFrame = fullFrame,
                    isRecognized = isRecognized,
                    isCriminal = isCriminal,
                    matchedPersonId = personId,
                    matchedPersonName = personName,
                    confidence = confidence,
                    dangerLevel = dangerLevel,
                    location = _state.value.currentLocation,
                    deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                )

                result.onSuccess { captureId ->
                    _state.value = _state.value.copy(lastSavedCaptureId = captureId)
                    val message = when {
                        isCriminal -> "🚨 Criminal $personName detected and saved!"
                        isRecognized -> "✅ $personName identified and saved!"
                        else -> "📸 Face captured and saved!"
                    }
                    updateStatusMessage(message)
                    Log.d(TAG, "Capture saved: $captureId for person: $personName")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving capture", e)
            }
        }
    }

    fun saveCurrentDetection() {
        viewModelScope.launch {
            val detectedFaces = _state.value.detectedFaces
            val fullFrame = lastFullFrameBitmap

            if (detectedFaces.isEmpty()) {
                updateStatusMessage("⚠️ No faces detected to save")
                return@launch
            }

            if (fullFrame == null) {
                updateStatusMessage("⚠️ No frame available")
                return@launch
            }

            detectedFaces.forEach { face ->
                face.croppedBitmap?.let { croppedFace ->
                    val personId = when {
                        face.personId != null -> face.personId
                        face.personName != null -> face.personName.hashCode().toString()
                        else -> {
                            "${face.boundingBox.left.toInt()}_${face.boundingBox.top.toInt()}_${face.boundingBox.width().toInt()}_${face.boundingBox.height().toInt()}".hashCode().toString()
                        }
                    }

                    if (isPersonInCooldown(personId)) {
                        val remaining = getCooldownRemaining(personId) / 1000
                        Log.d(TAG, "Skipping manual save for person due to cooldown: ${remaining}s remaining")
                        updateStatusMessage("⏳ Person already captured recently (${remaining}s)")
                        return@forEach
                    }

                    saveCaptureToFirestore(
                        croppedFace = croppedFace,
                        fullFrame = fullFrame,
                        isRecognized = face.personName != null,
                        isCriminal = face.isCriminal,
                        personId = face.personId,
                        personName = face.personName,
                        confidence = face.confidence,
                        dangerLevel = face.dangerLevel
                    )

                    startCooldownForPerson(personId)
                }
            }
        }
    }

    fun updateLocation(location: Location) {
        _state.value = _state.value.copy(currentLocation = location)

        // Update session location if streaming
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                sessionRepository.updateSessionLocation(
                    sessionId,
                    location.latitude,
                    location.longitude
                )
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
    }

    // ========== Screen Recording Methods ==========

    fun startRecording() {
        viewModelScope.launch {
            if (_state.value.recordingState.isRecording) return@launch

            try {
                _state.value = _state.value.copy(
                    recordingState = RecordingState(
                        isRecording = true,
                        recordingTime = "00:00"
                    )
                )
                recordingStartTime = System.currentTimeMillis()
                startRecordingTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
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
            recordingJob?.cancel()
            recordingJob = null

            _state.value = _state.value.copy(
                recordingState = _state.value.recordingState.copy(
                    isRecording = false,
                    recordingTime = "00:00"
                )
            )
            recordingStartTime = 0
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
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(
                outputUri = uri,
                recordingSaved = true
            )
        )
        updateStatusMessage("✅ Recording saved to Gallery")
    }

    fun onRecordingFailed(error: String) {
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
            recordingState = _state.value.recordingState.copy(recordingError = null)
        )
    }

    fun clearRecordingSaved() {
        _state.value = _state.value.copy(
            recordingState = _state.value.recordingState.copy(recordingSaved = false)
        )
    }

    // ========== Utility Methods ==========

    fun getNumPeople(): Long = _state.value.peopleCount

    fun refreshPeopleCount() {
        viewModelScope.launch {
            try {
                val count = personUseCase.refreshCount()
                _state.value = _state.value.copy(peopleCount = count)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh people count", e)
            }
        }
    }

    fun updateDetectedFaces(faces: List<DetectedFace>) {
        val hasCriminal = faces.any { it.isCriminal }
        updateDetectedFacesWithCriminals(faces, hasCriminal)
    }

    fun updateScanningMode(mode: ScanningMode) {
        _state.value = _state.value.copy(scanningMode = mode)
    }

    fun updateStatusMessage(message: String) {
        _state.value = _state.value.copy(statusMessage = message)
    }

    fun updateKnownPeople(people: List<KnownPerson>) {
        _state.value = _state.value.copy(knownPeople = people)
    }

    fun setModelInitialized(initialized: Boolean) {
        _state.value = _state.value.copy(modelInitialized = initialized)
    }

    fun setProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isProcessing = processing)
    }

    fun setLocationPermission(hasPermission: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = hasPermission)
    }

    fun clearDetectedFaces() {
        _state.value = _state.value.copy(
            detectedFaces = emptyList(),
            scanningMode = ScanningMode.IDLE,
            statusMessage = "🔍 Scanning for faces..."
        )
    }

    fun onRecognitionError(error: String) {
        _state.value = _state.value.copy(
            isProcessing = false,
            scanningMode = ScanningMode.IDLE,
            statusMessage = "⚠️ $error"
        )
    }

    /**
     * Save criminal detection to Firestore (called from overlay analyzer)
     */
    fun saveCriminalToFirestore(
        croppedFace: Bitmap,
        fullFrame: Bitmap,
        criminalId: String,
        criminalName: String,
        confidence: Float,
        dangerLevel: String,
        isSpoof: Boolean
    ) {
        viewModelScope.launch {
            if (isPersonInCooldown(criminalId)) {
                val remaining = getCooldownRemaining(criminalId) / 1000
                Log.d(TAG, "Skipping criminal $criminalName due to cooldown: ${remaining}s remaining")
                return@launch
            }

            try {
                val result = firestoreCaptureService.saveCapturedFace(
                    croppedFace = croppedFace,
                    fullFrame = fullFrame,
                    isRecognized = true,
                    isCriminal = true,
                    matchedPersonId = criminalId,
                    matchedPersonName = criminalName,
                    confidence = confidence,
                    dangerLevel = dangerLevel,
                    location = _state.value.currentLocation,
                    deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                )

                result.onSuccess { captureId ->
                    _state.value = _state.value.copy(lastSavedCaptureId = captureId)
                    Log.d(TAG, "✅ Criminal saved to Firestore: $captureId")
                    updateStatusMessage("🚨 Criminal $criminalName detected and saved!")
                    startCooldownForPerson(criminalId)
                }.onFailure { e ->
                    Log.e(TAG, "❌ Failed to save criminal", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving criminal to Firestore", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_state.value.isStreaming) {
            stopWebRtcStreaming()
        }
        recordingJob?.cancel()
        recordingJob = null
        cooldownCleanupJob?.cancel()
        cooldownCleanupJob = null
        lastFullFrameBitmap?.recycle()
        lastFullFrameBitmap = null
        personCooldowns.clear()
        webRtcManager.release()
    }}