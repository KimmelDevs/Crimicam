package com.example.crimicam.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.repository.AuthRepository
import com.example.crimicam.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    init {
        checkIfUserLoggedIn()
    }

    private fun checkIfUserLoggedIn() {
        if (authRepository.isUserLoggedIn()) {
            _loginState.value = LoginState(isSuccess = true)
        }
    }

    fun login(email: String, password: String) {
        // Validation
        if (email.isBlank()) {
            _loginState.value = LoginState(errorMessage = "Email cannot be empty")
            return
        }

        if (password.isBlank()) {
            _loginState.value = LoginState(errorMessage = "Password cannot be empty")
            return
        }

        viewModelScope.launch {
            _loginState.value = LoginState(isLoading = true)

            when (val result = authRepository.login(email, password)) {
                is Result.Success -> {
                    _loginState.value = LoginState(isSuccess = true)
                }
                is Result.Error -> {
                    val errorMessage = when {
                        result.exception.message?.contains("password") == true ->
                            "Invalid email or password"
                        result.exception.message?.contains("network") == true ->
                            "Network error. Please check your connection"
                        else -> result.exception.message ?: "Login failed"
                    }
                    _loginState.value = LoginState(errorMessage = errorMessage)
                }
                is Result.Loading -> {
                    _loginState.value = LoginState(isLoading = true)
                }
            }
        }
    }

    fun resetState() {
        _loginState.value = LoginState()
    }
}