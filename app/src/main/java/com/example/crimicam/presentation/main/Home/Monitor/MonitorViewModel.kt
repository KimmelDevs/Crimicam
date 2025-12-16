package com.example.crimicam.presentation.main.Home.Monitor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MonitorState(
    val sessions: List<WebRTCSession> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val selectedSession: WebRTCSession? = null
)

class MonitorViewModel(
    private val repository: WebRTCSignalingRepository = WebRTCSignalingRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    companion object {
        private const val TAG = "MonitorViewModel"
    }

    init {
        Log.d(TAG, "🎬 ViewModel initialized")
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "📱 Current User ID: $userId")

        if (userId == null) {
            Log.e(TAG, "❌ User not authenticated!")
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = "Please sign in to view cameras"
            )
        } else {
            loadSessions()
            observeSessions()
        }
    }

    fun loadSessions() {
        Log.d(TAG, "🔄 Loading sessions...")
        _state.value = _state.value.copy(isLoading = true)
    }

    private fun observeSessions() {
        viewModelScope.launch {
            Log.d(TAG, "👀 Starting to observe sessions...")
            try {
                repository.observeAllSessions().collect { sessions ->
                    Log.d(TAG, "📡 Received ${sessions.size} total sessions from repository")

                    if (sessions.isEmpty()) {
                        Log.w(TAG, "⚠️ No sessions found in database")
                    } else {
                        sessions.forEachIndexed { index, session ->
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d(TAG, "[$index] Session Details:")
                            Log.d(TAG, "  Device: ${session.deviceName}")
                            Log.d(TAG, "  ID: ${session.id}")
                            Log.d(TAG, "  UserID: ${session.userId}")
                            Log.d(TAG, "  DeviceID: ${session.deviceId}")
                            Log.d(TAG, "  Streaming: ${session.isStreaming}")
                            Log.d(TAG, "  Started: ${session.streamStartedAt}")
                            Log.d(TAG, "  Heartbeat: ${session.lastHeartbeat}")
                            Log.d(TAG, "  Age: ${System.currentTimeMillis() - session.lastHeartbeat}ms")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        }
                    }

                    // Filter for active streams
                    val activeSessions = sessions.filter {
                        val isActive = it.isStreaming
                        val isRecent = (System.currentTimeMillis() - it.lastHeartbeat) < 30000 // 30 seconds

                        Log.d(TAG, "Filtering ${it.deviceName}: isStreaming=$isActive, isRecent=$isRecent")

                        isActive && isRecent
                    }

                    Log.d(TAG, "✅ Active sessions after filter: ${activeSessions.size}")

                    _state.value = _state.value.copy(
                        sessions = activeSessions,
                        isLoading = false
                    )

                    Log.d(TAG, "✅ UI State updated with ${activeSessions.size} sessions")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error observing sessions: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun selectSession(session: WebRTCSession) {
        Log.d(TAG, "✅ Selected: ${session.deviceName} (${session.id})")
        _state.value = _state.value.copy(selectedSession = session)
    }

    fun clearSelectedSession() {
        _state.value = _state.value.copy(selectedSession = null)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            Log.d(TAG, "🗑️ Deleting session: $sessionId")
            when (repository.deleteSession(sessionId)) {
                is Result.Success -> {
                    Log.d(TAG, "✅ Deleted successfully")
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to delete")
                    _state.value = _state.value.copy(
                        errorMessage = "Failed to delete session"
                    )
                }
                is Result.Loading -> {}
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun refreshSessions() {
        Log.d(TAG, "🔄 Manual refresh requested")
        loadSessions()
    }
}

// Add this debug helper to CameraViewModel to verify streaming is actually starting
// Update the startWebRtcStreaming function in CameraViewModel:

/*
Add this logging to your CameraViewModel.startWebRtcStreaming():

fun startWebRtcStreaming() {
    viewModelScope.launch {
        try {
            val userId = auth.currentUser?.uid ?: run {
                Log.e(TAG, "❌ Cannot start stream - user not authenticated")
                updateStatusMessage("⚠️ Please sign in to start streaming")
                return@launch
            }

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )

            val deviceName = android.os.Build.MODEL

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "🎥 STARTING STREAM")
            Log.d(TAG, "  UserID: $userId")
            Log.d(TAG, "  DeviceID: $deviceId")
            Log.d(TAG, "  DeviceName: $deviceName")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Create session in Firestore
            val session = WebRTCSession(
                id = "",
                userId = userId,
                deviceId = deviceId,
                deviceName = deviceName,
                isStreaming = true,
                streamStartedAt = System.currentTimeMillis(),
                lastHeartbeat = System.currentTimeMillis(),
                latitude = _state.value.currentLocation?.latitude,
                longitude = _state.value.currentLocation?.longitude
            )

            Log.d(TAG, "📝 Creating session in Firestore...")
            val result = sessionRepository.createSession(session)

            when (result) {
                is Result.Success -> {
                    val sessionId = result.data
                    Log.d(TAG, "✅ Session created successfully!")
                    Log.d(TAG, "  SessionID: $sessionId")

                    currentSessionId = sessionId

                    // Start WebRTC
                    Log.d(TAG, "🔌 Starting WebRTC manager...")
                    webRtcManager.startStreaming(
                        sessionId = sessionId,
                        onIceCandidate = { candidate ->
                            handleIceCandidate(candidate, sessionId, userId, null)
                        },
                        onOfferCreated = { offer ->
                            handleOfferCreated(offer, sessionId, userId)
                        }
                    )

                    // Listen for viewer answers
                    observeViewerAnswers(sessionId, userId)

                    // Start heartbeat
                    startHeartbeat(sessionId)

                    _state.value = _state.value.copy(
                        isStreaming = true,
                        streamingSessionId = sessionId
                    )

                    updateStatusMessage("🔴 Live streaming started")
                    Log.d(TAG, "🎉 STREAMING ACTIVE: $sessionId")
                }
                is Result.Error -> {
                    Log.e(TAG, "❌ Failed to create session: ${result.exception.message}", result.exception)
                    updateStatusMessage("⚠️ Failed to create session")
                }
                else -> {
                    Log.w(TAG, "⚠️ Unexpected result type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start streaming", e)
            updateStatusMessage("⚠️ Failed to start streaming")
        }
    }
}
*/