package com.example.crimicam.presentation.main.Home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.EmergencyReport
import com.example.crimicam.data.model.ReportState
import com.example.crimicam.data.repository.EmergencyReportRepository
import com.example.crimicam.data.repository.NotificationItem
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class EmergencyReportViewModel : ViewModel() {
    private val repository = EmergencyReportRepository()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var friendReportsListener: ListenerRegistration? = null
    private var notificationsListener: ListenerRegistration? = null

    private val _reportState = MutableStateFlow(ReportState())
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _createReportState = MutableStateFlow<CreateReportState>(CreateReportState.Idle)
    val createReportState: StateFlow<CreateReportState> = _createReportState.asStateFlow()

    private val _notificationsState = MutableStateFlow<NotificationsState>(NotificationsState())
    val notificationsState: StateFlow<NotificationsState> = _notificationsState.asStateFlow()

    // Theme state - defaults to dark theme
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Track optimistic updates to prevent listener from overwriting
    private val optimisticUpdates = mutableMapOf<String, String>() // reportId to status
    private var lastUpdateTimestamp = 0L
    private val UPDATE_DEBOUNCE_MS = 2000L // 2 second debounce

    companion object {
        private const val TAG = "EmergencyReportVM"
    }

    init {
        Log.d(TAG, "EmergencyReportViewModel initialized with dark theme")
        startRealtimeReportUpdates()
        startRealtimeNotificationUpdates()
    }

    /**
     * Toggle theme between dark and light
     */
    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        Log.d(TAG, "🎨 Theme toggled to: ${if (_isDarkTheme.value) "Dark" else "Light"}")
    }

    /**
     * Set theme explicitly
     */
    fun setDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        Log.d(TAG, "🎨 Theme set to: ${if (isDark) "Dark" else "Light"}")
    }

    /**
     * Start real-time listener for friend reports with SERVER SOURCE
     */
    private fun startRealtimeReportUpdates() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for real-time reports")
            return
        }

        // Stop existing listener
        friendReportsListener?.remove()

        try {
            friendReportsListener = firestore.collection("emergency_reports")
                .whereArrayContains("friendsNotified", currentUser.uid)
                .limit(50)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to friend reports", error)
                        _reportState.value = _reportState.value.copy(
                            error = error.message ?: "Failed to load reports"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    viewModelScope.launch {
                        try {
                            // Check if data is from cache or server
                            val fromCache = snapshot.metadata.isFromCache
                            Log.d(TAG, "📡 Snapshot received - fromCache: $fromCache, pendingWrites: ${snapshot.metadata.hasPendingWrites()}")

                            // Debounce: Skip cache updates if we just made an update
                            val now = System.currentTimeMillis()
                            if (fromCache && now - lastUpdateTimestamp < UPDATE_DEBOUNCE_MS) {
                                Log.d(TAG, "⏭️ Skipping cached snapshot (debounced)")
                                return@launch
                            }

                            val reports = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val report = doc.toObject(EmergencyReport::class.java)

                                    // Check if we have an optimistic update for this report
                                    val optimisticStatus = optimisticUpdates[doc.id]
                                    if (optimisticStatus != null) {
                                        // Use optimistic update instead of server data
                                        Log.d(TAG, "🔄 Using optimistic status for ${doc.id}: $optimisticStatus")
                                        report?.copy(
                                            status = optimisticStatus,
                                            isResolved = optimisticStatus == "RESOLVED"
                                        )
                                    } else {
                                        // Log actual Firestore data
                                        Log.d(TAG, "📄 Report ${doc.id}: status=${doc.getString("status")}, isResolved=${doc.getBoolean("isResolved")}, fromCache=$fromCache")
                                        report
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error parsing report ${doc.id}", e)
                                    null
                                }
                            }.sortedByDescending { it.timestamp.toDate() }

                            val unreadCount = reports.count { !it.isResolved }

                            _reportState.value = _reportState.value.copy(
                                friendReports = reports,
                                unreadCount = unreadCount,
                                isLoading = false,
                                error = null
                            )

                            Log.d(TAG, "✅ Loaded ${reports.size} friend reports (${unreadCount} unread) - fromCache: $fromCache")

                            // Clear optimistic updates after server confirmation
                            if (!fromCache) {
                                optimisticUpdates.clear()
                                Log.d(TAG, "🧹 Cleared optimistic updates (server data received)")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing reports", e)
                            _reportState.value = _reportState.value.copy(
                                error = e.message ?: "Failed to process reports"
                            )
                        }
                    }
                }

            Log.d(TAG, "✅ Started real-time updates for friend reports")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up real-time listener", e)
            _reportState.value = _reportState.value.copy(
                error = e.message ?: "Failed to setup updates"
            )
        }
    }

    /**
     * Start real-time listener for notifications
     */
    private fun startRealtimeNotificationUpdates() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for real-time notifications")
            return
        }

        // Stop existing listener
        notificationsListener?.remove()

        try {
            notificationsListener = firestore.collection("users")
                .document(currentUser.uid)
                .collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to notifications", error)
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    viewModelScope.launch {
                        try {
                            val notifications = snapshot.documents.mapNotNull { doc ->
                                try {
                                    NotificationItem(
                                        id = doc.id,
                                        reportId = doc.getString("reportId") ?: "",
                                        userId = doc.getString("userId") ?: "",
                                        userName = doc.getString("userName") ?: "Unknown",
                                        title = doc.getString("title") ?: "",
                                        body = doc.getString("body") ?: "",
                                        type = doc.getString("type") ?: "",
                                        reportType = doc.getString("reportType") ?: "",
                                        address = doc.getString("address") ?: "",
                                        timestamp = doc.getTimestamp("timestamp"),
                                        read = doc.getBoolean("read") ?: false
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            val unreadCount = notifications.count { !it.read }

                            _notificationsState.value = NotificationsState(
                                notifications = notifications,
                                unreadCount = unreadCount,
                                isLoading = false,
                                error = null
                            )

                            Log.d(TAG, "📱 Real-time: ${notifications.size} notifications (${unreadCount} unread)")

                            // Update report state with notification count
                            _reportState.value = _reportState.value.copy(
                                unreadCount = unreadCount
                            )

                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing notifications", e)
                        }
                    }
                }

            Log.d(TAG, "✅ Started real-time updates for notifications")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up notifications listener", e)
        }
    }

    /**
     * Load friend reports (manual refresh)
     */
    fun loadFriendReports() {
        viewModelScope.launch {
            _reportState.value = _reportState.value.copy(isLoading = true)

            try {
                when (val result = repository.getFriendReports()) {
                    is Result.Success -> {
                        val unreadCount = result.data.count { !it.isResolved }
                        _reportState.value = _reportState.value.copy(
                            friendReports = result.data,
                            unreadCount = unreadCount,
                            isLoading = false,
                            error = null
                        )
                        Log.d(TAG, "✅ Manually loaded ${result.data.size} friend reports")
                    }
                    is Result.Error -> {
                        _reportState.value = _reportState.value.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Failed to load reports"
                        )
                        Log.e(TAG, "❌ Error loading friend reports", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading friend reports", e)
                _reportState.value = _reportState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Load notifications
     */
    fun loadNotifications() {
        viewModelScope.launch {
            _notificationsState.value = _notificationsState.value.copy(isLoading = true)

            try {
                when (val result = repository.getUserNotifications()) {
                    is Result.Success -> {
                        val unreadCount = result.data.count { !it.read }
                        _notificationsState.value = NotificationsState(
                            notifications = result.data,
                            unreadCount = unreadCount,
                            isLoading = false,
                            error = null
                        )

                        // Update report state with notification count
                        _reportState.value = _reportState.value.copy(
                            unreadCount = unreadCount
                        )

                        Log.d(TAG, "✅ Loaded ${result.data.size} notifications")
                    }
                    is Result.Error -> {
                        _notificationsState.value = _notificationsState.value.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Failed to load notifications"
                        )
                        Log.e(TAG, "❌ Error loading notifications", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading notifications", e)
                _notificationsState.value = _notificationsState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Mark notification as read
     */
    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            try {
                when (val result = repository.markNotificationAsRead(notificationId)) {
                    is Result.Success -> {
                        // Update local state
                        _notificationsState.value = _notificationsState.value.copy(
                            notifications = _notificationsState.value.notifications.map { notification ->
                                if (notification.id == notificationId) {
                                    notification.copy(read = true)
                                } else {
                                    notification
                                }
                            },
                            unreadCount = maxOf(0, _notificationsState.value.unreadCount - 1)
                        )

                        // Update report state
                        _reportState.value = _reportState.value.copy(
                            unreadCount = maxOf(0, _reportState.value.unreadCount - 1)
                        )

                        Log.d(TAG, "✅ Notification marked as read: $notificationId")
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Error marking notification as read", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception marking notification as read", e)
            }
        }
    }

    /**
     * Mark all notifications as read
     */
    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            try {
                when (val result = repository.markAllNotificationsAsRead()) {
                    is Result.Success -> {
                        // Update local state
                        _notificationsState.value = _notificationsState.value.copy(
                            notifications = _notificationsState.value.notifications.map { notification ->
                                notification.copy(read = true)
                            },
                            unreadCount = 0
                        )

                        // Update report state
                        _reportState.value = _reportState.value.copy(
                            unreadCount = 0
                        )

                        Log.d(TAG, "✅ All notifications marked as read")
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Error marking all notifications as read", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception marking all notifications as read", e)
            }
        }
    }

    /**
     * Load user's own reports
     */
    fun loadUserReports() {
        viewModelScope.launch {
            _reportState.value = _reportState.value.copy(isLoading = true)

            try {
                when (val result = repository.getUserReports()) {
                    is Result.Success -> {
                        _reportState.value = _reportState.value.copy(
                            reports = result.data,
                            isLoading = false,
                            error = null
                        )
                        Log.d(TAG, "✅ Loaded ${result.data.size} user reports")
                    }
                    is Result.Error -> {
                        _reportState.value = _reportState.value.copy(
                            isLoading = false,
                            error = result.exception.message ?: "Failed to load reports"
                        )
                        Log.e(TAG, "❌ Error loading user reports", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading user reports", e)
                _reportState.value = _reportState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Create emergency report (TEXT ONLY - NO AUDIO)
     */
    fun createReport(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        type: String = "EMERGENCY"
    ) {
        viewModelScope.launch {
            _createReportState.value = CreateReportState.Loading

            try {
                Log.d(TAG, "📝 Creating emergency report...")
                Log.d(TAG, "   Title: $title")
                Log.d(TAG, "   Type: $type")
                Log.d(TAG, "   Location: $latitude, $longitude")
                Log.d(TAG, "   Address: $address")

                when (val result = repository.createReport(
                    title = title,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    type = type
                )) {
                    is Result.Success -> {
                        _createReportState.value = CreateReportState.Success(result.data)
                        Log.d(TAG, "✅ Emergency report created successfully: ${result.data}")

                        // Refresh user reports
                        loadUserReports()

                    }
                    is Result.Error -> {
                        val errorMessage = result.exception.message ?: "Failed to create report"
                        _createReportState.value = CreateReportState.Error(errorMessage)
                        Log.e(TAG, "❌ Error creating report", result.exception)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to create report"
                _createReportState.value = CreateReportState.Error(errorMessage)
                Log.e(TAG, "❌ Exception creating report", e)
            }
        }
    }

    /**
     * Update report status with optimistic updates and server verification
     */
    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📝 Updating report $reportId status to $status")

                // Mark the time of this update
                lastUpdateTimestamp = System.currentTimeMillis()

                // Store optimistic update
                optimisticUpdates[reportId] = status
                Log.d(TAG, "💾 Stored optimistic update for $reportId: $status")

                // OPTIMISTIC UPDATE - Update local state FIRST
                val previousReports = _reportState.value.friendReports
                _reportState.value = _reportState.value.copy(
                    friendReports = _reportState.value.friendReports.map { report ->
                        if (report.id == reportId) {
                            report.copy(
                                status = status,
                                isResolved = status == "RESOLVED"
                            )
                        } else {
                            report
                        }
                    }
                )

                // Recalculate unread count
                val unreadCount = _reportState.value.friendReports.count { !it.isResolved }
                _reportState.value = _reportState.value.copy(unreadCount = unreadCount)

                Log.d(TAG, "✅ Local state updated optimistically (unread: $unreadCount)")

                // Now update Firestore (this will trigger the listener after server confirms)
                when (val result = repository.updateReportStatus(reportId, status)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Report status updated successfully in Firestore")

                        // Wait a bit to ensure server has propagated the update
                        delay(1000)

                        // Clear this specific optimistic update
                        optimisticUpdates.remove(reportId)
                        Log.d(TAG, "🧹 Cleared optimistic update for $reportId")
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Error updating report status in Firestore", result.exception)

                        // ROLLBACK - Restore previous state on error
                        optimisticUpdates.remove(reportId)
                        _reportState.value = _reportState.value.copy(
                            friendReports = previousReports,
                            error = result.exception.message ?: "Failed to update report"
                        )

                        // Recalculate unread count again
                        val rollbackUnreadCount = previousReports.count { !it.isResolved }
                        _reportState.value = _reportState.value.copy(unreadCount = rollbackUnreadCount)

                        Log.d(TAG, "⏮️ Rolled back to previous state due to error")
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception updating report status", e)
                optimisticUpdates.remove(reportId)
                _reportState.value = _reportState.value.copy(
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Delete report
     */
    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🗑️ Deleting report $reportId")

                when (val result = repository.deleteReport(reportId)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Report deleted successfully")

                        // Remove from local state
                        _reportState.value = _reportState.value.copy(
                            reports = _reportState.value.reports.filter { it.id != reportId }
                        )

                        // Refresh user reports
                        loadUserReports()
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Error deleting report", result.exception)
                        _reportState.value = _reportState.value.copy(
                            error = result.exception.message ?: "Failed to delete report"
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception deleting report", e)
                _reportState.value = _reportState.value.copy(
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Reset create report state
     */
    fun resetCreateReportState() {
        _createReportState.value = CreateReportState.Idle
        Log.d(TAG, "🔄 Reset create report state")
    }

    /**
     * Clear unread count
     */
    fun clearUnreadCount() {
        _reportState.value = _reportState.value.copy(unreadCount = 0)
        _notificationsState.value = _notificationsState.value.copy(unreadCount = 0)
        markAllNotificationsAsRead()
        Log.d(TAG, "🔄 Cleared unread count")
    }

    /**
     * Stop real-time updates
     */
    private fun stopRealtimeUpdates() {
        friendReportsListener?.remove()
        friendReportsListener = null
        notificationsListener?.remove()
        notificationsListener = null
        optimisticUpdates.clear()
        Log.d(TAG, "⏹️ Stopped all real-time updates")
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        Log.d(TAG, "❌ ViewModel cleared")
    }
}

sealed class CreateReportState {
    object Idle : CreateReportState()
    object Loading : CreateReportState()
    data class Success(val reportId: String) : CreateReportState()
    data class Error(val message: String) : CreateReportState()
}

data class NotificationsState(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)