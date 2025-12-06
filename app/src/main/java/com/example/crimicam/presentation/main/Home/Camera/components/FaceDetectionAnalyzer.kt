package com.example.crimicam.presentation.main.Home.Camera.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.crimicam.presentation.main.Home.Camera.CameraViewModel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FaceDetectionAnalyzer(
    private val viewModel: CameraViewModel
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val analyzeInterval = 1000L // Analyze every 1 second to avoid overload

    companion object {
        private const val TAG = "FaceDetectionAnalyzer"
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()

        // Skip if analyzing too frequently
        if (currentTimestamp - lastAnalyzedTimestamp < analyzeInterval) {
            imageProxy.close()
            return
        }

        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                Log.d(TAG, "Processing frame: ${bitmap.width}x${bitmap.height}")

                // Send to ViewModel for face detection and Firestore saving
                viewModel.processFrameForDetection(bitmap)

                lastAnalyzedTimestamp = currentTimestamp
            } else {
                Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Convert ImageProxy to Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Convert to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            // Rotate bitmap if needed
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                rotateBitmap(bitmap, rotationDegrees.toFloat())
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)

            // Fallback: Try YUV conversion
            try {
                imageProxyToBitmapYUV(imageProxy)
            } catch (e2: Exception) {
                Log.e(TAG, "YUV conversion also failed", e2)
                null
            }
        }
    }

    /**
     * Alternative YUV to Bitmap conversion
     */
    private fun imageProxyToBitmapYUV(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate if needed
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees.toFloat())
        } else {
            bitmap
        }
    }

    /**
     * Rotate bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}