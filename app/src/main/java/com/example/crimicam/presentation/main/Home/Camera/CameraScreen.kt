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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

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

    var previewSize by remember { mutableStateOf(Size.Zero) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    previewSize = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                }
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

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

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

        // Surveillance Grid Overlay
        SurveillanceGridOverlay(modifier = Modifier.fillMaxSize())

        // Face Detection Overlays
        if (previewSize != Size.Zero) {
            MovieStyleFaceOverlay(
                detectedFaces = state.detectedFaces,
                previewSize = previewSize,
                scanningMode = state.scanningMode,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Status Bar (like movie UI)
        MovieStyleStatusBar(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        )

        // Corner HUD Elements
        CornerHUDElements(
            modifier = Modifier.fillMaxSize()
        )

        // Bottom Info Panel
        MovieStyleBottomPanel(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun SurveillanceGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gridColor = Color.Cyan.copy(alpha = 0.15f)
        val strokeWidth = 1f

        // Vertical lines
        for (i in 1..8) {
            val x = size.width * i / 9
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth
            )
        }

        // Horizontal lines
        for (i in 1..6) {
            val y = size.height * i / 7
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
        }

        // Corner markers
        val cornerSize = 40f
        val cornerThickness = 3f
        val corners = listOf(
            // Top-left
            listOf(Offset(20f, 20f) to Offset(20f + cornerSize, 20f),
                Offset(20f, 20f) to Offset(20f, 20f + cornerSize)),
            // Top-right
            listOf(Offset(size.width - 20f - cornerSize, 20f) to Offset(size.width - 20f, 20f),
                Offset(size.width - 20f, 20f) to Offset(size.width - 20f, 20f + cornerSize)),
            // Bottom-left
            listOf(Offset(20f, size.height - 20f) to Offset(20f + cornerSize, size.height - 20f),
                Offset(20f, size.height - 20f - cornerSize) to Offset(20f, size.height - 20f)),
            // Bottom-right
            listOf(Offset(size.width - 20f - cornerSize, size.height - 20f) to Offset(size.width - 20f, size.height - 20f),
                Offset(size.width - 20f, size.height - 20f - cornerSize) to Offset(size.width - 20f, size.height - 20f))
        )

        corners.forEach { cornerLines ->
            cornerLines.forEach { (start, end) ->
                drawLine(Color.Cyan.copy(alpha = 0.6f), start, end, cornerThickness)
            }
        }
    }
}

