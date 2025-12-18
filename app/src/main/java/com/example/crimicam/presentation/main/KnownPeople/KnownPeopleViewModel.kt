package com.example.crimicam.presentation.main.KnownPeople

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.facerecognitionnetface.models.data.PersonRecord
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KnownPeopleViewModel(
    private val personUseCase: PersonUseCase,
    private val imageVectorUseCase: ImageVectorUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(KnownPeopleState())
    val state: StateFlow<KnownPeopleState> = _state.asStateFlow()

    // State for add person dialog
    val personNameState: MutableState<String> = mutableStateOf("")
    val personDescriptionState: MutableState<String> = mutableStateOf("")
    val selectedImageUri: MutableState<Uri?> = mutableStateOf(null)

    val isProcessingImages: MutableState<Boolean> = mutableStateOf(false)
    val numImagesProcessed: MutableState<Int> = mutableIntStateOf(0)

    init {
        loadKnownPeople()
    }

    fun loadKnownPeople() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            personUseCase.getAll().collect { personList ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    people = personList
                )

                // ✅ Load base64 images for each person
                loadPersonImages(personList)
            }
        }
    }

    /**
     * ✅ Load base64 images for all people
     */
    private fun loadPersonImages(people: List<PersonRecord>) {
        viewModelScope.launch {
            val personImagesMap = mutableMapOf<String, List<String>>()

            people.forEach { person ->
                try {
                    // Get all base64 images for this person
                    val base64Images = imageVectorUseCase.getPersonImages(person.personID)
                    personImagesMap[person.personID] = base64Images
                } catch (e: Exception) {
                    // If loading fails, just use empty list
                    personImagesMap[person.personID] = emptyList()
                }
            }

            _state.value = _state.value.copy(personImages = personImagesMap)
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
            isProcessingImages.value = true
            _state.value = _state.value.copy(isProcessing = true)

            try {
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)

                // Step 1: Add person with initial count of 0
                val personId = personUseCase.addPerson(
                    name = name,
                    numImages = 0
                )

                updateProgress(ProcessingStage.DETECTING_FACE, 0.25f)

                // Step 2: Add image with face detection, embedding, and base64 conversion
                val result = imageVectorUseCase.addImage(
                    personID = personId,
                    personName = name,
                    imageUri = imageUri
                )

                if (result.isSuccess) {
                    // Step 3: Atomically increment the person's image count
                    personUseCase.incrementImageCount(personId)

                    updateProgress(ProcessingStage.COMPLETE, 1.0f)

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingProgress = null
                    )

                    numImagesProcessed.value += 1

                    // Reload to get updated list and images
                    loadKnownPeople()

                    // Clear form
                    resetState()
                } else {
                    // If image addition failed, remove the person we just created
                    personUseCase.removePerson(personId)

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to add person image"
                    )
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = "Error adding person: ${e.message ?: "Unknown error"}"
                )
            } finally {
                isProcessingImages.value = false
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
            isProcessingImages.value = true
            _state.value = _state.value.copy(isProcessing = true)

            try {
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)

                // Find person
                val person = _state.value.people.find { it.personID == personId }
                if (person == null) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Person not found"
                    )
                    return@launch
                }

                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)
                updateProgress(ProcessingStage.CROPPING_FACE, 0.5f)
                updateProgress(ProcessingStage.EXTRACTING_FEATURES, 0.7f)
                updateProgress(ProcessingStage.CONVERTING_TO_BASE64, 0.85f)

                // Add image with face detection and embedding (includes base64 conversion)
                val result = imageVectorUseCase.addImage(
                    personID = personId,
                    personName = person.personName,
                    imageUri = imageUri
                )

                if (result.isSuccess) {
                    // Atomically increment the image count
                    personUseCase.incrementImageCount(personId)

                    updateProgress(ProcessingStage.COMPLETE, 1.0f)

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingProgress = null
                    )

                    numImagesProcessed.value += 1

                    // Reload to get updated image count and images
                    loadKnownPeople()
                } else {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to add image: ${result.exceptionOrNull()?.message}"
                    )
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = "Error adding image: ${e.message ?: "Unknown error"}"
                )
            } finally {
                isProcessingImages.value = false
            }
        }
    }

    private fun updateProgress(stage: ProcessingStage, progress: Float) {
        _state.value = _state.value.copy(
            processingProgress = ProcessingProgress(stage, progress)
        )
    }

    /**
     * Delete a person and all their images
     */
    fun deletePerson(personId: String) {
        viewModelScope.launch {
            try {
                // Remove images first
                imageVectorUseCase.removeImages(personId)

                // Remove person
                personUseCase.removePerson(personId)

                // Update UI immediately
                _state.value = _state.value.copy(
                    people = _state.value.people.filter { it.personID != personId },
                    personImages = _state.value.personImages.filterKeys { it != personId }
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to delete person: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun resetState() {
        personNameState.value = ""
        personDescriptionState.value = ""
        selectedImageUri.value = null
        numImagesProcessed.value = 0
    }

    /**
     * Refresh the known people list
     */
    fun refresh() {
        loadKnownPeople()
    }

    /**
     * Get person by ID
     */
    fun getPerson(personId: String): PersonRecord? {
        return _state.value.people.find { it.personID == personId }
    }

    /**
     * Check if a person name already exists
     */
    fun isPersonNameExists(name: String): Boolean {
        return _state.value.people.any { it.personName.equals(name, ignoreCase = true) }
    }
}