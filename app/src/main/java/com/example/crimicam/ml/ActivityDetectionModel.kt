package com.example.crimicam.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.*

/**
 * Scalable Activity Detection System
 * Easily extensible for new suspicious activities
 */
class ActivityDetectionModel(context: Context) {

    private val poseDetector: PoseDetector
    private val activityAnalyzer = ActivityAnalyzer()
    private val activityHistory = ActivityHistory()

    init {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)
    }

    /**
     * Main detection function - processes frame and returns suspicious activities
     */
    suspend fun detectSuspiciousActivity(bitmap: Bitmap): DetectionResult {
        val pose = detectPose(bitmap) ?: return DetectionResult.NoPoseDetected

        val activities = mutableListOf<SuspiciousActivity>()

        // Check each activity detector
        activityAnalyzer.detectors.forEach { detector ->
            val result = detector.analyze(pose, activityHistory)
            if (result.isDetected && result.confidence >= detector.confidenceThreshold) {
                activities.add(
                    SuspiciousActivity(
                        type = detector.activityType,
                        confidence = result.confidence,
                        duration = result.duration,
                        details = result.details
                    )
                )
            }
        }

        // Update history
        activityHistory.addPoseFrame(pose, System.currentTimeMillis())

        return if (activities.isNotEmpty()) {
            DetectionResult.Detected(activities)
        } else {
            DetectionResult.Normal
        }
    }

    private suspend fun detectPose(bitmap: Bitmap): Pose? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        poseDetector.process(image)
            .addOnSuccessListener { pose -> cont.resume(pose) }
            .addOnFailureListener { cont.resume(null) }
    }

    fun cleanup() {
        poseDetector.close()
    }
}

// ============= RESULT CLASSES =============

sealed class DetectionResult {
    object NoPoseDetected : DetectionResult()
    object Normal : DetectionResult()
    data class Detected(val activities: List<SuspiciousActivity>) : DetectionResult()
}

data class SuspiciousActivity(
    val type: ActivityType,
    val confidence: Float,
    val duration: Long,
    val details: Map<String, Any>
)

enum class ActivityType(val displayName: String, val severity: Severity) {
    LOITERING("Loitering", Severity.MEDIUM),
    PACING("Pacing", Severity.MEDIUM),
    CROUCHING("Crouching", Severity.HIGH),
    HIDING("Hiding/Concealing", Severity.HIGH),
    CLIMBING("Climbing", Severity.HIGH),
    AGGRESSIVE_GESTURE("Aggressive Gesture", Severity.HIGH),
    VANDALISM("Vandalism Motion", Severity.CRITICAL),
    RUNNING("Running", Severity.LOW)
}

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

// ============= ACTIVITY ANALYZER =============

class ActivityAnalyzer {
    val detectors = listOf(
        LoiteringDetector(),
        PacingDetector(),
        CrouchingDetector(),
        HidingDetector(),
        ClimbingDetector(),
        AggressiveGestureDetector(),
        VandalismDetector(),
        RunningDetector()
    )
}

// ============= BASE DETECTOR =============

abstract class ActivityDetector {
    abstract val activityType: ActivityType
    abstract val confidenceThreshold: Float

    abstract fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult

    data class AnalysisResult(
        val isDetected: Boolean,
        val confidence: Float,
        val duration: Long = 0L,
        val details: Map<String, Any> = emptyMap()
    )
}

// ============= ACTIVITY HISTORY =============

class ActivityHistory {
    private val maxHistorySize = 150 // 5 seconds at 30fps
    private val poseHistory = mutableListOf<PoseFrame>()
    private val positionHistory = mutableListOf<Position>()

    data class PoseFrame(val pose: Pose, val timestamp: Long)
    data class Position(val x: Float, val y: Float, val timestamp: Long)

