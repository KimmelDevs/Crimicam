// ============================================================================
// FILE: FaceDetectionOverlay.kt - COMPLETE WITH ALL IMPORTS
// ============================================================================
package com.example.crimicam.presentation.main.Home.Camera.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.example.crimicam.presentation.main.Home.Camera.CameraViewModel
import com.example.crimicam.presentation.main.Home.Camera.DetectedFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@SuppressLint("ViewConstructor")
@ExperimentalGetImage
class FaceDetectionOverlay(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val viewModel: CameraViewModel
) : FrameLayout(context) {

    private val flatSearch: Boolean = false
    private var overlayWidth: Int = 0
    private var overlayHeight: Int = 0

    private var imageTransform: Matrix = Matrix()
    private var boundingBoxTransform: Matrix = Matrix()
    private var isImageTransformedInitialized = false
    private var isBoundingBoxTransformedInitialized = false

    private lateinit var frameBitmap: Bitmap
    private var isProcessing = false
    private var cameraFacing: Int = CameraSelector.LENS_FACING_FRONT
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private lateinit var previewView: PreviewView

    var predictions: Array<Prediction> = arrayOf()

    companion object {
        private const val TAG = "FaceDetectionOverlay"
    }

    init {
        initializeCamera(cameraFacing)
        doOnLayout {
            overlayHeight = it.measuredHeight
            overlayWidth = it.measuredWidth
        }
    }

    fun initializeCamera(cameraFacing: Int) {
        this.cameraFacing = cameraFacing
        this.isImageTransformedInitialized = false
        this.isBoundingBoxTransformedInitialized = false

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val previewView = PreviewView(context)
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build()

                val frameAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                frameAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        frameAnalyzer
                    )
                    Log.d(TAG, "Camera bound successfully")
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
            },
            executor
        )

        if (childCount == 2) {
            removeView(this.previewView)
            removeView(this.boundingBoxOverlay)
        }

        this.previewView = previewView
        addView(this.previewView)

        val boundingBoxOverlayParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        this.boundingBoxOverlay = BoundingBoxOverlay(context)
        this.boundingBoxOverlay.setWillNotDraw(false)
        this.boundingBoxOverlay.setZOrderOnTop(true)
        addView(this.boundingBoxOverlay, boundingBoxOverlayParams)
    }

    /**
     * Crop bitmap from bounding box
     */
    private fun cropBitmapFromBoundingBox(bitmap: Bitmap, boundingBox: RectF): Bitmap? {
        return try {
            val left = boundingBox.left.toInt().coerceIn(0, bitmap.width)
            val top = boundingBox.top.toInt().coerceIn(0, bitmap.height)
            val right = boundingBox.right.toInt().coerceIn(0, bitmap.width)
            val bottom = boundingBox.bottom.toInt().coerceIn(0, bitmap.height)

            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)

            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping bitmap", e)
            null
        }
    }

    private val analyzer = ImageAnalysis.Analyzer { image ->
        if (isProcessing) {
            image.close()
            return@Analyzer
        }
        isProcessing = true

        // Transform android.media.Image to Bitmap
        frameBitmap = Bitmap.createBitmap(
            image.image!!.width,
            image.image!!.height,
            Bitmap.Config.ARGB_8888
        )
        frameBitmap.copyPixelsFromBuffer(image.planes[0].buffer)

        // Configure frameHeight and frameWidth for output2overlay transformation
        if (!isImageTransformedInitialized) {
            imageTransform = Matrix()
            imageTransform.apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
            }
            isImageTransformedInitialized = true
        }

        frameBitmap = Bitmap.createBitmap(
            frameBitmap,
            0,
            0,
            frameBitmap.width,
            frameBitmap.height,
            imageTransform,
            false
        )

        if (!isBoundingBoxTransformedInitialized) {
            boundingBoxTransform = Matrix()
            boundingBoxTransform.apply {
                setScale(
                    overlayWidth / frameBitmap.width.toFloat(),
                    overlayHeight / frameBitmap.height.toFloat()
                )
                if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                    postScale(
                        -1f,
                        1f,
                        overlayWidth.toFloat() / 2.0f,
                        overlayHeight.toFloat() / 2.0f
                    )
                }
            }
            isBoundingBoxTransformedInitialized = true
        }

        CoroutineScope(Dispatchers.Default).launch {
            val predictions = ArrayList<Prediction>()
            val detectedFaces = mutableListOf<DetectedFace>()

            // FIRST: Check for criminals (higher priority)
            val (criminalMetrics, criminalResults) =
                viewModel.criminalImageVectorUseCase.getNearestCriminalName(
                    frameBitmap = frameBitmap,
                    flatSearch = flatSearch,
                    confidenceThreshold = 0.6f
                )

            var hasCriminal = false

            criminalResults.forEach { criminalResult ->
                val isCriminal = criminalResult.criminalName != "Unknown"
                if (isCriminal) hasCriminal = true

                // Get ORIGINAL bounding box (before transformation) for cropping
                val originalBox = criminalResult.boundingBox.toRectF()

                // Get a COPY for transformation (for display)
                val displayBox = RectF(originalBox)

                val displayLabel = if (isCriminal) {
                    val spoofInfo = if (criminalResult.spoofResult?.isSpoof == true) {
                        " (SPOOF)"
                    } else ""
                    "${criminalResult.dangerLevel}$spoofInfo\n${criminalResult.criminalName}"
                } else {
                    ""
                }

                // Transform ONLY the display box to overlay coordinates
                boundingBoxTransform.mapRect(displayBox)

                predictions.add(
                    Prediction(
                        bbox = displayBox,
                        label = displayLabel,
                        isCriminal = isCriminal,
                        dangerLevel = if (isCriminal) criminalResult.dangerLevel else null,
                        confidence = criminalResult.confidence,
                        isSpoof = criminalResult.spoofResult?.isSpoof ?: false
                    )
                )

                // Crop face from ORIGINAL frame using ORIGINAL box
                val croppedFace = cropBitmapFromBoundingBox(frameBitmap, originalBox)

                detectedFaces.add(
                    DetectedFace(
                        boundingBox = displayBox,
                        personId = criminalResult.criminalID,
                        personName = criminalResult.criminalName,
                        confidence = criminalResult.confidence,
                        isCriminal = isCriminal,
                        dangerLevel = criminalResult.dangerLevel,
                        spoofDetected = criminalResult.spoofResult?.isSpoof ?: false,
                        croppedBitmap = croppedFace
                    )
                )

                // ✅ AUTO-SAVE CRIMINALS TO FIRESTORE
                if (isCriminal && croppedFace != null) {
                    Log.d(TAG, "🚨 Auto-saving criminal: ${criminalResult.criminalName} - ${criminalResult.dangerLevel}")

                    viewModel.saveCriminalToFirestore(
                        croppedFace = croppedFace,
                        fullFrame = frameBitmap,
                        criminalId = criminalResult.criminalID,
                        criminalName = criminalResult.criminalName,
                        confidence = criminalResult.confidence,
                        dangerLevel = criminalResult.dangerLevel,
                        isSpoof = criminalResult.spoofResult?.isSpoof ?: false
                    )
                }
            }

            // SECOND: If no criminals detected, check for known people
            if (!hasCriminal) {
                val (peopleMetrics, peopleResults) =
                    viewModel.imageVectorUseCase.getNearestPersonName(
                        frameBitmap = frameBitmap,
                        flatSearch = flatSearch,
                        confidenceThreshold = 0.6f
                    )

                peopleResults.forEach { personResult ->
                    val originalBox = personResult.boundingBox.toRectF()
                    val displayBox = RectF(originalBox)
                    var personName = personResult.personName

                    if (viewModel.getNumPeople() == 0L) {
                        personName = ""
                    }

                    val displayLabel = if (personResult.spoofResult != null && personResult.spoofResult.isSpoof) {
                        "$personName\n(SPOOF: ${(personResult.spoofResult.score * 100).toInt()}%)"
                    } else {
                        personName
                    }

                    boundingBoxTransform.mapRect(displayBox)

                    predictions.add(
                        Prediction(
                            bbox = displayBox,
                            label = displayLabel,
                            isCriminal = false,
                            dangerLevel = null,
                            confidence = personResult.confidence,
                            isSpoof = personResult.spoofResult?.isSpoof ?: false
                        )
                    )

                    val croppedFace = cropBitmapFromBoundingBox(frameBitmap, originalBox)

                    detectedFaces.add(
                        DetectedFace(
                            boundingBox = displayBox,
                            personName = if (personName.isNotBlank()) personName else null,
                            confidence = personResult.confidence,
                            isCriminal = false,
                            spoofDetected = personResult.spoofResult?.isSpoof ?: false,
                            croppedBitmap = croppedFace
                        )
                    )
                }

                viewModel.faceDetectionMetricsState.value = peopleMetrics
            } else {
                viewModel.faceDetectionMetricsState.value = criminalMetrics
            }

            withContext(Dispatchers.Main) {
                viewModel.updateDetectedFaces(detectedFaces)
                this@FaceDetectionOverlay.predictions = predictions.toTypedArray()
                boundingBoxOverlay.invalidate()
                isProcessing = false
            }
        }

        image.close()
    }

    data class Prediction(
        var bbox: RectF,
        var label: String,
        var isCriminal: Boolean = false,
        var dangerLevel: String? = null,
        var confidence: Float = 0f,
        var isSpoof: Boolean = false
    )

    inner class BoundingBoxOverlay(context: Context) :
        SurfaceView(context), SurfaceHolder.Callback {

        // Criminal colors - Fill
        private val criminalHighBoxPaint = Paint().apply {
            color = Color.parseColor("#4DFF0000")
            style = Paint.Style.FILL
        }

        private val criminalMediumBoxPaint = Paint().apply {
            color = Color.parseColor("#4DFFA500")
            style = Paint.Style.FILL
        }

        private val criminalLowBoxPaint = Paint().apply {
            color = Color.parseColor("#4DFFFF00")
            style = Paint.Style.FILL
        }

        // Criminal colors - Stroke
        private val criminalHighStrokePaint = Paint().apply {
            color = Color.parseColor("#FF0000")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val criminalMediumStrokePaint = Paint().apply {
            color = Color.parseColor("#FFA500")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        private val criminalLowStrokePaint = Paint().apply {
            color = Color.parseColor("#FFFF00")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Known people colors
        private val recognizedBoxPaint = Paint().apply {
            color = Color.parseColor("#4D00FF00")
            style = Paint.Style.FILL
        }

        private val unknownBoxPaint = Paint().apply {
            color = Color.parseColor("#4D808080")
            style = Paint.Style.FILL
        }

        private val recognizedStrokePaint = Paint().apply {
            color = Color.parseColor("#00FF00")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val unknownStrokePaint = Paint().apply {
            color = Color.parseColor("#808080")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Text paints
        private val textPaint = Paint().apply {
            strokeWidth = 2.0f
            textSize = 32f
            color = Color.WHITE
            isFakeBoldText = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        private val dangerTextPaint = Paint().apply {
            strokeWidth = 2.5f
            textSize = 38f
            color = Color.WHITE
            isFakeBoldText = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        private val textBackgroundPaint = Paint().apply {
            color = Color.parseColor("#DD000000")
            style = Paint.Style.FILL
        }

        private val criminalTextBackgroundPaint = Paint().apply {
            color = Color.parseColor("#EE000000")
            style = Paint.Style.FILL
        }

        init {
            holder.addCallback(this)
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}

        override fun onDraw(canvas: Canvas) {
            predictions.forEach { prediction ->
                val (fillPaint, strokePaint) = when {
                    prediction.isCriminal -> {
                        when (prediction.dangerLevel) {
                            "HIGH" -> Pair(criminalHighBoxPaint, criminalHighStrokePaint)
                            "MEDIUM" -> Pair(criminalMediumBoxPaint, criminalMediumStrokePaint)
                            "LOW" -> Pair(criminalLowBoxPaint, criminalLowStrokePaint)
                            else -> Pair(criminalHighBoxPaint, criminalHighStrokePaint)
                        }
                    }
                    prediction.label.isNotBlank() -> Pair(recognizedBoxPaint, recognizedStrokePaint)
                    else -> Pair(unknownBoxPaint, unknownStrokePaint)
                }

                canvas.drawRoundRect(prediction.bbox, 16f, 16f, fillPaint)
                canvas.drawRoundRect(prediction.bbox, 16f, 16f, strokePaint)

                if (prediction.label.isNotBlank()) {
                    val lines = prediction.label.split("\n")
                    var currentY = prediction.bbox.top - 20f

                    lines.reversed().forEachIndexed { reverseIndex, line ->
                        val index = lines.size - 1 - reverseIndex
                        val paint = if (prediction.isCriminal && index == 0) {
                            dangerTextPaint
                        } else {
                            textPaint
                        }

                        val textBounds = android.graphics.Rect()
                        val displayText = line.uppercase()
                        paint.getTextBounds(displayText, 0, displayText.length, textBounds)

                        val textX = prediction.bbox.left
                        val lineHeight = textBounds.height() + 16f
                        val textY = currentY - lineHeight

                        val adjustedTextY = if (textY < lineHeight) {
                            prediction.bbox.bottom + lineHeight * (reverseIndex + 1)
                        } else {
                            textY
                        }

                        val backgroundPaint = if (prediction.isCriminal) {
                            criminalTextBackgroundPaint
                        } else {
                            textBackgroundPaint
                        }

                        canvas.drawRect(
                            textX - 8f,
                            adjustedTextY - textBounds.height() - 8f,
                            textX + textBounds.width() + 16f,
                            adjustedTextY + 8f,
                            backgroundPaint
                        )

                        if (prediction.isCriminal && index == 0) {
                            paint.color = when (prediction.dangerLevel) {
                                "HIGH" -> Color.parseColor("#FF0000")
                                "MEDIUM" -> Color.parseColor("#FFA500")
                                "LOW" -> Color.parseColor("#FFFF00")
                                else -> Color.parseColor("#FF0000")
                            }
                        } else {
                            paint.color = Color.WHITE
                        }

                        canvas.drawText(displayText, textX, adjustedTextY, paint)
                        currentY = textY - 8f
                    }

                    if (prediction.confidence > 0f) {
                        val confidenceText = "${(prediction.confidence * 100).toInt()}%"
                        val confidencePaint = Paint().apply {
                            textSize = 24f
                            color = Color.WHITE
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                        canvas.drawText(
                            confidenceText,
                            prediction.bbox.right - 60f,
                            prediction.bbox.bottom - 10f,
                            confidencePaint
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// ADD THIS METHOD TO CameraViewModel.kt
// ============================================================================
/*
/**
 * Save criminal detection to Firestore (called from overlay analyzer)
 */
fun saveCriminalToFirestore(
    croppedFace: Bitmap,
    fullFrame: Bitmap,
    criminalId: String,
    criminalName: String,
    confidence: Float,
    dangerLevel: String,
    isSpoof: Boolean
) {
    viewModelScope.launch {
        try {
            val result = firestoreCaptureService.saveCapturedFace(
                croppedFace = croppedFace,
                fullFrame = fullFrame,
                isRecognized = true,
                isCriminal = true,
                matchedPersonId = criminalId,
                matchedPersonName = criminalName,
                confidence = confidence,
                dangerLevel = dangerLevel,
                location = _state.value.currentLocation,
                deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
            )

            result.onSuccess { captureId ->
                _state.value = _state.value.copy(lastSavedCaptureId = captureId)
                Log.d(TAG, "✅ Criminal saved to Firestore: $captureId")
                updateStatusMessage("🚨 Criminal detected and saved!")
            }.onFailure { e ->
                Log.e(TAG, "❌ Failed to save criminal", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving criminal to Firestore", e)
        }
    }
}
*/