package com.example.crimicam.presentation.main.Home

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.NotificationRepository
import com.example.crimicam.data.remote.FirestoreService
import com.example.crimicam.util.Result
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class RecentActivity(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val isCriminal: Boolean = false,
    val dangerLevel: String? = null,
    val firestoreTimestamp: Timestamp? = null // Add original timestamp for comparison
)

data class HomeState(
    val recentActivities: List<RecentActivity> = emptyList(),
    val isLoadingActivities: Boolean = false,
    val isRealtimeActive: Boolean = false,
    val activitiesError: String? = null,
    val newActivityCount: Int = 0, // Track new activities since last view
    val lastSeenTimestamp: Long = 0 // Track last seen activity timestamp
)

class HomeViewModel : ViewModel() {

    private val notificationRepository = NotificationRepository(
        firestoreService = FirestoreService(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
    )

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var snapshotListener: ListenerRegistration? = null
    private var lastKnownActivityIds = mutableSetOf<String>() // Track already seen activities
    private var ringtonePlayer: RingtonePlayer? = null
    private var isFirstLoad = true // Track if this is the first load after app start

    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Idle)
    val notificationState: StateFlow<NotificationState> = _notificationState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    companion object {
        private const val TAG = "HomeViewModel"
        private const val RINGTONE_DURATION_MS = 15000L
    }

    init {
        // Don't start realtime updates immediately - wait for UI to initialize
        Log.d(TAG, "HomeViewModel initialized")
    }

    /**
     * Initialize ringtone player with context
     */
    fun initializeRingtonePlayer(context: Context) {
        if (ringtonePlayer == null) {
            ringtonePlayer = RingtonePlayer(context)
            Log.d(TAG, "Ringtone player initialized")
        }
    }

    /**
     * Play ringtone for new activity with 8-second limit
     */
    private fun playRingtoneForActivity(activity: RecentActivity) {
        ringtonePlayer?.let { player ->
            viewModelScope.launch {
                try {
                    // Use different ringtones based on activity type
                    when {
                        activity.isCriminal && activity.dangerLevel == "CRITICAL" -> {
                            player.playCriticalAlert(RINGTONE_DURATION_MS)
                        }
                        activity.isCriminal -> {
                            player.playCriminalAlert(RINGTONE_DURATION_MS)
                        }
                        else -> {
                            player.playNewActivityAlert(RINGTONE_DURATION_MS)
                        }
                    }
                    Log.d(TAG, "Played ringtone for new activity: ${activity.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing ringtone", e)
                }
            }
        }
    }

    /**
     * Start listening for realtime updates from Firestore
     */
    fun startRealtimeUpdates() {
        stopRealtimeUpdates() // Clean up any existing listener

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot start realtime updates")
            _homeState.value = _homeState.value.copy(
                activitiesError = "Please sign in to view activities",
                isRealtimeActive = false
            )
            return
        }

        _homeState.value = _homeState.value.copy(
            isLoadingActivities = true,
            isRealtimeActive = false,
            activitiesError = null
        )

        try {
            val query = db.collection("users")
                .document(currentUser.uid)
                .collection("captured_faces")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)