@Composable
fun MovieStyleFaceOverlay(
    detectedFaces: List<DetectedFace>,
    previewSize: Size,
    scanningMode: ScanningMode,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

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
        detectedFaces.forEach { face ->
            val box = face.boundingBox
            val isIdentified = face.personName != null

            val boxColor = if (isIdentified) {
                Color(0xFF00FF00) // Green for identified
            } else {
                Color(0xFFFF0000) // Red for unknown
            }

            // Animated scanning line over face
            if (scanningMode == ScanningMode.DETECTING || scanningMode == ScanningMode.ANALYZING) {
                val scanY = box.top + (box.height() * scanLineY)
                drawLine(
                    color = boxColor.copy(alpha = 0.8f),
                    start = Offset(box.left, scanY),
                    end = Offset(box.right, scanY),
                    strokeWidth = 3f
                )
            }

            // Corner brackets (movie style)
            val cornerLength = minOf(box.width(), box.height()) * 0.25f
            var strokeWidth = 4f

            // Top-left corner
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.left, box.top),
                Offset(box.left + cornerLength, box.top), strokeWidth)
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.left, box.top),
                Offset(box.left, box.top + cornerLength), strokeWidth)

            // Top-right corner
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.right - cornerLength, box.top),
                Offset(box.right, box.top), strokeWidth)
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.right, box.top),
                Offset(box.right, box.top + cornerLength), strokeWidth)

            // Bottom-left corner
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.left, box.bottom - cornerLength),
                Offset(box.left, box.bottom), strokeWidth)
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.left, box.bottom),
                Offset(box.left + cornerLength, box.bottom), strokeWidth)

            // Bottom-right corner
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.right, box.bottom - cornerLength),
                Offset(box.right, box.bottom), strokeWidth)
            drawLine(boxColor.copy(alpha = pulseAlpha), Offset(box.right - cornerLength, box.bottom),
                Offset(box.right, box.bottom), strokeWidth)

            // Center crosshair
            val centerX = box.centerX()
            val centerY = box.centerY()
            drawCircle(
                color = boxColor.copy(alpha = pulseAlpha),
                radius = 6f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = boxColor.copy(alpha = pulseAlpha),
                radius = 3f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f)
            )

            // Info label above face
            val labelY = (box.top - 50f).coerceAtLeast(40f)
            val labelText = if (isIdentified) {
                face.personName?.uppercase() ?: "IDENTIFIED"
            } else {
                "UNKNOWN SUBJECT"
            }

            val confidenceText = "${(face.confidence * 100).toInt()}% MATCH"

            drawIntoCanvas { canvas ->
                // Background for text
                val bgPaint = android.graphics.Paint().apply {
                    color = if (isIdentified)
                        android.graphics.Color.argb(220, 0, 255, 0)
                    else
                        android.graphics.Color.argb(220, 255, 0, 0)
                }
                canvas.nativeCanvas.drawRect(
                    box.left - 10f,
                    labelY - 35f,
                    box.right + 10f,
                    labelY + 5f,
                    bgPaint
                )

                // Border
                val borderPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.nativeCanvas.drawRect(
                    box.left - 10f,
                    labelY - 35f,
                    box.right + 10f,
                    labelY + 5f,
                    borderPaint
                )

                // Name text
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 32f
                    isFakeBoldText = true
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                canvas.nativeCanvas.drawText(
                    labelText,
                    box.left,
                    labelY - 15f,
                    textPaint
                )

                // Confidence text
                val confPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 20f
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                canvas.nativeCanvas.drawText(
                    confidenceText,
                    box.left,
                    labelY,
                    confPaint
                )
            }

            // ID badge (bottom-right of box)
            if (isIdentified && face.personId != null) {
                val badgeX = box.right - 60f
                val badgeY = box.bottom + 30f

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.9f),
                    topLeft = Offset(badgeX - 10f, badgeY - 25f),
                    size = Size(80f, 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = boxColor,
                    topLeft = Offset(badgeX - 10f, badgeY - 25f),
                    size = Size(80f, 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    style = Stroke(width = 2f)
                )

                drawIntoCanvas { canvas ->
                    val idPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 18f
                        isFakeBoldText = true
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    canvas.nativeCanvas.drawText(
                        "ID: ${face.personId.take(4)}",
                        badgeX - 5f,
                        badgeY,
                        idPaint
                    )
                }
            }
        }
    }
}

@Composable
fun MovieStyleStatusBar(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    val currentTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var time by remember { mutableStateOf(currentTime.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            time = currentTime.format(Date())
        }
    }

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column {
            // Top row - System status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "REC",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        time,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "ONLINE",
                        color = Color.Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when (state.scanningMode) {
                            ScanningMode.IDENTIFIED -> Color(0xFF00FF00).copy(alpha = 0.2f)
                            ScanningMode.UNKNOWN -> Color(0xFFFF0000).copy(alpha = 0.2f)
                            else -> Color.Cyan.copy(alpha = 0.1f)
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        when (state.scanningMode) {
                            ScanningMode.IDENTIFIED -> Color(0xFF00FF00)
                            ScanningMode.UNKNOWN -> Color(0xFFFF0000)
                            else -> Color.Cyan
                        },
                        RoundedCornerShape(4.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    state.statusMessage,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun CornerHUDElements(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Top-left: Camera ID
        Text(
            "CAM-01 // FACIAL RECOGNITION",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            color = Color.Cyan.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        // Top-right: System info
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "SECURITY LEVEL: MAX",
                color = Color.Red.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "CLEARANCE: AUTHORIZED",
                color = Color.Green.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun MovieStyleBottomPanel(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Database info
            Column {
                Text(
                    "DATABASE",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${state.knownPeople.size} PROFILES",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Detection count
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "DETECTED",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${state.detectedFaces.size}",
                    color = if (state.detectedFaces.isEmpty()) Color.Gray else Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Identified count
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "IDENTIFIED",
                    color = Color.Cyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "${state.detectedFaces.count { it.personName != null }}",
                    color = Color(0xFF00FF00),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun RequestCameraPermissionContent(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Camera,
                null,
                modifier = Modifier.size(80.dp),
                tint = Color.Cyan
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "CAMERA ACCESS REQUIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "FACIAL RECOGNITION SYSTEM REQUIRES CAMERA PERMISSION",
                fontSize = 12.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Cyan
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "GRANT ACCESS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
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