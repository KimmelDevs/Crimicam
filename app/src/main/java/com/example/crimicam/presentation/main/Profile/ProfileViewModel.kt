package com.example.crimicam.presentation.main.Profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.FriendRequest
import com.example.crimicam.data.model.User
import com.example.crimicam.data.repository.AuthRepository
import com.example.crimicam.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val authRepository = AuthRepository()

    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState

    private val _friendCode = MutableStateFlow<String?>(null)
    val friendCode: StateFlow<String?> = _friendCode

    private val _isLoadingFriendCode = MutableStateFlow(false)
    val isLoadingFriendCode: StateFlow<Boolean> = _isLoadingFriendCode

    private val _friendCodeError = MutableStateFlow<String?>(null)
    val friendCodeError: StateFlow<String?> = _friendCodeError

    private val _friendsList = MutableStateFlow<List<User>>(emptyList())
    val friendsList: StateFlow<List<User>> = _friendsList

    private val _friendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests

    private val _sentFriendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val sentFriendRequests: StateFlow<List<FriendRequest>> = _sentFriendRequests

    private val _isLoadingFriends = MutableStateFlow(false)
    val isLoadingFriends: StateFlow<Boolean> = _isLoadingFriends

    private val _friendsError = MutableStateFlow<String?>(null)
    val friendsError: StateFlow<String?> = _friendsError

    private val _addFriendState = MutableStateFlow<AddFriendState>(AddFriendState.Idle)
    val addFriendState: StateFlow<AddFriendState> = _addFriendState

    private val _friendRequestsLoading = MutableStateFlow(false)
    val friendRequestsLoading: StateFlow<Boolean> = _friendRequestsLoading

    init {
        // Load data when ViewModel is created
        loadFriendCode()
        loadFriends()
        loadFriendRequests()
        loadSentFriendRequests()
    }

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

    fun loadFriendCode() {
        viewModelScope.launch {
            _isLoadingFriendCode.value = true
            _friendCodeError.value = null

            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.getUserFriendCode(currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            _friendCode.value = result.data
                        }
                        is Result.Error -> {
                            _friendCodeError.value = result.exception.message ?: "Failed to load friend code"
                        }
                        else -> {
                            _friendCodeError.value = "Unknown error loading friend code"
                        }
                    }
                } else {
                    _friendCodeError.value = "User not logged in"
                }
            } catch (e: Exception) {
                _friendCodeError.value = "Failed to load friend code: ${e.message}"
            } finally {
                _isLoadingFriendCode.value = false
            }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            _isLoadingFriends.value = true
            _friendsError.value = null

            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.getFriends(currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            _friendsList.value = result.data
                        }
                        is Result.Error -> {
                            _friendsError.value = result.exception.message ?: "Failed to load friends"
                        }
                        else -> {
                            _friendsError.value = "Unknown error loading friends"
                        }
                    }
                } else {
                    _friendsError.value = "User not logged in"
                }
            } catch (e: Exception) {
                _friendsError.value = "Failed to load friends: ${e.message}"
            } finally {
                _isLoadingFriends.value = false
            }
        }
    }

    fun loadFriendRequests() {
        viewModelScope.launch {
            _friendRequestsLoading.value = true

            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.getFriendRequests(currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            _friendRequests.value = result.data
                        }
                        is Result.Error -> {
                            // Handle error silently or show a message
                            _friendRequests.value = emptyList()
                        }
                        else -> {
                            _friendRequests.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                _friendRequests.value = emptyList()
            } finally {
                _friendRequestsLoading.value = false
            }
        }
    }

    fun loadSentFriendRequests() {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.getSentFriendRequests(currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            _sentFriendRequests.value = result.data
                        }
                        is Result.Error -> {
                            _sentFriendRequests.value = emptyList()
                        }
                        else -> {
                            _sentFriendRequests.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                _sentFriendRequests.value = emptyList()
            }
        }
    }

    fun addFriendByCode(friendCode: String) {
        viewModelScope.launch {
            _addFriendState.value = AddFriendState.Loading(friendCode)

            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.addFriendByCode(currentUser.uid, friendCode)

                    when (result) {
                        is Result.Success -> {
                            _addFriendState.value = AddFriendState.Success(result.data)
                            // Refresh sent requests list
                            loadSentFriendRequests()
                        }
                        is Result.Error -> {
                            _addFriendState.value = AddFriendState.Error(
                                friendCode = friendCode,
                                message = result.exception.message ?: "Failed to add friend"
                            )
                        }
                        else -> {
                            _addFriendState.value = AddFriendState.Error(
                                friendCode = friendCode,
                                message = "Unknown error"
                            )
                        }
                    }
                } else {
                    _addFriendState.value = AddFriendState.Error(
                        friendCode = friendCode,
                        message = "User not logged in"
                    )
                }
            } catch (e: Exception) {
                _addFriendState.value = AddFriendState.Error(
                    friendCode = friendCode,
                    message = e.message ?: "Failed to add friend"
                )
            }
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.acceptFriendRequest(requestId, currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            // Refresh both friend requests and friends list
                            loadFriendRequests()
                            loadFriends()
                        }
                        is Result.Error -> {
                            // Handle error
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.declineFriendRequest(requestId, currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            // Refresh friend requests list
                            loadFriendRequests()
                        }
                        is Result.Error -> {
                            // Handle error
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun cancelFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.cancelFriendRequest(requestId, currentUser.uid)

                    when (result) {
                        is Result.Success -> {
                            // Refresh sent requests list
                            loadSentFriendRequests()
                        }
                        is Result.Error -> {
                            // Handle error
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val result = authRepository.removeFriend(currentUser.uid, friendId)

                    when (result) {
                        is Result.Success -> {
                            // Refresh friends list
                            loadFriends()
                        }
                        is Result.Error -> {
                            // Handle error
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun resetAddFriendState() {
        _addFriendState.value = AddFriendState.Idle
    }

    fun getCurrentUser() = authRepository.getCurrentUser()
}

sealed class LogoutState {
    object Idle : LogoutState()
    object Loading : LogoutState()
    object Success : LogoutState()
    data class Error(val message: String) : LogoutState()
}

sealed class AddFriendState {
    object Idle : AddFriendState()
    data class Loading(val friendCode: String) : AddFriendState()
    data class Success(val message: String) : AddFriendState()
    data class Error(val friendCode: String, val message: String) : AddFriendState()
}