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
) {
    // Helper methods to safely access details
    fun getStringDetail(key: String): String? {
        return details[key] as? String
    }

    fun getIntDetail(key: String): Int? {
        return when (val value = details[key]) {
            is Int -> value
            is Long -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    fun getFloatDetail(key: String): Float? {
        return when (val value = details[key]) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is String -> value.toFloatOrNull()
            else -> null
        }
    }

    fun getBooleanDetail(key: String): Boolean? {
        return when (val value = details[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            is Int -> value != 0
            else -> null
        }
    }

    fun getStringListDetail(key: String): List<String>? {
        return when (val value = details[key]) {
            is List<*> -> value.filterIsInstance<String>()
            is Array<*> -> value.filterIsInstance<String>().toList()
            else -> null
        }
    }

    // Specific detail keys for YOLO detection
    companion object {
        const val DETAIL_DETECTED_OBJECTS = "detected_objects"
        const val DETAIL_OBJECT_COUNT = "object_count"
        const val DETAIL_BOUNDING_BOXES = "bounding_boxes"
        const val DETAIL_CONFIDENCE_SCORES = "confidence_scores"
        const val DETAIL_SECURITY_ALERT_TYPE = "security_alert_type"
        const val DETAIL_FACE_COUNT = "face_count"
        const val DETAIL_RECOGNITION_STATUS = "recognition_status"
    }
}

// Extension function to create details map for YOLO detection
fun createYOLODetails(
    detectedObjects: List<String>,
    objectCount: Int,
    boundingBoxes: List<String> = emptyList(),
    confidenceScores: List<Float> = emptyList(),
    securityAlertType: String? = null
): Map<String, Any> {
    return mapOf(
        SuspiciousActivityRecord.DETAIL_DETECTED_OBJECTS to detectedObjects,
        SuspiciousActivityRecord.DETAIL_OBJECT_COUNT to objectCount,
        SuspiciousActivityRecord.DETAIL_BOUNDING_BOXES to boundingBoxes,
        SuspiciousActivityRecord.DETAIL_CONFIDENCE_SCORES to confidenceScores
    ).let { baseMap ->
        if (securityAlertType != null) {
            baseMap + (SuspiciousActivityRecord.DETAIL_SECURITY_ALERT_TYPE to securityAlertType)
        } else {
            baseMap
        }
    }
}

// Extension function to create details map for face detection
fun createFaceDetectionDetails(
    faceCount: Int,
    recognizedFaces: Int = 0,
    confidence: Float = 0f
): Map<String, Any> {
    return mapOf(
        SuspiciousActivityRecord.DETAIL_FACE_COUNT to faceCount,
        SuspiciousActivityRecord.DETAIL_RECOGNITION_STATUS to if (recognizedFaces > 0) "recognized" else "unknown",
        "recognized_face_count" to recognizedFaces,
        "average_confidence" to confidence
    )
}