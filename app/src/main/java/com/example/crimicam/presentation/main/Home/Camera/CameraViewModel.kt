package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.CapturedFacesRepository
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.ml.FaceDetector
import com.example.crimicam.ml.FaceRecognizer
import com.example.crimicam.util.LocationManager
import com.example.crimicam.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetectedFace(
    val boundingBox: android.graphics.RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float
)

data class CameraState(
    val isProcessing: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val scanningMode: ScanningMode = ScanningMode.IDLE,
    val statusMessage: String = "🔍 Scanning for faces...",
    val hasLocationPermission: Boolean = false,
    val knownPeople: List<com.example.crimicam.data.model.KnownPerson> = emptyList()
)

enum class ScanningMode {
    IDLE,
    DETECTING,
    ANALYZING,
    IDENTIFIED,
    UNKNOWN
}

class CameraViewModel(
    private val capturedFacesRepository: CapturedFacesRepository = CapturedFacesRepository(),
    private val knownPeopleRepository: KnownPeopleRepository = KnownPeopleRepository(),
    private val faceDetector: FaceDetector = FaceDetector()
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var locationManager: LocationManager? = null
    private var lastProcessTime = 0L
    private val processingInterval = 100L

    // Cache for person images to avoid repeated Firestore calls
    private val personImagesCache = mutableMapOf<String, List<com.example.crimicam.data.model.PersonImage>>()
    private var knownPeopleList = emptyList<com.example.crimicam.data.model.KnownPerson>()

    init {
        loadKnownPeopleAndImages()
    }

    fun initLocationManager(context: Context) {
        locationManager = LocationManager(context)
        _state.value = _state.value.copy(
            hasLocationPermission = locationManager?.hasLocationPermission() ?: false
        )
    }

    fun initDetector(context: Context) {
        Log.d("CameraViewModel", "✅ Face Detection System Online")
    }

    private fun loadKnownPeopleAndImages() {
        viewModelScope.launch {
            when (val result = knownPeopleRepository.getKnownPeople()) {
                is Result.Success -> {
                    knownPeopleList = result.data

                    // Update state with known people list
                    _state.value = _state.value.copy(knownPeople = knownPeopleList)

                    // Load all images for each person
                    knownPeopleList.forEach { person ->
                        when (val imagesResult = knownPeopleRepository.getPersonImages(person.id)) {
                            is Result.Success -> {
                                personImagesCache[person.id] = imagesResult.data
                            }
                            else -> {
                                Log.e("CameraViewModel", "Failed to load images for ${person.name}")
                            }
                        }
                    }

                    Log.d("CameraViewModel", "Loaded ${knownPeopleList.size} people with ${personImagesCache.values.sumOf { it.size }} images")
                }
                is Result.Error -> {
                    Log.e("CameraViewModel", "Failed to load known people: ${result.exception.message}")
                }
                is Result.Loading -> {}
            }
        }
    }

    fun processFrame(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastProcessTime < processingInterval) {
            return
        }
        lastProcessTime = currentTime

        if (_state.value.isProcessing) return

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isProcessing = true,
                    scanningMode = ScanningMode.DETECTING
                )

                // Detect all faces in frame
                val faces = faceDetector.detectFaces(bitmap)

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        scanningMode = ScanningMode.IDLE,
                        detectedFaces = emptyList(),
                        statusMessage = "🔍 Scanning for faces..."
                    )
                    return@launch
                }

                // Process detected faces
                val detectedFaces = mutableListOf<DetectedFace>()
                var hasUnknown = false
                var identifiedCount = 0

                faces.forEach { face ->
                    val boundingBox = face.boundingBox
                    val rectF = android.graphics.RectF(
                        boundingBox.left.toFloat(),
                        boundingBox.top.toFloat(),
                        boundingBox.right.toFloat(),
                        boundingBox.bottom.toFloat()
                    )

                    // Crop and extract features
                    val croppedFace = faceDetector.cropFace(bitmap, face)
                    val faceFeatures = faceDetector.extractFaceFeatures(face)

                    // Try to match against known people
                    var matchedPersonId: String? = null
                    var matchedPersonName: String? = null
                    var confidence = 0f

                    if (personImagesCache.isNotEmpty()) {
                        // Get all images from all people
                        val allImages = personImagesCache.values.flatten()

                        // Find best match
                        val (personId, _, matchConfidence) = FaceRecognizer.findBestMatch(
                            faceFeatures,
                            allImages,
                            threshold = 0.6f
                        )

                        if (personId != null) {
                            matchedPersonId = personId
                            matchedPersonName = knownPeopleList.find { it.id == personId }?.name
                            confidence = matchConfidence
                            identifiedCount++
                        } else {
                            hasUnknown = true
                        }
                    } else {
                        hasUnknown = true
                    }

                    // Save to captured_faces collection
                    saveCapturedFace(bitmap, croppedFace, faceFeatures, matchedPersonId, matchedPersonName, confidence)

                    detectedFaces.add(
                        DetectedFace(
                            boundingBox = rectF,
                            personId = matchedPersonId,
                            personName = matchedPersonName,
                            confidence = confidence
                        )
                    )
                }

                // Update status message based on results
                val statusMessage = when {
                    identifiedCount > 0 && !hasUnknown ->
                        "✅ ${identifiedCount} IDENTIFIED"
                    hasUnknown && identifiedCount > 0 ->
                        "⚠️ ${identifiedCount} KNOWN • ${detectedFaces.size - identifiedCount} UNKNOWN"
                    hasUnknown ->
                        "⚠️ ${detectedFaces.size} UNKNOWN SUBJECT${if (detectedFaces.size > 1) "S" else ""}"
                    else -> "🔍 ANALYZING..."
                }

                val scanningMode = when {
                    identifiedCount > 0 -> ScanningMode.IDENTIFIED
                    hasUnknown -> ScanningMode.UNKNOWN
                    else -> ScanningMode.ANALYZING
                }

                _state.value = _state.value.copy(
                    isProcessing = false,
                    scanningMode = scanningMode,
                    detectedFaces = detectedFaces,
                    statusMessage = statusMessage
                )

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    scanningMode = ScanningMode.IDLE,
                    statusMessage = "⚠️ Detection error"
                )
            }
        }
    }

    private fun saveCapturedFace(
        originalBitmap: Bitmap,
        croppedFace: Bitmap?,
        faceFeatures: Map<String, Float>,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float = 0f
    ) {
        viewModelScope.launch {
            try {
                val locationData = locationManager?.getCurrentLocation()

                capturedFacesRepository.saveCapturedFace(
                    originalBitmap = originalBitmap,
                    croppedFaceBitmap = croppedFace,
                    faceFeatures = faceFeatures,
                    isRecognized = matchedPersonId != null,
                    matchedPersonId = matchedPersonId,
                    matchedPersonName = matchedPersonName,
                    confidence = confidence,
                    latitude = locationData?.latitude,
                    longitude = locationData?.longitude,
                    locationAccuracy = locationData?.accuracy,
                    address = locationData?.address
                )
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to save face: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
    }
}