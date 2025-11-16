package com.example.crimicam.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

/**
 * Fast human presence detector using TFLite model
 * Output: 0.0 - 1.0 (probability of human presence)
 * Threshold: > 0.5 = human present
 */
class PresenceDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 224 // Typical for mobile models

    // Image preprocessing pipeline
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f)) // Normalize to [0, 1]
        .build()

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "presence_detector.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(2) // Fast inference
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "✅ Presence detector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load presence detector: ${e.message}", e)
        }
    }

    /**
     * Detect human presence in frame
     * @return Float 0.0-1.0 (probability of human present)
     */
    fun detectPresence(bitmap: Bitmap): Float {
        val interpreter = this.interpreter ?: return 0f

        try {
            // Preprocess image
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            // Prepare output buffer
            val output = Array(1) { FloatArray(1) }

            // Run inference
            interpreter.run(processedImage.buffer, output)

            val probability = output[0][0]

            Log.d(TAG, "👁️ Presence probability: ${(probability * 100).toInt()}%")

            return probability

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting presence: ${e.message}", e)
            return 0f
        }
    }

    /**
     * Check if human is present (threshold = 0.5)
     */
    fun isHumanPresent(bitmap: Bitmap, threshold: Float = 0.5f): Boolean {
        return detectPresence(bitmap) >= threshold
    }

    fun cleanup() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "Presence detector cleaned up")
    }

    companion object {
        private const val TAG = "PresenceDetector"
    }
}