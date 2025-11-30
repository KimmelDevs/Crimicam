package com.example.crimicam.presentation.main.Home.Camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.example.crimicam.presentation.main.Home.Camera.components.AppAlertDialog
import com.example.crimicam.presentation.main.Home.Camera.components.DelayedVisibility
import com.example.crimicam.presentation.main.Home.Camera.components.FaceDetectionOverlay
import com.example.crimicam.presentation.main.Home.Camera.components.createAlertDialog
import org.koin.androidx.compose.koinViewModel

private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)
private lateinit var cameraPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: CameraViewModel = koinViewModel()
) {
    val context = LocalContext.current

    // Check camera permission
    LaunchedEffect(Unit) {
        cameraPermissionStatus.value =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenUI(viewModel)

        // Floating camera switch button
        IconButton(
            onClick = {
                if (cameraFacing.intValue == CameraSelector.LENS_FACING_BACK) {
                    cameraFacing.intValue = CameraSelector.LENS_FACING_FRONT
                } else {
                    cameraFacing.intValue = CameraSelector.LENS_FACING_BACK
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color.Cyan
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ScreenUI(viewModel: CameraViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Camera(viewModel)

        // Show metrics when people are detected
        DelayedVisibility(state.detectedFaces.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Recognition on ${state.detectedFaces.size} face(s)",
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Performance metrics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            "SYSTEM PERFORMANCE",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Database: ${state.knownPeople.size} profiles\n" +
                                    "Detected: ${state.detectedFaces.size}\n" +
                                    "Identified: ${state.detectedFaces.count { it.personName != null }}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Show warning when no known people
        DelayedVisibility(state.knownPeople.isEmpty()) {
            Text(
                text = "⚠️ NO PROFILES IN DATABASE",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        AppAlertDialog()
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun Camera(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraFacing by remember { cameraFacing }

    cameraPermissionStatus.value =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraPermissionStatus.value = true
        } else {
            cameraPermissionDialog()
        }
    }

    DelayedVisibility(cameraPermissionStatus.value) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                FaceDetectionOverlay(lifecycleOwner, context, viewModel)
            },
            update = { it.initializeCamera(cameraFacing) }
        )
    }

    DelayedVisibility(!cameraPermissionStatus.value) {
        RequestCameraPermissionContent(
            onRequestPermission = {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
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
                contentDescription = null,
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
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
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

private fun cameraPermissionDialog() {
    createAlertDialog(
        "Camera Permission",
        "The facial recognition system cannot function without camera permission.",
        "ALLOW",
        "CLOSE",
        onPositiveButtonClick = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onNegativeButtonClick = {
            // Handle deny action - could close app or show info
        }
    )
}