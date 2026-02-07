package com.example.crimicam.presentation.main.Home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crimicam.data.model.EmergencyReport
import java.text.SimpleDateFormat
import java.util.*

// Dark theme colors
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkCard = Color(0xFF2C2C2C)
private val DarkCardElevated = Color(0xFF383838)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFFB0B0B0)
private val DarkDivider = Color(0xFF404040)

@Composable
fun FriendReportsDialog(
    friendReports: List<EmergencyReport>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateStatus: (String, String) -> Unit
) {
    var selectedReport by remember { mutableStateOf<EmergencyReport?>(null) }
    var showResolved by remember { mutableStateOf(false) }

    // Filter reports based on resolved status - SIMPLE: just use what Firestore says
    val displayReports = if (showResolved) {
        friendReports
    } else {
        friendReports.filter { !it.isResolved }
    }

    val unresolvedCount = friendReports.count { !it.isResolved }
    val resolvedCount = friendReports.count { it.isResolved }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard)
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emergency Reports",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (showResolved) {
                                    "$unresolvedCount active • $resolvedCount resolved"
                                } else {
                                    "$unresolvedCount active reports"
                                },
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle resolved button
                        IconButton(
                            onClick = { showResolved = !showResolved }
                        ) {
                            Icon(
                                if (showResolved) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                contentDescription = "Toggle Resolved",
                                tint = if (showResolved) Color(0xFF66BB6A) else TextPrimary
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = TextPrimary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = TextPrimary)
                        }
                    }
                }

                HorizontalDivider(color = DarkDivider)

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFEF5350))
                        }
                    }

                    error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = error,
                                color = Color(0xFFEF5350)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF5350)
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }

                    displayReports.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (showResolved) "📭" else "✅",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (showResolved) {
                                    "No Emergency Reports"
                                } else {
                                    "All Reports Resolved!"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (showResolved) {
                                    "Reports from your friends will appear here"
                                } else {
                                    "Great job! All emergencies have been addressed"
                                },
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = displayReports,
                                key = { it.id }
                            ) { report ->
                                EmergencyReportCard(
                                    report = report,
                                    onClick = { selectedReport = report },
                                    onResolve = {
                                        onUpdateStatus(report.id, "RESOLVED")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Report Detail Dialog
    selectedReport?.let { report ->
        ReportDetailDialog(
            report = report,
            onDismiss = { selectedReport = null },
            onResolve = {
                onUpdateStatus(report.id, "RESOLVED")
                selectedReport = null
            }
        )
    }
}

@Composable
fun EmergencyReportCard(
    report: EmergencyReport,
    onClick: () -> Unit,
    onResolve: () -> Unit
) {
    // SIMPLE: Just use what's in the report object from Firestore
    val isResolved = report.isResolved

    val typeColor = when (report.type) {
        "EMERGENCY" -> Color(0xFFEF5350)
        "SUSPICIOUS" -> Color(0xFFFF9800)
        "HELP_NEEDED" -> Color(0xFF42A5F5)
        else -> Color(0xFF9E9E9E)
    }

    val typeEmoji = when (report.type) {
        "EMERGENCY" -> "🚨"
        "SUSPICIOUS" -> "⚠️"
        "HELP_NEEDED" -> "🆘"
        else -> "📢"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isResolved) DarkCard else DarkCardElevated
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.Top
            ) {
                // Type Icon
                Surface(
                    shape = CircleShape,
                    color = if (isResolved) {
                        Color(0xFF66BB6A).copy(alpha = 0.25f)
                    } else {
                        typeColor.copy(alpha = 0.25f)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = if (isResolved) "✓" else typeEmoji,
                            fontSize = 24.sp,
                            color = if (isResolved) Color(0xFF66BB6A) else Color.Unspecified
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // User Info
                    Text(
                        text = report.userName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isResolved) TextSecondary else typeColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = report.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Location
                    if (report.address.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = report.address,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time
                    Text(
                        text = formatTimestamp(report.timestamp),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    // Status Badge
                    if (isResolved) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF66BB6A).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "✓ Resolved",
                                fontSize = 11.sp,
                                color = Color(0xFF66BB6A),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Action Buttons - ONLY show if NOT resolved (based on Firestore data)
            if (!isResolved) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onResolve,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF66BB6A)
                        )
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Resolved")
                    }
                }
            }
        }
    }
}

@Composable
fun ReportDetailDialog(
    report: EmergencyReport,
    onDismiss: () -> Unit,
    onResolve: () -> Unit
) {
    // SIMPLE: Just use what's in the report object from Firestore
    val isResolved = report.isResolved

    val headerColor = when (report.type) {
        "EMERGENCY" -> Color(0xFFEF5350)
        "SUSPICIOUS" -> Color(0xFFFF9800)
        "HELP_NEEDED" -> Color(0xFF42A5F5)
        else -> Color(0xFF9E9E9E)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Surface(
                    color = if (isResolved) Color(0xFF66BB6A) else headerColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isResolved) {
                                "✓ RESOLVED"
                            } else {
                                report.type.replace("_", " ")
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // User Info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = headerColor.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = report.userName.take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = headerColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = report.userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = formatTimestamp(report.timestamp),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = report.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    if (report.description.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = DarkCard
                            )
                        ) {
                            Text(
                                text = report.description,
                                fontSize = 14.sp,
                                color = TextSecondary,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Location
                    if (report.location != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E3A5F)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF42A5F5),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Location",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF42A5F5)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = report.address.ifBlank {
                                            "${report.location.latitude}, ${report.location.longitude}"
                                        },
                                        fontSize = 14.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons - ONLY show if NOT resolved (based on Firestore data)
                if (!isResolved) {
                    Surface(
                        color = DarkCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                )
                            ) {
                                Text("Close")
                            }
                            Button(
                                onClick = onResolve,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF66BB6A)
                                )
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resolve")
                            }
                        }
                    }
                } else {
                    // Show resolved status
                    Surface(
                        color = Color(0xFF66BB6A).copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF66BB6A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "This report has been resolved",
                                fontSize = 14.sp,
                                color = Color(0xFF66BB6A),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val now = Date()
    val diff = now.time - date.time
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(date)
    }
}