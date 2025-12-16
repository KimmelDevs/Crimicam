package com.example.crimicam.presentation.main.Profile.ViewProfile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ViewProfileViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _userData = MutableStateFlow<User?>(null)
    val userData: StateFlow<User?> = _userData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _updateSuccess = MutableStateFlow(false)
    val updateSuccess: StateFlow<Boolean> = _updateSuccess.asStateFlow()

    init {
        loadUserData()
    }

    fun loadUserData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val currentUser = auth.currentUser
                println("DEBUG: Current user: $currentUser")

                if (currentUser != null) {
                    println("DEBUG: User UID: ${currentUser.uid}")

                    val userDocument = firestore.collection("users")
                        .document(currentUser.uid)
                        .get()
                        .await()

                    println("DEBUG: Document exists: ${userDocument.exists()}")
                    println("DEBUG: Document data: ${userDocument.data}")

                    if (userDocument.exists()) {
                        val user = userDocument.toObject(User::class.java)
                        println("DEBUG: Parsed user: $user")
                        _userData.value = user
                    } else {
                        val errorMsg = "User data not found in Firestore for UID: ${currentUser.uid}"
                        println("DEBUG: $errorMsg")
                        _errorMessage.value = errorMsg

                        val fallbackUser = User(
                            uid = currentUser.uid,
                            name = currentUser.displayName ?: "Unknown",
                            email = currentUser.email ?: "No email",
                            createdAt = System.currentTimeMillis()
                        )
                        _userData.value = fallbackUser
                    }
                } else {
                    val errorMsg = "No user logged in - currentUser is null"
                    println("DEBUG: $errorMsg")
                    _errorMessage.value = errorMsg
                }
            } catch (e: Exception) {
                val errorMsg = "Failed to load user data: ${e.message}"
                println("DEBUG: $errorMsg")
                _errorMessage.value = errorMsg
                e.printStackTrace()
            } finally {
                _isLoading.value = false
                println("DEBUG: Loading completed")
            }
        }
    }

    fun updateUserName(newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _updateSuccess.value = false

            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    firestore.collection("users")
                        .document(currentUser.uid)
                        .update("name", newName)
                        .await()

                    _userData.value = _userData.value?.copy(name = newName)
                    _updateSuccess.value = true
                } else {
                    _errorMessage.value = "No user logged in"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update username: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePassword(newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _updateSuccess.value = false

            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    currentUser.updatePassword(newPassword).await()
                    _updateSuccess.value = true
                } else {
                    _errorMessage.value = "No user logged in"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update password: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _updateSuccess.value = false
    }
}