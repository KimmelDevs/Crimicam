package com.example.crimicam.presentation.main.Home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.EmergencyReport
import com.example.crimicam.data.model.ReportState
import com.example.crimicam.data.repository.EmergencyReportRepository
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmergencyReportViewModel : ViewModel() {
    private val repository = EmergencyReportRepository()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var friendReportsListener: ListenerRegistration? = null

    private val _reportState = MutableStateFlow(ReportState())
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    private val _createReportState = MutableStateFlow<CreateReportState>(CreateReportState.Idle)
    val createReportState: StateFlow<CreateReportState> = _createReportState.asStateFlow()

    companion object {
        private const val TAG = "EmergencyReportVM"
    }

    init {
        Log.d(TAG, "EmergencyReportViewModel initialized")
        startRealtimeReportUpdates()
    }

    /**
     * Start real-time listener for friend reports
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
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
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
                            val reports = snapshot.documents.mapNotNull { doc ->
                                doc.toObject(EmergencyReport::class.java)?.copy(id = doc.id)
                            }

                            val unreadCount = reports.count { !it.isResolved }

                            _reportState.value = _reportState.value.copy(
                                friendReports = reports,
                                unreadCount = unreadCount,
                                isLoading = false,
                                error = null
                            )

                            Log.d(TAG, "✅ Loaded ${reports.size} friend reports (${unreadCount} unread)")
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
     * Update report status
     */
    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "📝 Updating report $reportId status to $status")

                when (val result = repository.updateReportStatus(reportId, status)) {
                    is Result.Success -> {
                        Log.d(TAG, "✅ Report status updated successfully")

                        // Update local state immediately
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

                        // Refresh both lists
                        loadFriendReports()
                        loadUserReports()
                    }
                    is Result.Error -> {
                        Log.e(TAG, "❌ Error updating report status", result.exception)
                        _reportState.value = _reportState.value.copy(
                            error = result.exception.message ?: "Failed to update report"
                        )
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception updating report status", e)
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
        Log.d(TAG, "🔄 Cleared unread count")
    }

    /**
     * Clear error
     */
    fun clearError() {
        _reportState.value = _reportState.value.copy(error = null)
    }

    /**
     * Stop real-time updates
     */
    private fun stopRealtimeUpdates() {
        friendReportsListener?.remove()
        friendReportsListener = null
        Log.d(TAG, "⏹️ Stopped real-time report updates")
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