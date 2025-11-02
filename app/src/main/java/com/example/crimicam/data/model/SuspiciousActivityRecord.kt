package com.example.crimicam.data.model


data class SuspiciousActivityRecord(
    val id: String = "",
    val userId: String = "",
    val activityType: String = "",
    val displayName: String = "",
    val severity: String = "",
    val confidence: Float = 0f,
    val duration: Long = 0,
    val details: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val frameImageBase64: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)