package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class DetectedFace(
    val boundingBox: RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float,
    val distance: Float = 0f
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
    private val processingInterval = 300L

    // BlazeFace Model
    private var blazeFaceInterpreter: Interpreter? = null

    // BlazeFace input/output specs
    private val inputSize = 128
    private val numBoxes = 896
    private val numCoords = 16
    private val faceDetectionThreshold = 0.7f
    private val iouThreshold = 0.3f

    // FaceNet Model
    private var faceNetModel: FaceNetModel? = null

    // Recognition settings
    private val useMetric = "l2"
    private val l2Threshold = 1.0f
    private val cosineThreshold = 0.6f

    // Cache for known people embeddings
    private val knownEmbeddingsCache = mutableMapOf<String, List<FloatArray>>()
    private var knownPeopleList = emptyList<com.example.crimicam.data.model.KnownPerson>()

    // Firestore listener for real-time updates
    private var knownPeopleListener: ListenerRegistration? = null

    init {
        setupKnownPeopleListener()
    }

    fun initLocationManager(context: Context) {
        locationManager = LocationManager(context)
        _state.value = _state.value.copy(
            hasLocationPermission = locationManager?.hasLocationPermission() ?: false
        )
    }

    /**
     * Initialize BlazeFace and FaceNet models
     */
    fun initDetector(context: Context) {
        viewModelScope.launch {
            try {
                // Initialize BlazeFace
                initBlazeFace(context)

                // Initialize FaceNet
                val modelInfo = Models.FACENET
                faceNetModel = FaceNetModel(
                    context = context,
                    model = modelInfo,
                    useGpu = true,
                    useXNNPack = true
                )

                _state.value = _state.value.copy(modelInitialized = true)
                Log.d("CameraViewModel", "✅ BlazeFace & FaceNet Models Initialized")
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Failed to initialize models: ${e.message}", e)
                _state.value = _state.value.copy(
                    modelInitialized = false,
                    statusMessage = "⚠️ Model initialization failed"
                )
            }
        }
    }

    /**
     * Initialize BlazeFace TFLite model
     */
    private fun initBlazeFace(context: Context) {
        try {
            val modelPath = "blaze_face_short_range.tflite"

            val options = Interpreter.Options()
            options.setNumThreads(4)

            // Use NNAPI delegate instead of GPU delegate
            options.setUseNNAPI(true)

            // Alternative: Use XNNPack delegate (more compatible)
            // options.setUseXNNPACK(true)

            blazeFaceInterpreter = Interpreter(FileUtil.loadMappedFile(context, modelPath), options)
            Log.d("CameraViewModel", "✅ BlazeFace initialized with NNAPI")
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to init BlazeFace: ${e.message}", e)
            throw e
        }
    }

    /**
     * Setup real-time listener for known people changes
     */
    private fun setupKnownPeopleListener() {
        val db = FirebaseFirestore.getInstance()

        knownPeopleListener = db.collection("known_people")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CameraViewModel", "Error listening to known people: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("CameraViewModel", "🔄 Known people collection changed, refreshing...")
                    loadKnownPeopleAndEmbeddings()
                }
            }
    }

    /**
     * Load TFLite model from assets (no longer needed with FileUtil)
     */

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

                        // Clear cache before reloading
                        knownEmbeddingsCache.clear()

                        // Load all embeddings for each person
                        knownPeopleList.forEach { person ->
                            when (val imagesResult = knownPeopleRepository.getPersonImages(person.id)) {
                                is Result.Success -> {
                                    val embeddings = imagesResult.data.mapNotNull { personImage ->
                                        try {
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
     * This is now automatically handled by Firestore listener
     */
    fun refreshKnownPeople() {
        // This method is kept for backward compatibility
        // but the Firestore listener already handles automatic updates
        Log.d("CameraViewModel", "Manual refresh requested (auto-refresh is active)")
    }

    /**
     * Process each camera frame
     */
    fun processFrame(bitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastProcessTime < processingInterval) {
            return
        }
        lastProcessTime = currentTime

        if (_state.value.isProcessing || blazeFaceInterpreter == null || faceNetModel == null) {
            return
        }

        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isProcessing = true,
                    scanningMode = ScanningMode.DETECTING
                )

                // Detect faces using BlazeFace
                val faces = detectFacesWithBlazeFace(bitmap)
                processFaces(faces, bitmap)

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
     * Detect faces using BlazeFace
     */
    private fun detectFacesWithBlazeFace(bitmap: Bitmap): List<RectF> {
        val interpreter = blazeFaceInterpreter ?: return emptyList()

        // Resize and normalize input
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resizedBitmap)

        // Prepare output buffers
        val outputBoxes = Array(1) { Array(numBoxes) { FloatArray(numCoords) } }
        val outputScores = Array(1) { FloatArray(numBoxes) }

        val outputs = mutableMapOf<Int, Any>()
        outputs[0] = outputBoxes
        outputs[1] = outputScores

        // Run inference
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        // Post-process detections
        return postProcessDetections(
            outputBoxes[0],
            outputScores[0],
            bitmap.width,
            bitmap.height
        )
    }

    /**
     * Convert bitmap to ByteBuffer for BlazeFace input
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                // Normalize to [-1, 1]
                byteBuffer.putFloat(((value shr 16 and 0xFF) / 127.5f - 1.0f))
                byteBuffer.putFloat(((value shr 8 and 0xFF) / 127.5f - 1.0f))
                byteBuffer.putFloat(((value and 0xFF) / 127.5f - 1.0f))
            }
        }

        return byteBuffer
    }

    /**
     * Post-process BlazeFace detections with NMS
     */
    private fun postProcessDetections(
        boxes: Array<FloatArray>,
        scores: FloatArray,
        imageWidth: Int,
        imageHeight: Int
    ): List<RectF> {
        val detections = mutableListOf<Pair<RectF, Float>>()

        // Filter by threshold
        for (i in scores.indices) {
            if (scores[i] > faceDetectionThreshold) {
                val box = boxes[i]

                // Convert normalized coords to image coords
                val ymin = (box[0] * imageHeight).coerceIn(0f, imageHeight.toFloat())
                val xmin = (box[1] * imageWidth).coerceIn(0f, imageWidth.toFloat())
                val ymax = (box[2] * imageHeight).coerceIn(0f, imageHeight.toFloat())
                val xmax = (box[3] * imageWidth).coerceIn(0f, imageWidth.toFloat())

                detections.add(Pair(RectF(xmin, ymin, xmax, ymax), scores[i]))
            }
        }

        // Apply Non-Maximum Suppression
        return nonMaxSuppression(detections, iouThreshold)
    }

    /**
     * Non-Maximum Suppression
     */
    private fun nonMaxSuppression(
        detections: List<Pair<RectF, Float>>,
        iouThreshold: Float
    ): List<RectF> {
        val sortedDetections = detections.sortedByDescending { it.second }
        val selectedBoxes = mutableListOf<RectF>()

        for (detection in sortedDetections) {
            val box = detection.first
            var shouldSelect = true

            for (selectedBox in selectedBoxes) {
                if (calculateIoU(box, selectedBox) > iouThreshold) {
                    shouldSelect = false
                    break
                }
            }

            if (shouldSelect) {
                selectedBoxes.add(box)
            }
        }

        return selectedBoxes
    }

    /**
     * Calculate Intersection over Union
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        val intersectionArea = max(0f, intersectionRight - intersectionLeft) *
                max(0f, intersectionBottom - intersectionTop)

        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)

        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    /**
     * Process detected faces
     */
    private suspend fun processFaces(faceBoxes: List<RectF>, bitmap: Bitmap) {
        if (faceBoxes.isEmpty()) {
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

        for (boundingBox in faceBoxes) {
            try {
                // Convert RectF to Rect for cropping
                val rect = android.graphics.Rect(
                    boundingBox.left.toInt(),
                    boundingBox.top.toInt(),
                    boundingBox.right.toInt(),
                    boundingBox.bottom.toInt()
                )

                val croppedFace = BitmapUtils.cropRectFromBitmap(bitmap, rect)
                val faceEmbedding = faceNetModel?.getFaceEmbedding(croppedFace) ?: continue

                val matchResult = findBestMatch(faceEmbedding)

                if (matchResult != null) {
                    identifiedCount++
                } else {
                    hasUnknown = true
                }

                saveCapturedFace(
                    originalBitmap = bitmap,
                    croppedFace = croppedFace,
                    faceEmbedding = faceEmbedding,
                    matchedPersonId = matchResult?.personId,
                    matchedPersonName = matchResult?.personName,
                    confidence = matchResult?.confidence ?: 0f,
                    distance = matchResult?.distance ?: 0f
                )

                detectedFaces.add(
                    DetectedFace(
                        boundingBox = boundingBox,
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
                            bestDistance = 1f - similarity
                            bestPersonId = personId
                        }
                    }
                }
            }
        }

        val isMatch = when (useMetric) {
            "l2" -> bestDistance < l2Threshold
            "cosine" -> bestSimilarity > cosineThreshold
            else -> false
        }

        if (isMatch && bestPersonId != null) {
            bestPersonName = knownPeopleList.find { it.id == bestPersonId }?.name

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

    private fun l2Distance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have same size" }
        return sqrt(
            embedding1.indices.sumOf { i ->
                (embedding1[i] - embedding2[i]).toDouble().pow(2.0)
            }.toFloat()
        )
    }

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
        blazeFaceInterpreter?.close()
        knownPeopleListener?.remove()
        Log.d("CameraViewModel", "ViewModel cleared")
    }
}