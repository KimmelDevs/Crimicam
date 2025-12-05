package com.example.crimicam.presentation.main.Home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.crimicam.R

@Composable
fun HomeScreen(
    navController: NavController
) {
    val viewModel: HomeViewModel = viewModel()
    val scrollState = rememberScrollState()
    val notificationState by viewModel.notificationState.collectAsState()
    val homeState by viewModel.homeState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
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

                // Recent Activity Header with Refresh and Notification Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Refresh Button
                        IconButton(
                            onClick = { viewModel.refreshActivities() },
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Notification Trigger Button
                        IconButton(
                            onClick = { viewModel.triggerNotification() },
                            modifier = Modifier.size(36.dp),
                            enabled = notificationState !is NotificationState.Loading
                        ) {
                            when (notificationState) {
                                is NotificationState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                is NotificationState.Success -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_notification),
                                        contentDescription = "Notification Sent",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                is NotificationState.Error -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_notification),
                                        contentDescription = "Notification Error",
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                is NotificationState.Idle -> {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_notification),
                                        contentDescription = "Trigger Notification",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Recent Activity Content
                when {
                    // Loading state (only show spinner if no activities yet)
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

                    // Error state
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
                                    onClick = { viewModel.refreshActivities() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    // Empty state
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
                            }
                        }
                    }

                    // Activities list
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

                // Bottom spacing
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Show error snackbar for notification errors
        if (notificationState is NotificationState.Error) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = Color(0xFFD32F2F)
            ) {
                Text(
                    text = (notificationState as NotificationState.Error).message,
                    color = Color.White
                )
            }
        }

        // Show success snackbar for notifications
        if (notificationState is NotificationState.Success) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "✅ Notification sent successfully",
                    color = Color.White
                )
            }
        }
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
            // Background Image
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f
            )

            // Gradient Overlay
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

            // Content
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

                // Arrow Icon
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
            // Icon based on activity type
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

            // Activity Info
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

            // Arrow Icon
            Icon(
                painter = painterResource(id = R.drawable.ic_open),
                contentDescription = "View Details",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        }

        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 1.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
        }
    }
}

