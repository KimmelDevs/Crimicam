package com.example.crimicam.ml

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs

data class MotionResult(
    val hasMotion: Boolean,
    val changePercentage: Float,
    val isCalibrating: Boolean = false
)

class MotionDetector(
    private val threshold: Float = 0.02f, // 2% change threshold
    private val calibrationFrames: Int = 5, // Number of frames to calibrate
    private val downsampleSize: Int = 64 // Smaller = faster
) {
    private var previousFrame: IntArray? = null
    private var frameCount = 0
    private var isCalibrated = false

    fun detectMotion(bitmap: Bitmap): MotionResult {
        try {
            // Downsample bitmap for faster processing
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                downsampleSize,
                downsampleSize,
                false
            )

            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            // Convert to grayscale for comparison
            val grayscale = IntArray(pixels.size)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Simple grayscale conversion
                grayscale[i] = (r + g + b) / 3
            }

            // Calibration phase - collect baseline frames
            if (!isCalibrated) {
                frameCount++
                previousFrame = grayscale

                if (frameCount >= calibrationFrames) {
                    isCalibrated = true
                    Log.d("MotionDetector", "✅ Calibration complete after $frameCount frames")
                }

                return MotionResult(
                    hasMotion = false,
                    changePercentage = 0f,
                    isCalibrating = true
                )
            }

            // No previous frame to compare
            val previous = previousFrame
            if (previous == null) {
                previousFrame = grayscale
                return MotionResult(
                    hasMotion = false,
                    changePercentage = 0f,
                    isCalibrating = false
                )
            }

            // Calculate pixel differences
            var totalDiff = 0L
            var changedPixels = 0
            val diffThreshold = 30 // Minimum difference to count as change

            for (i in grayscale.indices) {
                val diff = abs(grayscale[i] - previous[i])
                totalDiff += diff
                if (diff > diffThreshold) {
                    changedPixels++
                }
            }

            val avgDiff = totalDiff.toFloat() / grayscale.size
            val changePercentage = changedPixels.toFloat() / grayscale.size

            // Update previous frame
            previousFrame = grayscale

            val hasMotion = changePercentage >= threshold

            if (hasMotion) {
                Log.d("MotionDetector", "🏃 Motion: ${(changePercentage * 100).toInt()}% (avg diff: ${avgDiff.toInt()})")
            }

            return MotionResult(
                hasMotion = hasMotion,
                changePercentage = changePercentage,
                isCalibrating = false
            )

        } catch (e: Exception) {
            Log.e("MotionDetector", "Error detecting motion: ${e.message}", e)
            return MotionResult(
                hasMotion = true, // Fail-safe: assume motion to continue processing
                changePercentage = 1f,
                isCalibrating = false
            )
        }
    }

    fun reset() {
        previousFrame = null
        frameCount = 0
        isCalibrated = false
        Log.d("MotionDetector", "🔄 Motion detector reset - will recalibrate")
    }

    fun isReady(): Boolean = isCalibrated
}