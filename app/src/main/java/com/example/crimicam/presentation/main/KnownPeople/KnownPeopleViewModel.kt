package com.example.crimicam.presentation.main.KnownPeople

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.model.PersonImage
import com.example.crimicam.data.repository.KnownPeopleRepository
import com.example.crimicam.ml.FaceDetector
import com.example.crimicam.util.BitmapUtils
import com.example.crimicam.util.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KnownPeopleViewModel(
    private val repository: KnownPeopleRepository = KnownPeopleRepository(),
    private val faceDetector: FaceDetector = FaceDetector()
) : ViewModel() {

    private val _state = MutableStateFlow(KnownPeopleState())
    val state: StateFlow<KnownPeopleState> = _state.asStateFlow()

    init {
        loadKnownPeople()
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
            _state.value = _state.value.copy(isProcessing = true)

            try {
                // Stage 1: Loading Image
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)
                delay(300)

                val bitmap = BitmapUtils.getBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }

                // Stage 2: Detecting Face
                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                delay(500)

                val faces = faceDetector.detectFaces(bitmap)
                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image. Please try another photo."
                    )
                    return@launch
                }

                val face = faces.first()

                // Stage 3: Cropping Face
                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

                val croppedFace = faceDetector.cropFace(bitmap, face)
                if (croppedFace == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to crop face"
                    )
                    return@launch
                }

                // Stage 4: Extracting Features
                updateProgress(ProcessingStage.EXTRACTING_FEATURES, 0.7f)
                delay(400)

                val faceFeatures = faceDetector.extractFaceFeatures(face)

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

    /**
     * Add another image to an existing person
     */
    fun addImageToPerson(
        context: Context,
        personId: String,
        imageUri: Uri
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true)

            try {
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)
                delay(300)

                val bitmap = BitmapUtils.getBitmapFromUri(context, imageUri)
                if (bitmap == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load image"
                    )
                    return@launch
                }

                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                delay(500)

                val faces = faceDetector.detectFaces(bitmap)
                if (faces.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "No face detected in image"
                    )
                    return@launch
                }

                val face = faces.first()

                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                delay(300)

                val croppedFace = faceDetector.cropFace(bitmap, face)
                if (croppedFace == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to crop face"
                    )
                    return@launch
                }

                updateProgress(ProcessingStage.EXTRACTING_FEATURES, 0.7f)
                delay(400)

                val faceFeatures = faceDetector.extractFaceFeatures(face)

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
    }
}