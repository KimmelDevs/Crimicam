package com.example.crimicam.presentation.main.Home.Camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.crimicam.ml.YOLODetectionResult
import com.example.crimicam.ml.YOLODetector
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.ByteArrayOutputStream

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        viewModel.initDetector(context) // Initialize YOLO detector
        viewModel.initLocationManager(context)

        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }

        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        CameraPreviewView(viewModel = viewModel, navController = navController)
    } else {
        RequestCameraPermissionContent(
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraPreviewView(
    viewModel: CameraViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview with YOLO Detection Overlay
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        ) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                            processImageProxy(imageProxy, viewModel)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // YOLO Detection Overlay
        YOLODetectionOverlay(
            detections = state.yoloDetections,
            previewWidth = previewView.width.toFloat(),
            previewHeight = previewView.height.toFloat(),
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        state.securityAlert != null -> when (state.securityAlert) {
                            YOLODetector.SecurityAlert.WEAPON_DETECTED -> Color(0xFFD32F2F).copy(alpha = 0.95f)
                            YOLODetector.SecurityAlert.MULTIPLE_INTRUDERS -> Color(0xFFFF5252).copy(alpha = 0.95f)
                            YOLODetector.SecurityAlert.VEHICLE_WITH_PERSON -> Color(0xFFFF9800).copy(alpha = 0.95f)
                            YOLODetector.SecurityAlert.SUSPICIOUS_ITEMS -> Color(0xFFFFC107).copy(alpha = 0.95f)
                            YOLODetector.SecurityAlert.HIGH_CONFIDENCE_PERSON -> Color(0xFFFFC107).copy(alpha = 0.95f)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        }
                        state.suspiciousActivityDetected != null -> Color(0xFFFF5252).copy(alpha = 0.95f)
                        state.statusMessage.contains("Unknown") -> Color(0xFFFF9800).copy(alpha = 0.9f)
                        state.statusMessage.contains("Known") -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Main Status Message
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                state.securityAlert != null -> {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                state.suspiciousActivityDetected != null -> {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            Text(
                                text = state.statusMessage,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    state.securityAlert != null || state.suspiciousActivityDetected != null -> Color.White
                                    state.statusMessage.contains("Unknown") ||
                                            state.statusMessage.contains("Known") -> Color.White
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle with Detection Details
                        Text(
                            text = when {
                                state.securityAlert != null ->
                                    "🚨 ${state.yoloDetections.size} objects detected • Alert logged"
                                state.suspiciousActivityDetected != null ->
                                    "⚠️ Activity detected and logged"
                                state.yoloDetections.isNotEmpty() -> {
                                    val objects = state.yoloDetections.distinctBy { it.label }
                                        .joinToString { it.label }
                                    "📹 Detected: $objects"
                                }
                                else ->
                                    "${state.knownPeople.size} known people • AI Monitoring"
                            },
                            fontSize = 12.sp,
                            color = when {
                                state.securityAlert != null || state.suspiciousActivityDetected != null ->
                                    Color.White.copy(alpha = 0.9f)
                                state.statusMessage.contains("Unknown") ||
                                        state.statusMessage.contains("Known") ->
                                    Color.White.copy(alpha = 0.8f)
                                else -> Color.Gray
                            }
                        )
                    }

                    // Processing Indicator
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = if (state.securityAlert != null ||
                                state.suspiciousActivityDetected != null ||
                                state.statusMessage.contains("Unknown") ||
                                state.statusMessage.contains("Known")) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }

        // Bottom Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "AI Security Monitoring",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "YOLO Object Detection + Face Recognition + Activity Analysis",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Detection Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetectionStat(
                        icon = Icons.Default.Person,
                        label = "YOLO Detection",
                        color = MaterialTheme.colorScheme.primary,
                        active = state.yoloDetections.any { it.label == "person" }
                    )
                    DetectionStat(
                        icon = Icons.Default.DirectionsCar,
                        label = "Vehicle Detection",
                        color = MaterialTheme.colorScheme.tertiary,
                        active = state.yoloDetections.any { it.label in listOf("car", "truck", "motorcycle") }
                    )
                    DetectionStat(
                        icon = Icons.Default.Security,
                        label = "AI Analysis",
                        color = MaterialTheme.colorScheme.secondary,
                        active = state.securityAlert != null || state.suspiciousActivityDetected != null
                    )
                }
            }
        }
    }
}

@Composable
fun YOLODetectionOverlay(
    detections: List<YOLODetectionResult>,
    previewWidth: Float,
    previewHeight: Float,
    modifier: Modifier = Modifier
) {
    if (previewWidth == 0f || previewHeight == 0f) return

    Canvas(modifier = modifier) {
        detections.forEach { detection ->
            val boundingBox = detection.boundingBox

            // Scale bounding box to canvas size
            val scaleX = size.width / previewWidth
            val scaleY = size.height / previewHeight

            val left = boundingBox.left * scaleX
            val top = boundingBox.top * scaleY
            val right = boundingBox.right * scaleX
            val bottom = boundingBox.bottom * scaleY

            val boxWidth = right - left
            val boxHeight = bottom - top

            // Choose color based on object type
            val color = when (detection.label) {
                "person" -> Color(0xFFFF5252) // Red for people
                "car", "truck", "bus" -> Color(0xFFFF9800) // Orange for vehicles
                "motorcycle", "bicycle" -> Color(0xFFFFC107) // Amber for bikes
                "knife" -> Color(0xFFD32F2F) // Dark red for weapons
                "backpack", "handbag" -> Color(0xFFFFEB3B) // Yellow for bags
                else -> Color(0xFF4CAF50) // Green for other objects
            }

            // Draw bounding box with rounded corners
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )

            // Draw label background
            val label = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val textPadding = 8f
            val textWidth = label.length * 7f + textPadding * 2
            val textHeight = 24f

            // Draw rounded rectangle background for label
            drawRoundRect(
                color = color,
                topLeft = Offset(left, (top - textHeight).coerceAtLeast(0f)),
                size = Size(textWidth, textHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )

            // Note: Actual text rendering would require TextPainter or drawContext
            // This is a simplified version - in production you'd use proper text drawing
        }
    }
}

@Composable
fun DetectionStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    active: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) color else color.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (active) color else Color.Gray,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun RequestCameraPermissionContent(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Camera Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We need camera access for AI-powered security monitoring with YOLO object detection",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Permission")
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, viewModel: CameraViewModel) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap != null) {
            viewModel.processFrame(bitmap)
        }
    }
    imageProxy.close()
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val image = imageProxy.image ?: return null
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}