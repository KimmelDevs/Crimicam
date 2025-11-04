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
        viewModel.initDetector(context)
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
        // Camera Preview
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
                    containerColor = getStatusCardColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
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
                                color = getStatusTextColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Subtitle with Detection Details
                        Text(
                            text = getSubtitleText(state),
                            fontSize = 12.sp,
                            color = getSubtitleColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
                        )
                    }

                    // Processing Indicator
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            color = getProgressIndicatorColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
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
                    text = "Enhanced YOLO + Face Recognition + Activity Analysis",
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
                        label = "Person",
                        color = MaterialTheme.colorScheme.primary,
                        active = state.yoloDetections.any { it.label == "person" }
                    )
                    DetectionStat(
                        icon = Icons.Default.DirectionsCar,
                        label = "Vehicle",
                        color = MaterialTheme.colorScheme.tertiary,
                        active = state.yoloDetections.any {
                            it.label in listOf("car", "truck", "motorcycle", "bus")
                        }
                    )
                    DetectionStat(
                        icon = Icons.Default.Security,
                        label = "Alert",
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

            val scaleX = size.width / previewWidth
            val scaleY = size.height / previewHeight

            val left = boundingBox.left * scaleX
            val top = boundingBox.top * scaleY
            val right = boundingBox.right * scaleX
            val bottom = boundingBox.bottom * scaleY

            val boxWidth = right - left
            val boxHeight = bottom - top

            // Color based on tracking stability and object type
            val color = when {
                detection.label == "knife" -> Color(0xFFD32F2F)
                detection.frameCount >= 5 -> Color(0xFF4CAF50)  // Stable track (green)
                detection.frameCount >= 2 -> when (detection.label) {
                    "person" -> Color(0xFFFF5252)
                    "car", "truck", "bus" -> Color(0xFFFF9800)
                    "motorcycle", "bicycle" -> Color(0xFFFFC107)
                    "backpack", "handbag" -> Color(0xFFFFEB3B)
                    else -> Color(0xFF4CAF50)
                }
                else -> Color(0xFFBDBDBD)  // New/unstable track (gray)
            }

            // Draw bounding box with thickness based on stability
            val strokeWidth = if (detection.frameCount >= 3) 5f else 3f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = strokeWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )

            // Label with tracking info
            val label = buildString {
                if (detection.trackingId > 0) {
                    append("#${detection.trackingId} ")
                }
                append("${detection.label} ${(detection.confidence * 100).toInt()}%")
                if (detection.frameCount > 1) {
                    append(" (${detection.frameCount})")
                }
            }

            val textPadding = 8f
            val textWidth = label.length * 7f + textPadding * 2
            val textHeight = 24f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, (top - textHeight).coerceAtLeast(0f)),
                size = Size(textWidth, textHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )

            // Optional: Draw tracking ID badge for stable tracks
            if (detection.trackingId > 0 && detection.frameCount >= 3) {
                val badgeSize = 20f
                drawCircle(
                    color = Color.White,
                    radius = badgeSize / 2,
                    center = Offset(right - badgeSize, top + badgeSize)
                )
                drawCircle(
                    color = color,
                    radius = (badgeSize / 2) - 2f,
                    center = Offset(right - badgeSize, top + badgeSize)
                )
            }
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
                text = "We need camera access for enhanced AI security monitoring",
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

// Helper functions for color management
@Composable
private fun getStatusCardColor(
    securityAlert: YOLODetector.SecurityAlert?,
    suspiciousActivity: String?,
    statusMessage: String
): Color {
    return when {
        securityAlert != null -> when (securityAlert.severity) {
            5 -> Color(0xFFD32F2F).copy(alpha = 0.95f)  // CRITICAL
            4 -> Color(0xFFFF5252).copy(alpha = 0.95f)  // HIGH
            3, 2 -> Color(0xFFFF9800).copy(alpha = 0.95f)  // MEDIUM
            else -> Color(0xFFFFC107).copy(alpha = 0.95f)  // LOW
        }
        suspiciousActivity != null -> Color(0xFFFF5252).copy(alpha = 0.95f)
        statusMessage.contains("Unknown") -> Color(0xFFFF9800).copy(alpha = 0.9f)
        statusMessage.contains("Known") -> Color(0xFF4CAF50).copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
}

@Composable
private fun getStatusTextColor(
    securityAlert: YOLODetector.SecurityAlert?,
    suspiciousActivity: String?,
    statusMessage: String
): Color {
    return when {
        securityAlert != null || suspiciousActivity != null -> Color.White
        statusMessage.contains("Unknown") || statusMessage.contains("Known") -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun getSubtitleColor(
    securityAlert: YOLODetector.SecurityAlert?,
    suspiciousActivity: String?,
    statusMessage: String
): Color {
    return when {
        securityAlert != null || suspiciousActivity != null -> Color.White.copy(alpha = 0.9f)
        statusMessage.contains("Unknown") || statusMessage.contains("Known") -> Color.White.copy(alpha = 0.8f)
        else -> Color.Gray
    }
}

@Composable
private fun getProgressIndicatorColor(
    securityAlert: YOLODetector.SecurityAlert?,
    suspiciousActivity: String?,
    statusMessage: String
): Color {
    return if (securityAlert != null || suspiciousActivity != null ||
        statusMessage.contains("Unknown") || statusMessage.contains("Known")) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
}

private fun getSubtitleText(state: CameraState): String {
    return when {
        state.securityAlert != null ->
            "🚨 ${state.yoloDetections.size} objects • Alert logged"
        state.suspiciousActivityDetected != null ->
            "⚠️ Activity detected and logged"
        state.yoloDetections.isNotEmpty() -> {
            val objects = state.yoloDetections.distinctBy { it.label }
                .joinToString { it.label }
            "📹 Detected: $objects"
        }
        else ->
            "${state.knownPeople.size} known people • AI Monitoring"
    }
}