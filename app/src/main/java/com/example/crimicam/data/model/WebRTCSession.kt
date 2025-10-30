package com.example.crimicam.data.model


data class WebRTCSession(
    val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val isStreaming: Boolean = false,
    val streamStartedAt: Long = 0,

    // WebRTC Signaling Data
    val offer: String? = null,
    val answer: String? = null,
    val iceCandidates: List<String> = emptyList(),

    // Stream Info
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val lastHeartbeat: Long = 0
)