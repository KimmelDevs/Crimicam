package com.example.crimicam.presentation.main.Home.Camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Anchor data class for BlazeFace
data class Anchor(
    val x_center: Float,
    val y_center: Float,
    val w: Float,
    val h: Float
)

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

    // BlazeFace anchor-based detection parameters
    private val anchors = mutableListOf<Anchor>()
    private val inputSize = 128
    private val numBoxes = 896
    private val NUM_COORDS = 16
    private val X_SCALE = 128.0f
    private val Y_SCALE = 128.0f
    private val H_SCALE = 128.0f
    private val W_SCALE = 128.0f
    private val MIN_SCORE_THRESH = 0.75f
    private val iouThreshold = 0.3f

    // FaceNet Model
    private var faceNetModel: FaceNetModel? = null

    // Recognition settings
    private val useMetric = "l2"
    private val l2Threshold = 1.2f
    private val cosineThreshold = 0.4f

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
                Log.d("CameraViewModel", "🔄 Starting model initialization...")

                // Initialize BlazeFace
                initBlazeFace(context)

                // Generate anchors (CRITICAL!)
                generateAnchors()

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
                Log.e("CameraViewModel", "❌ Failed to initialize models: ${e.message}", e)
                _state.value = _state.value.copy(
                    modelInitialized = false,
                    statusMessage = "⚠️ Model initialization failed: ${e.message}"
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
            Log.d("CameraViewModel", "Loading BlazeFace model from: $modelPath")

            val options = Interpreter.Options()
            options.setNumThreads(4)

            try {
                options.setUseXNNPACK(true)
                blazeFaceInterpreter = Interpreter(FileUtil.loadMappedFile(context, modelPath), options)
                Log.d("CameraViewModel", "✅ BlazeFace initialized with XNNPack")
            } catch (e: Exception) {
                Log.w("CameraViewModel", "XNNPack failed, trying CPU-only: ${e.message}")
                val cpuOptions = Interpreter.Options()
                cpuOptions.setNumThreads(4)
                blazeFaceInterpreter = Interpreter(FileUtil.loadMappedFile(context, modelPath), cpuOptions)
                Log.d("CameraViewModel", "✅ BlazeFace initialized with CPU-only")
            }

        } catch (e: Exception) {
            Log.e("CameraViewModel", "❌ Failed to init BlazeFace: ${e.message}", e)
            throw e
        }
    }

    /**
     * Generate anchors for BlazeFace-ShortRange
     */
    private fun generateAnchors() {
        anchors.clear()

        // BlazeFace-ShortRange anchor configuration
        val strides = listOf(8, 16)
        val anchorOffsets = listOf(0.5f, 0.5f)

        var layerId = 0
        while (layerId < strides.size) {
            val stride = strides[layerId]
            val offset = anchorOffsets[layerId]

            val featureMapHeight = (inputSize / stride)
            val featureMapWidth = (inputSize / stride)

            for (y in 0 until featureMapHeight) {
                for (x in 0 until featureMapWidth) {
                    // Each location has 2 anchors (aspect ratios 1:1)
                    repeat(2) {
                        val x_center = (x + offset) / featureMapWidth
                        val y_center = (y + offset) / featureMapHeight

                        anchors.add(
                            Anchor(
                                x_center = x_center,
                                y_center = y_center,
                                w = 1.0f,
                                h = 1.0f
                            )
                        )
                    }
                }
            }
            layerId++
        }

        Log.d("CameraViewModel", "✅ Generated ${anchors.size} anchors")
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
     * Load all known people and their face embeddings from Firestore
     */
    private fun loadKnownPeopleAndEmbeddings() {
        viewModelScope.launch {
            try {
                when (val result = knownPeopleRepository.getKnownPeople()) {
                    is Result.Success -> {
                        knownPeopleList = result.data
                        _state.value = _state.value.copy(knownPeople = knownPeopleList)

                        knownEmbeddingsCache.clear()

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
                                is Result.Loading -> {
                                    // Handle loading state if needed
                                    Log.d("CameraViewModel", "Loading images for ${person.name}...")
                                }
                            }
                        }

                        val totalEmbeddings = knownEmbeddingsCache.values.sumOf { it.size }
                        Log.d("CameraViewModel", "✅ Loaded ${knownPeopleList.size} people with $totalEmbeddings embeddings")
                    }
                    is Result.Error -> {
                        Log.e("CameraViewModel", "Failed to load known people: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        // Handle loading state if needed
                        Log.d("CameraViewModel", "Loading known people...")
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error loading known people: ${e.message}", e)
            }
        }
    }

    fun refreshKnownPeople() {
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

                val faces = detectFacesWithBlazeFace(bitmap)
                processFaces(faces, bitmap)

            } catch (e: Exception) {
                Log.e("CameraViewModel", "❌ Error in processFrame: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    scanningMode = ScanningMode.IDLE,
                    statusMessage = "⚠️ Error: ${e.message?.take(50) ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Detect faces using BlazeFace with anchor-based decoding
     */
    private fun detectFacesWithBlazeFace(bitmap: Bitmap): List<RectF> {
        return try {
            val interpreter = blazeFaceInterpreter ?: run {
                Log.e("CameraViewModel", "❌ BlazeFace interpreter is null")
                return emptyList()
            }

            if (anchors.isEmpty()) {
                Log.e("CameraViewModel", "❌ Anchors not generated!")
                return emptyList()
            }

            // Resize and normalize input
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)

            // BlazeFace outputs:
            // regressors: [1, 896, 16] - bounding box coordinates + keypoints
            // classificators: [1, 896, 1] - face confidence scores
            val outputRegressors = Array(1) { Array(numBoxes) { FloatArray(NUM_COORDS) } }
            val outputScores = Array(1) { Array(numBoxes) { FloatArray(1) } }

            val outputs = mapOf(
                0 to outputRegressors,
                1 to outputScores
            )

            // Run inference
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

            // Decode detections using anchors
            val detections = decodeDetections(
                outputRegressors[0],
                outputScores[0],
                bitmap.width,
                bitmap.height
            )

            Log.d("CameraViewModel", "✅ Found ${detections.size} faces after decoding")
            return detections

        } catch (e: Exception) {
            Log.e("CameraViewModel", "❌ Error in detectFacesWithBlazeFace: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Decode anchor-based predictions into bounding boxes
     */
    private fun decodeDetections(
        rawBoxes: Array<FloatArray>,
        rawScores: Array<FloatArray>,
        imageWidth: Int,
        imageHeight: Int
    ): List<RectF> {
        val detections = mutableListOf<Pair<RectF, Float>>()

        for (i in rawScores.indices) {
            val score = 1.0f / (1.0f + exp(-rawScores[i][0])) // Sigmoid activation

            if (score < MIN_SCORE_THRESH) continue

            val anchor = anchors[i]
            val rawBox = rawBoxes[i]

            // Decode box coordinates from anchor-relative predictions
            val y_center = rawBox[0] / Y_SCALE * anchor.h + anchor.y_center
            val x_center = rawBox[1] / X_SCALE * anchor.w + anchor.x_center
            val h = exp(rawBox[2] / H_SCALE) * anchor.h
            val w = exp(rawBox[3] / W_SCALE) * anchor.w

            // Convert to corner coordinates
            val ymin = (y_center - h / 2.0f) * imageHeight
            val xmin = (x_center - w / 2.0f) * imageWidth
            val ymax = (y_center + h / 2.0f) * imageHeight
            val xmax = (x_center + w / 2.0f) * imageWidth

            // Clamp to image bounds
            val box = RectF(
                xmin.coerceIn(0f, imageWidth.toFloat()),
                ymin.coerceIn(0f, imageHeight.toFloat()),
                xmax.coerceIn(0f, imageWidth.toFloat()),
                ymax.coerceIn(0f, imageHeight.toFloat())
            )

            detections.add(Pair(box, score))
        }

        Log.d("CameraViewModel", "Decoded ${detections.size} detections above threshold")

        // Apply NMS
        return weightedNonMaxSuppression(detections)
    }

    /**
     * Weighted Non-Maximum Suppression (as used by BlazeFace)
     */
    private fun weightedNonMaxSuppression(
        detections: List<Pair<RectF, Float>>,
        iouThreshold: Float = 0.3f
    ): List<RectF> {
        if (detections.isEmpty()) return emptyList()

        val sortedDetections = detections.sortedByDescending { it.second }.toMutableList()
        val outputBoxes = mutableListOf<RectF>()

        while (sortedDetections.isNotEmpty()) {
            val best = sortedDetections.removeAt(0)
            outputBoxes.add(best.first)

            sortedDetections.removeAll { candidate ->
                calculateIoU(best.first, candidate.first) > iouThreshold
            }
        }

        return outputBoxes
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
        try {
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
                    val rect = android.graphics.Rect(
                        boundingBox.left.toInt(),
                        boundingBox.top.toInt(),
                        boundingBox.right.toInt(),
                        boundingBox.bottom.toInt()
                    )

                    val croppedFace = BitmapUtils.cropRectFromBitmap(bitmap, rect)
                    val faceEmbedding = faceNetModel?.getFaceEmbedding(croppedFace)

                    if (faceEmbedding == null) {
                        Log.w("CameraViewModel", "⚠️ Could not get face embedding")
                        continue
                    }

                    val matchResult = findBestMatch(faceEmbedding)

                    if (matchResult != null) {
                        identifiedCount++
                        Log.d("CameraViewModel", "✅ Face recognized: ${matchResult.personName} (${(matchResult.confidence * 100).toInt()}%)")
                    } else {
                        hasUnknown = true
                        Log.d("CameraViewModel", "⚠️ Unknown face detected")
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
                    Log.e("CameraViewModel", "❌ Error processing individual face: ${e.message}", e)
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

        } catch (e: Exception) {
            Log.e("CameraViewModel", "❌ Error in processFaces: ${e.message}", e)
            _state.value = _state.value.copy(
                isProcessing = false,
                scanningMode = ScanningMode.IDLE,
                statusMessage = "⚠️ Processing error"
            )
        }
    }

    private data class MatchResult(
        val personId: String,
        val personName: String,
        val confidence: Float,
        val distance: Float
    )

    private fun findBestMatch(faceEmbedding: FloatArray): MatchResult? {
        if (knownEmbeddingsCache.isEmpty()) {
            Log.d("CameraViewModel", "ℹ️ No known embeddings in cache")
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

        Log.d("CameraViewModel", "❌ No match found. Best distance: $bestDistance, threshold: $l2Threshold")
        return null
    }

    private fun l2Distance(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size)
        return sqrt(
            embedding1.indices.sumOf { i ->
                (embedding1[i] - embedding2[i]).toDouble().pow(2.0)
            }.toFloat()
        )
    }

    private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size)

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
                Log.d("CameraViewModel", "💾 Starting to save captured face...")

                val locationData = locationManager?.getCurrentLocation()
                Log.d("CameraViewModel", "📍 Location data: $locationData")

                // Convert face embedding to map with proper field names
                val faceFeatures = faceEmbedding.mapIndexed { index, value ->
                    "dim_$index" to value
                }.toMap()

                Log.d("CameraViewModel", "📊 Face features size: ${faceFeatures.size}")
                Log.d("CameraViewModel", "👤 Recognition info: isRecognized=${matchedPersonId != null}, person=$matchedPersonName, confidence=$confidence")

                val result = capturedFacesRepository.saveCapturedFace(
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

                when (result) {
                    is Result.Success -> {
                        Log.d("CameraViewModel", "✅ Face saved successfully with ID: ${result.data}")
                    }
                    is Result.Error -> {
                        Log.e("CameraViewModel", "❌ Failed to save face: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        // Handle loading state if needed
                        Log.d("CameraViewModel", "🔄 Saving face...")
                    }
                }

            } catch (e: Exception) {
                Log.e("CameraViewModel", "❌ Error in saveCapturedFace: ${e.message}", e)
            }
        }
    }

    /**
     * Debug function to check Firestore data
     */
    fun debugCheckFirestoreData() {
        viewModelScope.launch {
            Log.d("CameraViewModel", "🔍 DEBUG: Checking Firestore data...")
            val result = capturedFacesRepository.debugCheckFirestoreData()
            when (result) {
                is Result.Success -> {
                    Log.d("CameraViewModel", "✅ DEBUG: Found ${result.data.size} documents")
                }
                is Result.Error -> {
                    Log.e("CameraViewModel", "❌ DEBUG Error: ${result.exception.message}")
                }
                is Result.Loading -> {
                    Log.d("CameraViewModel", "🔄 DEBUG: Loading Firestore data...")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        blazeFaceInterpreter?.close()
        knownPeopleListener?.remove()
    }
}