package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class PersonRecord(
    // Firestore document ID
    var personID: String = "",

    // Basic person info
    val personName: String = "",
    val numImages: Long = 0L,

    // Use List instead of Array for Firestore compatibility
    val imageUris: List<String> = emptyList(),

    // If you have embeddings, use List<Float> instead of FloatArray
    val embeddings: List<Float> = emptyList(),

    // Timestamps
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
) {
    // Helper function to get display info
    @Exclude
    fun getDisplayInfo(): String = "$personName (${numImages} images)"

    // Add any other helper functions you need
}