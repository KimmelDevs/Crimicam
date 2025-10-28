package com.example.crimicam.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.AuthRepository
import com.example.crimicam.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SignupViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _signupState = MutableStateFlow(SignupState())
    val signupState: StateFlow<SignupState> = _signupState.asStateFlow()

    fun signUp(email: String, password: String, confirmPassword: String) {
        // Validation
        if (email.isBlank()) {
            _signupState.value = SignupState(errorMessage = "Email cannot be empty")
            return
        }

        if (password.isBlank()) {
            _signupState.value = SignupState(errorMessage = "Password cannot be empty")
            return
        }

        if (password != confirmPassword) {
            _signupState.value = SignupState(errorMessage = "Passwords do not match")
            return
        }

        if (password.length < 6) {
            _signupState.value = SignupState(errorMessage = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _signupState.value = SignupState(isLoading = true)

            when (val result = authRepository.signUp(email, password)) {
                is Result.Success -> {
                    _signupState.value = SignupState(isSuccess = true)
                }
                is Result.Error -> {
                    _signupState.value = SignupState(
                        errorMessage = result.exception.message ?: "Signup failed"
                    )
                }
                is Result.Loading -> {
                    _signupState.value = SignupState(isLoading = true)
                }
            }
        }
    }

    fun resetState() {
        _signupState.value = SignupState()
    }
}