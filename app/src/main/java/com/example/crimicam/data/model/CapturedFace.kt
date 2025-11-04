package com.example.crimicam.data.model

/**
 * Enhanced data model for captured person detections
 * Now supports multi-modal detection with or without visible faces
 */
data class CapturedFace(
    val id: String = "",
    val userId: String = "",

    // Image data
    val originalImageBase64: String = "",
    val croppedFaceBase64: String? = null,  // Now nullable for covered faces

    // Face recognition data
    val faceFeatures: Map<String, Float> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val isRecognized: Boolean = false,
    val matchedPersonId: String? = null,
    val matchedPersonName: String? = null,
    val confidence: Float = 0f,

    // Location data
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracy: Float? = null,
    val address: String? = null,

    // ═══════════════════════════════════════════════════════════
    // NEW: Multi-Modal Detection Fields
    // ═══════════════════════════════════════════════════════════

    /**
     * Detection mode used for this capture
     * Values: "face_and_pose", "face_only", "pose_only", "yolo_person"
     */
    val detectionMode: String? = null,

    /**
     * Detection category for filtering and analytics
     * Values: "full_detection", "face_only", "face_covered", "distance_detection"
     */
    val detectionCategory: String = "unknown",

    /**
     * Whether a visible face was detected in this capture
     */
    val hasVisibleFace: Boolean = true,

    /**
     * Whether body pose was successfully detected
     */
    val poseDetected: Boolean = false,

    /**
     * Whether YOLO detected the person
     */
    val yoloDetected: Boolean = false
) {
    /**
     * Check if this is a covered face detection
     */
    fun isCoveredFace(): Boolean = detectionCategory == "face_covered" ||
            (!hasVisibleFace && poseDetected)

    /**
     * Check if this is a full detection (both face and pose)
     */
    fun isFullDetection(): Boolean = detectionCategory == "full_detection" ||
            (hasVisibleFace && poseDetected)

    /**
     * Get human-readable detection description
     */
    fun getDetectionDescription(): String = when (detectionCategory) {
        "full_detection" -> "Full Detection (Face + Pose)"
        "face_only" -> "Face Detection Only"
        "face_covered" -> "Pose Detection (Face Covered)"
        "distance_detection" -> "Distance Detection (YOLO)"
        else -> "Detection"
    }

    /**
     * Get status icon for UI display
     */
    fun getStatusIcon(): String = when {
        isCoveredFace() -> "🎭"  // Covered face
        isFullDetection() -> "✅"  // Full detection
        hasVisibleFace -> "😊"  // Face only
        yoloDetected -> "👤"  // Person detected
        else -> "❓"  // Unknown
    }

    /**
     * Get confidence level description
     */
    fun getConfidenceLevel(): String = when {
        confidence >= 0.8f -> "High"
        confidence >= 0.6f -> "Medium"
        confidence >= 0.4f -> "Low"
        else -> "Very Low"
    }

    /**
     * Get recognition status text
     */
    fun getRecognitionStatus(): String = when {
        isRecognized && matchedPersonName != null -> "Known: $matchedPersonName"
        isCoveredFace() -> "Person (Face Hidden)"
        !hasVisibleFace -> "Person (No Face Visible)"
        else -> "Unknown Person"
    }

    /**
     * Check if location data is available
     */
    fun hasLocation(): Boolean = latitude != null && longitude != null

    /**
     * Get formatted location string
     */
    fun getLocationString(): String? {
        if (!hasLocation()) return null
        return if (address != null) {
            address
        } else {
            "Lat: ${String.format("%.4f", latitude)}, Lon: ${String.format("%.4f", longitude)}"
        }
    }

    /**
     * Get time ago string
     */
    fun getTimeAgo(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            diff < 604800_000 -> "${diff / 86400_000}d ago"
            else -> "${diff / 604800_000}w ago"
        }
    }
}

/**
 * Extension functions for list operations
 */
fun List<CapturedFace>.filterCoveredFaces() = filter { it.isCoveredFace() }
fun List<CapturedFace>.filterFullDetections() = filter { it.isFullDetection() }
fun List<CapturedFace>.filterRecognized() = filter { it.isRecognized }
fun List<CapturedFace>.filterUnrecognized() = filter { !it.isRecognized }
fun List<CapturedFace>.filterWithLocation() = filter { it.hasLocation() }

/**
 * Group detections by category
 */
fun List<CapturedFace>.groupByCategory(): Map<String, List<CapturedFace>> {
    return groupBy { it.detectionCategory }
}

/**
 * Get detection statistics from list
 */
fun List<CapturedFace>.getStats(): Map<String, Int> {
    return mapOf(
        "total" to size,
        "recognized" to count { it.isRecognized },
        "unrecognized" to count { !it.isRecognized },
        "covered_faces" to count { it.isCoveredFace() },
        "full_detections" to count { it.isFullDetection() },
        "with_location" to count { it.hasLocation() }
    )
}