package com.example.crimicam.presentation.main.Home.Camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
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
import org.koin.androidx.compose.koinViewModel

private val cameraPermissionStatus = mutableStateOf(false)
private val cameraFacing = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)
private lateinit var cameraPermissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>
private var screenRecorder: ScreenRecorderHelper? = null

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

        // Top controls container - recording and camera switch side by side
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
                // Recording controls
                RecordingControls(viewModel = viewModel)

                Spacer(modifier = Modifier.width(8.dp))

                // Vertical divider
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

    // Initialize screen recorder
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

    // Screen capture permission launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("CameraScreen", "Screen capture result: resultCode=${result.resultCode}, data=${result.data}")

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d("CameraScreen", "Starting screen recording via foreground service...")

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
            Log.e("CameraScreen", "Screen recording permission denied")
            viewModel.onRecordingFailed("Screen recording permission denied")
        }
    }

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("CameraScreen", "Audio permission granted")
            // Audio permission granted, now request screen capture
            if (activity != null) {
                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        } else {
            Log.e("CameraScreen", "Audio permission denied")
            viewModel.onRecordingFailed("Audio permission required for recording")
        }
    }

    // Storage permission launcher for Android 9 and below
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("CameraScreen", "Storage permission granted")
            // Check audio permission next
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Audio already granted, request screen capture
                if (activity != null) {
                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                    screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
                }
            } else {
                // Request audio permission
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            Log.e("CameraScreen", "Storage permission denied")
            viewModel.onRecognitionError("Storage permission required to save videos")
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Record button
        IconButton(
            onClick = {
                Log.d("CameraScreen", "Record button clicked, isRecording=${recordingState.isRecording}")

                if (recordingState.isRecording) {
                    Log.d("CameraScreen", "Stopping recording via service...")

                    val stopIntent = Intent(context, ScreenRecordingService::class.java).apply {
                        action = ScreenRecordingService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    viewModel.stopRecording()
                } else {
                    Log.d("CameraScreen", "Starting recording flow...")

                    // Check audio permission first
                    val hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasAudioPermission) {
                        Log.d("CameraScreen", "Requesting audio permission")
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@IconButton
                    }

                    // Check storage permission (for Android 9 and below)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ - No storage permission needed
                        if (activity != null) {
                            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                    as MediaProjectionManager
                            val intent = projectionManager.createScreenCaptureIntent()
                            Log.d("CameraScreen", "Launching screen capture intent")
                            screenCaptureLauncher.launch(intent)
                        } else {
                            Log.e("CameraScreen", "Activity is null!")
                        }
                    } else {
                        // Android 9 and below - need storage permission
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

        // Recording timer and status - compact version
        Column(
            horizontalAlignment = Alignment.Start
        ) {
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

            // Show save location when not recording
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

        // Show metrics when people are detected - adjusted padding for new layout
        DelayedVisibility(state.detectedFaces.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
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

                // Performance metrics - moved to bottom
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

        // Show warning when no known people
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
            // Handle deny action - inform user and optionally close the screen
            Log.d("CameraScreen", "Camera permission denied by user")

            // You can either:
            // 1. Show a toast message
            android.widget.Toast.makeText(
                context,
                "Camera permission is required for facial recognition",
                android.widget.Toast.LENGTH_LONG
            ).show()

            // 2. Or navigate back since camera is essential
            // navController.popBackStack()
        }
    )
}