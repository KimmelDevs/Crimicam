package com.example.crimicam.presentation.main.Admin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.facerecognitionnetface.models.data.CriminalRecord
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Factory

@Factory
class AdminViewModel(
    private val criminalUseCase: CriminalUseCase,
    private val criminalImageVectorUseCase: CriminalImageVectorUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    init {
        loadCriminals()
    }

    /**
     * Load all criminals from database
     */
    private fun loadCriminals() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            criminalUseCase.getAll()
                .catch { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load criminals: ${e.message}"
                        )
                    }
                }
                .collect { criminals ->
                    _state.update {
                        it.copy(
                            criminals = criminals,
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * Add a new criminal with face images
     */
    fun addCriminal(
        context: Context,
        imageUris: List<Uri>,
        name: String,
        description: String,
        dangerLevel: DangerLevel,
        notes: String,
        crimes: List<String>
    ) {
        if (name.isBlank()) {
            _state.update { it.copy(errorMessage = "Name cannot be empty") }
            return
        }

        if (imageUris.isEmpty()) {
            _state.update { it.copy(errorMessage = "Please select at least one image") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isProcessing = true,
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.LOADING_IMAGE,
                            progress = 0f
                        )
                    )
                }

                // Create criminal record
                val criminalRecord = CriminalRecord(
                    criminalName = name,
                    description = description,
                    dangerLevel = dangerLevel.name,
                    notes = notes,
                    crimes = crimes,
                    numImages = 0L,
                    isActive = true
                )

                // Update progress
                _state.update {
                    it.copy(
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.DETECTING_FACE,
                            progress = 0.2f
                        )
                    )
                }

                // Add criminal with images
                val criminalId = criminalUseCase.addCriminal(
                    criminal = criminalRecord,
                    imageUris = imageUris
                )

                // Update progress
                _state.update {
                    it.copy(
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.COMPLETE,
                            progress = 1f
                        )
                    )
                }

                // Show success message
                _state.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = null,
                        successMessage = "Criminal '$name' added successfully with ${imageUris.size} image(s)"
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = null,
                        errorMessage = "Failed to add criminal: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Add images to an existing criminal
     */
    fun addImagesToCriminal(
        context: Context,
        criminalId: String,
        imageUris: List<Uri>
    ) {
        if (imageUris.isEmpty()) {
            _state.update { it.copy(errorMessage = "Please select at least one image") }
            return
        }

        viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isProcessing = true,
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.LOADING_IMAGE,
                            progress = 0f
                        )
                    )
                }

                // Update progress
                _state.update {
                    it.copy(
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.DETECTING_FACE,
                            progress = 0.3f
                        )
                    )
                }

                // Add images
                val successCount = criminalUseCase.addImagesToCriminal(
                    context = context,
                    criminalId = criminalId,
                    imageUris = imageUris
                )

                // Update progress
                _state.update {
                    it.copy(
                        processingProgress = ProcessingProgress(
                            stage = ProcessingStage.COMPLETE,
                            progress = 1f
                        )
                    )
                }

                // Show result
                val message = if (successCount > 0) {
                    "Successfully added $successCount out of ${imageUris.size} image(s)"
                } else {
                    "Failed to add images. Please check face detection."
                }

                _state.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = null,
                        successMessage = message
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isProcessing = false,
                        processingProgress = null,
                        errorMessage = "Failed to add images: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Update criminal information
     */
    fun updateCriminal(
        criminalId: String,
        dangerLevel: DangerLevel? = null,
        description: String? = null,
        notes: String? = null,
        crimes: List<String>? = null,
        isActive: Boolean? = null
    ) {
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any>()

                dangerLevel?.let { updates["dangerLevel"] = it.name }
                description?.let { updates["description"] = it }
                notes?.let { updates["notes"] = it }
                crimes?.let { updates["crimes"] = it }
                isActive?.let { updates["isActive"] = it }

                if (updates.isEmpty()) {
                    _state.update { it.copy(errorMessage = "No updates provided") }
                    return@launch
                }

                criminalUseCase.updateCriminal(criminalId, updates)

                _state.update {
                    it.copy(successMessage = "Criminal updated successfully")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(errorMessage = "Failed to update criminal: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete a criminal and all associated images
     */
    fun deleteCriminal(criminalId: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }

                // Remove criminal (this also removes all associated images)
                criminalUseCase.removeCriminal(criminalId)

                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Criminal removed successfully"
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to remove criminal: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Search criminals by name
     */
    fun searchByName(name: String) {
        if (name.isBlank()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            try {
                _state.update { it.copy(isSearching = true) }

                val results = criminalUseCase.searchByName(name)

                _state.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Search failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clear search results
     */
    fun clearSearch() {
        _state.update { it.copy(searchResults = emptyList()) }
    }

    /**
     * Get criminal statistics
     */
    fun getCriminalStatistics(criminalId: String) {
        viewModelScope.launch {
            try {
                val stats = criminalUseCase.getCriminalStatistics(criminalId)
                // You can add this to state if needed
                println("Criminal Stats: $stats")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Clear all criminals (admin action)
     */
    fun clearAllCriminals() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }

                criminalUseCase.clearAll()

                _state.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "All criminals cleared successfully"
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to clear criminals: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Get database count
     */
    fun getDatabaseCount(): Long {
        return criminalUseCase.getCount()
    }

    /**
     * Refresh database count
     */
    fun refreshDatabaseCount() {
        viewModelScope.launch {
            try {
                criminalUseCase.refreshCount()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _state.update { it.copy(successMessage = null) }
    }

    /**
     * Reset state (useful when dismissing dialogs)
     */
    fun resetState() {
        _state.update {
            it.copy(
                isProcessing = false,
                processingProgress = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    /**
     * Filter criminals by danger level
     */
    fun filterByDangerLevel(dangerLevel: DangerLevel): List<CriminalRecord> {
        return _state.value.criminals.filter {
            it.dangerLevel == dangerLevel.name
        }
    }

    /**
     * Get active criminals only
     */
    fun getActiveCriminals(): List<CriminalRecord> {
        return _state.value.criminals.filter { it.isActive }
    }

    /**
     * Get inactive criminals only
     */
    fun getInactiveCriminals(): List<CriminalRecord> {
        return _state.value.criminals.filter { !it.isActive }
    }

    /**
     * Toggle criminal active status
     */
    fun toggleCriminalStatus(criminalId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                criminalUseCase.updateCriminal(
                    criminalId = criminalId,
                    updates = mapOf("isActive" to isActive)
                )

                _state.update {
                    it.copy(
                        successMessage = if (isActive)
                            "Criminal activated"
                        else
                            "Criminal deactivated"
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.update {
                    it.copy(errorMessage = "Failed to update status: ${e.message}")
                }
            }
        }
    }

    /**
     * Check if criminal name already exists
     */
    fun checkNameExists(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val exists = criminalUseCase.existsByName(name)
                onResult(exists)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup if needed
    }
}