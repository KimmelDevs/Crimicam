package com.example.crimicam.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs

data class YOLODetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classId: Int,
    val trackingId: Int = -1,  // NEW: Unique ID for tracking across frames
    val frameCount: Int = 0    // NEW: Number of consecutive frames detected
)

// NEW: Tracked object for maintaining state across frames
data class TrackedObject(
    val id: Int,
    val label: String,
    val lastBoundingBox: RectF,
    val lastSeen: Long,
    val confidence: Float,
    val frameCount: Int,
    val firstSeen: Long
)

// Detection history for confidence smoothing
data class DetectionHistory(
    val timestamp: Long,
    val detections: List<YOLODetectionResult>,
    val alert: YOLODetector.SecurityAlert
)

// Alert state tracking
data class AlertState(
    val alert: YOLODetector.SecurityAlert,
    val firstDetectedTime: Long,
    val lastDetectedTime: Long,
    val consecutiveCount: Int,
    val smoothedConfidence: Float
)

class YOLODetector(context: Context) {
    private var interpreter: Interpreter? = null
    private val labels: List<String> = try {
        loadLabels(context)
    } catch (e: Exception) {
        Log.e("YOLODetector", "❌ Error loading labels: ${e.message}", e)
        emptyList()
    }

    private val inputSize = 640
    private val confidenceThreshold = 0.6f
    private val iouThreshold = 0.5f

    // Security-relevant classes for crime detection
    private val securityClasses = listOf(
        "person", "car", "truck", "motorcycle", "bus",
        "bicycle", "backpack", "handbag", "knife", "bottle"
    )

    // Alert system improvements
    private val detectionHistory = mutableListOf<DetectionHistory>()
    private val maxHistorySize = 10
    private var currentAlertState: AlertState? = null

    // Alert cooldown settings
    private val alertCooldownMs = 5000L
    private val alertStabilityFrames = 3
    private val confidenceSmoothingWeight = 0.7f

    // Multi-factor scoring weights
    private val objectPresenceWeight = 0.4f
    private val objectCountWeight = 0.3f
    private val interactionWeight = 0.3f

    // NEW: Object tracking
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextTrackingId = 1
    private val trackingIouThreshold = 0.3f  // Lower threshold for tracking (objects can move)
    private val maxTrackingAge = 1000L  // Remove tracks after 1 second of not being seen

    // NEW: Size and aspect ratio constraints (in pixels relative to input size)
    private val minObjectSizePercent = 0.02f  // Min 2% of image
    private val maxObjectSizePercent = 0.95f  // Max 95% of image
    private val minAspectRatio = 0.2f  // Minimum width/height ratio
    private val maxAspectRatio = 5.0f  // Maximum width/height ratio

    // Class-specific aspect ratio constraints
    private val aspectRatioRanges = mapOf(
        "person" to (0.3f to 3.0f),      // People are typically vertical
        "car" to (1.2f to 3.5f),         // Cars are wider than tall
        "truck" to (1.2f to 4.0f),       // Trucks can be quite wide
        "bus" to (1.5f to 4.0f),         // Buses are very wide
        "motorcycle" to (0.8f to 2.5f),  // Motorcycles vary
        "bicycle" to (0.8f to 2.0f),     // Bikes are roughly square
        "knife" to (2.0f to 10.0f),      // Knives are very elongated
        "backpack" to (0.6f to 1.5f),    // Backpacks are roughly square
        "handbag" to (1.0f to 2.5f),     // Handbags vary
        "bottle" to (0.3f to 1.2f)       // Bottles are vertical
    )

    init {
        try {
            interpreter = loadModel(context)
            Log.d("YOLODetector", "✅ Model loaded successfully with ${labels.size} classes")
        } catch (e: Exception) {
            Log.e("YOLODetector", "❌ Error loading model: ${e.message}", e)
        }
    }

    private fun loadModel(context: Context): Interpreter {
        val modelFile = "yolov8n_float16.tflite"
        val assetFileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )

        val options = Interpreter.Options()
        options.setNumThreads(4)

