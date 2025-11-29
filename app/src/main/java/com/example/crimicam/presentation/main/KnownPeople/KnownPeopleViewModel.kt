package com.example.crimicam.presentation.main.KnownPeople

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.facerecognitionnetface.BitmapUtils
import com.example.crimicam.facerecognitionnetface.models.FaceNetModel
import com.example.crimicam.facerecognitionnetface.models.Models
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay
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

// Anchor data class for BlazeFace
data class Anchor(
    val x_center: Float,
    val y_center: Float,
    val w: Float,
    val h: Float
)

class KnownPeopleViewModel(
    private val repository: KnownPeopleRepository = KnownPeopleRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(KnownPeopleState())
    val state: StateFlow<KnownPeopleState> = _state.asStateFlow()

    // BlazeFace Model (same as CameraViewModel)
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

    // FaceNet Model (same as CameraViewModel)
    private var faceNetModel: FaceNetModel? = null

    init {
        loadKnownPeople()
    }

    /**
     * Initialize BlazeFace and FaceNet models
     */
    fun initFaceNetModel(context: Context) {
        viewModelScope.launch {
            try {
                Log.d("KnownPeopleVM", "🔄 Starting model initialization...")

                // Initialize BlazeFace
                initBlazeFace(context)

                // Generate anchors (CRITICAL!)
                generateAnchors()

                // Initialize FaceNet (same as CameraViewModel)
                val modelInfo = Models.FACENET
                faceNetModel = FaceNetModel(
                    context = context,
                    model = modelInfo,
                    useGpu = true,
                    useXNNPack = true
                )

                Log.d("KnownPeopleVM", "✅ BlazeFace & FaceNet Models Initialized")
            } catch (e: Exception) {
                Log.e("KnownPeopleVM", "Failed to initialize models: ${e.message}", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to initialize face recognition model"
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
            Log.d("KnownPeopleVM", "Loading BlazeFace model from: $modelPath")

            val options = Interpreter.Options()
            options.setNumThreads(4)

            try {
                options.setUseXNNPACK(true)
                blazeFaceInterpreter = Interpreter(FileUtil.loadMappedFile(context, modelPath), options)
                Log.d("KnownPeopleVM", "✅ BlazeFace initialized with XNNPack")
            } catch (e: Exception) {
                Log.w("KnownPeopleVM", "XNNPack failed, trying CPU-only: ${e.message}")
                val cpuOptions = Interpreter.Options()
                cpuOptions.setNumThreads(4)
                blazeFaceInterpreter = Interpreter(FileUtil.loadMappedFile(context, modelPath), cpuOptions)
                Log.d("KnownPeopleVM", "✅ BlazeFace initialized with CPU-only")
            }

        } catch (e: Exception) {
            Log.e("KnownPeopleVM", "❌ Failed to init BlazeFace: ${e.message}", e)
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

        Log.d("KnownPeopleVM", "✅ Generated ${anchors.size} anchors")
    }

    fun loadKnownPeople() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            when (val result = repository.getKnownPeople()) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        people = result.data
                    )
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.exception.message
                    )
                }
                is Result.Loading -> {
                    _state.value = _state.value.copy(isLoading = true)
                }
            }
        }
    }

    /**
     * Detect faces using BlazeFace with anchor-based decoding
     */
    private fun detectFacesWithBlazeFace(bitmap: Bitmap): List<RectF> {
        return try {
            val interpreter = blazeFaceInterpreter ?: run {
                Log.e("KnownPeopleVM", "❌ BlazeFace interpreter is null")
                return emptyList()
            }

            if (anchors.isEmpty()) {
                Log.e("KnownPeopleVM", "❌ Anchors not generated!")
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

            Log.d("KnownPeopleVM", "✅ Found ${detections.size} faces after decoding")
            return detections

        } catch (e: Exception) {
            Log.e("KnownPeopleVM", "❌ Error in detectFacesWithBlazeFace: ${e.message}", e)
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

        Log.d("KnownPeopleVM", "Decoded ${detections.size} detections above threshold")

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
     * Add a new person with their first image
     */
    fun processAndAddPerson(
        context: Context,
        imageUri: Uri,
        name: String,
        description: String
    ) {
        viewModelScope.launch {
            if (blazeFaceInterpreter == null || faceNetModel == null) {
                _state.value = _state.value.copy(
                    errorMessage = "Face recognition model not initialized"
                )
                return@launch
            }

            _state.value = _state.value.copy(isProcessing = true)

            try {
                // Stage 1: Loading Image
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)
                delay(300)

                val bitmap = com.example.crimicam.util.BitmapUtils.getBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }

                // Stage 2: Detecting Face with BlazeFace
                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                delay(500)

                val faces = detectFacesWithBlazeFace(bitmap)

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image. Please try another photo."
                    )
                    return@launch
                }

                val faceBox = faces.first()

                // Stage 3: Cropping Face
                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

                // Convert RectF to Rect for cropping
                val boundingBox = android.graphics.Rect(
                    faceBox.left.toInt(),
                    faceBox.top.toInt(),
                    faceBox.right.toInt(),
                    faceBox.bottom.toInt()
                )

                val croppedFace = BitmapUtils.cropRectFromBitmap(bitmap, boundingBox)

                // Stage 4: Extracting Features using FaceNet
                updateProgress(ProcessingStage.EXTRACTING_FEATURES, 0.7f)
                delay(400)

                val faceEmbedding = faceNetModel?.getFaceEmbedding(croppedFace)
                if (faceEmbedding == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to extract face features"
                    )
                    return@launch
                }

                // Convert FloatArray to Map<String, Float> for Firestore
                val faceFeatures = faceEmbedding.mapIndexed { index, value ->
                    "dim_$index" to value
                }.toMap()

                // Stage 5: Uploading
                updateProgress(ProcessingStage.UPLOADING, 0.9f)
                delay(300)

                // Create person and add first image
                when (val result = repository.addKnownPersonWithImage(
                    name = name,
                    description = description,
                    originalBitmap = bitmap,
                    croppedFaceBitmap = croppedFace,
                    faceFeatures = faceFeatures
                )) {
                    is Result.Success -> {
                        updateProgress(ProcessingStage.COMPLETE, 1.0f)
                        delay(300)

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingProgress = null,
                            people = listOf(result.data) + _state.value.people
                        )

                        Log.d("KnownPeopleVM", "✅ Added person: $name with ${faceEmbedding.size}-dim embedding")
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            errorMessage = result.exception.message
                        )
                    }
                    is Result.Loading -> {}
                }

            } catch (e: Exception) {
                Log.e("KnownPeopleVM", "Error processing person: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Add another image to an existing person
     */
    fun addImageToPerson(
        context: Context,
        personId: String,
        imageUri: Uri
    ) {
        viewModelScope.launch {
            if (blazeFaceInterpreter == null || faceNetModel == null) {
                _state.value = _state.value.copy(
                    errorMessage = "Face recognition model not initialized"
                )
                return@launch
            }

            _state.value = _state.value.copy(isProcessing = true)

            try {
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)
                delay(300)

                val bitmap = com.example.crimicam.util.BitmapUtils.getBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }

                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                delay(500)

                val faces = detectFacesWithBlazeFace(bitmap)

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image"
                    )
                    return@launch
                }

                val faceBox = faces.first()

                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

                val boundingBox = android.graphics.Rect(
                    faceBox.left.toInt(),
                    faceBox.top.toInt(),
                    faceBox.right.toInt(),
                    faceBox.bottom.toInt()
                )

                val croppedFace = BitmapUtils.cropRectFromBitmap(bitmap, boundingBox)

                updateProgress(ProcessingStage.EXTRACTING_FEATURES, 0.7f)
                delay(400)

                val faceEmbedding = faceNetModel?.getFaceEmbedding(croppedFace)
                if (faceEmbedding == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to extract face features"
                    )
                    return@launch
                }

                val faceFeatures = faceEmbedding.mapIndexed { index, value ->
                    "dim_$index" to value
                }.toMap()

                updateProgress(ProcessingStage.UPLOADING, 0.9f)
                delay(300)

                when (repository.addImageToPerson(
                    personId = personId,
                    originalBitmap = bitmap,
                    croppedFaceBitmap = croppedFace,
                    faceFeatures = faceFeatures
                )) {
                    is Result.Success -> {
                        updateProgress(ProcessingStage.COMPLETE, 1.0f)
                        delay(300)

                        _state.value = _state.value.copy(
                            isProcessing = false,
                            processingProgress = null
                        )

                        // Reload to get updated image count
                        loadKnownPeople()

                        Log.d("KnownPeopleVM", "✅ Added image to person: $personId")
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            errorMessage = "Failed to add image"
                        )
                    }
                    is Result.Loading -> {}
                }

            } catch (e: Exception) {
                Log.e("KnownPeopleVM", "Error adding image: ${e.message}", e)
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun updateProgress(stage: ProcessingStage, progress: Float) {
        _state.value = _state.value.copy(
            processingProgress = ProcessingProgress(stage, progress)
        )
    }

    fun deletePerson(personId: String) {
        viewModelScope.launch {
            when (repository.deleteKnownPerson(personId)) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        people = _state.value.people.filter { it.id != personId }
                    )
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to delete person"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deletePersonImage(personId: String, imageId: String) {
        viewModelScope.launch {
            when (repository.deletePersonImage(personId, imageId)) {
                is Result.Success -> {
                    loadKnownPeople() // Reload to update count
                }
                is Result.Error -> {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to delete image"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        blazeFaceInterpreter?.close()
        Log.d("KnownPeopleVM", "ViewModel cleared")
    }
}