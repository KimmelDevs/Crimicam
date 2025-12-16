package com.example.crimicam.presentation.main.Home.ActivityDetail

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

    // Log the captureId for debugging
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
                        // Activity Info Card (if viewing single capture)
                        state.selectedCapture?.let { capture ->
                            CaptureInfoCard(capture = capture)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = if (captureId != null && captureId != "null")
                                "Captured Media"
                            else
                                "All Captured Faces (${state.captures.size})",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Photo Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 800.dp)
                        ) {
                            items(state.captures) { capture ->
                                CapturedFaceCard(capture = capture)
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
fun CaptureInfoCard(capture: CapturedFaceData) {
    val backgroundColor = when {
        capture.isCriminal && capture.dangerLevel == "CRITICAL" -> Color(0xFFFFEBEE)
        capture.isCriminal -> Color(0xFFFFF3E0)
        capture.isRecognized -> Color(0xFFE8F5E9)
        else -> Color(0xFFFCE4EC)
    }

    val textColor = when {
        capture.isCriminal && capture.dangerLevel == "CRITICAL" -> Color(0xFFB71C1C)
        capture.isCriminal -> Color(0xFFE65100)
        capture.isRecognized -> Color(0xFF2E7D32)
        else -> Color(0xFFC2185B)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Text(
                text = when {
                    capture.isCriminal && capture.dangerLevel != null -> {
                        "🚨 ${capture.dangerLevel} DANGER: ${capture.matchedPersonName ?: "Unknown Criminal"}"
                    }
                    capture.isRecognized -> {
                        "✅ Identified: ${capture.matchedPersonName ?: "Unknown"}"
                    }
                    else -> "⚠️ Unknown Person Detected"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Confidence
            if (capture.confidence > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Confidence: ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${(capture.confidence * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            // Timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Captured: ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.8f)
                )
                Text(
                    text = capture.timestamp,
                    fontSize = 14.sp,
                    color = textColor
                )
            }

            // Location
            if (capture.address != null) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Location: ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = capture.address,
                        fontSize = 14.sp,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (capture.latitude != null && capture.longitude != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Coordinates: ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${"%.4f".format(capture.latitude)}, ${"%.4f".format(capture.longitude)}",
                        fontSize = 14.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun CapturedFaceCard(capture: CapturedFaceData) {
    val decodedBitmap = remember(capture.croppedFaceBase64) {
        decodeBase64ToBitmap(capture.croppedFaceBase64)
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
            // Display the cropped face image
            decodedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured Face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } ?: run {
                // Fallback if no image
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