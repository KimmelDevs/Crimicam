package com.example.crimicam.presentation.main.Home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.NotificationRepository
import com.example.crimicam.data.remote.FirestoreService
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    // Create dependencies directly in the ViewModel
    private val notificationRepository = NotificationRepository(
        firestoreService = FirestoreService(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
    )

    private val _notificationState = MutableStateFlow<NotificationState>(NotificationState.Idle)
    val notificationState: StateFlow<NotificationState> = _notificationState.asStateFlow()

    fun triggerNotification() {
        viewModelScope.launch {
            _notificationState.value = NotificationState.Loading
            when (val result = notificationRepository.triggerNotification()) {
                is Result.Success -> {
                    _notificationState.value = NotificationState.Success
                    // Reset to idle after a short delay
                    viewModelScope.launch {
                        delay(2000L)
                        _notificationState.value = NotificationState.Idle
                    }
                }
                is Result.Error -> {
                    _notificationState.value = NotificationState.Error(result.exception.message ?: "Failed to trigger notification")
                    // Reset to idle after a short delay
                    viewModelScope.launch {
                        delay(3000L)
                        _notificationState.value = NotificationState.Idle
                    }
                }
                else -> {
                    // This handles any other potential states, though Result should only have Success and Error
                    _notificationState.value = NotificationState.Idle
                }
            }
        }
    }
}

sealed class NotificationState {
    object Idle : NotificationState()
    object Loading : NotificationState()
    object Success : NotificationState()
    data class Error(val message: String) : NotificationState()
}