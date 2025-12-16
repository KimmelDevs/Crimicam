package com.example.crimicam.ml


import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class FaceDetector {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.15f)
        .build()

    private val detector = FaceDetection.getClient(options)

    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return try {
            detector.process(image).await()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cropFace(bitmap: Bitmap, face: Face): Bitmap? {
        return try {
            val bounds = face.boundingBox

            // Add 20% padding around face
            val padding = (bounds.width() * 0.2f).toInt()
            val left = maxOf(0, bounds.left - padding)
            val top = maxOf(0, bounds.top - padding)
            val right = minOf(bitmap.width, bounds.right + padding)
            val bottom = minOf(bitmap.height, bounds.bottom + padding)

            val width = right - left
            val height = bottom - top

            if (width > 0 && height > 0) {
                Bitmap.createBitmap(bitmap, left, top, width, height)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract simple face features for comparison
     * This is a simplified alternative to embeddings
     */
    fun extractFaceFeatures(face: Face): Map<String, Float> {
        val features = mutableMapOf<String, Float>()

        // Store face dimensions and angles
        features["width"] = face.boundingBox.width().toFloat()
        features["height"] = face.boundingBox.height().toFloat()
        features["aspectRatio"] = face.boundingBox.width().toFloat() / face.boundingBox.height().toFloat()

        // Store head rotation angles
        face.headEulerAngleX?.let { features["headX"] = it }
        face.headEulerAngleY?.let { features["headY"] = it }
        face.headEulerAngleZ?.let { features["headZ"] = it }

        // Store smile and eye open probabilities
        face.smilingProbability?.let { features["smile"] = it }
        face.leftEyeOpenProbability?.let { features["leftEye"] = it }
        face.rightEyeOpenProbability?.let { features["rightEye"] = it }

        return features
    }

    fun close() {
        detector.close()
    }
}