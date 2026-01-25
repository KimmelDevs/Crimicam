package com.example.crimicam.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class EmergencyReport(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val title: String = "",
    val description: String = "",
    val location: GeoPoint? = null,
    val address: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "ACTIVE", // ACTIVE, RESOLVED, CANCELLED
    val type: String = "EMERGENCY", // EMERGENCY, SUSPICIOUS, HELP_NEEDED
    val isResolved: Boolean = false,
    val resolvedAt: Timestamp? = null,
    val friendsNotified: List<String> = emptyList()
)

data class ReportState(
    val reports: List<EmergencyReport> = emptyList(),
    val friendReports: List<EmergencyReport> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val unreadCount: Int = 0
)