package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.CapturedFacesRepository
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.facerecognitionnetface.BitmapUtils
import com.example.crimicam.facerecognitionnetface.models.FaceNetModel
import com.example.crimicam.facerecognitionnetface.models.Models
import com.example.crimicam.util.LocationManager
import com.example.crimicam.util.Result
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

data class DetectedFace(
    val boundingBox: android.graphics.RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float,
    val distance: Float = 0f // L2 distance or cosine similarity
)

data class CameraState(
    val isProcessing: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val scanningMode: ScanningMode = ScanningMode.IDLE,
    val statusMessage: String = "🔍 Scanning for faces...",
    val hasLocationPermission: Boolean = false,
    val knownPeople: List<com.example.crimicam.data.model.KnownPerson> = emptyList(),
    val modelInitialized: Boolean = false
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
    private val knownPeopleRepository: KnownPeopleRepository = KnownPeopleRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    private var locationManager: LocationManager? = null
    private var lastProcessTime = 0L
    private val processingInterval = 300L // Process every 300ms

    // FaceNet Model
    private var faceNetModel: FaceNetModel? = null

    // ML Kit Face Detector
    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()
    private val faceDetector = FaceDetection.getClient(realTimeOpts)

    // Recognition settings
    private val useMetric = "l2" // "l2" or "cosine"
    private val l2Threshold = 1.0f // Adjust based on your model
    private val cosineThreshold = 0.6f // Adjust based on your model

    // Cache for known people embeddings
    // Map<PersonId, List<Embedding>>
    private val knownEmbeddingsCache = mutableMapOf<String, List<FloatArray>>()
    private var knownPeopleList = emptyList<com.example.crimicam.data.model.KnownPerson>()

    init {
        loadKnownPeopleAndEmbeddings()
    }

    fun initLocationManager(context: Context) {
        locationManager = LocationManager(context)
        _state.value = _state.value.copy(
            hasLocationPermission = locationManager?.hasLocationPermission() ?: false
        )
    }

    /**
     * Initialize FaceNet model with GPU support
     */
    fun initDetector(context: Context) {
        viewModelScope.launch {
            try {
                // Choose a model from Models.kt
                // Options: FACENET, FACENET_512, FACENET_QUANTIZED, FACENET_512_QUANTIZED
                val modelInfo = Models.FACENET // 128-dim embeddings

                faceNetModel = FaceNetModel(
                    context = context,
                    model = modelInfo,
                    useGpu = true,
                    useXNNPack = true
                )

                _state.value = _state.value.copy(modelInitialized = true)
                Log.d("CameraViewModel", "✅ FaceNet Model Initialized: ${modelInfo.name}")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to initialize FaceNet: ${e.message}", e)
                _state.value = _state.value.copy(
                    modelInitialized = false,
                    statusMessage = "⚠️ Model initialization failed"
                )
            }
        }
    }

    /**
     * Load all known people and their face embeddings from Firestore
     */
    private fun loadKnownPeopleAndEmbeddings() {
        viewModelScope.launch {
            try {
                when (val result = knownPeopleRepository.getKnownPeople()) {
                    is Result.Success -> {
                        knownPeopleList = result.data
                        _state.value = _state.value.copy(knownPeople = knownPeopleList)

                        // Load all embeddings for each person
                        knownPeopleList.forEach { person ->
                            when (val imagesResult = knownPeopleRepository.getPersonImages(person.id)) {
                                is Result.Success -> {
                                    val embeddings = imagesResult.data.mapNotNull { personImage ->
                                        try {
                                            // Convert Map<String, Float> to FloatArray
                                            // Ensure correct order: dim_0, dim_1, dim_2, ...
                                            val sortedFeatures = personImage.faceFeatures.toList()
                                                .sortedBy { it.first.removePrefix("dim_").toIntOrNull() ?: Int.MAX_VALUE }
                                                .map { it.second }

                                            sortedFeatures.toFloatArray()
                                        } catch (e: Exception) {
                                            Log.e("CameraViewModel", "Failed to parse embedding: ${e.message}")
                                            null
                                        }
                                    }

                                    if (embeddings.isNotEmpty()) {
                                        knownEmbeddingsCache[person.id] = embeddings
                                    }
                                }
                                is Result.Error -> {
                                    Log.e("CameraViewModel", "Failed to load images for ${person.name}: ${imagesResult.exception.message}")
                                }
                                is Result.Loading -> {}
                            }
                        }

                        val totalEmbeddings = knownEmbeddingsCache.values.sumOf { it.size }
                        Log.d("CameraViewModel", "✅ Loaded ${knownPeopleList.size} people with $totalEmbeddings embeddings")
                    }
                    is Result.Error -> {
                        Log.e("CameraViewModel", "Failed to load known people: ${result.exception.message}")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error loading known people: ${e.message}", e)
            }
        }
    }

    /**
     * Refresh known people cache (call this when new people are added)
     */
    fun refreshKnownPeople() {
        knownEmbeddingsCache.clear()
        loadKnownPeopleAndEmbeddings()
    }

    /**
     * Process each camera frame
     */
    fun processFrame(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()

        // Throttle processing
        if (currentTime - lastProcessTime < processingInterval) {
            return
        }
        lastProcessTime = currentTime

        // Skip if already processing or model not initialized
        if (_state.value.isProcessing || faceNetModel == null) {
            return
        }

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isProcessing = true,
                    scanningMode = ScanningMode.DETECTING
                )

                // Detect all faces using ML Kit
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        viewModelScope.launch {
                            processFaces(faces, bitmap)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CameraViewModel", "Face detection failed: ${e.message}", e)
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            scanningMode = ScanningMode.IDLE,
                            statusMessage = "⚠️ Detection error"
                        )
                    }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error in processFrame: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    scanningMode = ScanningMode.IDLE,
                    statusMessage = "⚠️ Processing error"
                )
            }
        }
    }

    /**
     * Process detected faces
     */
    private suspend fun processFaces(faces: List<Face>, bitmap: Bitmap) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(
                isProcessing = false,
                scanningMode = ScanningMode.IDLE,
                detectedFaces = emptyList(),
                statusMessage = "🔍 Scanning for faces..."
            )
            return
        }

        _state.value = _state.value.copy(scanningMode = ScanningMode.ANALYZING)

        val detectedFaces = mutableListOf<DetectedFace>()
        var hasUnknown = false
        var identifiedCount = 0

        for (face in faces) {
            try {
                val boundingBox = face.boundingBox

                // Crop the face from bitmap
                val croppedFace = BitmapUtils.cropRectFromBitmap(bitmap, boundingBox)

                // Extract face embedding using FaceNet
                val faceEmbedding = faceNetModel?.getFaceEmbedding(croppedFace) ?: continue

                // Try to match against known people
                val matchResult = findBestMatch(faceEmbedding)

                if (matchResult != null) {
                    identifiedCount++
                } else {
                    hasUnknown = true
                }

                // Save to captured_faces collection
                saveCapturedFace(
                    originalBitmap = bitmap,
                    croppedFace = croppedFace,
                    faceEmbedding = faceEmbedding,
                    matchedPersonId = matchResult?.personId,
                    matchedPersonName = matchResult?.personName,
                    confidence = matchResult?.confidence ?: 0f,
                    distance = matchResult?.distance ?: 0f
                )

                // Convert Rect to RectF for display
                val rectF = android.graphics.RectF(
                    boundingBox.left.toFloat(),
                    boundingBox.top.toFloat(),
                    boundingBox.right.toFloat(),
                    boundingBox.bottom.toFloat()
                )

                detectedFaces.add(
                    DetectedFace(
                        boundingBox = rectF,
                        personId = matchResult?.personId,
                        personName = matchResult?.personName,
                        confidence = matchResult?.confidence ?: 0f,
                        distance = matchResult?.distance ?: 0f
                    )
                )

            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error processing face: ${e.message}", e)
                continue
            }
        }

        // Update status message
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
    }

    /**
     * Find best matching person for the given face embedding
     */
    private data class MatchResult(
        val personId: String,
        val personName: String,
        val confidence: Float,
        val distance: Float
    )

    private fun findBestMatch(faceEmbedding: FloatArray): MatchResult? {
        if (knownEmbeddingsCache.isEmpty()) {
            return null
        }

        var bestPersonId: String? = null
        var bestPersonName: String? = null
        var bestDistance = Float.MAX_VALUE
        var bestSimilarity = 0f

        // Compare against all known embeddings
        knownEmbeddingsCache.forEach { (personId, embeddings) ->
            embeddings.forEach { knownEmbedding ->
                when (useMetric) {
                    "l2" -> {
                        val distance = l2Distance(faceEmbedding, knownEmbedding)
                        if (distance < bestDistance) {
                            bestDistance = distance
                            bestPersonId = personId
                        }
                    }
                    "cosine" -> {
                        val similarity = cosineSimilarity(faceEmbedding, knownEmbedding)
                        if (similarity > bestSimilarity) {
                            bestSimilarity = similarity
                            bestDistance = 1f - similarity // Convert to distance
                            bestPersonId = personId
                        }
                    }
                }
            }
        }

        // Check if match exceeds threshold
        val isMatch = when (useMetric) {
            "l2" -> bestDistance < l2Threshold
            "cosine" -> bestSimilarity > cosineThreshold
            else -> false
        }

        if (isMatch && bestPersonId != null) {
            bestPersonName = knownPeopleList.find { it.id == bestPersonId }?.name

            // Calculate confidence (0-1 scale)
            val confidence = when (useMetric) {
                "l2" -> (1f - (bestDistance / l2Threshold)).coerceIn(0f, 1f)
                "cosine" -> bestSimilarity
                else -> 0f
            }

            return MatchResult(
                personId = bestPersonId!!,
                personName = bestPersonName ?: "Unknown",
                confidence = confidence,
                distance = bestDistance
            )
        }

        return null
    }

    /**
     * Calculate L2 (Euclidean) distance between two embeddings
     */
    private fun l2Distance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }
        return sqrt(
            embedding1.indices.sumOf { i ->
                (embedding1[i] - embedding2[i]).toDouble().pow(2.0)
            }.toFloat()
        )
    }

    /**
     * Calculate cosine similarity between two embeddings
     * Returns value between 0 (not similar) and 1 (identical)
     */
    private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Save captured face to Firestore
     */
    private fun saveCapturedFace(
        originalBitmap: Bitmap,
        croppedFace: Bitmap,
        faceEmbedding: FloatArray,
        matchedPersonId: String? = null,
        matchedPersonName: String? = null,
        confidence: Float = 0f,
        distance: Float = 0f
    ) {
        viewModelScope.launch {
            try {
                val locationData = locationManager?.getCurrentLocation()

                // Convert FloatArray to Map<String, Float> for Firestore
                val faceFeatures = faceEmbedding.mapIndexed { index, value ->
                    "dim_$index" to value
                }.toMap()

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

                Log.d("CameraViewModel", "✅ Saved face - ${matchedPersonName ?: "Unknown"} (confidence: $confidence, distance: $distance)")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to save face: ${e.message}", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
        // FaceNetModel's interpreter will be garbage collected
        Log.d("CameraViewModel", "ViewModel cleared")
    }
}