package com.example.crimicam.presentation.main.Map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.   icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crimicam.data.service.CriminalLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MapScreen() {
    val context = LocalContext.current

    // FIXED: Use ViewModelFactory to provide Context
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context)
    )

    val state by viewModel.state.collectAsState()
    var selectedMarker by remember { mutableStateOf<CriminalMapMarker?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Philippines center coordinates
    val philippinesCenter = remember { GeoPoint(12.8797, 121.7740) }

    // Initialize OpenStreetMap configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName

        // Load criminal locations
        viewModel.loadCriminalLocations()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap View
        if (state.isLoading && state.criminalLocations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            OpenStreetMapView(
                center = philippinesCenter,
                zoomLevel = 6.0,
                criminalLocations = state.criminalLocations,
                onMarkerClick = { marker ->
                    selectedMarker = marker
                },
                onMapReady = { map ->
                    mapView = map
                }
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
                onClick = { mapView?.controller?.zoomIn() }
            )
            MapControlButton(
                icon = Icons.Default.Remove,
                onClick = { mapView?.controller?.zoomOut() }
            )
            MapControlButton(
                icon = Icons.Default.MyLocation,
                onClick = { mapView?.controller?.animateTo(philippinesCenter) }
            )
            MapControlButton(
                icon = Icons.Default.Refresh,
                onClick = { viewModel.loadCriminalLocations() }
            )
        }

        // Bottom Info Card
        selectedMarker?.let { marker ->
            CriminalInfoCard(
                marker = marker,
                onDismiss = { selectedMarker = null },
                onViewHistory = {
                    viewModel.loadLocationHistory(marker.criminalId)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }

        // Top Stats Bar
        MapStatsBar(
            criminalCount = state.criminalLocations.size,
            totalSightings = state.criminalLocations.sumOf { it.totalSightings },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        // Error message
        state.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(text = error)
            }
        }
    }
}

@Composable
fun OpenStreetMapView(
    center: GeoPoint,
    zoomLevel: Double,
    criminalLocations: List<CriminalLocation>,
    onMarkerClick: (CriminalMapMarker) -> Unit,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current
    var currentMapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // Set initial position and zoom
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

                currentMapView = this
                onMapReady(this)
            }
        },
        update = { mapView ->
            // Clear existing markers (except compass and location)
            val overlaysToKeep = mapView.overlays.filter {
                it is CompassOverlay || it is MyLocationNewOverlay
            }
            mapView.overlays.clear()
            mapView.overlays.addAll(overlaysToKeep)

            // Add criminal markers
            criminalLocations.forEach { criminalLocation ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(criminalLocation.latitude, criminalLocation.longitude)
                    title = criminalLocation.criminalName
                    snippet = criminalLocation.address ?: "Location unknown"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    // Set icon based on danger level
                    icon = when (criminalLocation.dangerLevel?.uppercase()) {
                        "CRITICAL" -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply {
                            setTint(android.graphics.Color.RED)
                        }
                        "HIGH" -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply {
                            setTint(android.graphics.Color.parseColor("#FF6B00"))
                        }
                        "MEDIUM" -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply {
                            setTint(android.graphics.Color.parseColor("#FFA726"))
                        }
                        "LOW" -> context.getDrawable(android.R.drawable.ic_dialog_info)?.apply {
                            setTint(android.graphics.Color.parseColor("#FFC107"))
                        }
                        else -> context.getDrawable(android.R.drawable.ic_dialog_alert)
                    }

                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(
                            CriminalMapMarker(
                                criminalId = criminalLocation.criminalId,
                                name = criminalLocation.criminalName,
                                latitude = criminalLocation.latitude,
                                longitude = criminalLocation.longitude,
                                address = criminalLocation.address,
                                lastSeen = criminalLocation.lastSeen?.toDate(),
                                dangerLevel = criminalLocation.dangerLevel,
                                totalSightings = criminalLocation.totalSightings
                            )
                        )
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

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
fun CriminalInfoCard(
    marker: CriminalMapMarker,
    onDismiss: () -> Unit,
    onViewHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dangerColor = when (marker.dangerLevel?.uppercase()) {
        "CRITICAL" -> Color(0xFFB71C1C)
        "HIGH" -> Color(0xFFD32F2F)
        "MEDIUM" -> Color(0xFFF57C00)
        "LOW" -> Color(0xFFFFA726)
        else -> Color(0xFF757575)
    }

    val lastSeenText = marker.lastSeen?.let { date ->
        val formatter = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
        formatter.format(date)
    } ?: "Unknown"

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
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = marker.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = dangerColor
                        ) {
                            Text(
                                text = marker.dangerLevel ?: "UNKNOWN",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Last seen: $lastSeenText",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Sightings: ${marker.totalSightings}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("✕", fontSize = 20.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📍 ${marker.address ?: "Location unknown"}",
                fontSize = 13.sp,
                color = Color.DarkGray
            )

            Text(
                text = "Coordinates: ${"%.4f".format(marker.latitude)}, ${"%.4f".format(marker.longitude)}",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewHistory,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dangerColor
                    )
                ) {
                    Text("View History", color = Color.White, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = { /* Navigate */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Navigate", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MapStatsBar(
    criminalCount: Int,
    totalSightings: Int,
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
                label = "Criminals",
                value = criminalCount.toString(),
                color = Color(0xFFD32F2F)
            )

            StatItem(
                label = "Total Sightings",
                value = totalSightings.toString(),
                color = Color(0xFFF57C00)
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

// Data class for map markers
data class CriminalMapMarker(
    val criminalId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val lastSeen: Date?,
    val dangerLevel: String?,
    val totalSightings: Int
)