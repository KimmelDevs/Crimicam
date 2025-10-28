package com.example.crimicam.data.model


data class KnownPerson(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val originalImageBase64: String = "",  // Compressed original image
    val croppedFaceBase64: String = "",    // Cropped face only
    val faceFeatures: Map<String, Float> = emptyMap(), // Simple face features instead of embeddings
    val imageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)