    fun addPoseFrame(pose: Pose, timestamp: Long) {
        poseHistory.add(PoseFrame(pose, timestamp))
        if (poseHistory.size > maxHistorySize) {
            poseHistory.removeAt(0)
        }

        // Track center position
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isNotEmpty()) {
            val centerX = landmarks.map { it.position.x }.average().toFloat()
            val centerY = landmarks.map { it.position.y }.average().toFloat()
            positionHistory.add(Position(centerX, centerY, timestamp))
            if (positionHistory.size > maxHistorySize) {
                positionHistory.removeAt(0)
            }
        }
    }

    fun getRecentPoses(durationMs: Long): List<PoseFrame> {
        val cutoff = System.currentTimeMillis() - durationMs
        return poseHistory.filter { it.timestamp >= cutoff }
    }

    fun getRecentPositions(durationMs: Long): List<Position> {
        val cutoff = System.currentTimeMillis() - durationMs
        return positionHistory.filter { it.timestamp >= cutoff }
    }

    fun clear() {
        poseHistory.clear()
        positionHistory.clear()
    }
}

// ============= POSE UTILITIES =============

object PoseUtils {
    fun getBodyAngle(pose: Pose): Float {
        val leftShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_SHOULDER) ?: return 0f
        val rightShoulder = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.RIGHT_SHOULDER) ?: return 0f
        val leftHip = pose.getPoseLandmark(com.google.mlkit.vision.pose.PoseLandmark.LEFT_HIP) ?: return 0f

        val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val hipY = leftHip.position.y

        return abs(shoulderMidY - hipY)
    }

    fun getKneeAngle(pose: Pose, isLeft: Boolean): Float {
        val hip = if (isLeft) pose.getPoseLandmark(11) else pose.getPoseLandmark(12)
        val knee = if (isLeft) pose.getPoseLandmark(13) else pose.getPoseLandmark(14)
        val ankle = if (isLeft) pose.getPoseLandmark(15) else pose.getPoseLandmark(16)

        if (hip == null || knee == null || ankle == null) return 180f

        val angle1 = atan2(hip.position.y - knee.position.y, hip.position.x - knee.position.x)
        val angle2 = atan2(ankle.position.y - knee.position.y, ankle.position.x - knee.position.x)

        return abs(Math.toDegrees((angle1 - angle2).toDouble()).toFloat())
    }

    fun getAverageMovement(positions: List<ActivityHistory.Position>): Float {
        if (positions.size < 2) return 0f

        var totalDist = 0f
        for (i in 1 until positions.size) {
            val dx = positions[i].x - positions[i - 1].x
            val dy = positions[i].y - positions[i - 1].y
            totalDist += sqrt(dx * dx + dy * dy)
        }

        return totalDist / positions.size
    }

    fun getHandsAboveHead(pose: Pose): Boolean {
        val nose = pose.getPoseLandmark(0) ?: return false
        val leftWrist = pose.getPoseLandmark(15) ?: return false
        val rightWrist = pose.getPoseLandmark(16) ?: return false

        return leftWrist.position.y < nose.position.y || rightWrist.position.y < nose.position.y
    }
}

// ============= INDIVIDUAL DETECTORS =============

class LoiteringDetector : ActivityDetector() {
    override val activityType = ActivityType.LOITERING
    override val confidenceThreshold = 0.7f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val positions = history.getRecentPositions(10000) // 10 seconds
        if (positions.size < 30) return AnalysisResult(false, 0f)

        val avgMovement = PoseUtils.getAverageMovement(positions)

        // Loitering = very little movement over long period
        val isLoitering = avgMovement < 5f && positions.size >= 150
        val confidence = if (isLoitering) {
            (1f - (avgMovement / 10f)).coerceIn(0f, 1f)
        } else 0f

        val duration = if (positions.isNotEmpty()) {
            positions.last().timestamp - positions.first().timestamp
        } else 0L

        return AnalysisResult(
            isDetected = isLoitering,
            confidence = confidence,
            duration = duration,
            details = mapOf("avgMovement" to avgMovement)
        )
    }
}

class PacingDetector : ActivityDetector() {
    override val activityType = ActivityType.PACING
    override val confidenceThreshold = 0.65f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val positions = history.getRecentPositions(5000) // 5 seconds
        if (positions.size < 50) return AnalysisResult(false, 0f)

