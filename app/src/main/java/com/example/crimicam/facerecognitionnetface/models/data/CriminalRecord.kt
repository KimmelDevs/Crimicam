package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class CriminalRecord(
    @PropertyName("criminalID")
    val criminalID: String = "",

    @PropertyName("criminalName")
    val criminalName: String = "",

    @PropertyName("numImages")
    val numImages: Long = 0,

    @PropertyName("dangerLevel")
    val dangerLevel: String = "LOW",

    @PropertyName("description")
    val description: String = "",

    @PropertyName("crimes")
    val crimes: List<String> = emptyList(),

    @PropertyName("lastSeen")
    val lastSeen: Timestamp? = null,

    @PropertyName("createdAt")
    val createdAt: Any? = null, // Changed to Any? to handle both Long and Timestamp

    @PropertyName("lastUpdated")
    val lastUpdated: Any? = null, // Changed to Any? to handle both Long and Timestamp

    @PropertyName("isActive")
    val isActive: Boolean = true,

    @PropertyName("notes")
    val notes: String = ""
) {
    // Helper functions to get proper Timestamp
    fun getCreatedAtTimestamp(): Timestamp {
        return when (createdAt) {
            is Timestamp -> createdAt
            is Long -> Timestamp(createdAt / 1000, ((createdAt % 1000) * 1000000).toInt())
            else -> Timestamp.now()
        }
    }

    fun getLastUpdatedTimestamp(): Timestamp {
        return when (lastUpdated) {
            is Timestamp -> lastUpdated
            is Long -> Timestamp(lastUpdated / 1000, ((lastUpdated % 1000) * 1000000).toInt())
            else -> Timestamp.now()
        }
    }
}