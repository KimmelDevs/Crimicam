package com.example.crimicam.facerecognitionnetface

/*
 * Copyright 2023 Shubham Panchal
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.camera.core.CameraSelector

/**
 * Custom View to draw bounding boxes over detected faces
 */
class BoundingBoxOverlay(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {

    // Properties accessed by FrameAnalyser
    var drawMaskLabel: Boolean = false
    var areDimsInit: Boolean = false
    var frameHeight: Int = 0
        set(value) {
            field = value
            areDimsInit = frameWidth != 0 && frameHeight != 0
        }
    var frameWidth: Int = 0
        set(value) {
            field = value
            areDimsInit = frameWidth != 0 && frameHeight != 0
        }

    var faceBoundingBoxes: ArrayList<Prediction> = ArrayList()

    private var cameraFacing: Int = CameraSelector.LENS_FACING_BACK

    // Paint objects for drawing
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#AA000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val maskTextPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 32f
        style = Paint.Style.FILL
    }

    /**
     * Set the camera facing direction for proper coordinate transformation
     */
    fun setCameraFacing(facing: Int) {
        this.cameraFacing = facing
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!areDimsInit) {
            return
        }

        val scaleX = width.toFloat() / frameWidth
        val scaleY = height.toFloat() / frameHeight

        for (prediction in faceBoundingBoxes) {
            val bbox = transformBoundingBox(prediction.bbox, scaleX, scaleY)

            // Draw bounding box
            canvas.drawRect(bbox, boxPaint)

            // Draw label background
            val labelText = prediction.label
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize

            val labelRect = RectF(
                bbox.left.toFloat(),
                bbox.top.toFloat() - textHeight - 16f,
                bbox.left.toFloat() + textWidth + 16f,
                bbox.top.toFloat()
            )
            canvas.drawRect(labelRect, backgroundPaint)

            // Draw label text
            canvas.drawText(
                labelText,
                bbox.left.toFloat() + 8f,
                bbox.top.toFloat() - 8f,
                textPaint
            )

            // Draw mask label if enabled and available
            if (drawMaskLabel && prediction.maskLabel.isNotEmpty()) {
                val maskText = prediction.maskLabel
                val maskTextWidth = maskTextPaint.measureText(maskText)
                val maskTextHeight = maskTextPaint.textSize

                val maskLabelRect = RectF(
                    bbox.left.toFloat(),
                    bbox.bottom.toFloat(),
                    bbox.left.toFloat() + maskTextWidth + 16f,
                    bbox.bottom.toFloat() + maskTextHeight + 16f
                )
                canvas.drawRect(maskLabelRect, backgroundPaint)

                canvas.drawText(
                    maskText,
                    bbox.left.toFloat() + 8f,
                    bbox.bottom.toFloat() + maskTextHeight + 4f,
                    maskTextPaint
                )
            }
        }
    }

    /**
     * Transform bounding box coordinates from frame coordinates to view coordinates
     */
    private fun transformBoundingBox(bbox: Rect, scaleX: Float, scaleY: Float): Rect {
        var left = (bbox.left * scaleX).toInt()
        var top = (bbox.top * scaleY).toInt()
        var right = (bbox.right * scaleX).toInt()
        var bottom = (bbox.bottom * scaleY).toInt()

        // Mirror horizontally for front camera
        if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
            val centerX = width / 2
            left = 2 * centerX - left
            right = 2 * centerX - right

            // Swap left and right after mirroring
            val temp = left
            left = right
            right = temp
        }

        return Rect(left, top, right, bottom)
    }

    /**
     * Clear all bounding boxes
     */
    fun clear() {
        faceBoundingBoxes.clear()
        invalidate()
    }
}