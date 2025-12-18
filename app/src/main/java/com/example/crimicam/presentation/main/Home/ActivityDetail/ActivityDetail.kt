package com.example.crimicam.presentation.main.Home.ActivityDetail

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    navController: NavController,
    captureId: String? = null
) {
    val viewModel: ActivityDetailViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(captureId) {
        Log.d("ActivityDetailScreen", "Received captureId: $captureId")

        if (captureId != null && captureId != "null" && captureId.isNotEmpty()) {
            viewModel.loadCaptureDetails(captureId)
        } else {
            viewModel.loadAllCaptures()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (captureId != null && captureId != "null")
                            "Activity Details"
                        else
                            "All Captures"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading captures...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚠️ Error",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error ?: "Unknown error",
                            color = Color.Red,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if (captureId != null && captureId != "null") {
                                viewModel.loadCaptureDetails(captureId)
                            } else {
                                viewModel.loadAllCaptures()
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                state.captures.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📭",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No captured faces found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start using the camera to capture faces",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Show detailed view if single capture
                        if (captureId != null && captureId != "null" && state.selectedCapture != null) {
                            DetailedCaptureView(capture = state.selectedCapture!!)
                        } else {
                            // Show grid for all captures
                            Text(
                                text = "All Captured Faces (${state.captures.size})",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 1000.dp)
                            ) {
                                items(state.captures) { capture ->
                                    CapturedFaceCard(capture = capture)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedCaptureView(capture: CapturedFaceData) {
    val backgroundColor = when {
        capture.isCriminal && capture.dangerLevel == "CRITICAL" -> Color(0xFFFFEBEE)
        capture.isCriminal -> Color(0xFFFFF3E0)
        capture.isRecognized -> Color(0xFFE8F5E9)
        else -> Color(0xFFF5F5F5)
    }

    val accentColor = when {
        capture.isCriminal && capture.dangerLevel == "CRITICAL" -> Color(0xFFB71C1C)
        capture.isCriminal -> Color(0xFFE65100)
        capture.isRecognized -> Color(0xFF2E7D32)
        else -> Color(0xFF616161)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        capture.isCriminal && capture.dangerLevel != null -> {
                            "🚨 ${capture.dangerLevel} DANGER"
                        }
                        capture.isRecognized -> "✅ IDENTIFIED"
                        else -> "⚠️ UNKNOWN PERSON"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                Text(
                    text = capture.matchedPersonName ?: "Unknown Person",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (capture.confidence > 0) {
                    Text(
                        text = "Confidence: ${(capture.confidence * 100).toInt()}%",
                        fontSize = 16.sp,
                        color = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Full Frame Image
        if (capture.fullFrameBase64 != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Text(
                        text = "Full Frame",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )

                    val fullFrameBitmap = remember(capture.fullFrameBase64) {
                        decodeBase64ToBitmap(capture.fullFrameBase64)
                    }

                    fullFrameBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full Frame",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // Cropped Face Image
        if (capture.croppedFaceBase64 != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Text(
                        text = "Detected Face",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )

                    val croppedBitmap = remember(capture.croppedFaceBase64) {
                        decodeBase64ToBitmap(capture.croppedFaceBase64)
                    }

                    croppedBitmap?.let { bitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Cropped Face",
                                modifier = Modifier
                                    .size(200.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }

        // Capture Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Capture Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Divider()

                // Person Info
                if (capture.matchedPersonId != null) {
                    InfoRow(
                        icon = Icons.Default.Person,
                        label = "Person ID",
                        value = capture.matchedPersonId
                    )
                }

                // Timestamp
                InfoRow(
                    icon = Icons.Default.AccessTime,
                    label = "Captured At",
                    value = capture.timestamp
                )

                // Detection Time
                if (capture.detectionTimeMs != null) {
                    InfoRow(
                        icon = Icons.Default.AccessTime,
                        label = "Detection Time",
                        value = "${capture.detectionTimeMs}ms"
                    )
                }

                // Danger Level
                if (capture.dangerLevel != null) {
                    InfoRow(
                        icon = Icons.Default.Shield,
                        label = "Danger Level",
                        value = capture.dangerLevel,
                        valueColor = Color.Red
                    )
                }

                // Status
                InfoRow(
                    icon = Icons.Default.Shield,
                    label = "Status",
                    value = when {
                        capture.isCriminal -> "Criminal"
                        capture.isRecognized -> "Recognized"
                        else -> "Unknown"
                    },
                    valueColor = when {
                        capture.isCriminal -> Color.Red
                        capture.isRecognized -> Color.Green
                        else -> Color.Gray
                    }
                )
            }
        }

        // Location Information Card
        if (capture.latitude != null && capture.longitude != null || capture.address != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Location Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    if (capture.address != null) {
                        InfoRow(
                            icon = Icons.Default.LocationOn,
                            label = "Address",
                            value = capture.address
                        )
                    }

                    if (capture.latitude != null && capture.longitude != null) {
                        InfoRow(
                            icon = Icons.Default.LocationOn,
                            label = "Coordinates",
                            value = "${"%.6f".format(capture.latitude)}, ${"%.6f".format(capture.longitude)}"
                        )
                    }
                }
            }
        }

        // Device Information Card
        if (capture.deviceId != null || capture.deviceModel != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Device Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    if (capture.deviceModel != null) {
                        InfoRow(
                            icon = Icons.Default.Phone,
                            label = "Device Model",
                            value = capture.deviceModel
                        )
                    }

                    if (capture.deviceId != null) {
                        InfoRow(
                            icon = Icons.Default.Phone,
                            label = "Device ID",
                            value = capture.deviceId.take(16) + "..."
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.Black
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = valueColor,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun CapturedFaceCard(capture: CapturedFaceData) {
    val decodedBitmap = remember(capture.fullFrameBase64, capture.croppedFaceBase64) {
        // Prefer full frame, fallback to cropped
        decodeBase64ToBitmap(capture.fullFrameBase64 ?: capture.croppedFaceBase64)
    }

    val cardColor = when {
        capture.isCriminal && capture.dangerLevel == "CRITICAL" -> Color(0xFFB71C1C)
        capture.isCriminal && capture.dangerLevel == "HIGH" -> Color(0xFFD32F2F)
        capture.isCriminal -> Color(0xFFFF6F00)
        capture.isRecognized -> Color(0xFF4CAF50)
        else -> Color(0xFFE91E63)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            decodedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured Face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📷",
                            fontSize = 32.sp
                        )
                        Text(
                            text = "No Image",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Criminal danger badge
            if (capture.isCriminal && capture.dangerLevel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = capture.dangerLevel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Overlay with person name/status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when {
                            capture.isCriminal -> capture.matchedPersonName ?: "Criminal"
                            capture.isRecognized -> capture.matchedPersonName ?: "Known"
                            else -> "Unknown"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Confidence badge
            if (capture.confidence > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${(capture.confidence * 100).toInt()}%",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

fun decodeBase64ToBitmap(base64String: String?): android.graphics.Bitmap? {
    return if (base64String.isNullOrEmpty()) {
        null
    } else {
        try {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ActivityDetailScreen", "Error decoding base64 image", e)
            null
        }
    }
}