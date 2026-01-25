package com.example.crimicam.presentation.main.Map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crimicam.data.service.CriminalLocation
import com.google.android.gms.location.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current

    // Request location permissions
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(context)
    )

    val state by viewModel.state.collectAsState()
    var selectedMarker by remember { mutableStateOf<CriminalMapMarker?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var isRequestingLocation by remember { mutableStateOf(false) }

    // Destination mode states
    var isSelectingDestination by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }

    // Check if location permissions are granted
    val hasLocationPermission = locationPermissionsState.allPermissionsGranted

    // Default center point (Calbayog City) with proper zoom
    val initialCenter = userLocation ?: GeoPoint(12.0667, 124.6000)
    val initialZoom = 13.0 // City-level zoom (13-15 is good for city view)

    // Initialize OpenStreetMap configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.loadCriminalLocations()
    }

    // Get user location when permission is granted
    LaunchedEffect(hasLocationPermission, isRequestingLocation) {
        if (hasLocationPermission && isRequestingLocation) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val locationRequest = LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 5000
                    fastestInterval = 2000
                    maxWaitTime = 10000
                }

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            userLocation = GeoPoint(location.latitude, location.longitude)
                            locationAccuracy = location.accuracy
                            isLocationEnabled = true
                            isRequestingLocation = false
                        }
                    }

                    override fun onLocationAvailability(availability: LocationAvailability) {
                        if (!availability.isLocationAvailable) {
                            isLocationEnabled = false
                        }
                    }
                }

                // Get last known location first
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        userLocation = GeoPoint(it.latitude, it.longitude)
                        locationAccuracy = it.accuracy
                        isLocationEnabled = true
                        isRequestingLocation = false
                    }
                }.addOnFailureListener {
                    isLocationEnabled = false
                    isRequestingLocation = false
                }

                // Request location updates
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )

            } catch (e: SecurityException) {
                isLocationEnabled = false
                isRequestingLocation = false
            } catch (e: Exception) {
                isLocationEnabled = false
                isRequestingLocation = false
            }
        } else if (!hasLocationPermission) {
            userLocation = GeoPoint(12.0667, 124.6000)
            isLocationEnabled = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Permission Dialog
        if (!hasLocationPermission && locationPermissionsState.shouldShowRationale) {
            PermissionRationaleDialog(
                onDismiss = { /* Do nothing */ },
                onRequestPermission = {
                    locationPermissionsState.launchMultiplePermissionRequest()
                    isRequestingLocation = true
                },
                onGoToSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

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
                center = initialCenter,
                zoomLevel = initialZoom,
                criminalLocations = state.criminalLocations,
                selectedDestination = selectedDestination,
                isSelectingDestination = isSelectingDestination,
                isLocationEnabled = isLocationEnabled,
                hasLocationPermission = hasLocationPermission,
                onMarkerClick = { marker ->
                    if (!isSelectingDestination) {
                        selectedMarker = marker
                    }
                },
                onMapClick = { geoPoint ->
                    if (isSelectingDestination) {
                        selectedDestination = geoPoint
                    }
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
                onClick = {
                    if (hasLocationPermission) {
                        isRequestingLocation = true
                        userLocation?.let { location ->
                            mapView?.controller?.animateTo(location)
                            mapView?.controller?.setZoom(15.0)
                        }
                    } else {
                        locationPermissionsState.launchMultiplePermissionRequest()
                        isRequestingLocation = true
                    }
                },
                enabled = hasLocationPermission || !locationPermissionsState.shouldShowRationale
            )
            MapControlButton(
                icon = Icons.Default.Refresh,
                onClick = { viewModel.loadCriminalLocations() }
            )

            // Location Permission Button
            if (!hasLocationPermission) {
                MapControlButton(
                    icon = Icons.Default.LocationDisabled,
                    onClick = {
                        locationPermissionsState.launchMultiplePermissionRequest()
                        isRequestingLocation = true
                    },
                    containerColor = Color.Red.copy(alpha = 0.8f),
                    contentColor = Color.White
                )
            }
        }

        // Location Indicator (only shows if location is enabled)
        if (isLocationEnabled && userLocation != null && !isSelectingDestination && selectedDestination == null) {
            LocationIndicator(
                location = userLocation!!,
                accuracy = locationAccuracy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .padding(top = 80.dp) // Adjusted to not overlap with stats
            )
        }

        // Location Loading Indicator
        if (isRequestingLocation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Getting your location...",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Add Destination Button
        if (!isSelectingDestination && selectedDestination == null) {
            FloatingActionButton(
                onClick = { isSelectingDestination = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AddLocation, contentDescription = "Add Destination")
                    Text("Add Destination")
                }
            }
        }

        // Destination Selection Banner
        if (isSelectingDestination) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tap on map to set destination",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    TextButton(
                        onClick = {
                            isSelectingDestination = false
                            selectedDestination = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }

        // Destination Confirmation Card
        selectedDestination?.let { destination ->
            if (!isSelectingDestination) {
                DestinationCard(
                    destination = destination,
                    onConfirm = {
                        // TODO: Handle navigation to destination
                        // You can add navigation logic here
                    },
                    onRemove = {
                        selectedDestination = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            } else {
                // Show confirm button when destination is selected in selection mode
                FloatingActionButton(
                    onClick = { isSelectingDestination = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = Color(0xFF4CAF50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White)
                        Text("Confirm", color = Color.White)
                    }
                }
            }
        }

        // Bottom Info Card (Criminal info)
        if (!isSelectingDestination && selectedDestination == null) {
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
        }

        // Top Stats Bar
        MapStatsBar(
            criminalCount = state.criminalLocations.size,
            totalSightings = state.criminalLocations.sumOf { it.totalSightings },
            hasLocationPermission = hasLocationPermission,
            isLocationEnabled = isLocationEnabled,
            onRequestLocation = {
                if (!hasLocationPermission) {
                    locationPermissionsState.launchMultiplePermissionRequest()
                    isRequestingLocation = true
                } else {
                    isRequestingLocation = true
                }
            },
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
    selectedDestination: GeoPoint?,
    isSelectingDestination: Boolean,
    isLocationEnabled: Boolean,
    hasLocationPermission: Boolean,
    onMarkerClick: (CriminalMapMarker) -> Unit,
    onMapClick: (GeoPoint) -> Unit,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current
    var currentMapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // Set proper zoom levels
                minZoomLevel = 3.0
                maxZoomLevel = 20.0

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

                // Add location overlay if location is enabled and permission is granted
                if (isLocationEnabled && hasLocationPermission) {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)
                }

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

            // Update location overlay if location is enabled and permission is granted
            if (isLocationEnabled && hasLocationPermission) {
                val existingLocationOverlay = mapView.overlays.firstOrNull { it is MyLocationNewOverlay }
                if (existingLocationOverlay == null) {
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                    locationOverlay.enableMyLocation()
                    mapView.overlays.add(locationOverlay)
                }
            } else {
                // Remove location overlay if location is disabled or no permission
                mapView.overlays.removeIf { it is MyLocationNewOverlay }
            }

            // Add tap listener for destination selection
            mapView.setOnTouchListener { _, event ->
                if (isSelectingDestination && event.action == android.view.MotionEvent.ACTION_UP) {
                    val projection = mapView.projection
                    val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                    onMapClick(geoPoint)
                }
                false
            }

            // Add destination marker if selected
            selectedDestination?.let { destination ->
                val destinationMarker = Marker(mapView).apply {
                    position = destination
                    title = "Destination"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                        setTint(android.graphics.Color.BLUE)
                    }
                }
                mapView.overlays.add(destinationMarker)
            }

            // Add criminal markers
            criminalLocations.forEach { criminalLocation ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(criminalLocation.latitude, criminalLocation.longitude)
                    title = criminalLocation.criminalName
                    snippet = criminalLocation.address ?: "Location unknown"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    // Set icon based on danger level
                    icon = when (criminalLocation.dangerLevel?.uppercase()) {
                        "CRITICAL" -> context.getDrawable(android.R.drawable.ic_dialog_alert)
                            ?.apply {
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
                        if (!isSelectingDestination) {
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
                        }
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
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Location Permission Required",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("This app needs location permission to:")
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Show your current location on the map")
                Text("• Navigate to destinations")
                Text("• Provide accurate crime location data")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Please grant location permission to use all features.")
            }
        },
        confirmButton = {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Open Settings")
            }
        }
    )
}

@Composable
fun LocationIndicator(
    location: GeoPoint,
    accuracy: Float?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Location icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2196F3))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Your Location",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Location details
            Column {
                Text(
                    text = "Your Location",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                accuracy?.let {
                    Text(
                        text = "Accuracy: ${"%.1f".format(it)}m",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun DestinationCard(
    destination: GeoPoint,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Destination Set",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Text(
                            text = "Lat: ${"%.4f".format(destination.latitude)}, Lon: ${"%.4f".format(destination.longitude)}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Open Google Maps with navigation to destination
                        val uri = Uri.parse(
                            "google.navigation:q=${destination.latitude},${destination.longitude}"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }

                        try {
                            context.startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            // If Google Maps not installed, open in browser
                            val browserUri = Uri.parse(
                                "https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}"
                            )
                            val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)
                            context.startActivity(browserIntent)
                        }

                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate", color = Color.White, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", fontSize = 13.sp)
                }
            }
        }
    }
}
@Composable
fun MapControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = Color.White,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    FloatingActionButton(
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        modifier = Modifier.size(48.dp),
        containerColor = containerColor,
        contentColor = contentColor
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
    hasLocationPermission: Boolean,
    isLocationEnabled: Boolean,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
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

            // Location status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !hasLocationPermission -> Color.Red
                                isLocationEnabled -> Color(0xFF4CAF50)
                                else -> Color(0xFFFF9800)
                            }
                        )
                )
                Text(
                    text = when {
                        !hasLocationPermission -> "Location permission required"
                        isLocationEnabled -> "Location active"
                        else -> "Getting location..."
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                if (!hasLocationPermission) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onRequestLocation,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                }
            }
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