package com.example.crimicam.ml

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs

/**
 * Lightweight motion detector using frame differencing
 * Detects significant changes between consecutive frames
 *
 * NO EXTERNAL DEPENDENCIES NEEDED - uses only Android SDK
 */
class MotionDetector {

    private var previousFrame: IntArray? = null
    private var frameWidth = 0
    private var frameHeight = 0

    // Configurable thresholds
    private val pixelThreshold = 30 // Pixel difference threshold (0-255)
    private val motionThreshold = 0.05f // 5% of pixels must change

    data class MotionResult(
        val hasMotion: Boolean,
        val changePercentage: Float,
        val confidence: Float
    )

    /**
     * Detect motion by comparing current frame with previous frame
     * @param bitmap Current camera frame
     * @return MotionResult with motion status and details
     */
    fun detectMotion(bitmap: Bitmap): MotionResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)

        // Extract pixel data from bitmap
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // First frame - no motion detection possible
        if (previousFrame == null || frameWidth != width || frameHeight != height) {
            previousFrame = pixels
            frameWidth = width
            frameHeight = height
            return MotionResult(
                hasMotion = true, // Assume motion on first frame
                changePercentage = 0f,
                confidence = 0f
            )
        }

        // Compare current frame with previous frame
        var changedPixels = 0
        val totalPixels = pixels.size

        for (i in pixels.indices) {
            val diff = getPixelDifference(pixels[i], previousFrame!![i])
            if (diff > pixelThreshold) {
                changedPixels++
            }
        }

        // Calculate metrics
        val changePercentage = (changedPixels.toFloat() / totalPixels)
        val hasMotion = changePercentage >= motionThreshold
        val confidence = (changePercentage / motionThreshold).coerceIn(0f, 1f)

        // Update previous frame for next comparison
        previousFrame = pixels

        // Log motion detection
        if (hasMotion) {
            Log.d(TAG, "🏃 Motion detected: ${(changePercentage * 100).toInt()}% pixels changed")
        }

        return MotionResult(
            hasMotion = hasMotion,
            changePercentage = changePercentage,
            confidence = confidence
        )
    }

    /**
     * Calculate pixel difference (simplified grayscale comparison)
     * Compares RGB values and returns average difference
     */
    private fun getPixelDifference(pixel1: Int, pixel2: Int): Int {
        // Extract RGB components
        val r1 = (pixel1 shr 16) and 0xFF
        val g1 = (pixel1 shr 8) and 0xFF
        val b1 = pixel1 and 0xFF

        val r2 = (pixel2 shr 16) and 0xFF
        val g2 = (pixel2 shr 8) and 0xFF
        val b2 = pixel2 and 0xFF

        // Calculate average difference across all channels
        return (abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)) / 3
    }

    /**
     * Reset detector state
     * Call this when camera is paused/resumed or orientation changes
     */
    fun reset() {
        previousFrame = null
        frameWidth = 0
        frameHeight = 0
        Log.d(TAG, "Motion detector reset")
    }

    /**
     * Adjust sensitivity
     * @param sensitivity 0.0 (least sensitive) to 1.0 (most sensitive)
     */
    fun setSensitivity(sensitivity: Float) {
        // Lower threshold = more sensitive to motion
        val adjustedThreshold = 0.1f * (1f - sensitivity) // 0.01 to 0.1
        Log.d(TAG, "Motion sensitivity adjusted to ${(sensitivity * 100).toInt()}%")
    }

    companion object {
        private const val TAG = "MotionDetector"
    }
}