package com.example.crimicam.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Fast human presence detector using TFLite model
 * Output: 0.0 - 1.0 (probability of human presence)
 * Threshold: > 0.5 = human present
 *
 * Dependencies: org.tensorflow:tensorflow-lite:2.14.0
 */
class PresenceDetector(context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 224 // Typical for mobile models

    init {
        try {
            // Load model from assets
            val assetFileDescriptor = context.assets.openFd("presence_detector.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            // Create interpreter with options
            val options = Interpreter.Options().apply {
                setNumThreads(2) // Fast inference
            }
            interpreter = Interpreter(modelBuffer, options)

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
            // Preprocess image manually
            val inputBuffer = preprocessBitmap(bitmap)

            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Run inference
            interpreter.run(inputBuffer, outputBuffer)

            // Read result
            outputBuffer.rewind()
            val probability = outputBuffer.float

            Log.d(TAG, "👁️ Presence probability: ${(probability * 100).toInt()}%")

            return probability

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting presence: ${e.message}", e)
            return 0f
        }
    }

    /**
     * Preprocess bitmap to model input format
     */
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        // Resize bitmap to input size
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Allocate buffer: 4 bytes per float * inputSize * inputSize * 3 channels
        val inputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).apply {
            order(ByteOrder.nativeOrder())
        }

        // Extract pixels and normalize
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // Extract RGB and normalize to [0, 1]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()
        return inputBuffer
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