package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
data class CriminalImageRecord(
    @PropertyName("recordID")
    val recordID: String = "",

    @PropertyName("criminalID")
    val criminalID: String = "",

    @PropertyName("criminalName")
    val criminalName: String = "",

    @PropertyName("faceEmbedding")
    val faceEmbedding: List<Float> = emptyList(),

    @PropertyName("imageUri")
    val imageUri: String = "",

    @PropertyName("dangerLevel")
    val dangerLevel: String = "LOW",

    @PropertyName("createdAt")
    val createdAt: Any? = null // Changed to Any?
) {
    fun getCreatedAtTimestamp(): Timestamp {
        return when (createdAt) {
            is Timestamp -> createdAt
            is Long -> Timestamp(createdAt / 1000, ((createdAt % 1000) * 1000000).toInt())
            else -> Timestamp.now()
        }
    }
}