            snapshotListener = query.addSnapshotListener { snapshot, error ->
                viewModelScope.launch {
                    if (error != null) {
                        Log.e(TAG, "Realtime listener error", error)
                        _homeState.value = _homeState.value.copy(
                            isLoadingActivities = false,
                            isRealtimeActive = false,
                            activitiesError = error.message ?: "Failed to load activities"
                        )
                        return@launch
                    }

                    if (snapshot == null) {
                        return@launch
                    }

                    // Skip the initial load to prevent playing ringtones for existing activities
                    if (isFirstLoad) {
                        Log.d(TAG, "First load - skipping ringtones for existing activities")
                        // Store existing activity IDs so they won't trigger ringtones
                        snapshot.documents.forEach { doc ->
                            lastKnownActivityIds.add(doc.id)
                        }
                        isFirstLoad = false
                    }

                    val newActivities = mutableListOf<RecentActivity>()
                    val allActivities = mutableListOf<RecentActivity>()

                    snapshot.documents.forEach { doc ->
                        try {
                            val data = doc.data ?: return@forEach

                            val isCriminal = data["is_criminal"] as? Boolean ?: false
                            val isRecognized = data["is_recognized"] as? Boolean ?: false
                            val personName = data["matched_person_name"] as? String
                            val dangerLevel = data["danger_level"] as? String
                            val address = data["address"] as? String
                            val timestamp = data["timestamp"] as? Timestamp

                            // Format timestamp
                            val timeString = timestamp?.toDate()?.let { date ->
                                val now = Date()
                                val diff = now.time - date.time
                                val minutes = diff / (1000 * 60)
                                val hours = diff / (1000 * 60 * 60)
                                val days = diff / (1000 * 60 * 60 * 24)

                                when {
                                    minutes < 1 -> "Just now"
                                    minutes < 60 -> "$minutes min ago"
                                    hours < 24 -> "$hours hr ago"
                                    days < 7 -> "$days days ago"
                                    else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                                }
                            } ?: "Unknown time"

                            // Create title
                            val title = when {
                                isCriminal && dangerLevel != null -> {
                                    when (dangerLevel.uppercase()) {
                                        "CRITICAL" -> "🚨 CRITICAL THREAT: ${personName ?: "Unknown Criminal"}"
                                        "HIGH" -> "⚠️ HIGH DANGER: ${personName ?: "Unknown Criminal"}"
                                        "MEDIUM" -> "⚠️ MEDIUM RISK: ${personName ?: "Unknown Criminal"}"
                                        "LOW" -> "⚠️ LOW RISK: ${personName ?: "Unknown Criminal"}"
                                        else -> "🚨 Criminal: ${personName ?: "Unknown"}"
                                    }
                                }
                                isRecognized && personName != null -> "✅ Identified: $personName"
                                else -> "❓ Unknown Person Detected"
                            }

                            // Create subtitle
                            val subtitle = buildString {
                                append(timeString)
                                if (address != null) {
                                    append(" • ")
                                    // Truncate long addresses
                                    val shortAddress = if (address.length > 40) {
                                        address.take(37) + "..."
                                    } else {
                                        address
                                    }
                                    append(shortAddress)
                                }
                            }

                            val activity = RecentActivity(
                                id = doc.id,
                                title = title,
                                subtitle = subtitle,
                                timestamp = timeString,
                                isCriminal = isCriminal,
                                dangerLevel = dangerLevel,
                                firestoreTimestamp = timestamp
                            )

                            allActivities.add(activity)

                            // Check if this is a new activity (not seen before)
                            if (!lastKnownActivityIds.contains(doc.id)) {
                                newActivities.add(activity)
                                lastKnownActivityIds.add(doc.id)
                                Log.d(TAG, "New activity detected: ${activity.title}")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing activity", e)
                        }
                    }

                    // Update state with all activities
                    _homeState.value = _homeState.value.copy(
                        recentActivities = allActivities,
                        isLoadingActivities = false,
                        isRealtimeActive = true,
                        activitiesError = null,
                        newActivityCount = newActivities.size
                    )

                    // Play ringtone for each new activity (only if not first load)
                    if (!isFirstLoad) {
                        newActivities.forEach { activity ->
                            playRingtoneForActivity(activity)
                        }
                    }

                    if (newActivities.isNotEmpty()) {
                        Log.d(TAG, "New activities detected: ${newActivities.size}")
                        Log.d(TAG, "New activity titles: ${newActivities.map { it.title }}")
                    }

                    Log.d(TAG, "Realtime update: Total ${allActivities.size} activities, New ${newActivities.size} activities")

                }
            }

            Log.d(TAG, "Started realtime updates for user ${currentUser.uid}")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up realtime listener", e)
            _homeState.value = _homeState.value.copy(
                isLoadingActivities = false,
                isRealtimeActive = false,
                activitiesError = e.message ?: "Failed to setup realtime updates"
            )
        }
    }

    /**
     * Reset new activity count (call this when user views the screen)
     */
    fun resetNewActivityCount() {
        _homeState.value = _homeState.value.copy(newActivityCount = 0)
        Log.d(TAG, "Reset new activity count")
    }

    /**
     * Clear all seen activities (for testing or reset)
     */
    fun clearSeenActivities() {
        lastKnownActivityIds.clear()
        isFirstLoad = true // Reset first load flag
        Log.d(TAG, "Cleared seen activities")
    }

    /**
     * Stop realtime updates and clean up the listener
     */
    fun stopRealtimeUpdates() {
        snapshotListener?.remove()
        snapshotListener = null
        _homeState.value = _homeState.value.copy(isRealtimeActive = false)
        Log.d(TAG, "Stopped realtime updates")
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        ringtonePlayer?.release()
        ringtonePlayer = null
        Log.d(TAG, "Cleaned up resources")
    }

    /**
     * Refresh activities manually (can be called from retry button)
     */
    fun refreshActivities() {
        Log.d(TAG, "Manual refresh triggered")
        startRealtimeUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        cleanup()
        Log.d(TAG, "ViewModel cleared")
    }
}

sealed class NotificationState {
    object Idle : NotificationState()
    object Loading : NotificationState()
    object Success : NotificationState()
    data class Error(val message: String) : NotificationState()
}

/**
 * Ringtone player class to handle different types of alerts with 8-second limit
 */
class RingtonePlayer(private val context: Context) {
    private var currentRingtone: android.media.Ringtone? = null
    private var ringtoneJob: kotlinx.coroutines.Job? = null

    fun playNewActivityAlert(durationMillis: Long = 8000L) {
        playRingtoneWithDuration(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            durationMillis
        )
    }

    fun playCriminalAlert(durationMillis: Long = 8000L) {
        // Use alarm sound for criminals
        playRingtoneWithDuration(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            durationMillis
        )
    }

    fun playCriticalAlert(durationMillis: Long = 8000L) {
        // Use a more urgent sound for critical threats
        playRingtoneWithDuration(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            durationMillis
        )
    }

    private fun playRingtoneWithDuration(uri: Uri, durationMillis: Long) {
        // Cancel any existing ringtone job
        ringtoneJob?.cancel()
        ringtoneJob = null

        // Stop any currently playing ringtone
        stop()

        try {
            currentRingtone = RingtoneManager.getRingtone(context, uri).apply {
                play()
            }

            // Schedule auto-stop after duration
            ringtoneJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(durationMillis)
                stop()
                Log.d("RingtonePlayer", "Ringtone stopped after ${durationMillis}ms")
            }

            Log.d("RingtonePlayer", "Playing ringtone for ${durationMillis}ms")

        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error playing ringtone", e)
            stop()
        }
    }

    fun stop() {
        try {
            currentRingtone?.stop()
            currentRingtone = null
            ringtoneJob?.cancel()
            ringtoneJob = null
            Log.d("RingtonePlayer", "Ringtone stopped manually")
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error stopping ringtone", e)
        }
    }

    fun release() {
        stop()
        Log.d("RingtonePlayer", "Ringtone player released")
    }
}