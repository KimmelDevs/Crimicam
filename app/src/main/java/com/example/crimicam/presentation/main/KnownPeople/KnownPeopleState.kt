package com.example.crimicam.presentation.main.KnownPeople

import com.example.crimicam.data.model.KnownPerson

data class KnownPeopleState(
    val isLoading: Boolean = false,
    val people: List<KnownPerson> = emptyList(),
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
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