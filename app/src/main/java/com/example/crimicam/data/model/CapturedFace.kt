package com.example.crimicam.data.model

data class CapturedFace(
    val id: String = "",
    val userId: String = "",
    val originalImageBase64: String = "",
    val croppedFaceBase64: String = "",
    val faceFeatures: Map<String, Float> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val isRecognized: Boolean = false,
    val matchedPersonId: String? = null,
    val matchedPersonName: String? = null,
    val confidence: Float = 0f,
    // NEW: Location data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val address: String? = null
)