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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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

    // Overlay toggles
    var showBoundingBoxes by remember { mutableStateOf(true) }
    var showTrackingIds by remember { mutableStateOf(true) }
    var showConfidence by remember { mutableStateOf(true) }
    var showSkeletons by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showControlPanel by remember { mutableStateOf(false) }

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

        // Grid Overlay
        if (showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        // YOLO Detection Overlay
        if (showBoundingBoxes || showTrackingIds || showConfidence || showSkeletons) {
            EnhancedYOLOOverlay(
                detections = state.yoloDetections,
                previewWidth = previewView.width.toFloat(),
                previewHeight = previewView.height.toFloat(),
                showBoundingBoxes = showBoundingBoxes,
                showTrackingIds = showTrackingIds,
                showConfidence = showConfidence,
                showSkeletons = showSkeletons,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ═══════════════════════════════════════════════════════════
        // NEW: Motion Indicator (top-left corner)
        // ═══════════════════════════════════════════════════════════
        MotionIndicator(
            motionDetected = state.motionDetected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // ═══════════════════════════════════════════════════════════
        // UPDATED: Enhanced Top Status Card
        // ═══════════════════════════════════════════════════════════
        EnhancedTopStatusCard(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )

        // ═══════════════════════════════════════════════════════════
        // NEW: Processing Stage Indicator
        // ═══════════════════════════════════════════════════════════
        ProcessingStageIndicator(
            stage = state.processingStage,
            motionDetected = state.motionDetected,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp)
        )

        // ═══════════════════════════════════════════════════════════
        // NEW: Presence Detection Indicator
        // ═══════════════════════════════════════════════════════════
        PresenceDetectionIndicator(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp)
        )

        // Floating Controls Toggle Button
        FloatingActionButton(
            onClick = { showControlPanel = !showControlPanel },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 100.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (showControlPanel) Icons.Default.Close else Icons.Default.Settings,
                contentDescription = "Toggle Controls"
            )
        }

        // Control Panel
        AnimatedVisibility(
            visible = showControlPanel,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            ControlPanel(
                showBoundingBoxes = showBoundingBoxes,
                showTrackingIds = showTrackingIds,
                showConfidence = showConfidence,
                showSkeletons = showSkeletons,
                showGrid = showGrid,
                onToggleBoundingBoxes = { showBoundingBoxes = !showBoundingBoxes },
                onToggleTrackingIds = { showTrackingIds = !showTrackingIds },
                onToggleConfidence = { showConfidence = !showConfidence },
                onToggleSkeletons = { showSkeletons = !showSkeletons },
                onToggleGrid = { showGrid = !showGrid }
            )
        }

        // Bottom Info Card
        BottomInfoCard(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════
// NEW COMPOSABLES FOR PRESENCE DETECTION
// ═══════════════════════════════════════════════════════════

@Composable
fun MotionIndicator(
    motionDetected: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = motionDetected,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                    shape = CircleShape
                )
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "motion_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "motion_alpha"
            )

            Icon(
                imageVector = Icons.Default.DirectionsRun,
                contentDescription = "Motion",
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ProcessingStageIndicator(
    stage: ProcessingStage,
    motionDetected: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = stage != ProcessingStage.IDLE && stage != ProcessingStage.COMPLETE,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )

                Column {
                    Text(
                        text = when (stage) {
                            ProcessingStage.CHECKING_MOTION -> "🏃 Checking for motion..."
                            ProcessingStage.CHECKING_PRESENCE -> "👁️ Detecting human presence..."
                            ProcessingStage.DETECTING_OBJECTS -> "🎯 Analyzing objects..."
                            ProcessingStage.RECOGNIZING_FACES -> "🔍 Recognizing faces..."
                            else -> "Processing..."
                        },
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    if (stage == ProcessingStage.CHECKING_MOTION || motionDetected) {
                        Text(
                            text = if (motionDetected) "✓ Motion detected" else "No motion",
                            fontSize = 10.sp,
                            color = if (motionDetected) Color(0xFF4CAF50) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresenceDetectionIndicator(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.presenceConfidence > 0f || state.processingStage == ProcessingStage.CHECKING_PRESENCE,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    state.presenceConfidence >= 0.7f -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                    state.presenceConfidence >= 0.5f -> Color(0xFFFF9800).copy(alpha = 0.9f)
                    state.presenceConfidence > 0f -> Color(0xFF9E9E9E).copy(alpha = 0.9f)
                    else -> Color.Black.copy(alpha = 0.8f)
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Icon(
                    imageVector = when {
                        state.presenceConfidence >= 0.5f -> Icons.Default.Visibility
                        else -> Icons.Default.Visibility
                    },
                    contentDescription = "Presence Detection",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(
                            scaleX = if (state.presenceConfidence >= 0.5f) scale else 1f,
                            scaleY = if (state.presenceConfidence >= 0.5f) scale else 1f
                        )
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.presenceConfidence >= 0.7f -> "✓ Human Presence Confirmed"
                            state.presenceConfidence >= 0.5f -> "⚠ Human Detected"
                            state.presenceConfidence > 0f -> "👻 No Human Detected"
                            else -> "🔍 Checking Presence..."
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = state.presenceConfidence,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }

                Text(
                    text = "${(state.presenceConfidence * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun EnhancedTopStatusCard(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = getStatusCardColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                            state.presenceConfidence >= 0.5f -> {
                                Icon(
                                    imageVector = Icons.Default.Person,
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

                    Text(
                        text = getEnhancedSubtitleText(state),
                        fontSize = 12.sp,
                        color = getSubtitleColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
                    )
                }

                if (state.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = getProgressIndicatorColor(state.securityAlert, state.suspiciousActivityDetected, state.statusMessage)
                    )
                }
            }

            // Detection Mode Indicator
            if (state.personDetectionMode != PersonDetectionMode.NONE) {
                Spacer(modifier = Modifier.height(8.dp))
                DetectionModeIndicator(mode = state.personDetectionMode)
            }

            // Presence confidence mini-bar
            if (state.presenceConfidence >= 0.5f &&
                state.securityAlert == null &&
                state.suspiciousActivityDetected == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    LinearProgressIndicator(
                        progress = state.presenceConfidence,
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White.copy(alpha = 0.8f),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "${(state.presenceConfidence * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun getEnhancedSubtitleText(state: CameraState): String {
    return when {
        state.securityAlert != null ->
            "🚨 ${state.yoloDetections.size} objects • Alert logged"
        state.suspiciousActivityDetected != null ->
            "⚠️ Activity detected and logged"
        state.processingStage == ProcessingStage.CHECKING_MOTION ->
            "Checking for movement..."
        state.processingStage == ProcessingStage.CHECKING_PRESENCE ->
            "Analyzing for human presence..."
        state.presenceConfidence >= 0.5f && state.yoloDetections.isEmpty() ->
            "Human detected • ${(state.presenceConfidence * 100).toInt()}% confidence"
        state.yoloDetections.isNotEmpty() -> {
            val objects = state.yoloDetections.distinctBy { it.label }
                .joinToString { it.label }
            "📹 Detected: $objects"
        }
        !state.motionDetected ->
            "No motion • Standby mode"
        else ->
            "${state.knownPeople.size} known people • AI Monitoring"
    }
}

// ═══════════════════════════════════════════════════════════
// EXISTING COMPOSABLES (keeping all your original ones)
// ═══════════════════════════════════════════════════════════

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gridColor = Color.White.copy(alpha = 0.2f)
        val strokeWidth = 1f

        // Vertical lines
        for (i in 1..2) {
            val x = size.width * i / 3
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }

        // Horizontal lines
        for (i in 1..2) {
            val y = size.height * i / 3
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        }

        // Center crosshair
        val centerX = size.width / 2
        val centerY = size.height / 2
        val crosshairSize = 20f

        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX - crosshairSize, centerY),
            end = Offset(centerX + crosshairSize, centerY),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX, centerY - crosshairSize),
            end = Offset(centerX, centerY + crosshairSize),
            strokeWidth = 2f
        )
    }
}

@Composable
fun EnhancedYOLOOverlay(
    detections: List<YOLODetectionResult>,
    previewWidth: Float,
    previewHeight: Float,
    showBoundingBoxes: Boolean,
    showTrackingIds: Boolean,
    showConfidence: Boolean,
    showSkeletons: Boolean,
    modifier: Modifier = Modifier
) {
    if (previewWidth == 0f || previewHeight == 0f) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier) {
        val scaleX = size.width / previewWidth
        val scaleY = size.height / previewHeight

        detections.forEach { detection ->
            val box = detection.boundingBox
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY
            val boxWidth = right - left
            val boxHeight = bottom - top
            val centerX = (left + right) / 2
            val centerY = (top + bottom) / 2

            val baseColor = when {
                detection.label == "knife" -> Color(0xFFD32F2F)
                detection.frameCount >= 5 -> Color(0xFF4CAF50)
                detection.frameCount >= 2 -> when (detection.label) {
                    "person" -> Color(0xFFFF5252)
                    "car", "truck", "bus" -> Color(0xFFFF9800)
                    "motorcycle", "bicycle" -> Color(0xFFFFC107)
                    "backpack", "handbag" -> Color(0xFFFFEB3B)
                    else -> Color(0xFF4CAF50)
                }
                else -> Color(0xFFBDBDBD)
            }

            val color = if (detection.frameCount == 1) {
                baseColor.copy(alpha = pulseAlpha)
            } else {
                baseColor
            }

            if (showBoundingBoxes) {
                val strokeWidth = if (detection.frameCount >= 3) 5f else 3f
                val cornerLength = minOf(boxWidth, boxHeight) * 0.2f

                // Draw corner brackets
                drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
                drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)
                drawLine(color, Offset(right - cornerLength, top), Offset(right, top), strokeWidth)
                drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)
                drawLine(color, Offset(left, bottom - cornerLength), Offset(left, bottom), strokeWidth)
                drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
                drawLine(color, Offset(right, bottom - cornerLength), Offset(right, bottom), strokeWidth)
                drawLine(color, Offset(right - cornerLength, bottom), Offset(right, bottom), strokeWidth)

                drawCircle(color = color, radius = 4f, center = Offset(centerX, centerY))

                if (detection.frameCount >= 2) {
                    drawLine(color.copy(alpha = 0.3f), Offset(left, top), Offset(centerX, centerY), 1f)
                    drawLine(color.copy(alpha = 0.3f), Offset(right, top), Offset(centerX, centerY), 1f)
                    drawLine(color.copy(alpha = 0.3f), Offset(left, bottom), Offset(centerX, centerY), 1f)
                    drawLine(color.copy(alpha = 0.3f), Offset(right, bottom), Offset(centerX, centerY), 1f)
                }
            }

            if (showSkeletons && detection.label == "person" && detection.frameCount >= 2) {
                drawPersonSkeleton(color, left, top, boxWidth, boxHeight)
            }

            if (showTrackingIds && detection.trackingId > 0) {
                val badgeRadius = 20f
                val badgeX = right - badgeRadius - 5f
                val badgeY = top + badgeRadius + 5f

                drawCircle(color.copy(alpha = 0.3f), badgeRadius + 4f, Offset(badgeX, badgeY))
                drawCircle(Color.Black.copy(alpha = 0.8f), badgeRadius, Offset(badgeX, badgeY))
                drawCircle(color, badgeRadius, Offset(badgeX, badgeY), style = Stroke(width = 2f))

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    canvas.nativeCanvas.drawText("#${detection.trackingId}", badgeX, badgeY + 8f, paint)
                }
            }

            if (showConfidence || showTrackingIds) {
                val label = buildString {
                    if (showTrackingIds && detection.trackingId > 0) append("#${detection.trackingId} ")
                    append(detection.label)
                    if (showConfidence) append(" ${(detection.confidence * 100).toInt()}%")
                    if (detection.frameCount > 1) append(" [${detection.frameCount}f]")
                }

                val textPadding = 12f
                val textSize = 28f
                val textWidth = label.length * 10f + textPadding * 2
                val textHeight = textSize + textPadding
                val labelX = left
                val labelY = (top - textHeight - 5f).coerceAtLeast(5f)

                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.7f))
                    ),
                    topLeft = Offset(labelX, labelY),
                    size = Size(textWidth, textHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.3f),
                    topLeft = Offset(labelX, labelY),
                    size = Size(textWidth, textHeight),
                    style = Stroke(width = 1f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        this.color = Color.White.toArgb()
                        this.textSize = textSize
                        isFakeBoldText = true
                        setShadowLayer(2f, 0f, 0f, Color.Black.toArgb())
                    }
                    canvas.nativeCanvas.drawText(label, labelX + textPadding, labelY + textSize + textPadding / 2, paint)
                }
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPersonSkeleton(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    val headRadius = width * 0.15f
    val headX = left + width / 2
    val headY = top + headRadius + 10f

    val neckY = headY + headRadius + 5f
    val shoulderY = neckY + 10f
    val elbowY = shoulderY + height * 0.2f
    val wristY = elbowY + height * 0.2f
    val hipY = shoulderY + height * 0.25f
    val kneeY = hipY + height * 0.25f
    val ankleY = kneeY + height * 0.25f

    val shoulderWidth = width * 0.4f
    val hipWidth = width * 0.3f

    drawCircle(color, headRadius, Offset(headX, headY), style = Stroke(width = 3f))
    drawLine(color, Offset(headX, neckY), Offset(headX, hipY), 3f)
    drawLine(color, Offset(headX - shoulderWidth / 2, shoulderY), Offset(headX + shoulderWidth / 2, shoulderY), 3f)
    drawLine(color, Offset(headX - shoulderWidth / 2, shoulderY), Offset(headX - shoulderWidth / 2 - 10f, elbowY), 3f)
    drawLine(color, Offset(headX - shoulderWidth / 2 - 10f, elbowY), Offset(headX - shoulderWidth / 2 - 5f, wristY), 3f)
    drawLine(color, Offset(headX + shoulderWidth / 2, shoulderY), Offset(headX + shoulderWidth / 2 + 10f, elbowY), 3f)
    drawLine(color, Offset(headX + shoulderWidth / 2 + 10f, elbowY), Offset(headX + shoulderWidth / 2 + 5f, wristY), 3f)
    drawLine(color, Offset(headX - hipWidth / 2, hipY), Offset(headX + hipWidth / 2, hipY), 3f)
    drawLine(color, Offset(headX - hipWidth / 2, hipY), Offset(headX - hipWidth / 2, kneeY), 3f)
    drawLine(color, Offset(headX - hipWidth / 2, kneeY), Offset(headX - hipWidth / 2 + 5f, ankleY), 3f)
    drawLine(color, Offset(headX + hipWidth / 2, hipY), Offset(headX + hipWidth / 2, kneeY), 3f)
    drawLine(color, Offset(headX + hipWidth / 2, kneeY), Offset(headX + hipWidth / 2 - 5f, ankleY), 3f)

    val joints = listOf(
        Offset(headX - shoulderWidth / 2, shoulderY),
        Offset(headX + shoulderWidth / 2, shoulderY),
        Offset(headX - shoulderWidth / 2 - 10f, elbowY),
        Offset(headX + shoulderWidth / 2 + 10f, elbowY),
        Offset(headX - hipWidth / 2, hipY),
        Offset(headX + hipWidth / 2, hipY),
        Offset(headX - hipWidth / 2, kneeY),
        Offset(headX + hipWidth / 2, kneeY)
    )
    joints.forEach { joint -> drawCircle(color, 4f, joint) }
}

