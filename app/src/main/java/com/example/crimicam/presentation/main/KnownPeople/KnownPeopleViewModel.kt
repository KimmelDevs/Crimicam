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
            isProcessingImages.value = true
            _state.value = _state.value.copy(isProcessing = true)

            try {
                updateProgress(ProcessingStage.LOADING_IMAGE, 0.1f)

                // Add person to database - returns String ID
                val personId = personUseCase.addPerson(
                    name = name,
                    numImages = 1 // Starting with 1 image
                )

                updateProgress(ProcessingStage.DETECTING_FACE, 0.3f)

                // Add image with face detection and embedding
                // Use String personID directly (no conversion to Long)
                val result = imageVectorUseCase.addImage(
                    personID = personId, // Use String ID directly
                    personName = name,
                    imageUri = imageUri
                )

                if (result.isSuccess) {
                    updateProgress(ProcessingStage.COMPLETE, 1.0f)

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingProgress = null
                    )

                    numImagesProcessed.value += 1

                    // Reload to get updated list
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

                updateProgress(ProcessingStage.DETECTING_FACE, 0.4f)

                // Add image with face detection and embedding
                // Use String personID directly
                val result = imageVectorUseCase.addImage(
                    personID = personId, // Use String ID directly
                    personName = person.personName,
                    imageUri = imageUri
                )

                if (result.isSuccess) {
                    updateProgress(ProcessingStage.COMPLETE, 1.0f)

                    // Update person's image count by creating a new person record
                    // Note: This will create a new document with same name but different ID
                    // You might want to update your PersonUseCase to have an update method
                    val updatedPerson = person.copy(numImages = person.numImages + 1)
                    personUseCase.addPerson(
                        name = updatedPerson.personName,
                        numImages = updatedPerson.numImages
                    )

                    _state.value = _state.value.copy(
                        isProcessing = false,
                        processingProgress = null
                    )

                    numImagesProcessed.value += 1

                    // Reload to get updated image count
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

    /**
     * Update person's image count (helper method)
     */
    private suspend fun updatePersonImageCount(personId: String, newCount: Long) {
        val person = _state.value.people.find { it.personID == personId }
        person?.let {
            // Create updated person record
            val updatedPerson = it.copy(numImages = newCount)
            // You might need to add an update method to your PersonUseCase
            // For now, we'll remove and re-add with updated count
            personUseCase.removePerson(personId)
            personUseCase.addPerson(
                name = updatedPerson.personName,
                numImages = updatedPerson.numImages
            )
        }
    }

    private fun updateProgress(stage: ProcessingStage, progress: Float) {
        _state.value = _state.value.copy(
            processingProgress = ProcessingProgress(stage, progress)
        )
    }

    fun deletePerson(personId: String) {
        viewModelScope.launch {
            try {
                // Remove images first - use String ID
                imageVectorUseCase.removeImages(personId)

                // Remove person
                personUseCase.removePerson(personId)

                // Update UI immediately
                _state.value = _state.value.copy(
                    people = _state.value.people.filter { it.personID != personId }
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