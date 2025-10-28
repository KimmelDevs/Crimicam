package com.example.crimicam.domain.repository

import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseUser

interface IAuthRepository {
    suspend fun signUp(email: String, password: String): Result<FirebaseUser>
    suspend fun login(email: String, password: String): Result<FirebaseUser>
    fun logout()
    fun getCurrentUser(): FirebaseUser?
    fun isUserLoggedIn(): Boolean
}