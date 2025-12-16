
// 2. StreamViewerScreen - Updated UI
package com.example.crimicam.presentation.main.Home.Monitor

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.crimicam.webrtc.WebRTCManager
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.text.SimpleDateFormat
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun StreamViewerScreen(
    sessionId: String,
    navController: NavController,
    viewModel: StreamViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val eglBase = remember { EglBase.create() }
    var surfaceViewRenderer: SurfaceViewRenderer? by remember { mutableStateOf(null) }

    LaunchedEffect(sessionId) {
        viewModel.connectToStream(context, sessionId)
    }

    DisposableEffect(Unit) {
        onDispose {
            surfaceViewRenderer?.release()
            eglBase.release()
            viewModel.disconnect()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    setEnableHardwareScaler(true)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    init(eglBase.eglBaseContext, null)
                    surfaceViewRenderer = this
                    viewModel.attachVideoRenderer(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            TopStreamBar(
                session = state.session,
                connectionState = state.connectionState,
                isConnected = state.isConnected,
                onBack = { navController.popBackStack() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom info
            if (state.isConnected) {
                StreamInfo(session = state.session)
            }
        }

        // Loading overlay
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Connecting to stream...",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Error overlay
        state.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Red
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Connection Failed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        error,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

@Composable
fun TopStreamBar(
    session: com.example.crimicam.data.model.WebRTCSession?,
    connectionState: WebRTCManager.RTCConnectionState,
    isConnected: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        when (connectionState) {
                            WebRTCManager.RTCConnectionState.CONNECTED -> Color(0xFF4CAF50)
                            WebRTCManager.RTCConnectionState.CONNECTING -> Color.Yellow
                            WebRTCManager.RTCConnectionState.FAILED -> Color.Red
                            else -> Color.Gray
                        },
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (connectionState) {
                    WebRTCManager.RTCConnectionState.CONNECTED -> "LIVE"
                    WebRTCManager.RTCConnectionState.CONNECTING -> "CONNECTING"
                    WebRTCManager.RTCConnectionState.FAILED -> "FAILED"
                    else -> "DISCONNECTED"
                },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = session?.deviceName ?: "Unknown Device",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (session?.address != null) {
                Text(
                    text = session.address,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            } else if (session?.latitude != null && session.longitude != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${session.latitude}, ${session.longitude}",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun StreamInfo(session: com.example.crimicam.data.model.WebRTCSession?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                "STREAM INFORMATION",
                color = Color.Cyan,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            session?.let {
                Text(
                    text = buildString {
                        append("Device: ${it.deviceName}\n")
                        append("Device ID: ${it.deviceId}\n")
                        append("Started: ${formatTimestamp(it.streamStartedAt)}\n")
                        append("Last Update: ${formatTimestamp(it.lastHeartbeat)}")
                        if (it.latitude != null && it.longitude != null) {
                            append("\nLocation: ${it.latitude}, ${it.longitude}")
                        }
                        if (it.address != null) {
                            append("\nAddress: ${it.address}")
                        }
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}