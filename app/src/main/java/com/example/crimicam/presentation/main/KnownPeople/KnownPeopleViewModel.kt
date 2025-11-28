package com.example.crimicam.presentation.main.KnownPeople

import android.content.Context
import android.graphics.Bitmap
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class KnownPeopleViewModel(
    private val repository: KnownPeopleRepository = KnownPeopleRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(KnownPeopleState())
    val state: StateFlow<KnownPeopleState> = _state.asStateFlow()

    // Use the SAME FaceNet model as CameraViewModel
    private var faceNetModel: FaceNetModel? = null

    // Use ML Kit for face detection (same as CameraViewModel)
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setMinFaceSize(0.15f)
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    init {
        loadKnownPeople()
    }

    /**
     * Initialize FaceNet model (call this from your Fragment/Activity)
     */
    fun initFaceNetModel(context: Context) {
        viewModelScope.launch {
            try {
                // Use the SAME model as CameraViewModel
                val modelInfo = Models.FACENET // 128-dim embeddings

                faceNetModel = FaceNetModel(
                    context = context,
                    model = modelInfo,
                    useGpu = true,
                    useXNNPack = true
                )

                Log.d("KnownPeopleVM", "✅ FaceNet Model Initialized")
            } catch (e: Exception) {
                Log.e("KnownPeopleVM", "Failed to initialize FaceNet: ${e.message}", e)
                _state.value = _state.value.copy(
                    errorMessage = "Failed to initialize face recognition model"
                )
            }
        }
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
     * Add a new person with their first image
     */
    fun processAndAddPerson(
        context: Context,
        imageUri: Uri,
        name: String,
        description: String
    ) {
        viewModelScope.launch {
            if (faceNetModel == null) {
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

                // Stage 2: Detecting Face with ML Kit
                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                delay(500)

                val inputImage = InputImage.fromBitmap(bitmap, 0)

                // Use suspendCoroutine to convert Task to suspend function
                val faces = try {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<com.google.mlkit.vision.face.Face>> { continuation ->
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { detectedFaces ->
                                continuation.resume(detectedFaces) {}
                            }
                            .addOnFailureListener { e ->
                                Log.e("KnownPeopleVM", "Face detection failed: ${e.message}", e)
                                continuation.resume(emptyList()) {}
                            }
                    }
                } catch (e: Exception) {
                    Log.e("KnownPeopleVM", "Face detection error: ${e.message}", e)
                    emptyList()
                }

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image. Please try another photo."
                    )
                    return@launch
                }

                val face = faces.first()
                val boundingBox = face.boundingBox

                // Stage 3: Cropping Face
                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

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
            if (faceNetModel == null) {
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

                val inputImage = InputImage.fromBitmap(bitmap, 0)

                val faces = try {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<com.google.mlkit.vision.face.Face>> { continuation ->
                        faceDetector.process(inputImage)
                            .addOnSuccessListener { detectedFaces ->
                                continuation.resume(detectedFaces) {}
                            }
                            .addOnFailureListener { e ->
                                Log.e("KnownPeopleVM", "Face detection failed: ${e.message}", e)
                                continuation.resume(emptyList()) {}
                            }
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image"
                    )
                    return@launch
                }

                val face = faces.first()
                val boundingBox = face.boundingBox

                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

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
        faceDetector.close()
        Log.d("KnownPeopleVM", "ViewModel cleared")
    }
}