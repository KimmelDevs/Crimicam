package com.example.crimicam.presentation.main.Profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState

    fun logout() {
        viewModelScope.launch {
            _logoutState.value = LogoutState.Loading
            try {
                authRepository.logout()
                _logoutState.value = LogoutState.Success
            } catch (e: Exception) {
                _logoutState.value = LogoutState.Error(e.message ?: "Logout failed")
            }
        }
    }

    fun resetLogoutState() {
        _logoutState.value = LogoutState.Idle
    }
}

sealed class LogoutState {
    object Idle : LogoutState()
    object Loading : LogoutState()
    object Success : LogoutState()
    data class Error(val message: String) : LogoutState()
}