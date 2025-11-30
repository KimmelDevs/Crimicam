package com.example.crimicam.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages user session and provides current user ID safely
 * This ensures we always have a valid user ID for database operations
 */
class UserSessionManager {

    private val auth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            _isLoggedIn.value = firebaseAuth.currentUser != null
        }
    }

    /**
     * Get current user ID
     * @throws IllegalStateException if user is not logged in
     */
    fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be logged in to access this feature")
    }

    /**
     * Get current user ID or null if not logged in
     */
    fun getCurrentUserIdOrNull(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Check if user is currently logged in
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    /**
     * Get current Firebase user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
    }

    companion object {
        @Volatile
        private var instance: UserSessionManager? = null

        fun getInstance(): UserSessionManager {
            return instance ?: synchronized(this) {
                instance ?: UserSessionManager().also { instance = it }
            }
        }
    }
}