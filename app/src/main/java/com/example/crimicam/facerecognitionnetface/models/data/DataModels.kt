package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class FaceImageRecord(
    var recordID: String = "",
    val personID: String = "", // Changed to String
    val personName: String = "",
    val faceEmbedding: List<Float> = emptyList(),
    val imageUri: String = "", // Correct field name (not imageUrl)
    val createdAt: Timestamp = Timestamp.now(),
    val confidence: Float = 0.0f
) {
    @Exclude
    fun getEmbeddingAsArray(): FloatArray {
        return faceEmbedding.toFloatArray()
    }
}