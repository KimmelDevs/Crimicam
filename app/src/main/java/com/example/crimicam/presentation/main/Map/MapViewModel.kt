package com.example.crimicam.presentation.main.Map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.EmergencyReport
import com.example.crimicam.data.service.CriminalLocation
import com.example.crimicam.data.service.FirestoreCaptureService
import com.example.crimicam.data.service.LocationHistory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapState(
    val isLoading: Boolean = false,
    val criminalLocations: List<CriminalLocation> = emptyList(),
    val unresolvedReports: List<EmergencyReport> = emptyList(),
    val friendReports: List<EmergencyReport> = emptyList(),
    val selectedCriminalHistory: List<LocationHistory> = emptyList(),
    val error: String? = null
)

class MapViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

    private val firestoreService = FirestoreCaptureService(context)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var unresolvedReportsListener: ListenerRegistration? = null
    private var friendReportsListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "MapViewModel"
    }

    init {
        Log.d(TAG, "MapViewModel initialized")
        startRealtimeReportUpdates()
        startRealtimeFriendReportsUpdates()
    }

    /**
     * Start real-time listener for unresolved reports
     */
    private fun startRealtimeReportUpdates() {
        // Stop existing listener
        unresolvedReportsListener?.remove()

        try {
            unresolvedReportsListener = firestore.collection("emergency_reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to unresolved reports", error)
                        _state.value = _state.value.copy(
                            error = error.message ?: "Failed to load reports"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    viewModelScope.launch {
                        try {
                            val reports = snapshot.documents.mapNotNull { doc ->
                                try {
                                    doc.toObject(EmergencyReport::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing report: ${doc.id}", e)
                                    null
                                }
                            }

                            _state.value = _state.value.copy(
                                unresolvedReports = reports,
                                isLoading = false,
                                error = null
                            )

                            Log.d(TAG, "✅ Real-time: Loaded ${reports.size} unresolved reports")
                            Log.d(TAG, "📍 Reports with location: ${reports.count { it.location != null }}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing reports", e)
                            _state.value = _state.value.copy(
                                error = e.message ?: "Failed to process reports"
                            )
                        }
                    }
                }

            Log.d(TAG, "✅ Started real-time updates for unresolved reports")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up real-time listener for reports", e)
            _state.value = _state.value.copy(
                error = e.message ?: "Failed to setup updates"
            )
        }
    }

    /**
     * Start real-time listener for friend reports
     */
    private fun startRealtimeFriendReportsUpdates() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for real-time friend reports")
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
                        _state.value = _state.value.copy(
                            error = error.message ?: "Failed to load friend reports"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    viewModelScope.launch {
                        try {
                            val reports = snapshot.documents.mapNotNull { doc ->
                                try {
                                    doc.toObject(EmergencyReport::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing friend report: ${doc.id}", e)
                                    null
                                }
                            }

                            _state.value = _state.value.copy(
                                friendReports = reports,
                                isLoading = false,
                                error = null
                            )

                            Log.d(TAG, "✅ Real-time: Loaded ${reports.size} friend reports")
                            Log.d(TAG, "📍 Friend reports with location: ${reports.count { it.location != null }}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing friend reports", e)
                            _state.value = _state.value.copy(
                                error = e.message ?: "Failed to process friend reports"
                            )
                        }
                    }
                }

            Log.d(TAG, "✅ Started real-time updates for friend reports")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up real-time listener for friend reports", e)
            _state.value = _state.value.copy(
                error = e.message ?: "Failed to setup friend reports updates"
            )
        }
    }

    /**
     * Load unresolved reports (manual refresh)
     */
    fun loadUnresolvedReports() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Manual refresh: Loading unresolved reports...")
                _state.value = _state.value.copy(isLoading = true)

                firestore.collection("emergency_reports")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .addOnSuccessListener { documents ->
                        viewModelScope.launch {
                            try {
                                val reports = documents.mapNotNull { doc ->
                                    try {
                                        doc.toObject(EmergencyReport::class.java).copy(id = doc.id)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing report: ${doc.id}", e)
                                        null
                                    }
                                }

                                _state.value = _state.value.copy(
                                    unresolvedReports = reports,
                                    isLoading = false,
                                    error = null
                                )

                                Log.d(TAG, "✅ Manual: Loaded ${reports.size} unresolved reports")
                                Log.d(TAG, "📍 Reports with location: ${reports.count { it.location != null }}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing reports", e)
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    error = e.message ?: "Failed to process reports"
                                )
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = "Failed to load reports: ${e.message}"
                            )
                            Log.e(TAG, "❌ Error loading unresolved reports", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
                Log.e(TAG, "❌ Exception loading unresolved reports", e)
            }
        }
    }

    /**
     * Load friend reports (manual refresh)
     */
    fun loadFriendReports() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _state.value = _state.value.copy(
                        error = "User not authenticated"
                    )
                    return@launch
                }

                Log.d(TAG, "🔄 Manual refresh: Loading friend reports...")
                _state.value = _state.value.copy(isLoading = true)

                firestore.collection("emergency_reports")
                    .whereArrayContains("friendsNotified", currentUser.uid)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .addOnSuccessListener { documents ->
                        viewModelScope.launch {
                            try {
                                val reports = documents.mapNotNull { doc ->
                                    try {
                                        doc.toObject(EmergencyReport::class.java).copy(id = doc.id)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing friend report: ${doc.id}", e)
                                        null
                                    }
                                }

                                _state.value = _state.value.copy(
                                    friendReports = reports,
                                    isLoading = false,
                                    error = null
                                )

                                Log.d(TAG, "✅ Manual: Loaded ${reports.size} friend reports")
                                Log.d(TAG, "📍 Friend reports with location: ${reports.count { it.location != null }}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing friend reports", e)
                                _state.value = _state.value.copy(
                                    isLoading = false,
                                    error = e.message ?: "Failed to process friend reports"
                                )
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = "Failed to load friend reports: ${e.message}"
                            )
                            Log.e(TAG, "❌ Error loading friend reports", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
                Log.e(TAG, "❌ Exception loading friend reports", e)
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

                firestore.collection("emergency_reports")
                    .document(reportId)
                    .update("isResolved", status == "RESOLVED")
                    .addOnSuccessListener {
                        viewModelScope.launch {
                            Log.d(TAG, "✅ Report status updated successfully")

                            // Update local state immediately
                            _state.value = _state.value.copy(
                                unresolvedReports = _state.value.unresolvedReports.map { report ->
                                    if (report.id == reportId) {
                                        report.copy(
                                            status = status,
                                            isResolved = status == "RESOLVED"
                                        )
                                    } else {
                                        report
                                    }
                                },
                                friendReports = _state.value.friendReports.map { report ->
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

                            // Real-time listeners will update automatically
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(
                                error = "Failed to update report: ${e.message}"
                            )
                            Log.e(TAG, "❌ Error updating report", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message
                )
                Log.e(TAG, "❌ Exception updating report", e)
            }
        }
    }

    /**
     * Load criminal locations
     */
    fun loadCriminalLocations() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                Log.d(TAG, "Loading criminal locations...")
                val result = firestoreService.getCriminalLocations()

                result.onSuccess { locations ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        criminalLocations = locations,
                        error = null
                    )
                    Log.d(TAG, "✅ Loaded ${locations.size} criminal locations")
                }.onFailure { exception ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load criminal locations"
                    )
                    Log.e(TAG, "❌ Error loading criminal locations", exception)
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
                Log.e(TAG, "❌ Exception loading criminal locations", e)
            }
        }
    }

    /**
     * Load location history for a specific criminal
     */
    fun loadLocationHistory(criminalId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading location history for criminal: $criminalId")
                val result = firestoreService.getCriminalLocationHistory(criminalId)

                result.onSuccess { history ->
                    _state.value = _state.value.copy(
                        selectedCriminalHistory = history
                    )
                    Log.d(TAG, "✅ Loaded ${history.size} location history entries for $criminalId")
                }.onFailure { exception ->
                    Log.e(TAG, "❌ Error loading location history", exception)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading location history", e)
            }
        }
    }

    /**
     * Refresh all data
     */
    fun refresh() {
        Log.d(TAG, "🔄 Refreshing all data...")
        loadCriminalLocations()
        loadUnresolvedReports()
        loadFriendReports()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Stop real-time updates
     */
    private fun stopRealtimeUpdates() {
        unresolvedReportsListener?.remove()
        unresolvedReportsListener = null
        friendReportsListener?.remove()
        friendReportsListener = null
        Log.d(TAG, "⏹️ Stopped all real-time updates")
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeUpdates()
        Log.d(TAG, "❌ ViewModel cleared")
    }
}

class MapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}