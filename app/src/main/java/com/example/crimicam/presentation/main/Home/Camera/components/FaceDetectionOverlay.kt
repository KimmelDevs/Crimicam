package com.example.crimicam.presentation.main.Home.Camera.components

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@SuppressLint("ViewConstructor")
@ExperimentalGetImage
class FaceDetectionOverlay(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
    private val viewModel: CameraViewModel
) : FrameLayout(context) {

    // Use flat search for precise cosine similarity calculation
    private val flatSearch: Boolean = true
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
                } catch (exc: Exception) {
                    Log.e("Camera", "Use case binding failed", exc)
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
                    // Mirror the bounding box coordinates for front camera
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

            // Use ImageVectorUseCase to detect and recognize faces
            val (metrics, results) = viewModel.imageVectorUseCase.getNearestPersonName(
                frameBitmap,
                flatSearch
            )

            results.forEach { (name, boundingBox, spoofResult) ->
                val box = boundingBox.toRectF()
                var personName = name

                // Check if database is empty
                if (viewModel.getNumPeople() == 0L) {
                    personName = ""
                }

                // Add spoof detection info if available
                val displayLabel = if (spoofResult != null && spoofResult.isSpoof) {
                    "$personName (SPOOF: ${(spoofResult.score * 100).toInt()}%)"
                } else {
                    personName
                }

                // Transform box to overlay coordinates
                boundingBoxTransform.mapRect(box)
                predictions.add(Prediction(box, displayLabel))

                // Create DetectedFace for ViewModel state
                detectedFaces.add(
                    DetectedFace(
                        boundingBox = box,
                        personId = null,
                        personName = if (personName.isNotBlank()) personName else null,
                        confidence = 0.85f,
                        distance = 0f
                    )
                )
            }

            withContext(Dispatchers.Main) {
                // Update ViewModel with metrics and detected faces
                viewModel.faceDetectionMetricsState.value = metrics
                viewModel.updateDetectedFaces(detectedFaces)

                // Update overlay UI
                this@FaceDetectionOverlay.predictions = predictions.toTypedArray()
                boundingBoxOverlay.invalidate()
                isProcessing = false
            }
        }

        image.close()
    }

    data class Prediction(
        var bbox: RectF,
        var label: String
    )

    inner class BoundingBoxOverlay(context: Context) :
        SurfaceView(context), SurfaceHolder.Callback {

        // Semi-transparent green fill for recognized faces
        private val recognizedBoxPaint = Paint().apply {
            color = Color.parseColor("#4D00FF00") // Green with alpha
            style = Paint.Style.FILL
        }

        // Semi-transparent red fill for unknown faces
        private val unknownBoxPaint = Paint().apply {
            color = Color.parseColor("#4DFF0000") // Red with alpha
            style = Paint.Style.FILL
        }

        // Bright green stroke for recognized faces
        private val recognizedStrokePaint = Paint().apply {
            color = Color.parseColor("#00FF00") // Bright green
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Bright red stroke for unknown faces
        private val unknownStrokePaint = Paint().apply {
            color = Color.parseColor("#FF0000") // Bright red
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Text paint for labels
        private val textPaint = Paint().apply {
            strokeWidth = 2.0f
            textSize = 36f
            color = Color.WHITE
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }

        // Background for text labels
        private val textBackgroundPaint = Paint().apply {
            color = Color.parseColor("#CC000000") // Semi-transparent black
            style = Paint.Style.FILL
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {}

        override fun surfaceDestroyed(holder: SurfaceHolder) {}

        override fun onDraw(canvas: Canvas) {
            predictions.forEach { prediction ->
                // Determine if face is recognized or unknown
                val isRecognized = prediction.label.isNotBlank() &&
                        !prediction.label.contains("SPOOF", ignoreCase = true)

                // Draw filled box
                canvas.drawRoundRect(
                    prediction.bbox,
                    16f,
                    16f,
                    if (isRecognized) recognizedBoxPaint else unknownBoxPaint
                )

                // Draw box outline
                canvas.drawRoundRect(
                    prediction.bbox,
                    16f,
                    16f,
                    if (isRecognized) recognizedStrokePaint else unknownStrokePaint
                )

                // Draw label with background if available
                if (prediction.label.isNotBlank()) {
                    val textBounds = android.graphics.Rect()
                    val displayText = prediction.label.uppercase()
                    textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)

                    val textX = prediction.bbox.left
                    val textY = prediction.bbox.top - 10f

                    // Ensure text stays within bounds
                    val adjustedTextY = if (textY < textBounds.height() + 16f) {
                        prediction.bbox.bottom + textBounds.height() + 16f
                    } else {
                        textY
                    }

                    // Background for text
                    canvas.drawRect(
                        textX - 8f,
                        adjustedTextY - textBounds.height() - 8f,
                        textX + textBounds.width() + 8f,
                        adjustedTextY + 8f,
                        textBackgroundPaint
                    )

                    // Draw text
                    canvas.drawText(
                        displayText,
                        textX,
                        adjustedTextY,
                        textPaint
                    )
                }
            }
        }
    }
}