package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class FaceImageRecord(
    var recordID: String = "",
    val personID: String = "", // Can be person ID or criminal ID
    val personName: String = "",
    val faceEmbedding: List<Float> = emptyList(),
    val imageUri: String = "", // Optional - for known people images

    val base64Image: String? = null, // ✅ ADD THIS
    val createdAt: Timestamp? = null, // Use Timestamp for consistency
    val confidence: Float = 0.0f,

    // Legacy field support (for backward compatibility)
    @Deprecated("Use createdAt with Timestamp")
    val timestamp: Timestamp? = null
) {
    @Exclude
    fun getEmbeddingAsArray(): FloatArray {
        return faceEmbedding.toFloatArray()
    }
}