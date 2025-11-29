package com.example.crimicam.presentation.main.Home.ActivityDetail

import android.graphics.BitmapFactory
import android.util.Base64
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

    LaunchedEffect(captureId) {
        if (captureId != null) {
            viewModel.loadCaptureDetails(captureId)
        } else {
            viewModel.loadAllCaptures()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Details") },
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
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                            text = "Error: ${state.error}",
                            color = Color.Red,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if (captureId != null) {
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
                            text = "No captured faces found",
                            fontSize = 16.sp,
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (capture.isRecognized)
                                        Color(0xFFE8F5E9)
                                    else
                                        Color(0xFFFCE4EC)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = if (capture.isRecognized) {
                                            "✅ Identified: ${capture.matchedPersonName ?: "Unknown"}"
                                        } else {
                                            "⚠️ Unknown Person Detected"
                                        },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (capture.isRecognized)
                                            Color(0xFF2E7D32)
                                        else
                                            Color(0xFFC2185B)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (capture.isRecognized) {
                                        Text(
                                            text = "Confidence: ${(capture.confidence * 100).toInt()}%",
                                            fontSize = 14.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    Text(
                                        text = "Captured: ${capture.timestamp}",
                                        fontSize = 14.sp,
                                        color = if (capture.isRecognized)
                                            Color(0xFF2E7D32)
                                        else
                                            Color(0xFFC2185B)
                                    )

                                    if (capture.address != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Location: ${capture.address}",
                                            fontSize = 14.sp,
                                            color = if (capture.isRecognized)
                                                Color(0xFF2E7D32)
                                            else
                                                Color(0xFFC2185B)
                                        )
                                    } else if (capture.latitude != null && capture.longitude != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Coordinates: ${capture.latitude}, ${capture.longitude}",
                                            fontSize = 14.sp,
                                            color = if (capture.isRecognized)
                                                Color(0xFF2E7D32)
                                            else
                                                Color(0xFFC2185B)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (captureId != null) "Captured Media" else "All Captured Faces",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Photo Grid - Fixed height calculation
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 600.dp) // Use heightIn instead of fixed height
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
fun CapturedFaceCard(capture: CapturedFaceData) {
    val decodedBitmap = remember(capture.croppedFaceBase64) {
        decodeBase64ToBitmap(capture.croppedFaceBase64)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (capture.isRecognized)
                Color(0xFF4CAF50)
            else
                Color(0xFFE91E63)
        )
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
                // Fallback if no image or decoding failed
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Image",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
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
                        text = if (capture.isRecognized) {
                            capture.matchedPersonName ?: "Known"
                        } else {
                            "Unknown"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Confidence badge
            if (capture.isRecognized && capture.confidence > 0) {
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

// Remove @Composable annotation from this function - it's a regular function
fun decodeBase64ToBitmap(base64String: String?): android.graphics.Bitmap? {
    return if (base64String.isNullOrEmpty()) {
        null
    } else {
        try {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class CapturedFaceData(
    val id: String = "",
    val croppedFaceBase64: String? = null,
    val isRecognized: Boolean = false,
    val matchedPersonName: String? = null,
    val confidence: Float = 0f,
    val timestamp: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)