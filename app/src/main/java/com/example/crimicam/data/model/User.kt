package com.example.crimicam.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis()
)