        return Interpreter(modelBuffer, options)
    }

    private fun loadLabels(context: Context): List<String> {
        return context.assets.open("labels.txt")
            .bufferedReader()
            .useLines { it.toList() }
    }

    fun detect(bitmap: Bitmap): List<YOLODetectionResult> {
        if (interpreter == null) {
            Log.e("YOLODetector", "Interpreter not initialized")
            return emptyList()
        }

        try {
            val input = preprocessImage(bitmap)
            val output = Array(1) { Array(84) { FloatArray(8400) } }
            interpreter?.run(input, output)

            val rawDetections = processOutput(output[0], bitmap.width, bitmap.height)

            // NEW: Apply size and aspect ratio filtering
            val filteredDetections = filterDetectionsBySize(rawDetections, bitmap.width, bitmap.height)

            // NEW: Apply tracking
            val trackedDetections = applyTracking(filteredDetections)

            return trackedDetections
        } catch (e: Exception) {
            Log.e("YOLODetector", "Error during detection: ${e.message}", e)
            return emptyList()
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(
            intValues, 0, resizedBitmap.width,
            0, 0, resizedBitmap.width, resizedBitmap.height
        )

        var pixel = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val value = intValues[pixel++]
                inputBuffer.putFloat((value shr 16 and 0xFF) / 255.0f)
                inputBuffer.putFloat((value shr 8 and 0xFF) / 255.0f)
                inputBuffer.putFloat((value and 0xFF) / 255.0f)
            }
        }

        return inputBuffer
    }

    private fun processOutput(
        output: Array<FloatArray>,
        originalWidth: Int,
        originalHeight: Int
    ): List<YOLODetectionResult> {
        val detections = mutableListOf<YOLODetectionResult>()

        for (i in 0 until 8400) {
            var maxClassIdx = -1
            var maxScore = -1f

            for (c in 0 until 80) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClassIdx = c
                }
            }

            if (maxClassIdx != -1 && maxScore > confidenceThreshold) {
                val className = labels.getOrNull(maxClassIdx) ?: "unknown"

                if (className in securityClasses) {
                    val xCenter = output[0][i] / inputSize
                    val yCenter = output[1][i] / inputSize
                    val width = output[2][i] / inputSize
                    val height = output[3][i] / inputSize

                    val left = ((xCenter - width / 2) * originalWidth).coerceAtLeast(0f)
                    val top = ((yCenter - height / 2) * originalHeight).coerceAtLeast(0f)
                    val right = ((xCenter + width / 2) * originalWidth).coerceAtMost(originalWidth.toFloat())
                    val bottom = ((yCenter + height / 2) * originalHeight).coerceAtMost(originalHeight.toFloat())

                    detections.add(
                        YOLODetectionResult(
                            label = className,
                            confidence = maxScore,
                            boundingBox = RectF(left, top, right, bottom),
                            classId = maxClassIdx
                        )
                    )
                }
            }
        }

        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<YOLODetectionResult>): List<YOLODetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val result = mutableListOf<YOLODetectionResult>()
        val groupedByClass = detections.groupBy { it.label }

        groupedByClass.forEach { (_, classDetections) ->
            val sortedDetections = classDetections.sortedByDescending { it.confidence }

            for (detection in sortedDetections) {
                var shouldAdd = true

                for (kept in result) {
                    if (detection.label == kept.label) {
                        val iou = calculateIoU(detection.boundingBox, kept.boundingBox)
                        if (iou > iouThreshold) {
                            shouldAdd = false
                            break
                        }
                    }
                }

                if (shouldAdd) {
                    result.add(detection)
                }
            }
        }

        return result
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectLeft = max(box1.left, box2.left)
        val intersectTop = max(box1.top, box2.top)
        val intersectRight = min(box1.right, box2.right)
        val intersectBottom = min(box1.bottom, box2.bottom)

        val intersectWidth = max(0f, intersectRight - intersectLeft)
        val intersectHeight = max(0f, intersectBottom - intersectTop)
        val intersectArea = intersectWidth * intersectHeight

        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectArea

        return if (unionArea > 0) intersectArea / unionArea else 0f
    }

    // NEW: Filter detections by size and aspect ratio
    private fun filterDetectionsBySize(
        detections: List<YOLODetectionResult>,
        imageWidth: Int,
        imageHeight: Int
    ): List<YOLODetectionResult> {
        val filtered = mutableListOf<YOLODetectionResult>()
        val totalArea = imageWidth * imageHeight

        detections.forEach { detection ->
            val box = detection.boundingBox
            val width = box.right - box.left
            val height = box.bottom - box.top
            val area = width * height

            // Size filtering
            val areaPercent = area / totalArea
            if (areaPercent < minObjectSizePercent) {
                Log.d("YOLODetector", "Filtered ${detection.label}: too small (${(areaPercent * 100).toInt()}%)")
                return@forEach
            }

            if (areaPercent > maxObjectSizePercent) {
                Log.d("YOLODetector", "Filtered ${detection.label}: too large (${(areaPercent * 100).toInt()}%)")
                return@forEach
            }

            // Aspect ratio filtering
            val aspectRatio = width / height

            // General aspect ratio check
            if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) {
                Log.d("YOLODetector", "Filtered ${detection.label}: invalid aspect ratio ($aspectRatio)")
                return@forEach
            }

            // Class-specific aspect ratio check
            val classRange = aspectRatioRanges[detection.label]
            if (classRange != null) {
                val (minRatio, maxRatio) = classRange
                if (aspectRatio < minRatio || aspectRatio > maxRatio) {
                    Log.d("YOLODetector", "Filtered ${detection.label}: aspect ratio $aspectRatio outside class range [$minRatio, $maxRatio]")
                    return@forEach
                }
            }

            // Dimension sanity check - reject extremely thin objects
            if (width < 10f || height < 10f) {
                Log.d("YOLODetector", "Filtered ${detection.label}: dimension too small (${width}x${height})")
                return@forEach
            }

            filtered.add(detection)
        }

        if (detections.size != filtered.size) {
            Log.d("YOLODetector", "Size/Aspect filtering: ${detections.size} -> ${filtered.size} detections")
        }

        return filtered
    }

    // NEW: Apply object tracking across frames
    private fun applyTracking(detections: List<YOLODetectionResult>): List<YOLODetectionResult> {
        val currentTime = System.currentTimeMillis()
        val trackedDetections = mutableListOf<YOLODetectionResult>()
        val matchedTrackIds = mutableSetOf<Int>()

        // Clean up old tracks
        val expiredTracks = trackedObjects.filter { (_, track) ->
            currentTime - track.lastSeen > maxTrackingAge
        }
        expiredTracks.forEach { (id, track) ->
            Log.d("YOLODetector", "Track expired: ID=$id ${track.label} (age=${currentTime - track.lastSeen}ms)")
            trackedObjects.remove(id)
        }

        // Match detections to existing tracks
        detections.forEach { detection ->
            var bestMatchId = -1
            var bestIoU = trackingIouThreshold

            // Find best matching track
            trackedObjects.forEach { (id, track) ->
                if (track.label == detection.label && id !in matchedTrackIds) {
                    val iou = calculateIoU(detection.boundingBox, track.lastBoundingBox)
                    if (iou > bestIoU) {
                        bestIoU = iou
                        bestMatchId = id
                    }
                }
            }

            if (bestMatchId != -1) {
                // Update existing track
                val existingTrack = trackedObjects[bestMatchId]!!
                trackedObjects[bestMatchId] = TrackedObject(
                    id = bestMatchId,
                    label = detection.label,
                    lastBoundingBox = detection.boundingBox,
                    lastSeen = currentTime,
                    confidence = detection.confidence,
                    frameCount = existingTrack.frameCount + 1,
                    firstSeen = existingTrack.firstSeen
                )
                matchedTrackIds.add(bestMatchId)

                trackedDetections.add(
                    detection.copy(
                        trackingId = bestMatchId,
                        frameCount = existingTrack.frameCount + 1
                    )
                )

                Log.d("YOLODetector", "Track updated: ID=$bestMatchId ${detection.label} frames=${existingTrack.frameCount + 1}")
            } else {
                // Create new track
                val newId = nextTrackingId++
                trackedObjects[newId] = TrackedObject(
                    id = newId,
                    label = detection.label,
                    lastBoundingBox = detection.boundingBox,
                    lastSeen = currentTime,
                    confidence = detection.confidence,
                    frameCount = 1,
                    firstSeen = currentTime
                )

                trackedDetections.add(
                    detection.copy(
                        trackingId = newId,
                        frameCount = 1
                    )
                )

                Log.d("YOLODetector", "Track created: ID=$newId ${detection.label}")
            }
        }

        return trackedDetections
    }

    // Enhanced analysis with all improvements
    fun analyzeSuspiciousBehavior(detections: List<YOLODetectionResult>): SecurityAlertResult {
        val currentTime = System.currentTimeMillis()

        val preliminaryAlert = getPreliminaryAlert(detections)
        addToHistory(DetectionHistory(currentTime, detections, preliminaryAlert))

        if (isInCooldown(preliminaryAlert, currentTime)) {
            return SecurityAlertResult(
                alert = SecurityAlert.NONE,
                confidence = 0f,
                shouldTrigger = false,
                reason = "Alert cooldown active"
            )
        }

        val smoothedConfidence = calculateSmoothedConfidence(detections)
        val score = calculateMultiFactorScore(detections, smoothedConfidence)

        if (!validateDetection(detections, score)) {
            return SecurityAlertResult(
                alert = SecurityAlert.NONE,
                confidence = score,
                shouldTrigger = false,
                reason = "Failed validation checks"
            )
        }

        val shouldTrigger = updateAlertState(preliminaryAlert, currentTime, smoothedConfidence)

        return SecurityAlertResult(
            alert = if (shouldTrigger) preliminaryAlert else SecurityAlert.NONE,
            confidence = smoothedConfidence,
            shouldTrigger = shouldTrigger,
            reason = if (shouldTrigger) "Alert conditions met" else "Waiting for stability"
        )
    }

    private fun getPreliminaryAlert(detections: List<YOLODetectionResult>): SecurityAlert {
        if (detections.isEmpty()) return SecurityAlert.NONE

        // NEW: Prefer detections with higher frame counts (more stable)
        val stableDetections = detections.filter { it.frameCount >= 2 }
        val detectionsToUse = if (stableDetections.isNotEmpty()) stableDetections else detections

        val people = detectionsToUse.filter { it.label == "person" }
        val vehicles = detectionsToUse.filter { it.label in listOf("car", "truck", "motorcycle", "bus") }
        val weapons = detectionsToUse.filter { it.label == "knife" }
        val suspicious = detectionsToUse.filter { it.label in listOf("backpack", "handbag") }

        val highConfidenceWeapons = weapons.filter { it.confidence > 0.7f }
        val highConfidencePeople = people.filter { it.confidence > 0.7f }

        return when {
            highConfidenceWeapons.isNotEmpty() -> SecurityAlert.WEAPON_DETECTED
            highConfidencePeople.size >= 3 -> SecurityAlert.MULTIPLE_INTRUDERS
            people.any { it.confidence > 0.65f } && vehicles.any { it.confidence > 0.65f } ->
                SecurityAlert.VEHICLE_WITH_PERSON
            suspicious.size >= 2 && people.any { it.confidence > 0.65f } ->
                SecurityAlert.SUSPICIOUS_ITEMS
            people.any { it.confidence > 0.85f } -> SecurityAlert.HIGH_CONFIDENCE_PERSON
            else -> SecurityAlert.NONE
        }
    }

    private fun addToHistory(detection: DetectionHistory) {
        detectionHistory.add(detection)
        if (detectionHistory.size > maxHistorySize) {
            detectionHistory.removeAt(0)
        }
    }

    private fun isInCooldown(alert: SecurityAlert, currentTime: Long): Boolean {
        if (alert == SecurityAlert.NONE) return false

        val lastAlert = currentAlertState
        if (lastAlert != null && lastAlert.alert == alert) {
            val timeSinceLastAlert = currentTime - lastAlert.lastDetectedTime
            if (timeSinceLastAlert < alertCooldownMs) {
                return true
            }
        }
        return false
    }

    private fun calculateSmoothedConfidence(detections: List<YOLODetectionResult>): Float {
        if (detections.isEmpty()) return 0f

        val currentMaxConfidence = detections.maxOfOrNull { it.confidence } ?: 0f
        val recentDetections = detectionHistory.takeLast(5)
        if (recentDetections.isEmpty()) return currentMaxConfidence

        var smoothed = currentMaxConfidence
        recentDetections.reversed().forEachIndexed { index, history ->
            val historicalConfidence = history.detections.maxOfOrNull { it.confidence } ?: 0f
            val weight = confidenceSmoothingWeight * floatPow(1 - confidenceSmoothingWeight, index + 1)
            smoothed = smoothed * (1 - weight) + historicalConfidence * weight
        }

        return smoothed
    }

    private fun calculateMultiFactorScore(
        detections: List<YOLODetectionResult>,
        smoothedConfidence: Float
    ): Float {
        val presenceScore = smoothedConfidence * objectPresenceWeight
        val countScore = min(detections.size / 5f, 1f) * objectCountWeight
        val interactionScore = calculateInteractionScore(detections) * interactionWeight

        return presenceScore + countScore + interactionScore
    }

    private fun calculateInteractionScore(detections: List<YOLODetectionResult>): Float {
        if (detections.size < 2) return 0f

        var interactions = 0
        val people = detections.filter { it.label == "person" }
        val others = detections.filter { it.label != "person" }

        people.forEach { person ->
            others.forEach { obj ->
                if (areNearby(person.boundingBox, obj.boundingBox)) {
                    interactions++
                }
            }
        }

        return min(interactions / 3f, 1f)
    }

    private fun areNearby(box1: RectF, box2: RectF, threshold: Float = 0.3f): Boolean {
        val centerX1 = (box1.left + box1.right) / 2
        val centerY1 = (box1.top + box1.bottom) / 2
        val centerX2 = (box2.left + box2.right) / 2
        val centerY2 = (box2.top + box2.bottom) / 2

        val distance = sqrt(
            (centerX1 - centerX2) * (centerX1 - centerX2) +
                    (centerY1 - centerY2) * (centerY1 - centerY2)
        )

        val width1 = box1.right - box1.left
        val height1 = box1.bottom - box1.top
        val width2 = box2.right - box2.left
        val height2 = box2.bottom - box2.top
        val avgSize = (width1 + height1 + width2 + height2) / 4

        return distance < avgSize * threshold
    }

    private fun validateDetection(detections: List<YOLODetectionResult>, score: Float): Boolean {
        if (score < 0.3f) return false

        // Already filtered by size in filterDetectionsBySize, so skip tiny detection check

        val recentAlerts = detectionHistory.takeLast(3).map { it.alert }
        val hasConsistentAlert = recentAlerts.count { it != SecurityAlert.NONE } >= 2

        val currentAlert = getPreliminaryAlert(detections)
        if (currentAlert.severity >= 4) return true

        return hasConsistentAlert || recentAlerts.isEmpty()
    }

    private fun updateAlertState(
        alert: SecurityAlert,
        currentTime: Long,
        confidence: Float
    ): Boolean {
        val currentState = currentAlertState

        if (alert == SecurityAlert.NONE) {
            currentAlertState = null
            return false
        }

        if (currentState == null || currentState.alert != alert) {
            currentAlertState = AlertState(
                alert = alert,
                firstDetectedTime = currentTime,
                lastDetectedTime = currentTime,
                consecutiveCount = 1,
                smoothedConfidence = confidence
            )
            return false
        } else {
            val updatedState = currentState.copy(
                lastDetectedTime = currentTime,
                consecutiveCount = currentState.consecutiveCount + 1,
                smoothedConfidence = confidence
            )
            currentAlertState = updatedState

            return updatedState.consecutiveCount >= alertStabilityFrames
        }
    }

    fun getDetectionStats(): DetectionStats {
        return DetectionStats(
            historySize = detectionHistory.size,
            currentAlertState = currentAlertState,
            recentAlerts = detectionHistory.takeLast(5).map { it.alert },
            activeTracksCount = trackedObjects.size,
            trackedObjects = trackedObjects.values.toList()
        )
    }

    // NEW: Get tracking information for a specific object
    fun getTrackInfo(trackingId: Int): TrackedObject? {
        return trackedObjects[trackingId]
    }

    // NEW: Clear all tracks
    fun clearTracks() {
        trackedObjects.clear()
        nextTrackingId = 1
        Log.d("YOLODetector", "All tracks cleared")
    }

    // Backward compatibility
    fun analyzeSecurity(detections: List<YOLODetectionResult>): SecurityAlert {
        return analyzeSuspiciousBehavior(detections).alert
    }

    fun cleanup() {
        interpreter?.close()
        interpreter = null
        detectionHistory.clear()
        currentAlertState = null
        trackedObjects.clear()
    }

    private fun floatPow(base: Float, exp: Int): Float {
        var result = 1f
        repeat(exp) { result *= base }
        return result
    }

    data class SecurityAlertResult(
        val alert: SecurityAlert,
        val confidence: Float,
        val shouldTrigger: Boolean,
        val reason: String
    )

    data class DetectionStats(
        val historySize: Int,
        val currentAlertState: AlertState?,
        val recentAlerts: List<SecurityAlert>,
        val activeTracksCount: Int,  // NEW
        val trackedObjects: List<TrackedObject>  // NEW
    )

    enum class SecurityAlert(val displayName: String, val severity: Int) {
        NONE("Normal", 0),
        HIGH_CONFIDENCE_PERSON("Person Detected", 1),
        SUSPICIOUS_ITEMS("Suspicious Items", 2),
        MASKED_PERSON("Masked Person Detected", 3),
        VEHICLE_WITH_PERSON("Vehicle with Person", 3),
        MULTIPLE_INTRUDERS("Multiple People", 4),
        WEAPON_DETECTED("Weapon Detected!", 5)
    }
}