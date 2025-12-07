package com.example.crimicam.presentation.main.Home

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

data class RecentActivity(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val isCriminal: Boolean = false,
    val dangerLevel: String? = null
)

data class HomeState(
    val recentActivities: List<RecentActivity> = emptyList(),
    val isLoadingActivities: Boolean = false,
    val isRealtimeActive: Boolean = false,
    val activitiesError: String? = null
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

    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Idle)
    val notificationState: StateFlow<NotificationState> = _notificationState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    companion object {
        private const val TAG = "HomeViewModel"
    }

    init {
        startRealtimeUpdates()
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

                    if (snapshot == null || snapshot.metadata.hasPendingWrites()) {
                        return@launch
                    }

                    val activities = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null

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

                            RecentActivity(
                                id = doc.id,
                                title = title,
                                subtitle = subtitle,
                                timestamp = timeString,
                                isCriminal = isCriminal,
                                dangerLevel = dangerLevel
                            )

                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing activity", e)
                            null
                        }
                    }

                    _homeState.value = _homeState.value.copy(
                        recentActivities = activities,
                        isLoadingActivities = false,
                        isRealtimeActive = true,
                        activitiesError = null
                    )

                    Log.d(TAG, "Realtime update: Loaded ${activities.size} activities")
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
     * Stop realtime updates and clean up the listener
     */
    fun stopRealtimeUpdates() {
        snapshotListener?.remove()
        snapshotListener = null
        _homeState.value = _homeState.value.copy(isRealtimeActive = false)
        Log.d(TAG, "Stopped realtime updates")
    }

    fun triggerNotification() {
        viewModelScope.launch {
            _notificationState.value = NotificationState.Loading
            when (val result = notificationRepository.triggerNotification()) {
                is Result.Success -> {
                    _notificationState.value = NotificationState.Success
                    viewModelScope.launch {
                        delay(2000L)
                        _notificationState.value = NotificationState.Idle
                    }
                }
                is Result.Error -> {
                    _notificationState.value = NotificationState.Error(
                        result.exception.message ?: "Failed to trigger notification"
                    )
                    viewModelScope.launch {
                        delay(3000L)
                        _notificationState.value = NotificationState.Idle
                    }
                }
                else -> {
                    _notificationState.value = NotificationState.Idle
                }
            }
        }
    }

    /**
     * Refresh activities manually (can be called from retry button)
     */
    fun refreshActivities() {
        startRealtimeUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
    }
}

sealed class NotificationState {
    object Idle : NotificationState()
    object Loading : NotificationState()
    object Success : NotificationState()
    data class Error(val message: String) : NotificationState()
}