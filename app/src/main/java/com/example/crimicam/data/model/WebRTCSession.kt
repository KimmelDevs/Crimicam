package com.example.crimicam.data.model

data class WebRTCSession(
    val id: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val isStreaming: Boolean = false,
    val streamStartedAt: Long = System.currentTimeMillis(),
    val lastHeartbeat: Long = System.currentTimeMillis(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)