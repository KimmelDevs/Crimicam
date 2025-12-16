package com.example.crimicam.presentation.main.Admin

import com.example.crimicam.facerecognitionnetface.models.data.CriminalRecord

// Update AdminState to use CriminalRecord
data class AdminState(
    val criminals: List<CriminalRecord> = emptyList(),
    val searchResults: List<CriminalRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val processingProgress: ProcessingProgress? = null
)

data class ProcessingProgress(
    val stage: ProcessingStage,
    val progress: Float // 0.0 to 1.0
)



enum class ProcessingStage {
    LOADING_IMAGE,
    DETECTING_FACE,
    CROPPING_FACE,
    COMPRESSING,
    EXTRACTING_FEATURES,
    UPLOADING,
    COMPLETE
}

enum class DangerLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}