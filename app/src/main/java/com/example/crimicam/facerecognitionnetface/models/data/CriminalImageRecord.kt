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
    val createdAt: Timestamp = Timestamp.now()
)