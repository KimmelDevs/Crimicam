package com.example.crimicam.presentation.main.Admin

import com.example.crimicam.data.model.Criminal
data class AdminState(
    val isLoading: Boolean = false,
    val criminals: List<Criminal> = emptyList(),
    val errorMessage: String? = null,
    val isProcessing: Boolean = false,
    val successMessage: String? = null,
    val criminalCount: Long = 0L
)
