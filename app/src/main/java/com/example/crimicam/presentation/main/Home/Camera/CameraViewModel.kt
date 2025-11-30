package com.example.crimicam.presentation.main.Home.Camera

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.facerecognitionnetface.models.data.RecognitionMetrics
import com.example.crimicam.facerecognitionnetface.models.domain.ImageVectorUseCase
import com.example.crimicam.facerecognitionnetface.models.domain.PersonUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectedFace(
    val boundingBox: RectF,
    val personId: String? = null,
    val personName: String? = null,
    val confidence: Float,
    val distance: Float = 0f
)

data class CameraState(
    val isProcessing: Boolean = false,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val scanningMode: ScanningMode = ScanningMode.IDLE,
    val statusMessage: String = "🔍 Scanning for faces...",
    val hasLocationPermission: Boolean = false,
    val knownPeople: List<KnownPerson> = emptyList(),
    val modelInitialized: Boolean = false
)

enum class ScanningMode {
    IDLE,
    DETECTING,
    ANALYZING,
    IDENTIFIED,
    UNKNOWN
}

class CameraViewModel(
    val personUseCase: PersonUseCase,
    val imageVectorUseCase: ImageVectorUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()

    val faceDetectionMetricsState = mutableStateOf<RecognitionMetrics?>(null)

    // Get number of known people in database
    fun getNumPeople(): Long = personUseCase.getCount()

    // Update state with detected faces
    fun updateDetectedFaces(faces: List<DetectedFace>) {
        _state.value = _state.value.copy(
            detectedFaces = faces,
            scanningMode = when {
                faces.isEmpty() -> ScanningMode.IDLE
                faces.any { it.personName != null } -> ScanningMode.IDENTIFIED
                else -> ScanningMode.UNKNOWN
            }
        )
    }

    // Update known people list
    fun updateKnownPeople(people: List<KnownPerson>) {
        _state.value = _state.value.copy(knownPeople = people)
    }

    // Update model initialization status
    fun setModelInitialized(initialized: Boolean) {
        _state.value = _state.value.copy(modelInitialized = initialized)
    }

    // Update processing status
    fun setProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isProcessing = processing)
    }
}