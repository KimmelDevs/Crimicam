package com.example.crimicam.data.model

import com.google.firebase.firestore.DocumentId

data class PersonImage(
    @DocumentId
    val id: String = "",
    val personId: String = "",
    val originalImageBase64: String = "",
    val croppedFaceBase64: String = "",
    val faceFeatures: Map<String, Float> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)
