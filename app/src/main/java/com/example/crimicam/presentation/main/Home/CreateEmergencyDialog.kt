package com.example.crimicam.presentation.main.Home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

@Composable
fun CreateEmergencyReportDialog(
    onDismiss: () -> Unit,
    onReportCreated: (
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        type: String
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EMERGENCY") }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var address by remember { mutableStateOf("") }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            // Auto-fetch location after permission granted
            fetchCurrentLocation(
                context = context,
                onLocationReceived = { lat, lon, addr ->
                    latitude = lat
                    longitude = lon
                    address = addr
                    isLoadingLocation = false
                    locationError = null
                },
                onError = { error ->
                    locationError = error
                    isLoadingLocation = false
                }
            )
        }
    }

    // Auto-fetch location on dialog open if permission is granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && latitude == 0.0 && longitude == 0.0) {
            isLoadingLocation = true
            fetchCurrentLocation(
                context = context,
                onLocationReceived = { lat, lon, addr ->
                    latitude = lat
                    longitude = lon
                    address = addr
                    isLoadingLocation = false
                    locationError = null
                },
                onError = { error ->
                    locationError = error
                    isLoadingLocation = false
                }
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = Color(0xFFD32F2F)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Emergency Report",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Emergency Type Selection
                    Text(
                        text = "Emergency Type",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmergencyTypeChip(
                            label = "🚨 Emergency",
                            type = "EMERGENCY",
                            isSelected = selectedType == "EMERGENCY",
                            onClick = { selectedType = "EMERGENCY" },
                            modifier = Modifier.weight(1f)
                        )
                        EmergencyTypeChip(
                            label = "⚠️ Suspicious",
                            type = "SUSPICIOUS",
                            isSelected = selectedType == "SUSPICIOUS",
                            onClick = { selectedType = "SUSPICIOUS" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EmergencyTypeChip(
                            label = "🆘 Help Needed",
                            type = "HELP_NEEDED",
                            isSelected = selectedType == "HELP_NEEDED",
                            onClick = { selectedType = "HELP_NEEDED" },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("Brief description of the emergency") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Title, contentDescription = null)
                        }
                    )

                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Provide detailed information about the situation") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null
                            )
                        }
                    )

                    // Location Section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Location",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1976D2)
                                )

                                if (!hasLocationPermission) {
                                    TextButton(
                                        onClick = {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Enable Location")
                                    }
                                } else if (isLoadingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            isLoadingLocation = true
                                            fetchCurrentLocation(
                                                context = context,
                                                onLocationReceived = { lat, lon, addr ->
                                                    latitude = lat
                                                    longitude = lon
                                                    address = addr
                                                    isLoadingLocation = false
                                                    locationError = null
                                                },
                                                onError = { error ->
                                                    locationError = error
                                                    isLoadingLocation = false
                                                }
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh Location",
                                            tint = Color(0xFF1976D2)
                                        )
                                    }
                                }
                            }

                            if (locationError != null) {
                                Text(
                                    text = "⚠️ $locationError",
                                    fontSize = 12.sp,
                                    color = Color(0xFFD32F2F)
                                )
                            } else if (latitude != 0.0 && longitude != 0.0) {
                                Text(
                                    text = if (address.isNotBlank()) address else "$latitude, $longitude",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            } else if (!hasLocationPermission) {
                                Text(
                                    text = "Location permission required",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Info Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFF57C00),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your friends will be notified immediately about this emergency.",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Action Buttons
                Surface(
                    color = Color(0xFFF5F5F5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onReportCreated(
                                        title,
                                        description,
                                        latitude,
                                        longitude,
                                        address,
                                        selectedType
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = title.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send Report")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyTypeChip(
    label: String,
    type: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = when (type) {
                "EMERGENCY" -> Color(0xFFD32F2F)
                "SUSPICIOUS" -> Color(0xFFF57C00)
                "HELP_NEEDED" -> Color(0xFF1976D2)
                else -> MaterialTheme.colorScheme.primary
            },
            selectedLabelColor = Color.White
        )
    )
}

private fun fetchCurrentLocation(
    context: android.content.Context,
    onLocationReceived: (Double, Double, String) -> Unit,
    onError: (String) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    try {
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location.latitude, location.longitude, "")
            } else {
                onError("Unable to get current location")
            }
        }.addOnFailureListener { exception ->
            onError("Location error: ${exception.message}")
        }
    } catch (e: SecurityException) {
        onError("Location permission denied")
    }
}