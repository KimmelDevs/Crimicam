package com.example.crimicam.presentation.main.Map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapScreen() {
    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }

    // Sample markers for demonstration
    val markers = remember {
        listOf(
            MapMarker(1, "Camera 1", "Active", 0.3f, 0.4f, MarkerType.CAMERA),
            MapMarker(2, "Alert Zone", "Recent Activity", 0.6f, 0.3f, MarkerType.ALERT),
            MapMarker(3, "Camera 2", "Offline", 0.5f, 0.7f, MarkerType.CAMERA),
            MapMarker(4, "Known Person", "John Doe spotted", 0.7f, 0.6f, MarkerType.PERSON)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map Background
        MapBackground()

        // Map Markers
        markers.forEach { marker ->
            MapMarkerPin(
                marker = marker,
                onClick = { selectedMarker = marker }
            )
        }

        // Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(
                icon = Icons.Default.Add,
                onClick = { /* Zoom in */ }
            )
            MapControlButton(
                icon = Icons.Default.Remove,
                onClick = { /* Zoom out */ }
            )
            MapControlButton(
                icon = Icons.Default.MyLocation,
                onClick = { /* Center on location */ }
            )
        }

        // Bottom Info Card
        selectedMarker?.let { marker ->
            MarkerInfoCard(
                marker = marker,
                onDismiss = { selectedMarker = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        // Top Stats Bar
        MapStatsBar(
            cameraCount = markers.count { it.type == MarkerType.CAMERA },
            alertCount = markers.count { it.type == MarkerType.ALERT },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}

@Composable
fun MapBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E9))
    ) {
        // Grid pattern to simulate map
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(20) {
                Row(modifier = Modifier.weight(1f)) {
                    repeat(20) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 0.5.dp,
                                    color = Color(0xFFBDBDBD).copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }
        }

        // Simulated roads/paths
        Box(
            modifier = Modifier
                .fillMaxWidth(0.1f)
                .fillMaxHeight()
                .offset(x = 120.dp)
                .background(Color(0xFFBDBDBD).copy(alpha = 0.3f))
        )

        Box(
            modifier = Modifier
                .fillMaxHeight(0.1f)
                .fillMaxWidth()
                .offset(y = 180.dp)
                .background(Color(0xFFBDBDBD).copy(alpha = 0.3f))
        )
    }
}

@Composable
fun MapMarkerPin(
    marker: MapMarker,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(
                    x = (marker.x * 350).dp,
                    y = (marker.y * 650).dp
                )
                .clickable(onClick = onClick)
        ) {
            // Marker pin
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (marker.type) {
                            MarkerType.CAMERA -> Color(0xFF2196F3)
                            MarkerType.ALERT -> Color(0xFFF44336)
                            MarkerType.PERSON -> Color(0xFFFF9800)
                        }
                    )
                    .border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = marker.name,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Pulse effect
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .offset(y = (-45).dp)
                    .clip(CircleShape)
                    .background(
                        when (marker.type) {
                            MarkerType.CAMERA -> Color(0xFF2196F3).copy(alpha = 0.2f)
                            MarkerType.ALERT -> Color(0xFFF44336).copy(alpha = 0.2f)
                            MarkerType.PERSON -> Color(0xFFFF9800).copy(alpha = 0.2f)
                        }
                    )
            )
        }
    }
}

@Composable
fun MapControlButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        containerColor = Color.White,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Composable
fun MarkerInfoCard(
    marker: MapMarker,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                Text(
                    text = marker.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = marker.status,
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* View details */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View Details")
                }

                OutlinedButton(
                    onClick = { /* Navigate */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Navigate")
                }
            }
        }
    }
}

@Composable
fun MapStatsBar(
    cameraCount: Int,
    alertCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatItem(
                label = "Cameras",
                value = cameraCount.toString(),
                color = Color(0xFF2196F3)
            )

            StatItem(
                label = "Alerts",
                value = alertCount.toString(),
                color = Color(0xFFF44336)
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )

        Column {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

// Data classes
data class MapMarker(
    val id: Int,
    val name: String,
    val status: String,
    val x: Float,
    val y: Float,
    val type: MarkerType
)

enum class MarkerType {
    CAMERA, ALERT, PERSON
}