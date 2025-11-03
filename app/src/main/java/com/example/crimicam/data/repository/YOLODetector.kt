package com.example.crimicam.data.repository


import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val classId: Int
)

class YOLODetector(context: Context) {
    private var model: Interpreter
    private val labels: List<String>

    // YOLOv8 output shape: [1, 84, 8400]
    private val inputSize = 640
    private val outputShape = floatArrayOf(1f, 84f, 8400f)

    // COCO classes relevant for security
    private val securityClasses = listOf("person", "car", "truck", "motorcycle", "bus")

    init {
        model = loadModel(context)
        labels = loadLabels(context)
    }

    private fun loadModel(context: Context): Interpreter {
        val assetFileDescriptor = context.assets.openFd("yolov8n_float16.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val options = Interpreter.Options()
        options.setNumThreads(4)

        return Interpreter(modelBuffer, options)
    }

    private fun loadLabels(context: Context): List<String> {
        return context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val input = preprocessImage(bitmap)
        val output = Array(1) { Array(84) { FloatArray(8400) } }

        model.run(input, output)

        return processOutput(output[0])
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        var pixel = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val value = intValues[pixel++]

                // Normalize to [0,1]
                inputBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((value and 0xFF) / 255.0f))
            }
        }

        return inputBuffer
    }

    private fun processOutput(output: Array<FloatArray>): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val confidenceThreshold = 0.5f

        // YOLOv8 output: 84 x 8400 (4 bbox + 80 classes)
        for (i in 0 until 8400) {
            var maxClass = -1
            var maxScore = -1f

            // Find class with highest score
            for (c in 0 until 80) {
                val score = output[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = c
                }
            }

            // Check if it's a security-relevant class and above threshold
            if (maxClass != -1 && maxScore > confidenceThreshold) {
                val className = labels.getOrNull(maxClass) ?: "unknown"

                if (className in securityClasses) {
                    val bbox = floatArrayOf(
                        output[0][i], // x_center
                        output[1][i], // y_center
                        output[2][i], // width
                        output[3][i]  // height
                    )

                    // Convert normalized coordinates to pixel coordinates
                    val left = (bbox[0] - bbox[2] / 2) * inputSize
                    val top = (bbox[1] - bbox[3] / 2) * inputSize
                    val right = (bbox[0] + bbox[2] / 2) * inputSize
                    val bottom = (bbox[1] + bbox[3] / 2) * inputSize

                    results.add(
                        DetectionResult(
                            label = className,
                            confidence = maxScore,
                            boundingBox = RectF(left, top, right, bottom),
                            classId = maxClass
                        )
                    )
                }
            }
        }

        // Apply NMS (Non-Maximum Suppression)
        return nonMaxSuppression(results)
    }

    private fun nonMaxSuppression(detections: List<DetectionResult>): List<DetectionResult> {
        return detections.sortedByDescending { it.confidence }.take(10) // Simple NMS - take top 10
    }

    fun analyzeSuspiciousBehavior(detections: List<DetectionResult>): SecurityAlert {
        val people = detections.filter { it.label == "person" }
        val vehicles = detections.filter { it.label in listOf("car", "truck", "motorcycle") }

        return when {
            people.size >= 3 -> SecurityAlert.MULTIPLE_INTRUDERS
            people.isNotEmpty() && vehicles.isNotEmpty() -> SecurityAlert.VEHICLE_WITH_PERSON
            people.any { it.confidence > 0.8 } -> SecurityAlert.HIGH_CONFIDENCE_PERSON
            else -> SecurityAlert.NONE
        }
    }

    enum class SecurityAlert {
        NONE, MULTIPLE_INTRUDERS, VEHICLE_WITH_PERSON, HIGH_CONFIDENCE_PERSON
    }
}