package com.example.crimicam.presentation.main.Home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.crimicam.EmergencyReportNotificationManager
import com.example.crimicam.R
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel,
    emergencyViewModel: EmergencyReportViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val homeState by homeViewModel.homeState.collectAsState()
    val reportState by emergencyViewModel.reportState.collectAsState()
    val createReportState by emergencyViewModel.createReportState.collectAsState()

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // Initialize ringtone player
    LaunchedEffect(Unit) {
        homeViewModel.initializeRingtonePlayer(context)
    }

    LaunchedEffect(Unit) {
        // Check if there are new emergency reports from notifications
        if (EmergencyReportNotificationManager.hasNewEmergencyReport) {
            delay(1000) // Small delay to ensure UI is ready
            showNotificationsDialog = true
            EmergencyReportNotificationManager.hasNewEmergencyReport = false
            emergencyViewModel.loadFriendReports() // Refresh data
        }
    }

    // Start realtime updates
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        homeViewModel.startRealtimeUpdates()
    }

    // Load friend reports and notifications
    LaunchedEffect(Unit) {
        emergencyViewModel.loadFriendReports()
        emergencyViewModel.loadNotifications()

        // Subscribe to user-specific topic for emergency reports
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            FirebaseMessaging.getInstance().subscribeToTopic("emergency_user_${user.uid}")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Timber.d("✅ Subscribed to emergency_user_${user.uid} topic")
                    }
                }
        }
    }

    // Reset new activity count
    LaunchedEffect(Unit) {
        homeViewModel.resetNewActivityCount()
    }

    // Handle create report success
    LaunchedEffect(createReportState) {
        if (createReportState is CreateReportState.Success) {
            showEmergencyDialog = false
            emergencyViewModel.resetCreateReportState()
        }
    }

    // Handle notification from MainActivity
    LaunchedEffect(Unit) {
        // Check if we should open emergency reports from notification
        // This is handled by MainActivity.shouldOpenEmergencyReports flag
    }

    val newActivityCount = homeState.newActivityCount
    var showNewActivityBadge by remember { mutableStateOf(false) }

    LaunchedEffect(newActivityCount) {
        if (newActivityCount > 0) {
            showNewActivityBadge = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header with Notification Icon
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .height(6.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Crimicam",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    }

                    // Notification Icon with Badge
                    Box {
                        IconButton(
                            onClick = {
                                showNotificationsDialog = true
                                emergencyViewModel.clearUnreadCount()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Emergency Reports",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Unread badge (shows emergency report notifications)
                        if (reportState.unreadCount > 0) {
                            Badge(
                                containerColor = Color.Red,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text(
                                    text = "${reportState.unreadCount}",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Camera Feature Card
                FeatureCard(
                    title = "Camera",
                    description = "Access camera to capture intruders!",
                    gradientColors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)),
                    imageRes = R.drawable.camera,
                    onClick = {
                        navController.navigate("camera")
                    }
                )

                // Monitor Feature Card
                FeatureCard(
                    title = "Monitor",
                    description = "Monitor captured media and surveillance",
                    gradientColors = listOf(Color(0xFF0083B0), Color(0xFF00B4DB)),
                    imageRes = R.drawable.monitor,
                    onClick = {
                        navController.navigate("monitor")
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Recent Activity Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        if (showNewActivityBadge && newActivityCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = Color.Red,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        showNewActivityBadge = false
                                        homeViewModel.resetNewActivityCount()
                                    }
                            ) {
                                Text(
                                    text = "$newActivityCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { homeViewModel.refreshActivities() },
                            modifier = Modifier.size(36.dp),
                            enabled = !homeState.isLoadingActivities
                        ) {
                            if (homeState.isLoadingActivities) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Activities",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!homeState.isRealtimeActive && !homeState.isLoadingActivities) {
                    Text(
                        text = "🔄 Realtime updates paused",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Recent Activity Content
                when {
                    homeState.isLoadingActivities && homeState.recentActivities.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Loading activities...",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    homeState.activitiesError != null -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "⚠️ Error Loading Activities",
                                    color = Color(0xFFC62828),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = homeState.activitiesError ?: "Unknown error",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = { homeViewModel.startRealtimeUpdates() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Text("Retry Connection")
                                }
                            }
                        }
                    }

                    homeState.recentActivities.isEmpty() -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "📭",
                                    fontSize = 48.sp
                                )
                                Text(
                                    text = "No Recent Activity",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Start using the camera to capture faces",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                if (!homeState.isRealtimeActive) {
                                    Button(
                                        onClick = { homeViewModel.startRealtimeUpdates() },
                                        modifier = Modifier.padding(top = 12.dp)
                                    ) {
                                        Text("Enable Realtime Updates")
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                homeState.recentActivities.forEachIndexed { index, activity ->
                                    RecentActivityCard(
                                        activity = activity,
                                        showDivider = index < homeState.recentActivities.lastIndex,
                                        onClick = {
                                            navController.navigate("activity_detail/${activity.id}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Emergency Report Button
        FloatingActionButton(
            onClick = { showEmergencyDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFFD32F2F),
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergency Report",
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "EMERGENCY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Emergency Report Dialog
    if (showEmergencyDialog) {
        CreateEmergencyReportDialog(
            onDismiss = {
                showEmergencyDialog = false
                emergencyViewModel.resetCreateReportState()
            },
            onReportCreated = { title, description, lat, lon, address, type ->
                emergencyViewModel.createReport(title, description, lat, lon, address, type)
            }
        )
    }

    // Friend Reports Dialog (shows emergency reports from friends)
    if (showNotificationsDialog) {
        FriendReportsDialog(
            friendReports = reportState.friendReports,
            isLoading = reportState.isLoading,
            error = reportState.error,
            onDismiss = { showNotificationsDialog = false },
            onRefresh = {
                emergencyViewModel.loadFriendReports()
                emergencyViewModel.loadNotifications()
            },
            onUpdateStatus = { reportId, status ->
                emergencyViewModel.updateReportStatus(reportId, status)
            }
        )
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    gradientColors: List<Color>,
    imageRes: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                gradientColors[0].copy(alpha = 0.6f),
                                gradientColors[1].copy(alpha = 0.6f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_open),
                    contentDescription = "Navigate",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp)
                )
            }
        }
    }
}

@Composable
fun RecentActivityCard(
    activity: RecentActivity,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    activity.isCriminal && activity.dangerLevel == "CRITICAL" -> Color(0xFFB71C1C)
                    activity.isCriminal && activity.dangerLevel == "HIGH" -> Color(0xFFD32F2F)
                    activity.isCriminal && activity.dangerLevel == "MEDIUM" -> Color(0xFFF57C00)
                    activity.isCriminal && activity.dangerLevel == "LOW" -> Color(0xFFFFA726)
                    else -> Color(0xFF1976D2)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = when {
                            activity.isCriminal && activity.dangerLevel == "CRITICAL" -> "🚨"
                            activity.isCriminal -> "⚠️"
                            else -> "👤"
                        },
                        fontSize = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = activity.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activity.subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_open),
                contentDescription = "View Details",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 1.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
        }
    }
}