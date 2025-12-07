package com.example.crimicam.presentation.main.Home.Camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.crimicam.presentation.main.Home.Camera.components.AppAlertDialog
import com.example.crimicam.presentation.main.Home.Camera.components.DelayedVisibility
import com.example.crimicam.presentation.main.Home.Camera.components.FaceDetectionOverlay
import com.example.crimicam.presentation.main.Home.Camera.components.ScreenRecorderHelper
import com.example.crimicam.presentation.main.Home.Camera.components.ScreenRecordingService
import com.example.crimicam.presentation.main.Home.Camera.components.createAlertDialog
import com.google.android.gms.location.*
import org.koin.androidx.compose.koinViewModel

private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)
private lateinit var cameraPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>
private var screenRecorder: ScreenRecorderHelper? = null
private lateinit var locationCallback: LocationCallback

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CameraScreen(
    navController: NavController,
    viewModel: CameraViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Location tracking
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setLocationPermission(isGranted)
        if (isGranted) {
            startLocationUpdates(fusedLocationClient, viewModel, context)
        }
    }

    // Check camera permission
    LaunchedEffect(Unit) {
        cameraPermissionStatus.value =
            ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Request location permission and start updates
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setLocationPermission(true)
            startLocationUpdates(fusedLocationClient, viewModel, context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Stop location updates when screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            stopLocationUpdates(fusedLocationClient)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenUI(viewModel)

        // Back button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Top controls container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(8.dp)
            ) {
                RecordingControls(viewModel = viewModel)

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Camera switch button
                IconButton(
                    onClick = {
                        if (cameraFacing.intValue == CameraSelector.LENS_FACING_BACK) {
                            cameraFacing.intValue = CameraSelector.LENS_FACING_FRONT
                        } else {
                            cameraFacing.intValue = CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Transparent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Manual capture button with cooldown indicator
                Box {
                    IconButton(
                        onClick = {
                            if (state.isInCooldown) {
                                // Show cooldown message
                                viewModel.updateStatusMessage(
                                    "⏳ Please wait ${state.cooldownRemaining / 1000}s before capturing"
                                )
                            } else {
                                viewModel.saveCurrentDetection()
                                Log.d("CameraScreen", "Manual capture triggered")
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (state.isInCooldown) Color.Gray else Color.Green.copy(alpha = 0.8f),
                                CircleShape
                            ),
                        enabled = !state.isInCooldown
                    ) {
                        if (state.isInCooldown) {
                            Text(
                                text = "${state.cooldownRemaining / 1000}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Manual Capture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingControls(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.state.collectAsState()
    val recordingState = state.recordingState

    LaunchedEffect(Unit) {
        if (screenRecorder == null) {
            screenRecorder = ScreenRecorderHelper(
                context = context,
                onRecordingComplete = { uri ->
                    viewModel.onRecordingSaved(uri)
                },
                onRecordingError = { error ->
                    viewModel.onRecordingFailed(error)
                }
            )
            ScreenRecordingService.screenRecorderHelper = screenRecorder
        }
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ScreenRecordingService.ACTION_START
                putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecordingService.EXTRA_DATA, result.data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            viewModel.startRecording()
        } else {
            viewModel.onRecordingFailed("Screen recording permission denied")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (activity != null) {
                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        } else {
            viewModel.onRecordingFailed("Audio permission required for recording")
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (activity != null) {
                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                }
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            viewModel.onRecognitionError("Storage permission required to save videos")
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = {
                if (recordingState.isRecording) {
                    val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
                        action = ScreenRecordingService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    viewModel.stopRecording()
                } else {
                    val hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasAudioPermission) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@IconButton
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (activity != null) {
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                    as MediaProjectionManager
                            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    } else {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            if (activity != null) {
                                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                        as MediaProjectionManager
                                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                            }
                        } else {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                }
            },
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (recordingState.isRecording) Color.Red else Color.White,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = if (recordingState.isRecording) "Stop Recording" else "Start Recording",
                tint = if (recordingState.isRecording) Color.White else Color.Red,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            if (recordingState.isRecording) {
                Text(
                    text = recordingState.recordingTime,
                    color = Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "REC",
                    color = Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "READY",
                    color = Color.Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!recordingState.isRecording) {
                Text(
                    text = "Gallery/CrimiCam",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ScreenUI(viewModel: CameraViewModel) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Camera(viewModel)

        // Show cooldown status
        if (state.isInCooldown) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⏳ Cooldown: ${state.cooldownRemaining / 1000}s",
                    color = Color.Yellow,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DelayedVisibility(state.detectedFaces.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (state.isInCooldown) 110.dp else 72.dp, start = 16.dp, end = 16.dp)
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
                            text = buildString {
                                append("Database: ${state.knownPeople.size} profiles\n")
                                append("Detected: ${state.detectedFaces.size}\n")
                                append("Identified: ${state.detectedFaces.count { it.personName != null }}")
                                if (state.recordingState.isRecording) {
                                    append("\nRecording: ${state.recordingState.recordingTime}")
                                }
                                if (state.isInCooldown) {
                                    append("\nCooldown: ${state.cooldownRemaining / 1000}s")
                                }
                                append("\nSave to: Gallery/CrimiCam")
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        DelayedVisibility(state.knownPeople.isEmpty()) {
            Text(
                text = "⚠️ NO PROFILES IN DATABASE",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 72.dp)
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
            cameraPermissionDialog(context)
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

private fun cameraPermissionDialog(context: Context) {
    createAlertDialog(
        "Camera Permission",
        "The facial recognition system cannot function without camera permission.",
        "ALLOW",
        "CLOSE",
        onPositiveButtonClick = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onNegativeButtonClick = {
            android.widget.Toast.makeText(
                context,
                "Camera permission is required for facial recognition",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    )
}

// Location helper functions
private fun startLocationUpdates(
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: CameraViewModel,
    context: Context
) {
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val locationRequest = LocationRequest.create().apply {
        interval = 10000
        fastestInterval = 5000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                viewModel.updateLocation(location)
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )
}

private fun stopLocationUpdates(fusedLocationClient: FusedLocationProviderClient) {
    if (::locationCallback.isInitialized) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}