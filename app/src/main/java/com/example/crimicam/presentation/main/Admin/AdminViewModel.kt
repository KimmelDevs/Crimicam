package com.example.crimicam.presentation.main.Admin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.CriminalsRepository
import com.example.crimicam.util.BitmapUtils
import com.example.crimicam.util.Result
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdminViewModel(
    private val repository: CriminalsRepository = CriminalsRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    private var faceNetInterpreter: Interpreter? = null

    init {
        loadCriminals()
    }

    // Initialize FaceNet model (call this in your UI when AdminViewModel is created)
    fun initFaceNetModel(context: Context) {
        try {
            val modelFile = context.assets.openFd("facenet.tflite") // or mobilefacenet.tflite
            val inputStream = modelFile.createInputStream()
            val modelBytes = ByteArray(modelFile.declaredLength.toInt())
            inputStream.read(modelBytes)

            val byteBuffer = ByteBuffer.allocateDirect(modelBytes.size)
            byteBuffer.order(ByteOrder.nativeOrder())
            byteBuffer.put(modelBytes)

            faceNetInterpreter = Interpreter(byteBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = _state.value.copy(
                errorMessage = "Failed to load face recognition model: ${e.message}"
            )
        }
    }

    fun loadCriminals() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            when (val result = repository.getAllCriminals()) {
                is Result.Success -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        criminals = result.data
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

    fun addCriminal(
        context: Context,
        imageUri: Uri,
        firstName: String,
        lastName: String,
        middleName: String = "",
        dateOfBirth: String,
        gender: String,
        nationality: String,
        nationalId: String = "",
        height: Int,
        weight: Int,
        eyeColor: String,
        hairColor: String,
        build: String,
        skinTone: String,
        lastKnownAddress: String = "",
        currentCity: String = "",
        currentProvince: String = "",
        status: String,
        riskLevel: String,
        isArmed: Boolean = false,
        isDangerous: Boolean = false,
        notes: String = ""
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true)

            try {
                // Check if model is initialized
                if (faceNetInterpreter == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Face recognition model not initialized"
                    )
                    return@launch
                }

                // Load image from URI
                val bitmap = BitmapUtils.getBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }

                // STEP 1: Detect face in the image
                val faceDetectorOptions = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                val detector = FaceDetection.getClient(faceDetectorOptions)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val faces = detector.process(inputImage).await()

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image. Please use a clear photo with a visible face."
                    )
                    return@launch
                }

                // Use the first detected face
                val face = faces[0]
                val boundingBox = face.boundingBox

                // STEP 2: Crop face from image
                val croppedFace = Bitmap.createBitmap(
                    bitmap,
                    boundingBox.left.coerceAtLeast(0),
                    boundingBox.top.coerceAtLeast(0),
                    boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                    boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
                )

                // STEP 3: Extract face embeddings
                val embeddings = extractFaceEmbeddings(croppedFace)
                if (embeddings == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to extract face features"
                    )
                    return@launch
                }

                // STEP 4: Convert embeddings to map for storage
                val faceFeatures = embeddings.mapIndexed { index, value ->
                    "embedding_$index" to value
                }.toMap()

                // STEP 5: Add criminal to repository with embeddings
                when (val result = repository.addCriminal(
                    firstName = firstName,
                    lastName = lastName,
                    middleName = middleName,
                    dateOfBirth = dateOfBirth,
                    gender = gender,
                    nationality = nationality,
                    nationalId = nationalId,
                    height = height,
                    weight = weight,
                    eyeColor = eyeColor,
                    hairColor = hairColor,
                    build = build,
                    skinTone = skinTone,
                    lastKnownAddress = lastKnownAddress,
                    currentCity = currentCity,
                    currentProvince = currentProvince,
                    status = status,
                    riskLevel = riskLevel,
                    isArmed = isArmed,
                    isDangerous = isDangerous,
                    mugshotBitmap = bitmap,
                    croppedFaceBitmap = croppedFace, // Save cropped face
                    faceFeatures = faceFeatures, // Save embeddings!
                    notes = notes
                )) {
                    is Result.Success -> {
                        _state.value = _state.value.copy(
                            isProcessing = false,
                            criminals = listOf(result.data) + _state.value.criminals
                        )
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
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    // Extract face embeddings using FaceNet model
    private fun extractFaceEmbeddings(faceBitmap: Bitmap): FloatArray? {
        return try {
            val interpreter = faceNetInterpreter ?: return null

            // Resize face to model input size (usually 160x160 for FaceNet)
            val inputSize = 160
            val resizedFace = Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)

            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            inputBuffer.order(ByteOrder.nativeOrder())

            // Normalize and fill buffer
            val pixels = IntArray(inputSize * inputSize)
            resizedFace.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (pixel in pixels) {
                // Normalize to [-1, 1] or [0, 1] depending on your model
                val r = ((pixel shr 16 and 0xFF) / 255.0f - 0.5f) / 0.5f
                val g = ((pixel shr 8 and 0xFF) / 255.0f - 0.5f) / 0.5f
                val b = ((pixel and 0xFF) / 255.0f - 0.5f) / 0.5f

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            // Run inference
            val outputSize = 128 // or 512 depending on your model
            val output = Array(1) { FloatArray(outputSize) }
            interpreter.run(inputBuffer, output)

            // Return embeddings
            output[0]

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        faceNetInterpreter?.close()
    }
}