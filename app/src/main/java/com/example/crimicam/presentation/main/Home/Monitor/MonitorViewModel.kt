package com.example.crimicam.presentation.main.Home.Monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.util.Result
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
        loadSessions()
        observeSessions()
    }

    fun loadSessions() {
        _state.value = _state.value.copy(isLoading = true)
    }

    private fun observeSessions() {
        viewModelScope.launch {
            repository.observeAllSessions().collect { sessions ->
                _state.value = _state.value.copy(
                    sessions = sessions,
                    isLoading = false
                )
            }
        }
    }

    fun selectSession(session: WebRTCSession) {
        _state.value = _state.value.copy(selectedSession = session)
    }

    fun clearSelectedSession() {
        _state.value = _state.value.copy(selectedSession = null)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            when (repository.deleteSession(sessionId)) {
                is Result.Success -> {
                    // Session deleted successfully
                }
                is Result.Error -> {
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