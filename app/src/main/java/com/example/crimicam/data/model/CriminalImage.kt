package com.example.crimicam.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class CriminalImage(
    @DocumentId
    val id: String = "",
    @PropertyName("criminal_id") val criminalId: String = "",
    @PropertyName("image_base64") val imageBase64: String = "",
    @PropertyName("image_type") val imageType: String = "mugshot", // mugshot, full_body, tattoo, etc.
    @PropertyName("created_by_officer_id") val createdByOfficerId: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
)