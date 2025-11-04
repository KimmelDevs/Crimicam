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

data class YOLODetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classId: Int
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
    private val confidenceThreshold = 0.6f  // Increased from 0.5 for fewer false positives
    private val iouThreshold = 0.5f  // Increased from 0.45 for better NMS

    // Security-relevant classes for crime detection
    private val securityClasses = listOf(
        "person", "car", "truck", "motorcycle", "bus",
        "bicycle", "backpack", "handbag", "knife", "bottle"
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
        // options.addDelegate(GpuDelegate()) // Enable GPU if needed

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

            // YOLOv8 output shape: [1, 84, 8400]
            // Dimensions are: [batch, features, anchors]
            val output = Array(1) { Array(84) { FloatArray(8400) } }

            interpreter?.run(input, output)

            return processOutput(output[0], bitmap.width, bitmap.height)
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

                // Normalize to [0,1] - RGB order
                inputBuffer.putFloat((value shr 16 and 0xFF) / 255.0f) // R
                inputBuffer.putFloat((value shr 8 and 0xFF) / 255.0f)  // G
                inputBuffer.putFloat((value and 0xFF) / 255.0f)        // B
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

        // YOLOv8 output format: [84, 8400]
        // First 4 rows: x_center, y_center, width, height (in normalized coordinates)
        // Next 80 rows: class probabilities for 80 COCO classes

        for (i in 0 until 8400) {
            var maxClassIdx = -1
            var maxScore = -1f

            // Find class with highest confidence (rows 4-83)
            for (c in 0 until 80) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClassIdx = c
                }
            }

            // Check if detection meets threshold
            if (maxClassIdx != -1 && maxScore > confidenceThreshold) {
                val className = labels.getOrNull(maxClassIdx) ?: "unknown"

                // Only process security-relevant classes
                if (className in securityClasses) {
                    // Get bounding box coordinates (normalized 0-1 relative to input size)
                    val xCenter = output[0][i] / inputSize
                    val yCenter = output[1][i] / inputSize
                    val width = output[2][i] / inputSize
                    val height = output[3][i] / inputSize

                    // Convert to pixel coordinates in original image
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

        // Apply Non-Maximum Suppression
        return nonMaxSuppression(detections)
    }

    private fun nonMaxSuppression(detections: List<YOLODetectionResult>): List<YOLODetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val result = mutableListOf<YOLODetectionResult>()

        // Group by class for better NMS
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

    // NEW: Method called by CameraViewModel with improved logic
    fun analyzeSuspiciousBehavior(detections: List<YOLODetectionResult>): SecurityAlert {
        if (detections.isEmpty()) return SecurityAlert.NONE

        val people = detections.filter { it.label == "person" }
        val vehicles = detections.filter { it.label in listOf("car", "truck", "motorcycle", "bus") }
        val weapons = detections.filter { it.label == "knife" }
        val suspicious = detections.filter { it.label in listOf("backpack", "handbag") }

        // Higher confidence threshold for critical alerts
        val highConfidenceWeapons = weapons.filter { it.confidence > 0.7f }
        val highConfidencePeople = people.filter { it.confidence > 0.7f }

        return when {
            // Critical: Weapons detected with high confidence
            highConfidenceWeapons.isNotEmpty() -> SecurityAlert.WEAPON_DETECTED

            // High: Multiple people with good confidence
            highConfidencePeople.size >= 3 -> SecurityAlert.MULTIPLE_INTRUDERS

            // Medium: Person with vehicle (require both to have decent confidence)
            people.any { it.confidence > 0.65f } &&
                    vehicles.any { it.confidence > 0.65f } -> SecurityAlert.VEHICLE_WITH_PERSON

            // Medium: Multiple suspicious items with person
            suspicious.size >= 2 &&
                    people.any { it.confidence > 0.65f } -> SecurityAlert.SUSPICIOUS_ITEMS

            // Low: Single high confidence person
            people.any { it.confidence > 0.85f } -> SecurityAlert.HIGH_CONFIDENCE_PERSON

            else -> SecurityAlert.NONE
        }
    }

    // Keep the old method for backward compatibility
    fun analyzeSecurity(detections: List<YOLODetectionResult>): SecurityAlert {
        return analyzeSuspiciousBehavior(detections)
    }

    fun cleanup() {
        interpreter?.close()
        interpreter = null
    }

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