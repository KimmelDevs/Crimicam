package com.example.crimicam.presentation.main.Admin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.Criminal
import com.example.crimicam.facerecognitionnetface.models.domain.CriminalUseCase
import com.example.crimicam.util.BitmapUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminState(
    val isLoading: Boolean = false,
    val criminals: List<Criminal> = emptyList(),
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
    val successMessage: String? = null,
    val criminalCount: Long = 0L
)

class AdminViewModel(
    private val criminalUseCase: CriminalUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    init {
        loadCriminals()
        refreshCriminalCount()
    }

    /**
     * Get number of criminals in database
     */
    fun getNumCriminals(): Long = _state.value.criminalCount

    /**
     * Load all criminals with reactive updates
     */
    private fun loadCriminals() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            try {
                criminalUseCase.getAll().collect { criminals ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        criminals = criminals.sortedByDescending { it.createdAt },
                        criminalCount = criminals.size.toLong()
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load criminals"
                )
            }
        }
    }

    /**
     * Refresh criminal count from database
     */
    fun refreshCriminalCount() {
        viewModelScope.launch {
            try {
                val count = criminalUseCase.refreshCount()
                _state.value = _state.value.copy(criminalCount = count)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Add a new criminal with face images
     */
    fun addCriminal(
        context: Context,
        imageUris: List<Uri>,
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
                // 1. Load all images from URIs
                val imageBitmaps = imageUris.mapNotNull { uri ->
                    BitmapUtils.getBitmapFromUri(context, uri)
                }

                if (imageBitmaps.isEmpty()) {
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        errorMessage = "Failed to load images"
                    )
                    return@launch
                }

                // 2. Create Criminal object
                val criminal = Criminal(
                    id = "", // Will be auto-generated
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
                    notes = notes,
                    imageCount = 0, // Will be updated after processing
                    createdAt = Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                // 3. Add criminal with images
                val criminalId = criminalUseCase.addCriminal(
                    criminal = criminal,
                    imageBitmaps = imageBitmaps
                )

                // Get updated criminal with correct image count
                val addedCriminal = criminalUseCase.getCriminal(criminalId)

                _state.value = _state.value.copy(
                    isProcessing = false,
                    successMessage = "Criminal added successfully with ${addedCriminal?.imageCount ?: 0} face images"
                )
                refreshCriminalCount()

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    errorMessage = e.message ?: "Failed to add criminal"
                )
            }
        }
    }

    /**
     * Add additional images to an existing criminal
     */
    fun addCriminalImages(
        context: Context,
        criminalId: String,
        imageUris: List<Uri>
    ) {
        viewModelScope.launch {
            try {
                val imageBitmaps = imageUris.mapNotNull { uri ->
                    BitmapUtils.getBitmapFromUri(context, uri)
                }

                if (imageBitmaps.isEmpty()) {
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to load images"
                    )
                    return@launch
                }

                val successfulImages = criminalUseCase.addCriminalImages(
                    criminalId = criminalId,
                    imageBitmaps = imageBitmaps
                )

                _state.value = _state.value.copy(
                    successMessage = "Added $successfulImages new face images"
                )

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Failed to add images"
                )
            }
        }
    }

    /**
     * Update criminal information
     */
    fun updateCriminal(criminalId: String, updates: Map<String, Any>) {
        viewModelScope.launch {
            try {
                criminalUseCase.updateCriminal(criminalId, updates)
                _state.value = _state.value.copy(
                    successMessage = "Criminal updated successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Failed to update criminal"
                )
            }
        }
    }

    /**
     * Remove a criminal
     */
    fun removeCriminal(criminalId: String) {
        viewModelScope.launch {
            try {
                criminalUseCase.removeCriminal(criminalId)
                _state.value = _state.value.copy(
                    successMessage = "Criminal removed successfully"
                )
                refreshCriminalCount()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Failed to remove criminal"
                )
            }
        }
    }

    /**
     * Search criminals by name
     */
    fun searchByName(firstName: String, lastName: String) {
        viewModelScope.launch {
            try {
                val results = criminalUseCase.searchByName(firstName, lastName)
                _state.value = _state.value.copy(criminals = results)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Search failed"
                )
            }
        }
    }

    /**
     * Filter by status
     */
    fun filterByStatus(status: String) {
        viewModelScope.launch {
            try {
                val results = criminalUseCase.searchByStatus(status)
                _state.value = _state.value.copy(criminals = results)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Filter failed"
                )
            }
        }
    }

    /**
     * Filter by risk level
     */
    fun filterByRiskLevel(riskLevel: String) {
        viewModelScope.launch {
            try {
                val results = criminalUseCase.searchByRiskLevel(riskLevel)
                _state.value = _state.value.copy(criminals = results)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Filter failed"
                )
            }
        }
    }

    /**
     * Reset filters (show all criminals)
     */
    fun resetFilters() {
        loadCriminals()
    }

    /**
     * Clear all criminals
     */
    fun clearAllCriminals() {
        viewModelScope.launch {
            try {
                criminalUseCase.clearAll()
                _state.value = _state.value.copy(
                    successMessage = "All criminals cleared",
                    criminals = emptyList(),
                    criminalCount = 0L
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = e.message ?: "Failed to clear criminals"
                )
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _state.value = _state.value.copy(successMessage = null)
    }
}