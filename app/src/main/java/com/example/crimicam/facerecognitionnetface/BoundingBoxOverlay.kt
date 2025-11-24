package com.example.crimicam.facerecognitionnetface

import android.graphics.RectF
import androidx.camera.core.CameraSelector

/**
 * Data class for face predictions
 * Used by the face detection overlay system
 */
data class Prediction(
    val bbox: RectF,
    val label: String,
    val maskLabel: String = "",
    val confidence: Float = 0f
)

/**
 * Helper class for bounding box transformations
 * Converts camera coordinates to screen coordinates
 */
class BoundingBoxTransformer {

    private var frameWidth = 0
    private var frameHeight = 0
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var isInitialized = false
    private var cameraFacing: Int = CameraSelector.LENS_FACING_BACK

    fun initialize(
        frameWidth: Int,
        frameHeight: Int,
        viewWidth: Float,
        viewHeight: Float,
        cameraFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        this.frameWidth = frameWidth
        this.frameHeight = frameHeight
        this.viewWidth = viewWidth
        this.viewHeight = viewHeight
        this.cameraFacing = cameraFacing
        this.isInitialized = true
    }

    /**
     * Transform camera frame coordinates to screen coordinates
     */
    fun transformBoundingBox(bbox: RectF): RectF {
        if (!isInitialized) {
            Logger.logWarning("BoundingBoxTransformer not initialized")
            return bbox
        }

        val xFactor = viewWidth / frameWidth.toFloat()
        val yFactor = viewHeight / frameHeight.toFloat()

        val transformedBox = RectF(
            bbox.left * xFactor,
            bbox.top * yFactor,
            bbox.right * xFactor,
            bbox.bottom * yFactor
        )

        // Mirror horizontally for front camera
        if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            val centerX = viewWidth / 2f
            transformedBox.left = 2 * centerX - transformedBox.left
            transformedBox.right = 2 * centerX - transformedBox.right

            // Swap left and right after mirroring
            val temp = transformedBox.left
            transformedBox.left = transformedBox.right
            transformedBox.right = temp
        }

        return transformedBox
    }

    /**
     * Batch transform multiple bounding boxes
     */
    fun transformBoundingBoxes(predictions: List<Prediction>): List<Prediction> {
        return predictions.map { prediction ->
            prediction.copy(bbox = transformBoundingBox(prediction.bbox))
        }
    }
}