        // Detect repetitive back-and-forth movement
        val directionChanges = countDirectionChanges(positions)
        val avgMovement = PoseUtils.getAverageMovement(positions)

        // Pacing = moderate movement with frequent direction changes
        val isPacing = directionChanges >= 3 && avgMovement in 10f..40f
        val confidence = if (isPacing) {
            (directionChanges / 6f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isPacing,
            confidence = confidence,
            details = mapOf(
                "directionChanges" to directionChanges,
                "avgMovement" to avgMovement
            )
        )
    }

    private fun countDirectionChanges(positions: List<ActivityHistory.Position>): Int {
        if (positions.size < 3) return 0

        var changes = 0
        var prevDirection = 0 // -1 = left, 1 = right, 0 = stationary

        for (i in 1 until positions.size) {
            val dx = positions[i].x - positions[i - 1].x
            val newDirection = when {
                dx > 2 -> 1
                dx < -2 -> -1
                else -> prevDirection
            }

            if (newDirection != 0 && newDirection != prevDirection) {
                changes++
            }
            prevDirection = newDirection
        }

        return changes
    }
}

class CrouchingDetector : ActivityDetector() {
    override val activityType = ActivityType.CROUCHING
    override val confidenceThreshold = 0.75f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val bodyAngle = PoseUtils.getBodyAngle(pose)
        val leftKneeAngle = PoseUtils.getKneeAngle(pose, true)
        val rightKneeAngle = PoseUtils.getKneeAngle(pose, false)

        // Crouching = compressed body, bent knees
        val isCrouching = bodyAngle < 100f &&
                (leftKneeAngle < 120f || rightKneeAngle < 120f)

        val confidence = if (isCrouching) {
            val angleScore = 1f - (bodyAngle / 150f)
            val kneeScore = 1f - (min(leftKneeAngle, rightKneeAngle) / 180f)
            ((angleScore + kneeScore) / 2f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isCrouching,
            confidence = confidence,
            details = mapOf(
                "bodyAngle" to bodyAngle,
                "leftKneeAngle" to leftKneeAngle,
                "rightKneeAngle" to rightKneeAngle
            )
        )
    }
}

class HidingDetector : ActivityDetector() {
    override val activityType = ActivityType.HIDING
    override val confidenceThreshold = 0.7f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.size < 10) {
            // Many landmarks not visible = possibly hiding
            return AnalysisResult(
                isDetected = true,
                confidence = 0.8f,
                details = mapOf("visibleLandmarks" to landmarks.size)
            )
        }

        // Check if person is in compact/concealed position
        val bodyAngle = PoseUtils.getBodyAngle(pose)
        val handsNearFace = checkHandsNearFace(pose)

        val isHiding = bodyAngle < 80f || handsNearFace
        val confidence = if (isHiding) 0.75f else 0f

        return AnalysisResult(
            isDetected = isHiding,
            confidence = confidence,
            details = mapOf("bodyAngle" to bodyAngle)
        )
    }

    private fun checkHandsNearFace(pose: Pose): Boolean {
        val nose = pose.getPoseLandmark(0) ?: return false
        val leftWrist = pose.getPoseLandmark(15)
        val rightWrist = pose.getPoseLandmark(16)

        fun distance(x1: Float, y1: Float, x2: Float, y2: Float) =
            sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

        val leftDist = leftWrist?.let {
            distance(it.position.x, it.position.y, nose.position.x, nose.position.y)
        } ?: Float.MAX_VALUE

        val rightDist = rightWrist?.let {
            distance(it.position.x, it.position.y, nose.position.x, nose.position.y)
        } ?: Float.MAX_VALUE

        return leftDist < 100f || rightDist < 100f
    }
}

class ClimbingDetector : ActivityDetector() {
    override val activityType = ActivityType.CLIMBING
    override val confidenceThreshold = 0.7f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val handsAboveHead = PoseUtils.getHandsAboveHead(pose)
        val positions = history.getRecentPositions(2000)

        // Climbing = hands above head + upward movement
        val verticalMovement = if (positions.size >= 20) {
            positions.first().y - positions.last().y // positive = moving up
        } else 0f

