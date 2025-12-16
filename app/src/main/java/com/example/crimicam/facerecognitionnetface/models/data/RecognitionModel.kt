package com.example.crimicam.facerecognitionnetface.models.data

data class RecognitionMetrics(
    val timeFaceDetection: Long = 0,
    val timeFaceEmbedding: Long = 0,
    val timeVectorSearch: Long = 0,
    val timeFaceSpoofDetection: Long = 0,
    val totalFacesDetected: Int = 0 // Add this field
)