@Composable
fun ControlPanel(
    showBoundingBoxes: Boolean,
    showTrackingIds: Boolean,
    showConfidence: Boolean,
    showSkeletons: Boolean,
    showGrid: Boolean,
    onToggleBoundingBoxes: () -> Unit,
    onToggleTrackingIds: () -> Unit,
    onToggleConfidence: () -> Unit,
    onToggleSkeletons: () -> Unit,
    onToggleGrid: () -> Unit
) {
    Card(
        modifier = Modifier.width(280.dp).padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Display Options", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Divider(color = Color.White.copy(alpha = 0.2f))
            ControlToggle(Icons.Default.CropSquare, "Bounding Boxes", showBoundingBoxes, { onToggleBoundingBoxes() })
            ControlToggle(Icons.Default.Tag, "Tracking IDs", showTrackingIds, { onToggleTrackingIds() })
            ControlToggle(Icons.Default.ShowChart, "Confidence %", showConfidence, { onToggleConfidence() })
            ControlToggle(Icons.Default.Accessibility, "Pose Skeleton", showSkeletons, { onToggleSkeletons() })
            ControlToggle(Icons.Default.GridOn, "Grid Overlay", showGrid, { onToggleGrid() })
        }
    }
}

@Composable
fun ControlToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(20.dp))
            Text(label, color = if (checked) Color.White else Color.Gray, fontSize = 14.sp, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
        }
        Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ), modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DetectionModeIndicator(mode: PersonDetectionMode) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (mode) {
            PersonDetectionMode.FACE_AND_POSE -> {
                Icon(Icons.Default.Face, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                Icon(Icons.Default.Accessibility, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                Text("Full Detection: Face + Pose", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            PersonDetectionMode.FACE_ONLY -> {
                Icon(Icons.Default.Face, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                Text("Face Detection Active", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            PersonDetectionMode.POSE_ONLY -> {
                Icon(Icons.Default.Accessibility, null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                Text("Pose Detection (Face Hidden)", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            PersonDetectionMode.YOLO_PERSON -> {
                Icon(Icons.Default.Person, null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                Text("Object Detection Only", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            PersonDetectionMode.NONE -> {}
        }
    }
}

@Composable
fun BottomInfoCard(state: CameraState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("AI Security Monitoring", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Multi-Modal Detection: YOLO + Face + Pose Analysis", fontSize = 12.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DetectionStat(Icons.Default.Person, "Person", state.yoloDetections.count { it.label == "person" }, MaterialTheme.colorScheme.primary, state.yoloDetections.any { it.label == "person" })
                DetectionStat(Icons.Default.DirectionsCar, "Vehicle", state.yoloDetections.count { it.label in listOf("car", "truck", "motorcycle", "bus") }, MaterialTheme.colorScheme.tertiary, state.yoloDetections.any { it.label in listOf("car", "truck", "motorcycle", "bus") })
                DetectionStat(Icons.Default.Security, "Alert", if (state.securityAlert != null || state.suspiciousActivityDetected != null) 1 else 0, MaterialTheme.colorScheme.secondary, state.securityAlert != null || state.suspiciousActivityDetected != null)
            }

            state.detectionStats?.let { stats ->
                if (stats.activeTracksCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.TrackChanges, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("${stats.activeTracksCount} Active Tracks", fontSize = 11.sp, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Text("${stats.historySize} Frame Buffer", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetectionStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int = 0, color: Color, active: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (active) color else color.copy(alpha = 0.3f), modifier = Modifier.size(24.dp))
            if (count > 0) {
                Box(
                    modifier = Modifier.offset(x = 12.dp, y = (-12).dp).size(16.dp)
                        .background(color, CircleShape).border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (count > 9) "9+" else count.toString(), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = if (active) color else Color.Gray, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun RequestCameraPermissionContent(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Camera, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Camera Permission Required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("We need camera access for enhanced AI security monitoring with multi-modal detection", fontSize = 14.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Camera, null)
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

@Composable
private fun getStatusCardColor(securityAlert: YOLODetector.SecurityAlert?, suspiciousActivity: String?, statusMessage: String): Color {
    return when {
        securityAlert != null -> when (securityAlert.severity) {
            5 -> Color(0xFFD32F2F).copy(alpha = 0.95f)
            4 -> Color(0xFFFF5252).copy(alpha = 0.95f)
            3, 2 -> Color(0xFFFF9800).copy(alpha = 0.95f)
            else -> Color(0xFFFFC107).copy(alpha = 0.95f)
        }
        suspiciousActivity != null -> Color(0xFFFF5252).copy(alpha = 0.95f)
        statusMessage.contains("Unknown") -> Color(0xFFFF9800).copy(alpha = 0.9f)
        statusMessage.contains("Known") -> Color(0xFF4CAF50).copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    }
}

@Composable
private fun getStatusTextColor(securityAlert: YOLODetector.SecurityAlert?, suspiciousActivity: String?, statusMessage: String): Color {
    return when {
        securityAlert != null || suspiciousActivity != null -> Color.White
        statusMessage.contains("Unknown") || statusMessage.contains("Known") -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun getSubtitleColor(securityAlert: YOLODetector.SecurityAlert?, suspiciousActivity: String?, statusMessage: String): Color {
    return when {
        securityAlert != null || suspiciousActivity != null -> Color.White.copy(alpha = 0.9f)
        statusMessage.contains("Unknown") || statusMessage.contains("Known") -> Color.White.copy(alpha = 0.8f)
        else -> Color.Gray
    }
}

@Composable
private fun getProgressIndicatorColor(securityAlert: YOLODetector.SecurityAlert?, suspiciousActivity: String?, statusMessage: String): Color {
    return if (securityAlert != null || suspiciousActivity != null || statusMessage.contains("Unknown") || statusMessage.contains("Known")) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }
}