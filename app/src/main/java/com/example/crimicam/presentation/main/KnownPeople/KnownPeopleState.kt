package com.example.crimicam.presentation.main.KnownPeople

import com.example.crimicam.facerecognitionnetface.models.data.PersonRecord
data class KnownPeopleState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingProgress: ProcessingProgress? = null,
    val people: List<PersonRecord> = emptyList(),
    val personImages: Map<String, List<String>> = emptyMap(), // ✅ Map of personID to base64 images
    val errorMessage: String? = null
)

data class ProcessingProgress(
    val stage: ProcessingStage,
    val progress: Float // 0.0 to 1.0
)

enum class ProcessingStage {
    LOADING_IMAGE,
    DETECTING_FACE,
    CROPPING_FACE,
    EXTRACTING_FEATURES,
    CONVERTING_TO_BASE64, // ✅ New stage
    UPLOADING,
    COMPLETE
}