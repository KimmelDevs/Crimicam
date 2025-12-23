package com.example.crimicam.presentation.main.Home

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.NotificationRepository
import com.example.crimicam.data.remote.FirestoreService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class RecentActivity(
    val id: String,
    val title: String,
    val subtitle: String,
    val timestamp: String,
    val isCriminal: Boolean = false,
    val dangerLevel: String? = null,
    val firestoreTimestamp: Timestamp? = null,
    val userId: String = "", // Add user ID to identify who created the activity
    val userName: String? = null // Optional: store user name for display
)

data class HomeState(
    val recentActivities: List<RecentActivity> = emptyList(),
    val isLoadingActivities: Boolean = false,
    val isRealtimeActive: Boolean = false,
    val activitiesError: String? = null,
    val newActivityCount: Int = 0,
    val lastSeenTimestamp: Long = 0,
    val lastNotificationData: Map<String, String>? = null,
    val showNotificationAlert: Boolean = false,
    val notificationTitle: String = "",
    val notificationBody: String = ""
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
    private var lastKnownActivityIds = mutableSetOf<String>()
    private var ringtonePlayer: RingtonePlayer? = null
    private var isFirstLoad = true

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    companion object {
        private const val TAG = "HomeViewModel"
        private const val RINGTONE_DURATION_MS = 10000L
        private const val GLOBAL_ACTIVITIES_COLLECTION = "global_activities"
    }

    init {
        Log.d(TAG, "HomeViewModel initialized")
    }

    /**
     * Initialize ringtone player
     */
    fun initializeRingtonePlayer(context: Context) {
        if (ringtonePlayer == null) {
            ringtonePlayer = RingtonePlayer(context)
            Log.d(TAG, "✅ Ringtone player initialized")
        }
    }

    /**
     * Handle incoming broadcast notification
     */
    fun handleBroadcastNotification(data: Map<String, String>) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🎯 Processing broadcast notification in ViewModel")

                val type = data["type"]
                if (type != "ACTIVITY_BROADCAST") {
                    Log.d(TAG, "Ignoring notification type: $type")
                    return@launch
                }

                // Extract data
                val title = data["title"] ?: "New Activity"
                val body = data["body"] ?: "Activity detected"
                val isCriminal = data["isCriminal"]?.toBoolean() ?: false
                val dangerLevel = data["dangerLevel"] ?: "LOW"
                val userId = data["userId"] ?: "unknown"
                val userName = data["userName"] ?: "Unknown User"
                val faceId = data["faceId"] ?: System.currentTimeMillis().toString()
                val address = data["address"] ?: "Unknown location"
                val personName = data["personName"] ?: "Unknown Person"
                val isRecognized = data["isRecognized"]?.toBoolean() ?: false

                Log.d(TAG, "📢 Broadcast notification received:")
                Log.d(TAG, "   Title: $title")
                Log.d(TAG, "   Body: $body")
                Log.d(TAG, "   From user: $userName ($userId)")
                Log.d(TAG, "   Face ID: $faceId")
                Log.d(TAG, "   Criminal: $isCriminal")
                Log.d(TAG, "   Danger: $dangerLevel")
                Log.d(TAG, "   Address: $address")
                Log.d(TAG, "   Person: $personName")
                Log.d(TAG, "   Recognized: $isRecognized")

                // Update state with notification data
                _homeState.value = _homeState.value.copy(
                    lastNotificationData = data,
                    showNotificationAlert = true,
                    notificationTitle = title,
                    notificationBody = body
                )

                // Play appropriate ringtone
                playRingtoneForNotification(isCriminal, dangerLevel)

                // Increment new activity count
                val currentCount = _homeState.value.newActivityCount
                _homeState.value = _homeState.value.copy(
                    newActivityCount = currentCount + 1
                )

                // Create activity for UI
                val timeString = "Just now"
                val subtitle = "From $userName • $timeString"

                val activity = RecentActivity(
                    id = faceId,
                    title = title,
                    subtitle = subtitle,
                    timestamp = timeString,
                    isCriminal = isCriminal,
                    dangerLevel = dangerLevel,
                    userId = userId,
                    userName = userName
                )

                // Add to recent activities
                val currentActivities = _homeState.value.recentActivities.toMutableList()
                currentActivities.add(0, activity)

                // Keep only last 20 activities
                val limitedActivities = if (currentActivities.size > 20) {
                    currentActivities.take(20)
                } else {
                    currentActivities
                }

                _homeState.value = _homeState.value.copy(
                    recentActivities = limitedActivities
                )

                Log.d(TAG, "✅ Broadcast notification processed successfully")
                Log.d(TAG, "📊 New activity count: ${_homeState.value.newActivityCount}")

                // Auto-hide notification alert after 5 seconds
                viewModelScope.launch {
                    kotlinx.coroutines.delay(5000)
                    _homeState.value = _homeState.value.copy(
                        showNotificationAlert = false
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing broadcast notification", e)
            }
        }
    }

    /**
     * Play ringtone based on notification type
     */
    private fun playRingtoneForNotification(isCriminal: Boolean, dangerLevel: String?) {
        ringtonePlayer?.let { player ->
            viewModelScope.launch {
                try {
                    when {
                        isCriminal && dangerLevel == "CRITICAL" -> {
                            player.playCriticalAlert(RINGTONE_DURATION_MS)
                            Log.d(TAG, "🔊 Playing CRITICAL alert ringtone")
                        }
                        isCriminal -> {
                            player.playCriminalAlert(RINGTONE_DURATION_MS)
                            Log.d(TAG, "🔊 Playing criminal alert ringtone")
                        }
                        else -> {
                            player.playNewActivityAlert(RINGTONE_DURATION_MS)
                            Log.d(TAG, "🔊 Playing new activity ringtone")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error playing ringtone", e)
                }
            }
        } ?: run {
            Log.w(TAG, "⚠️ Ringtone player not initialized")
        }
    }

    /**
     * Start listening for user's own activities AND global activities
     */
    fun startRealtimeUpdates() {
        stopRealtimeUpdates()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "⚠️ No authenticated user")
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
            // Query from user's own collection (this is what was working before)
            val userQuery = db.collection("users")
                .document(currentUser.uid)
                .collection("captured_faces")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)

            snapshotListener = userQuery.addSnapshotListener { snapshot, error ->
                viewModelScope.launch {
                    if (error != null) {
                        Log.e(TAG, "❌ Realtime listener error", error)
                        _homeState.value = _homeState.value.copy(
                            isLoadingActivities = false,
                            isRealtimeActive = false,
                            activitiesError = error.message ?: "Failed to load activities"
                        )
                        return@launch
                    }

                    if (snapshot == null) return@launch

                    // Track first load
                    if (isFirstLoad) {
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
                            val userId = currentUser.uid
                            val userName = currentUser.displayName ?: currentUser.email ?: "You"

                            // Format time
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
                                        "CRITICAL" -> "🚨 CRITICAL THREAT: ${personName ?: "Unknown"}"
                                        "HIGH" -> "⚠️ HIGH DANGER: ${personName ?: "Unknown"}"
                                        "MEDIUM" -> "⚠️ MEDIUM RISK: ${personName ?: "Unknown"}"
                                        "LOW" -> "⚠️ LOW RISK: ${personName ?: "Unknown"}"
                                        else -> "🚨 Criminal: ${personName ?: "Unknown"}"
                                    }
                                }
                                isRecognized && personName != null -> "✅ Identified: $personName"
                                else -> "❓ Unknown Person Detected"
                            }

                            // Create subtitle
                            val subtitle = buildString {
                                // Show user name
                                append("👤 $userName")
                                append(" • ")
                                append(timeString)
                                if (address != null) {
                                    append(" • ")
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
                                firestoreTimestamp = timestamp,
                                userId = userId,
                                userName = userName
                            )

                            allActivities.add(activity)

                            // Check if new activity
                            if (!lastKnownActivityIds.contains(doc.id)) {
                                newActivities.add(activity)
                                lastKnownActivityIds.add(doc.id)
                                Log.d(TAG, "📝 New local activity: ${activity.title}")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error parsing activity", e)
                        }
                    }

                    // Now ALSO listen to global activities
                    listenToGlobalActivities(currentUser, allActivities, newActivities)

                }
            }

            Log.d(TAG, "✅ Started realtime updates for user ${currentUser.uid}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up realtime listener", e)
            _homeState.value = _homeState.value.copy(
                isLoadingActivities = false,
                isRealtimeActive = false,
                activitiesError = e.message ?: "Failed to setup updates"
            )
        }
    }

    /**
     * Listen to global activities collection
     */
    private fun listenToGlobalActivities(
        currentUser: com.google.firebase.auth.FirebaseUser,
        existingActivities: MutableList<RecentActivity>,
        newActivities: MutableList<RecentActivity>
    ) {
        val globalQuery = db.collection(GLOBAL_ACTIVITIES_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)

        val globalListener = globalQuery.addSnapshotListener { snapshot, error ->
            viewModelScope.launch {
                if (error != null) {
                    Log.e(TAG, "❌ Global activities listener error", error)
                    return@launch
                }

                if (snapshot == null) return@launch

                Log.d(TAG, "🌍 Global activities snapshot: ${snapshot.documents.size} documents")

                val allActivities = existingActivities.toMutableList()
                val globalNewActivities = mutableListOf<RecentActivity>()

                snapshot.documents.forEach { doc ->
                    try {
                        val data = doc.data ?: return@forEach

                        val isCriminal = data["is_criminal"] as? Boolean ?: false
                        val isRecognized = data["is_recognized"] as? Boolean ?: false
                        val personName = data["matched_person_name"] as? String
                        val dangerLevel = data["danger_level"] as? String
                        val address = data["address"] as? String
                        val timestamp = data["timestamp"] as? Timestamp
                        val userId = data["user_id"] as? String ?: "unknown"
                        val userName = data["user_name"] as? String ?: "Unknown User"

                        // Skip if this is the current user's activity (already in personal list)
                        if (userId == currentUser.uid) {
                            return@forEach
                        }

                        // Format time
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
                                    "CRITICAL" -> "🚨 CRITICAL THREAT: ${personName ?: "Unknown"}"
                                    "HIGH" -> "⚠️ HIGH DANGER: ${personName ?: "Unknown"}"
                                    "MEDIUM" -> "⚠️ MEDIUM RISK: ${personName ?: "Unknown"}"
                                    "LOW" -> "⚠️ LOW RISK: ${personName ?: "Unknown"}"
                                    else -> "🚨 Criminal: ${personName ?: "Unknown"}"
                                }
                            }
                            isRecognized && personName != null -> "✅ Identified: $personName"
                            else -> "❓ Unknown Person Detected"
                        }

                        // Create subtitle with user info
                        val subtitle = buildString {
                            // Show user name if available
                            append("👤 $userName")
                            append(" • ")
                            append(timeString)
                            if (address != null && address.isNotBlank() && address != "Unknown location") {
                                append(" • ")
                                val shortAddress = if (address.length > 30) {
                                    address.take(27) + "..."
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
                            firestoreTimestamp = timestamp,
                            userId = userId,
                            userName = userName
                        )

                        // Check if this activity already exists in our list
                        val existingActivity = allActivities.find { it.id == doc.id }
                        if (existingActivity == null) {
                            allActivities.add(activity)
                            if (!lastKnownActivityIds.contains(doc.id)) {
                                globalNewActivities.add(activity)
                                lastKnownActivityIds.add(doc.id)
                                Log.d(TAG, "🌍 New global activity from $userName: ${activity.title}")
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing global activity", e)
                    }
                }

                // Sort all activities by timestamp (newest first)
                val sortedActivities = allActivities.sortedByDescending {
                    it.firestoreTimestamp?.seconds ?: 0L
                }.take(20) // Limit to 20 most recent

                // Update state
                _homeState.value = _homeState.value.copy(
                    recentActivities = sortedActivities,
                    isLoadingActivities = false,
                    isRealtimeActive = true,
                    activitiesError = null
                )

                Log.d(TAG, "✅ State updated with ${sortedActivities.size} total activities")

                // Play ringtone for new activities (excluding current user's own)
                val allNewActivities = newActivities + globalNewActivities
                if (allNewActivities.isNotEmpty() && !isFirstLoad) {
                    val externalNewActivities = allNewActivities.filter { it.userId != currentUser.uid }

                    if (externalNewActivities.isNotEmpty()) {
                        Log.d(TAG, "🎯 ${externalNewActivities.size} NEW external activities found")
                        externalNewActivities.forEach { activity ->
                            playRingtoneForActivity(activity)
                        }
                        _homeState.value = _homeState.value.copy(
                            newActivityCount = _homeState.value.newActivityCount + externalNewActivities.size
                        )
                        Log.d(TAG, "📈 New activity count: ${_homeState.value.newActivityCount}")
                    }
                }

                Log.d(TAG, "📊 Activities updated: ${sortedActivities.size} total")
            }
        }
    }

    /**
     * Play ringtone for activity
     */
    private fun playRingtoneForActivity(activity: RecentActivity) {
        ringtonePlayer?.let { player ->
            viewModelScope.launch {
                try {
                    val currentUser = auth.currentUser
                    // Don't play ringtone for current user's own activities
                    if (currentUser != null && activity.userId == currentUser.uid) {
                        return@launch
                    }

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
                    Log.d(TAG, "🔊 Playing ringtone for: ${activity.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error playing ringtone", e)
                }
            }
        }
    }

    /**
     * Add a new activity to the global feed
     */
    fun addActivityToGlobalFeed(activityData: Map<String, Any>) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "⚠️ Cannot add to global feed: No authenticated user")
                    return@launch
                }

                // Get user display name
                val userName = currentUser.displayName ?: currentUser.email ?: "Anonymous User"

                Log.d(TAG, "➕ Adding activity to global feed for user: $userName")

                // Add user info to activity data
                val globalActivityData = activityData.toMutableMap().apply {
                    put("user_id", currentUser.uid)
                    put("user_name", userName)
                    put("timestamp", FieldValue.serverTimestamp())
                }

                // Add to global activities collection
                db.collection(GLOBAL_ACTIVITIES_COLLECTION)
                    .add(globalActivityData)
                    .addOnSuccessListener { documentReference ->
                        Log.d(TAG, "✅ Activity added to global feed with ID: ${documentReference.id}")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Error adding activity to global feed", e)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error adding to global feed", e)
            }
        }
    }

    /**
     * Reset new activity count
     */
    fun resetNewActivityCount() {
        _homeState.value = _homeState.value.copy(newActivityCount = 0)
        Log.d(TAG, "🔄 Reset new activity count")
    }

    /**
     * Hide notification alert
     */
    fun hideNotificationAlert() {
        _homeState.value = _homeState.value.copy(showNotificationAlert = false)
    }

    /**
     * Clear last notification data
     */
    fun clearLastNotificationData() {
        _homeState.value = _homeState.value.copy(lastNotificationData = null)
    }

    /**
     * Refresh activities
     */
    fun refreshActivities() {
        Log.d(TAG, "🔄 Manual refresh triggered")
        startRealtimeUpdates()
    }

    /**
     * Stop updates
     */
    fun stopRealtimeUpdates() {
        snapshotListener?.remove()
        snapshotListener = null
        _homeState.value = _homeState.value.copy(isRealtimeActive = false)
        Log.d(TAG, "⏹️ Stopped realtime updates")
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        ringtonePlayer?.release()
        ringtonePlayer = null
        Log.d(TAG, "🧹 Cleaned up resources")
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        cleanup()
        Log.d(TAG, "❌ ViewModel cleared")
    }
}

/**
 * Ringtone player
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
        playRingtoneWithDuration(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            durationMillis
        )
    }

    fun playCriticalAlert(durationMillis: Long = 8000L) {
        playRingtoneWithDuration(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            durationMillis
        )
    }

    private fun playRingtoneWithDuration(uri: Uri, durationMillis: Long) {
        ringtoneJob?.cancel()
        stop()

        try {
            currentRingtone = RingtoneManager.getRingtone(context, uri).apply {
                play()
            }

            ringtoneJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(durationMillis)
                stop()
            }

            Log.d("RingtonePlayer", "🔊 Playing ringtone for ${durationMillis}ms")

        } catch (e: Exception) {
            Log.e("RingtonePlayer", "❌ Error playing ringtone", e)
            stop()
        }
    }

    fun stop() {
        try {
            currentRingtone?.stop()
            currentRingtone = null
            ringtoneJob?.cancel()
            ringtoneJob = null
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "❌ Error stopping ringtone", e)
        }
    }

    fun release() {
        stop()
        Log.d("RingtonePlayer", "🧹 Ringtone player released")
    }
}