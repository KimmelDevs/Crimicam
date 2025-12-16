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

    init {
        Log.d("MonitorViewModel", "🎬 ViewModel initialized")
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("MonitorViewModel", "📱 Current User ID: $userId")
        loadSessions()
        observeSessions()
    }

    fun loadSessions() {
        Log.d("MonitorViewModel", "🔄 Loading sessions...")
        _state.value = _state.value.copy(isLoading = true)
    }

    private fun observeSessions() {
        viewModelScope.launch {
            Log.d("MonitorViewModel", "👀 Starting to observe sessions...")
            try {
                repository.observeAllSessions().collect { sessions ->
                    Log.d("MonitorViewModel", "📡 Received ${sessions.size} sessions")

                    if (sessions.isEmpty()) {
                        Log.w("MonitorViewModel", "⚠️ No sessions found")
                    } else {
                        sessions.forEachIndexed { index, session ->
                            Log.d("MonitorViewModel", "[$index] Device: ${session.deviceName}")
                            Log.d("MonitorViewModel", "     ID: ${session.id}")
                            Log.d("MonitorViewModel", "     UserID: ${session.userId}")
                            Log.d("MonitorViewModel", "     DeviceID: ${session.deviceId}")
                            Log.d("MonitorViewModel", "     Streaming: ${session.isStreaming}")
                        }
                    }

                    _state.value = _state.value.copy(
                        sessions = sessions,
                        isLoading = false
                    )

                    Log.d("MonitorViewModel", "✅ UI State updated")
                }
            } catch (e: Exception) {
                Log.e("MonitorViewModel", "❌ Error: ${e.message}", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun selectSession(session: WebRTCSession) {
        Log.d("MonitorViewModel", "✅ Selected: ${session.deviceName} (${session.id})")
        _state.value = _state.value.copy(selectedSession = session)
    }

    fun clearSelectedSession() {
        _state.value = _state.value.copy(selectedSession = null)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            Log.d("MonitorViewModel", "🗑️ Deleting session: $sessionId")
            when (repository.deleteSession(sessionId)) {
                is Result.Success -> {
                    Log.d("MonitorViewModel", "✅ Deleted successfully")
                }
                is Result.Error -> {
                    Log.e("MonitorViewModel", "❌ Failed to delete")
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
}