package com.example.crimicam.presentation.signup


data class SignupState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)