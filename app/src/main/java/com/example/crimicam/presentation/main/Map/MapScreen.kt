package com.example.crimicam.presentation.main.Map

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen() {
    val context = LocalContext.current
    var selectedMarker by remember { mutableStateOf<MapMarker?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Philippines center coordinates (Manila)
    val philippinesCenter = remember { GeoPoint(12.8797, 121.7740) }

    // Sample markers for demonstration in the Philippines
    val markers = remember {
        listOf(
            MapMarker(
                id = 1,
                name = "Manila Camera",
                status = "Active",
                latitude = 14.5995,
                longitude = 120.9842,
                type = MarkerType.CAMERA
            ),
            MapMarker(
                id = 2,
                name = "Cebu Alert Zone",
                status = "Recent Activity",
                latitude = 10.3157,
                longitude = 123.8854,
                type = MarkerType.ALERT
            ),
            MapMarker(
                id = 3,
                name = "Davao Camera",
                status = "Offline",
                latitude = 7.1907,
                longitude = 125.4553,
                type = MarkerType.CAMERA
            ),
            MapMarker(
                id = 4,
                name = "Known Person - Baguio",
                status = "John Doe spotted",
                latitude = 16.4023,
                longitude = 120.5960,
                type = MarkerType.PERSON
            )
        )
    }

    // Initialize OpenStreetMap configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap View
        OpenStreetMapView(
            center = philippinesCenter,
            zoomLevel = 6.0,
            markers = markers,
            onMarkerClick = { marker ->
                selectedMarker = marker
            },
            onMapReady = { map ->
                mapView = map
            }
        )

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
                onClick = {
                    mapView?.controller?.zoomIn()
                }
            )
            MapControlButton(
                icon = Icons.Default.Remove,
                onClick = {
                    mapView?.controller?.zoomOut()
                }
            )
            MapControlButton(
                icon = Icons.Default.MyLocation,
                onClick = {
                    mapView?.controller?.animateTo(philippinesCenter)
                }
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
fun OpenStreetMapView(
    center: GeoPoint,
    zoomLevel: Double,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // Set initial position and zoom (Philippines)
                controller.setZoom(zoomLevel)
                controller.setCenter(center)

                // Add compass
                val compassOverlay = CompassOverlay(
                    context,
                    InternalCompassOrientationProvider(context),
                    this
                ).apply {
                    enableCompass()
                }
                overlays.add(compassOverlay)

                // Add location overlay
                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)

                // Add markers
                markers.forEach { mapMarker ->
                    val marker = Marker(this).apply {
                        position = GeoPoint(mapMarker.latitude, mapMarker.longitude)
                        title = mapMarker.name
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // Set different icons based on marker type
                        icon = when (mapMarker.type) {
                            MarkerType.CAMERA -> context.getDrawable(android.R.drawable.ic_menu_camera)
                            MarkerType.ALERT -> context.getDrawable(android.R.drawable.ic_dialog_alert)
                            MarkerType.PERSON -> context.getDrawable(android.R.drawable.ic_menu_myplaces)
                        }

                        setOnMarkerClickListener { _, _ ->
                            onMarkerClick(mapMarker)
                            true
                        }
                    }
                    overlays.add(marker)
                }
            }
        },
        update = { mapView ->
            // Update map if needed
            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
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
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
                Column {
                    Text(
                        text = marker.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "Lat: ${"%.4f".format(marker.latitude)}, Lng: ${"%.4f".format(marker.longitude)}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
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
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (marker.type) {
                            MarkerType.CAMERA -> Color(0xFF2196F3)
                            MarkerType.ALERT -> Color(0xFFF44336)
                            MarkerType.PERSON -> Color(0xFFFF9800)
                        }
                    )
                ) {
                    Text("View Details", color = Color.White)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

// Updated Data classes with actual coordinates
data class MapMarker(
    val id: Int,
    val name: String,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val type: MarkerType
)

enum class MarkerType {
    CAMERA, ALERT, PERSON
}