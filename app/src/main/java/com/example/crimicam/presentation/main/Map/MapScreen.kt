package com.example.crimicam.presentation.main.Map

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crimicam.data.model.EmergencyReport
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
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════════════════
// MapScreen
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current

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

    // ── original state ──────────────────────────────────────────────────
    var selectedMarker        by remember { mutableStateOf<CriminalMapMarker?>(null) }
    var selectedReport        by remember { mutableStateOf<EmergencyReport?>(null) }
    var selectedFriendReport  by remember { mutableStateOf<EmergencyReport?>(null) }
    var mapView               by remember { mutableStateOf<MapView?>(null) }
    var userLocation          by remember { mutableStateOf<GeoPoint?>(null) }
    var locationAccuracy      by remember { mutableStateOf<Float?>(null) }

    var isSelectingDestination by remember { mutableStateOf(false) }
    var selectedDestination    by remember { mutableStateOf<GeoPoint?>(null) }

    var showReportsLayer       by remember { mutableStateOf(true) }
    var showFriendReportsLayer by remember { mutableStateOf(true) }

    // ── NEW: navigation-monitor state ───────────────────────────────────
    /**
     * True once the user has tapped "Navigate" — the monitor is running.
     * Set back to false when the destination is removed or friends are notified.
     */
    var navigationActive       by remember { mutableStateOf(false) }

    /**
     * 0, 1, 2, or 3.  Drives which warning card is visible.
     */
    var wrongWayCount          by remember { mutableStateOf(0) }

    /**
     * Flipped to true after the ViewModel has been told to notify friends.
     * Shows the "Friends Notified" confirmation card.
     */
    var friendsNotified        by remember { mutableStateOf(false) }

    /**
     * The monitor instance.  Kept in a mutable ref so the LocationCallback
     * (which is created once in a DisposableEffect) can always see the latest one.
     */
    val navigationMonitorRef   = remember { mutableStateOf<NavigationMonitor?>(null) }
    // END NEW ─────────────────────────────────────────────────────────────

    val hasLocationPermission = locationPermissionsState.allPermissionsGranted

    val initialCenter = remember(userLocation) {
        userLocation ?: GeoPoint(12.0667, 124.6000)
    }
    val initialZoom = 13.0

    // ── init osmdroid + load data ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName
        viewModel.refresh()
    }

    // ── location updates ──────────────────────────────────────────────────
    DisposableEffect(hasLocationPermission) {
        var locationCallback: LocationCallback? = null
        var fusedLocationClient: FusedLocationProviderClient? = null

        if (hasLocationPermission) {
            try {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val locationRequest = LocationRequest.create().apply {
                    priority          = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval          = 10000
                    fastestInterval   = 5000
                }

                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            val geoPoint = GeoPoint(location.latitude, location.longitude)
                            userLocation     = geoPoint
                            locationAccuracy = location.accuracy

                            // ── feed into NavigationMonitor if active ──────────
                            navigationMonitorRef.value?.update(geoPoint)
                        }
                    }
                }

                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        userLocation     = GeoPoint(it.latitude, it.longitude)
                        locationAccuracy = it.accuracy
                    }
                }

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

            } catch (e: SecurityException) {
                // handle
            }
        }

        onDispose {
            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        }
    }

    // ── helper: start the monitor (called when user taps Navigate) ────────
    fun startNavigationMonitor(destination: GeoPoint) {
        // reset UI state
        wrongWayCount     = 0
        friendsNotified   = false
        navigationActive  = true

        navigationMonitorRef.value = NavigationMonitor(
            destination = destination,
            onWrongDirectionWarning = { count ->
                // runs on whatever thread the location update came in on —
                // state writes are fine because Compose will post them.
                wrongWayCount = count
            },
            onNotifyFriends = {
                wrongWayCount = 3
                // tell the ViewModel to write to Firestore + send notifications
                userLocation?.let { loc ->
                    viewModel.notifyFriendsWrongWay(
                        destLat  = destination.latitude,
                        destLon  = destination.longitude,
                        userLat  = loc.latitude,
                        userLon  = loc.longitude
                    )
                }
                friendsNotified  = true
                navigationActive = false
            }
        )
    }

    // ── helper: stop / clean up the monitor ──────────────────────────────
    fun stopNavigationMonitor() {
        navigationMonitorRef.value?.reset()
        navigationMonitorRef.value = null
        navigationActive  = false
        wrongWayCount     = 0
        friendsNotified   = false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════════

    Box(modifier = Modifier.fillMaxSize()) {

        // ── map or loading indicator ──────────────────────────────────────
        if (state.isLoading && state.criminalLocations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            OpenStreetMapView(
                center                    = initialCenter,
                zoomLevel                 = initialZoom,
                criminalLocations         = state.criminalLocations,
                unresolvedReports         = if (showReportsLayer) state.unresolvedReports else emptyList(),
                friendReports             = if (showFriendReportsLayer) state.friendReports else emptyList(),
                selectedDestination       = selectedDestination,
                isSelectingDestination    = isSelectingDestination,
                userLocation              = userLocation,
                hasLocationPermission     = hasLocationPermission,
                onMarkerClick = { marker ->
                    if (!isSelectingDestination) {
                        selectedMarker = marker; selectedReport = null; selectedFriendReport = null
                    }
                },
                onReportMarkerClick = { report ->
                    if (!isSelectingDestination) {
                        selectedReport = report; selectedMarker = null; selectedFriendReport = null
                    }
                },
                onFriendReportClick = { report ->
                    if (!isSelectingDestination) {
                        selectedFriendReport = report; selectedMarker = null; selectedReport = null
                    }
                },
                onMapClick = { geoPoint ->
                    if (isSelectingDestination) selectedDestination = geoPoint
                },
                onMapReady = { map -> mapView = map }
            )
        }

        // ── stats bar ─────────────────────────────────────────────────────
        MapStatsBar(
            criminalCount          = state.criminalLocations.size,
            totalSightings         = state.criminalLocations.sumOf { it.totalSightings },
            unresolvedReportsCount = state.unresolvedReports.size,
            friendReportsCount     = state.friendReports.size,
            hasLocationPermission  = hasLocationPermission,
            isLocationEnabled      = userLocation != null,
            onRequestLocation      = { locationPermissionsState.launchMultiplePermissionRequest() },
            modifier               = Modifier.align(Alignment.TopStart).padding(16.dp)
        )

        // ── location indicator ────────────────────────────────────────────
        userLocation?.let { location ->
            if (!isSelectingDestination && selectedDestination == null) {
                LocationIndicator(
                    location = location,
                    accuracy = locationAccuracy,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).padding(top = 100.dp)
                )
            }
        }

        // ── right-side control buttons ────────────────────────────────────
        Column(
            modifier           = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(icon = Icons.Default.Add,    onClick = { mapView?.controller?.zoomIn() })
            MapControlButton(icon = Icons.Default.Remove, onClick = { mapView?.controller?.zoomOut() })
            MapControlButton(
                icon = Icons.Default.MyLocation,
                onClick = {
                    if (hasLocationPermission) {
                        userLocation?.let { loc ->
                            mapView?.controller?.animateTo(loc)
                            mapView?.controller?.setZoom(17.0)
                        }
                    } else {
                        locationPermissionsState.launchMultiplePermissionRequest()
                    }
                }
            )
            MapControlButton(icon = Icons.Default.Refresh, onClick = { viewModel.refresh() })

            MapControlButton(
                icon          = Icons.Default.Notifications,
                onClick       = { showReportsLayer = !showReportsLayer },
                containerColor = if (showReportsLayer) Color(0xFFEF5350) else Color.White,
                contentColor   = if (showReportsLayer) Color.White else Color(0xFFEF5350)
            )
            MapControlButton(
                icon          = Icons.Default.Group,
                onClick       = { showFriendReportsLayer = !showFriendReportsLayer },
                containerColor = if (showFriendReportsLayer) Color(0xFF2196F3) else Color.White,
                contentColor   = if (showFriendReportsLayer) Color.White else Color(0xFF2196F3)
            )

            if (!hasLocationPermission) {
                MapControlButton(
                    icon           = Icons.Default.LocationDisabled,
                    onClick        = { locationPermissionsState.launchMultiplePermissionRequest() },
                    containerColor = Color.Red.copy(alpha = 0.8f),
                    contentColor   = Color.White
                )
            }
        }

        // ── "Add Destination" FAB ─────────────────────────────────────────
        if (!isSelectingDestination && selectedDestination == null) {
            FloatingActionButton(
                onClick = { isSelectingDestination = true },
                modifier        = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor  = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier            = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AddLocation, contentDescription = "Add Destination")
                    Text("Add Destination")
                }
            }
        }

        // ── "Tap on map…" instruction card ───────────────────────────────
        if (isSelectingDestination) {
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Tap on map to set destination", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = { isSelectingDestination = false; selectedDestination = null }) {
                        Text("Cancel")
                    }
                }
            }
        }

        // ── destination card / confirm FAB ────────────────────────────────
        selectedDestination?.let { destination ->
            if (!isSelectingDestination) {
                DestinationCard(
                    destination = destination,
                    onConfirm   = {
                        // ── START the navigation monitor here ────────────────
                        startNavigationMonitor(destination)
                    },
                    onRemove = {
                        // ── STOP the monitor and clear everything ───────────
                        stopNavigationMonitor()
                        selectedDestination = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            } else {
                FloatingActionButton(
                    onClick        = { isSelectingDestination = false },
                    modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = Color(0xFF4CAF50)
                ) {
                    Row(
                        modifier            = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White)
                        Text("Confirm", color = Color.White)
                    }
                }
            }
        }

        // ── NEW: Wrong-Way Warning card (count == 1 or 2) ──────────────────
        if (navigationActive && wrongWayCount in 1..2 && !friendsNotified) {
            WrongWayWarningCard(
                count    = wrongWayCount,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }

        // ── NEW: Friends-Notified confirmation card (count == 3) ───────────
        if (friendsNotified) {
            FriendsNotifiedCard(
                onDismiss = {
                    // user acknowledges — clean up everything
                    stopNavigationMonitor()
                    selectedDestination = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
        // END NEW ───────────────────────────────────────────────────────────

        // ── criminal info card ────────────────────────────────────────────
        if (!isSelectingDestination && selectedDestination == null && selectedReport == null && selectedFriendReport == null) {
            selectedMarker?.let { marker ->
                CriminalInfoCard(
                    marker        = marker,
                    onDismiss     = { selectedMarker = null },
                    onViewHistory = { viewModel.loadLocationHistory(marker.criminalId) },
                    modifier      = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }

        // ── emergency report info card ────────────────────────────────────
        if (!isSelectingDestination && selectedDestination == null && selectedMarker == null && selectedFriendReport == null) {
            selectedReport?.let { report ->
                EmergencyReportInfoCard(
                    report    = report,
                    onDismiss = { selectedReport = null },
                    onResolve = { viewModel.updateReportStatus(report.id, "RESOLVED"); selectedReport = null },
                    modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }

        // ── friend report info card ───────────────────────────────────────
        if (!isSelectingDestination && selectedDestination == null && selectedMarker == null && selectedReport == null) {
            selectedFriendReport?.let { report ->
                FriendReportInfoCard(
                    report    = report,
                    onDismiss = { selectedFriendReport = null },
                    onResolve = { viewModel.updateReportStatus(report.id, "RESOLVED"); selectedFriendReport = null },
                    modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }

        // ── permission rationale ──────────────────────────────────────────
        if (!hasLocationPermission && locationPermissionsState.shouldShowRationale) {
            PermissionRationaleDialog(
                onDismiss         = { },
                onRequestPermission = { locationPermissionsState.launchMultiplePermissionRequest() },
                onGoToSettings    = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // ── error snackbar ────────────────────────────────────────────────
        state.error?.let { error ->
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                Text(text = error)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// NEW COMPOSABLES  –  WrongWayWarningCard  &  FriendsNotifiedCard
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Shown when wrongWayCount is 1 or 2.
 * count == 1  →  "You might be going the wrong way!"
 * count == 2  →  "You are still going the wrong way! One more and friends will be notified."
 */
@Composable
fun WrongWayWarningCard(count: Int, modifier: Modifier = Modifier) {
    val bgColor = if (count == 1) Color(0xFFFF9800) else Color(0xFFEF5350)   // orange → red

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // pulsing warning icon
            Icon(
                Icons.Default.Warning,
                contentDescription = "Wrong way warning",
                tint               = Color.White,
                modifier           = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text         = if (count == 1) "⚠️ You might be going the wrong way!" else "⚠️ Still going the wrong way!",
                    fontSize     = 16.sp,
                    fontWeight   = FontWeight.Bold,
                    color        = Color.White
                )
                Text(
                    text     = if (count == 1)
                        "Keep going and we'll check again."
                    else
                        "One more check and your friends will be notified automatically.",
                    fontSize = 13.sp,
                    color    = Color.White.copy(alpha = 0.9f)
                )
            }

            // small badge showing count / 3
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.25f)
            ) {
                Text(
                    text     = "$count/3",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color    = Color.White
                )
            }
        }
    }
}

/**
 * Shown once the monitor has fired onNotifyFriends (count reached 3).
 */
@Composable
fun FriendsNotifiedCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))   // dark red
    ) {
        Column(
            modifier  = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = "Friends notified",
                            tint               = Color.White,
                            modifier           = Modifier.size(28.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text       = "🚨 Friends Notified!",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                    Text(
                        text     = "You have been moving away from your destination. All your friends have been notified of your location.",
                        fontSize = 13.sp,
                        color    = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // dismiss button
            Button(
                onClick        = onDismiss,
                modifier       = Modifier.fillMaxWidth(),
                colors         = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("OK, I understand", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OpenStreetMapView   (unchanged from original)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun OpenStreetMapView(
    center: GeoPoint,
    zoomLevel: Double,
    criminalLocations: List<CriminalLocation>,
    unresolvedReports: List<EmergencyReport>,
    friendReports: List<EmergencyReport>,
    selectedDestination: GeoPoint?,
    isSelectingDestination: Boolean,
    userLocation: GeoPoint?,
    hasLocationPermission: Boolean,
    onMarkerClick: (CriminalMapMarker) -> Unit,
    onReportMarkerClick: (EmergencyReport) -> Unit,
    onFriendReportClick: (EmergencyReport) -> Unit,
    onMapClick: (GeoPoint) -> Unit,
    onMapReady: (MapView) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                controller.setZoom(zoomLevel)
                controller.setCenter(center)

                val compassOverlay = CompassOverlay(
                    context,
                    InternalCompassOrientationProvider(context),
                    this
                ).apply { enableCompass() }
                overlays.add(compassOverlay)

                onMapReady(this)
            }
        },
        update = { mapView ->
            val overlaysToKeep = mapView.overlays.filterIsInstance<CompassOverlay>()
            mapView.overlays.clear()
            mapView.overlays.addAll(overlaysToKeep)

            // user location marker
            userLocation?.let { location ->
                val userMarker = Marker(mapView).apply {
                    position = location
                    title    = "You are here"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = context.getDrawable(android.R.drawable.presence_online)?.apply {
                        setTint(android.graphics.Color.BLUE)
                        setBounds(0, 0, 60, 60)
                    }
                }
                mapView.overlays.add(userMarker)
            }

            // friend report markers
            friendReports.forEach { report ->
                report.location?.let { location ->
                    val friendMarker = Marker(mapView).apply {
                        position = GeoPoint(location.latitude, location.longitude)
                        title    = "👤 ${report.userName}"
                        snippet  = report.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = context.getDrawable(android.R.drawable.ic_menu_myplaces)?.apply {
                            setTint(android.graphics.Color.parseColor("#2196F3"))
                            setBounds(0, 0, 80, 80)
                        }
                        val starOverlay = Marker(mapView).apply {
                            position = GeoPoint(location.latitude + 0.0002, location.longitude + 0.0002)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = context.getDrawable(android.R.drawable.star_on)?.apply {
                                setTint(android.graphics.Color.parseColor("#FFD700"))
                                setBounds(0, 0, 30, 30)
                            }
                        }
                        mapView.overlays.add(starOverlay)
                        setOnMarkerClickListener { _, _ ->
                            if (!isSelectingDestination) onFriendReportClick(report)
                            true
                        }
                    }
                    mapView.overlays.add(friendMarker)
                }
            }

            // unresolved report markers
            unresolvedReports.forEach { report ->
                report.location?.let { location ->
                    val reportMarker = Marker(mapView).apply {
                        position = GeoPoint(location.latitude, location.longitude)
                        title    = report.title
                        snippet  = report.userName
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = when (report.type) {
                            "EMERGENCY"   -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply { setTint(android.graphics.Color.parseColor("#EF5350")); setBounds(0,0,80,80) }
                            "SUSPICIOUS"  -> context.getDrawable(android.R.drawable.ic_menu_info_details)?.apply { setTint(android.graphics.Color.parseColor("#FF9800")); setBounds(0,0,80,80) }
                            "HELP_NEEDED" -> context.getDrawable(android.R.drawable.ic_menu_help)?.apply { setTint(android.graphics.Color.parseColor("#42A5F5")); setBounds(0,0,80,80) }
                            else          -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply { setTint(android.graphics.Color.GRAY); setBounds(0,0,80,80) }
                        }
                        setOnMarkerClickListener { _, _ ->
                            if (!isSelectingDestination) onReportMarkerClick(report)
                            true
                        }
                    }
                    mapView.overlays.add(reportMarker)
                }
            }

            // criminal markers
            criminalLocations.forEach { criminalLocation ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(criminalLocation.latitude, criminalLocation.longitude)
                    title    = criminalLocation.criminalName
                    snippet  = criminalLocation.address ?: "Location unknown"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = when (criminalLocation.dangerLevel?.uppercase()) {
                        "CRITICAL" -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply { setTint(android.graphics.Color.RED) }
                        "HIGH"     -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply { setTint(android.graphics.Color.parseColor("#FF6B00")) }
                        "MEDIUM"   -> context.getDrawable(android.R.drawable.ic_dialog_alert)?.apply { setTint(android.graphics.Color.parseColor("#FFA726")) }
                        "LOW"      -> context.getDrawable(android.R.drawable.ic_dialog_info)?.apply  { setTint(android.graphics.Color.parseColor("#FFC107")) }
                        else       -> context.getDrawable(android.R.drawable.ic_dialog_alert)
                    }
                    setOnMarkerClickListener { _, _ ->
                        if (!isSelectingDestination) {
                            onMarkerClick(CriminalMapMarker(
                                criminalId     = criminalLocation.criminalId,
                                name           = criminalLocation.criminalName,
                                latitude       = criminalLocation.latitude,
                                longitude      = criminalLocation.longitude,
                                address        = criminalLocation.address,
                                lastSeen       = criminalLocation.lastSeen?.toDate(),
                                dangerLevel    = criminalLocation.dangerLevel,
                                totalSightings = criminalLocation.totalSightings
                            ))
                        }
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            // destination marker
            selectedDestination?.let { destination ->
                val destinationMarker = Marker(mapView).apply {
                    position = destination
                    title    = "Destination"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)?.apply {
                        setTint(android.graphics.Color.BLUE)
                    }
                }
                mapView.overlays.add(destinationMarker)
            }

            // tap listener for destination selection
            mapView.setOnTouchListener { _, event ->
                if (isSelectingDestination && event.action == android.view.MotionEvent.ACTION_UP) {
                    val projection = mapView.projection
                    val geoPoint   = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                    onMapClick(geoPoint)
                }
                false
            }

            mapView.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// All remaining composables are EXACTLY as they were in the original file.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FriendReportInfoCard(
    report: EmergencyReport,
    onDismiss: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFF2196F3).copy(alpha = 0.2f), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Group, contentDescription = "Friend", tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Friend's Report", fontSize = 12.sp, color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                        Text(text = report.userName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.Gray) }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = report.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 2)
            Spacer(modifier = Modifier.height(8.dp))

            if (report.description.isNotBlank()) {
                Text(text = report.description, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = report.address.ifBlank { "Location unknown" }, fontSize = 13.sp, color = Color.DarkGray)
            }
            report.location?.let { location ->
                Text(text = "Coordinates: ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}", fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Reported: ${formatFriendReportTime(report.timestamp)}", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!report.isResolved) {
                    Button(onClick = onResolve, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))) {
                        Icon(Icons.Default.Check, contentDescription = "Mark Safe", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Safe", fontSize = 13.sp, color = Color.White)
                    }
                }
                report.location?.let { location ->
                    Button(
                        onClick = {
                            val uri    = Uri.parse("google.navigation:q=${location.latitude},${location.longitude}")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                            try { context.startActivity(intent) } catch (e: android.content.ActivityNotFoundException) {
                                val browserUri    = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${location.latitude},${location.longitude}")
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = "Navigate", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Navigate", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatFriendReportTime(timestamp: com.google.firebase.Timestamp): String {
    val date    = timestamp.toDate()
    val now     = Date()
    val diff    = now.time - date.time
    val minutes = diff / (1000 * 60)
    val hours   = diff / (1000 * 60 * 60)
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "$minutes minutes ago"
        hours   < 24 -> "$hours hours ago"
        else         -> SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(date)
    }
}

@Composable
fun MapStatsBar(
    criminalCount: Int, totalSightings: Int, unresolvedReportsCount: Int, friendReportsCount: Int,
    hasLocationPermission: Boolean, isLocationEnabled: Boolean, onRequestLocation: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatItem(label = "Criminals", value = criminalCount.toString(),         color = Color(0xFFD32F2F))
                StatItem(label = "Sightings", value = totalSightings.toString(),        color = Color(0xFFF57C00))
                StatItem(label = "Reports",   value = unresolvedReportsCount.toString(), color = Color(0xFFEF5350))
                StatItem(label = "Friends",   value = friendReportsCount.toString(),    color = Color(0xFF2196F3))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(
                    when {
                        !hasLocationPermission -> Color.Red
                        isLocationEnabled      -> Color(0xFF4CAF50)
                        else                   -> Color(0xFFFF9800)
                    }
                ))
                Text(
                    text = when {
                        !hasLocationPermission -> "Location permission required"
                        isLocationEnabled      -> "Location active"
                        else                   -> "Getting location..."
                    },
                    fontSize = 12.sp, color = Color.Gray
                )
                if (!hasLocationPermission) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onRequestLocation, modifier = Modifier.padding(start = 8.dp)) { Text("Enable", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleDialog(onDismiss: () -> Unit, onRequestPermission: () -> Unit, onGoToSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(text = "Location Permission Required", fontWeight = FontWeight.Bold) },
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
            Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Grant Permission") }
        },
        dismissButton = { TextButton(onClick = onGoToSettings) { Text("Open Settings") } }
    )
}

@Composable
fun EmergencyReportInfoCard(report: EmergencyReport, onDismiss: () -> Unit, onResolve: () -> Unit, modifier: Modifier = Modifier) {
    val typeColor = when (report.type) { "EMERGENCY" -> Color(0xFFEF5350); "SUSPICIOUS" -> Color(0xFFFF9800); "HELP_NEEDED" -> Color(0xFF42A5F5); else -> Color(0xFF9E9E9E) }
    val typeEmoji = when (report.type) { "EMERGENCY" -> "🚨"; "SUSPICIOUS" -> "⚠️"; "HELP_NEEDED" -> "🆘"; else -> "📢" }
    val lastSeenText = try { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(report.timestamp.toDate()) } catch (e: Exception) { "Unknown" }

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = typeEmoji, fontSize = 24.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(text = report.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 2)
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = typeColor) {
                                Text(text = report.type.replace("_", " "), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = report.userName, fontSize = 12.sp, color = Color.Gray)
                    }
                    Text(text = "Reported: $lastSeenText", fontSize = 11.sp, color = Color.Gray)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (report.description.isNotBlank()) { Text(text = report.description, fontSize = 13.sp, color = Color.DarkGray, maxLines = 3); Spacer(modifier = Modifier.height(8.dp)) }
            Text(text = "📍 ${report.address.ifBlank { "Location unknown" }}", fontSize = 13.sp, color = Color.DarkGray)
            report.location?.let { location -> Text(text = "Coordinates: ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}", fontSize = 11.sp, color = Color.Gray) }

            if (!report.isResolved) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResolve, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Resolved", color = Color.White, fontSize = 13.sp)
                    }
                    report.location?.let {
                        OutlinedButton(onClick = { /* navigate */ }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = Color(0xFF66BB6A).copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF66BB6A), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "This report has been resolved", fontSize = 13.sp, color = Color(0xFF66BB6A), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun LocationIndicator(location: GeoPoint, accuracy: Float?, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2196F3)).padding(8.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocationOn, contentDescription = "Your Location", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(text = "Your Location", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}", fontSize = 12.sp, color = Color.Gray)
                accuracy?.let { Text(text = "Accuracy: ${"%.1f".format(it)}m", fontSize = 11.sp, color = Color(0xFF4CAF50)) }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Column {
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun DestinationCard(destination: GeoPoint, onConfirm: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(28.dp))
                    Column {
                        Text(text = "Destination Set", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(text = "Lat: ${"%.4f".format(destination.latitude)}, Lon: ${"%.4f".format(destination.longitude)}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // launch Google Maps navigation
                        val uri    = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }
                        try { context.startActivity(intent) } catch (e: android.content.ActivityNotFoundException) {
                            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                        }
                        // start monitor AFTER Google Maps opens
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate", color = Color.White, fontSize = 13.sp)
                }

                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MapControlButton(icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, containerColor: Color = Color.White, contentColor: Color = MaterialTheme.colorScheme.primary) {
    FloatingActionButton(
        onClick        = onClick,
        modifier       = Modifier.size(48.dp),
        containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
        contentColor   = if (enabled) contentColor   else contentColor.copy(alpha = 0.5f)
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Composable
fun CriminalInfoCard(marker: CriminalMapMarker, onDismiss: () -> Unit, onViewHistory: () -> Unit, modifier: Modifier = Modifier) {
    val dangerColor = when (marker.dangerLevel?.uppercase()) { "CRITICAL" -> Color(0xFFB71C1C); "HIGH" -> Color(0xFFD32F2F); "MEDIUM" -> Color(0xFFF57C00); "LOW" -> Color(0xFFFFA726); else -> Color(0xFF757575) }
    val lastSeenText = marker.lastSeen?.let { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(it) } ?: "Unknown"

    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = marker.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = dangerColor) {
                            Text(text = marker.dangerLevel ?: "UNKNOWN", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Last seen: $lastSeenText", fontSize = 12.sp, color = Color.Gray)
                    Text(text = "Sightings: ${marker.totalSightings}", fontSize = 12.sp, color = Color.Gray)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "📍 ${marker.address ?: "Location unknown"}", fontSize = 13.sp, color = Color.DarkGray)
            Text(text = "Coordinates: ${"%.4f".format(marker.latitude)}, ${"%.4f".format(marker.longitude)}", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onViewHistory, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = dangerColor)) {
                    Text("View History", color = Color.White, fontSize = 13.sp)
                }
                OutlinedButton(onClick = { /* Navigate */ }, modifier = Modifier.weight(1f)) {
                    Text("Navigate", fontSize = 13.sp)
                }
            }
        }
    }
}

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