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
    val dangerLevel: String = "LOW", // LOW, MEDIUM, HIGH, CRITICAL

    @PropertyName("description")
    val description: String = "",

    @PropertyName("crimes")
    val crimes: List<String> = emptyList(),

    @PropertyName("lastSeen")
    val lastSeen: Timestamp? = null,

    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),

    @PropertyName("lastUpdated")
    val lastUpdated: Timestamp = Timestamp.now(),

    @PropertyName("isActive")
    val isActive: Boolean = true,

    @PropertyName("notes")
    val notes: String = ""
)