        val isClimbing = handsAboveHead && verticalMovement > 20f
        val confidence = if (isClimbing) {
            ((verticalMovement / 50f) * 0.7f + 0.3f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isClimbing,
            confidence = confidence,
            details = mapOf(
                "handsAboveHead" to handsAboveHead,
                "verticalMovement" to verticalMovement
            )
        )
    }
}

class AggressiveGestureDetector : ActivityDetector() {
    override val activityType = ActivityType.AGGRESSIVE_GESTURE
    override val confidenceThreshold = 0.65f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val recentPoses = history.getRecentPoses(1000)
        if (recentPoses.size < 10) return AnalysisResult(false, 0f)

        // Detect rapid arm movements
        val armMovement = calculateArmMovementSpeed(recentPoses)

        val isAggressive = armMovement > 50f
        val confidence = if (isAggressive) {
            (armMovement / 100f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isAggressive,
            confidence = confidence,
            details = mapOf("armMovement" to armMovement)
        )
    }

    private fun calculateArmMovementSpeed(poses: List<ActivityHistory.PoseFrame>): Float {
        var totalMovement = 0f

        for (i in 1 until poses.size) {
            val prev = poses[i - 1].pose
            val curr = poses[i].pose

            val prevLeftWrist = prev.getPoseLandmark(15)
            val currLeftWrist = curr.getPoseLandmark(15)
            val prevRightWrist = prev.getPoseLandmark(16)
            val currRightWrist = curr.getPoseLandmark(16)

            if (prevLeftWrist != null && currLeftWrist != null) {
                totalMovement += abs(currLeftWrist.position.x - prevLeftWrist.position.x)
                totalMovement += abs(currLeftWrist.position.y - prevLeftWrist.position.y)
            }

            if (prevRightWrist != null && currRightWrist != null) {
                totalMovement += abs(currRightWrist.position.x - prevRightWrist.position.x)
                totalMovement += abs(currRightWrist.position.y - prevRightWrist.position.y)
            }
        }

        return totalMovement / poses.size
    }
}

class VandalismDetector : ActivityDetector() {
    override val activityType = ActivityType.VANDALISM
    override val confidenceThreshold = 0.7f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val recentPoses = history.getRecentPoses(2000)
        if (recentPoses.size < 20) return AnalysisResult(false, 0f)

        // Vandalism = repetitive striking motions
        val strikeMotions = detectStrikeMotions(recentPoses)

        val isVandalism = strikeMotions >= 2
        val confidence = if (isVandalism) {
            (strikeMotions / 4f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isVandalism,
            confidence = confidence,
            details = mapOf("strikeMotions" to strikeMotions)
        )
    }

    private fun detectStrikeMotions(poses: List<ActivityHistory.PoseFrame>): Int {
        var strikes = 0
        var prevArmHeight = 0f

        for (frame in poses) {
            val leftWrist = frame.pose.getPoseLandmark(15)
            val rightWrist = frame.pose.getPoseLandmark(16)

            val avgArmHeight = listOfNotNull(leftWrist, rightWrist)
                .map { it.position.y }
                .average().toFloat()

            // Detect downward strike (arm goes down quickly)
            if (avgArmHeight - prevArmHeight > 30f) {
                strikes++
            }

            prevArmHeight = avgArmHeight
        }

        return strikes
    }
}

class RunningDetector : ActivityDetector() {
    override val activityType = ActivityType.RUNNING
    override val confidenceThreshold = 0.6f

    override fun analyze(pose: Pose, history: ActivityHistory): AnalysisResult {
        val positions = history.getRecentPositions(1000)
        if (positions.size < 20) return AnalysisResult(false, 0f)

        val avgMovement = PoseUtils.getAverageMovement(positions)

        // Running = high movement speed
        val isRunning = avgMovement > 40f
        val confidence = if (isRunning) {
            (avgMovement / 80f).coerceIn(0f, 1f)
        } else 0f

        return AnalysisResult(
            isDetected = isRunning,
            confidence = confidence,
            details = mapOf("avgMovement" to avgMovement)
        )
    }
}