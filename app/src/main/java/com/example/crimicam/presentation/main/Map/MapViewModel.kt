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
import kotlinx.coroutines.tasks.await

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

    // ── real-time listeners ───────────────────────────────────────────

    private fun startRealtimeReportUpdates() {
        unresolvedReportsListener?.remove()

        try {
            unresolvedReportsListener = firestore.collection("emergency_reports")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to unresolved reports", error)
                        _state.value = _state.value.copy(error = error.message ?: "Failed to load reports")
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing reports", e)
                            _state.value = _state.value.copy(error = e.message ?: "Failed to process reports")
                        }
                    }
                }
            Log.d(TAG, "✅ Started real-time updates for unresolved reports")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up real-time listener for reports", e)
            _state.value = _state.value.copy(error = e.message ?: "Failed to setup updates")
        }
    }

    private fun startRealtimeFriendReportsUpdates() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for real-time friend reports")
            return
        }
        friendReportsListener?.remove()

        try {
            friendReportsListener = firestore.collection("emergency_reports")
                .whereArrayContains("friendsNotified", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to friend reports", error)
                        _state.value = _state.value.copy(error = error.message ?: "Failed to load friend reports")
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing friend reports", e)
                            _state.value = _state.value.copy(error = e.message ?: "Failed to process friend reports")
                        }
                    }
                }
            Log.d(TAG, "✅ Started real-time updates for friend reports")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up real-time listener for friend reports", e)
            _state.value = _state.value.copy(error = e.message ?: "Failed to setup friend reports updates")
        }
    }

    // ── manual load / refresh ─────────────────────────────────────────

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
                                        Log.e(TAG, "Error parsing report: ${doc.id}", e); null
                                    }
                                }
                                _state.value = _state.value.copy(unresolvedReports = reports, isLoading = false, error = null)
                                Log.d(TAG, "✅ Manual: Loaded ${reports.size} unresolved reports")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing reports", e)
                                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to process reports")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(isLoading = false, error = "Failed to load reports: ${e.message}")
                            Log.e(TAG, "❌ Error loading unresolved reports", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
                Log.e(TAG, "❌ Exception loading unresolved reports", e)
            }
        }
    }

    fun loadFriendReports() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _state.value = _state.value.copy(error = "User not authenticated")
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
                                        Log.e(TAG, "Error parsing friend report: ${doc.id}", e); null
                                    }
                                }
                                _state.value = _state.value.copy(friendReports = reports, isLoading = false, error = null)
                                Log.d(TAG, "✅ Manual: Loaded ${reports.size} friend reports")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing friend reports", e)
                                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to process friend reports")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(isLoading = false, error = "Failed to load friend reports: ${e.message}")
                            Log.e(TAG, "❌ Error loading friend reports", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
                Log.e(TAG, "❌ Exception loading friend reports", e)
            }
        }
    }

    // ── report status ─────────────────────────────────────────────────

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
                            _state.value = _state.value.copy(
                                unresolvedReports = _state.value.unresolvedReports.map { report ->
                                    if (report.id == reportId) report.copy(status = status, isResolved = status == "RESOLVED") else report
                                },
                                friendReports = _state.value.friendReports.map { report ->
                                    if (report.id == reportId) report.copy(status = status, isResolved = status == "RESOLVED") else report
                                }
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        viewModelScope.launch {
                            _state.value = _state.value.copy(error = "Failed to update report: ${e.message}")
                            Log.e(TAG, "❌ Error updating report", e)
                        }
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
                Log.e(TAG, "❌ Exception updating report", e)
            }
        }
    }

    // ── criminal locations ────────────────────────────────────────────

    fun loadCriminalLocations() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                Log.d(TAG, "Loading criminal locations...")
                val result = firestoreService.getCriminalLocations()
                result.onSuccess { locations ->
                    _state.value = _state.value.copy(isLoading = false, criminalLocations = locations, error = null)
                    Log.d(TAG, "✅ Loaded ${locations.size} criminal locations")
                }.onFailure { exception ->
                    _state.value = _state.value.copy(isLoading = false, error = exception.message ?: "Failed to load criminal locations")
                    Log.e(TAG, "❌ Error loading criminal locations", exception)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Unknown error occurred")
                Log.e(TAG, "❌ Exception loading criminal locations", e)
            }
        }
    }

    fun loadLocationHistory(criminalId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading location history for criminal: $criminalId")
                val result = firestoreService.getCriminalLocationHistory(criminalId)
                result.onSuccess { history ->
                    _state.value = _state.value.copy(selectedCriminalHistory = history)
                    Log.d(TAG, "✅ Loaded ${history.size} location history entries for $criminalId")
                }.onFailure { exception ->
                    Log.e(TAG, "❌ Error loading location history", exception)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading location history", e)
            }
        }
    }

    // ── WRONG WAY → notify friends ────────────────────────────────────
    /**
     * Called by NavigationMonitor when the user has been moving away from
     * the destination 3 times in a row.
     *
     * Writes a report of type "WRONG_WAY" into emergency_reports and
     * populates friendsNotified with every uid in the current user's
     * "friends" sub-collection — exactly the same pattern used by
     * EmergencyReportViewModel.createReport / EmergencyReportRepository.
     *
     * @param destLat  Latitude of the destination the user was heading to.
     * @param destLon  Longitude of the destination the user was heading to.
     * @param userLat  Current latitude of the user.
     * @param userLon  Current longitude of the user.
     */
    fun notifyFriendsWrongWay(
        destLat: Double,
        destLon: Double,
        userLat: Double,
        userLon: Double
    ) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.e(TAG, "❌ notifyFriendsWrongWay — no authenticated user")
                    _state.value = _state.value.copy(error = "Not authenticated")
                    return@launch
                }

                Log.d(TAG, "🚨 notifyFriendsWrongWay — fetching friends list...")

                // ── 1. grab every friend uid from the user's friends sub-collection ──
                val friendsSnapshot = firestore
                    .collection("users")
                    .document(currentUser.uid)
                    .collection("friends")
                    .get()
                    .await()

                val friendUids = friendsSnapshot.documents.mapNotNull { it.getString("uid") }

                if (friendUids.isEmpty()) {
                    Log.w(TAG, "⚠️  No friends to notify")
                    _state.value = _state.value.copy(error = "No friends to notify")
                    return@launch
                }

                Log.d(TAG, "📋 Friends to notify: $friendUids")

                // ── 2. build the report document ──────────────────────────────────
                val reportData = hashMapOf<String, Any>(
                    "title"            to "⚠️ Wrong Way Alert",
                    "description"      to "I seem to be going the wrong way! My destination was (${"%.4f".format(destLat)}, ${"%.4f".format(destLon)}). " +
                            "I am currently at (${"%.4f".format(userLat)}, ${"%.4f".format(userLon)}).",
                    "type"             to "WRONG_WAY",
                    "status"           to "ACTIVE",
                    "isResolved"       to false,
                    "userId"          to currentUser.uid,
                    "userName"         to (currentUser.displayName ?: currentUser.email ?: "Unknown"),
                    "friendsNotified"  to friendUids,
                    "timestamp"        to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    // location map  — matches how your EmergencyReport.location is stored
                    "location"         to hashMapOf(
                        "latitude"  to userLat,
                        "longitude" to userLon
                    ),
                    "address"          to ""   // you could reverse-geocode here if you want
                )

                // ── 3. write to Firestore ─────────────────────────────────────────
                val docRef = firestore.collection("emergency_reports").add(reportData).await()
                Log.d(TAG, "✅ Wrong-way report created: ${docRef.id}")

                // ── 4. write a notification into each friend's notifications sub-col ─
                val notificationData = hashMapOf<String, Any>(
                    "reportId"   to docRef.id,
                    "userId"    to currentUser.uid,
                    "userName"   to (currentUser.displayName ?: currentUser.email ?: "Unknown"),
                    "title"      to "⚠️ Wrong Way Alert",
                    "body"       to "${currentUser.displayName ?: "A friend"} seems to be going the wrong way!",
                    "type"       to "WRONG_WAY",
                    "reportType" to "WRONG_WAY",
                    "address"    to "",
                    "timestamp"  to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "read"       to false
                )

                for (friendUid in friendUids) {
                    firestore
                        .collection("users")
                        .document(friendUid)
                        .collection("notifications")
                        .add(notificationData)
                        .await()
                }

                Log.d(TAG, "✅ Notifications sent to ${friendUids.size} friends")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in notifyFriendsWrongWay", e)
                _state.value = _state.value.copy(error = "Failed to notify friends: ${e.message}")
            }
        }
    }

    // ── utility ───────────────────────────────────────────────────────

    fun refresh() {
        Log.d(TAG, "🔄 Refreshing all data...")
        loadCriminalLocations()
        loadUnresolvedReports()
        loadFriendReports()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

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