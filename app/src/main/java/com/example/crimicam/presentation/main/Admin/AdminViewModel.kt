package com.example.crimicam.presentation.main.Admin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.CriminalsRepository
import com.example.crimicam.util.BitmapUtils
import com.example.crimicam.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: CriminalsRepository = CriminalsRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    init {
        loadCriminals()
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
                // Load all images from URIs
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

                // Add criminal with multiple images
                when (val result = repository.addCriminalWithImages(
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
                    imageBitmaps = imageBitmaps,
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

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}