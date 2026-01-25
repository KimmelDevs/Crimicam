package com.example.crimicam.data.repository

import com.example.crimicam.data.model.FriendRequest
import com.example.crimicam.data.model.User
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun signUp(name: String, email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                // Generate unique friend code
                val friendCode = generateUniqueFriendCode()

                // Save user data to Firestore with friend code
                val user = User(
                    uid = firebaseUser.uid,
                    name = name,
                    email = email,
                    friendCode = friendCode,
                    friends = emptyList(),
                    friendRequests = emptyList(),
                    createdAt = System.currentTimeMillis()
                )
                saveUserToFirestore(user)
                Result.Success(firebaseUser)
            } else {
                Result.Error(Exception("Signup failed"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                Result.Success(firebaseUser)
            } else {
                Result.Error(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    private suspend fun saveUserToFirestore(user: User) {
        try {
            firestore.collection("users")
                .document(user.uid)
                .set(user)
                .await()
        } catch (e: Exception) {
            throw e
        }
    }

    // Generate a unique 6-character friend code
    private suspend fun generateUniqueFriendCode(): String {
        val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var code: String
        var attempts = 0

        do {
            // Generate 6-character code
            code = (1..6)
                .map { characters.random() }
                .joinToString("")

            // Check if code already exists
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("friendCode", code)
                .limit(1)
                .get()
                .await()

            attempts++

            // If code doesn't exist or we've tried too many times, use it
            if (querySnapshot.isEmpty || attempts >= 5) {
                break
            }
        } while (true)

        return code
    }

    // Friend management functions
    suspend fun addFriendByCode(currentUserId: String, friendCode: String): Result<String> {
        return try {
            // Find user by friend code
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("friendCode", friendCode)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.Error(Exception("User with this code not found"))
            }

            val friendDoc = querySnapshot.documents[0]
            val friendId = friendDoc.id

            // Check if trying to add yourself
            if (friendId == currentUserId) {
                return Result.Error(Exception("Cannot add yourself as a friend"))
            }

            // Check if already friends
            val currentUserDoc = firestore.collection("users")
                .document(currentUserId)
                .get()
                .await()

            val currentFriends = currentUserDoc.get("friends") as? List<String> ?: emptyList()
            if (currentFriends.contains(friendId)) {
                return Result.Error(Exception("Already friends with this user"))
            }

            // Check if there's already a pending request
            val existingRequest = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", currentUserId)
                .whereEqualTo("toUserId", friendId)
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .await()

            if (!existingRequest.isEmpty) {
                return Result.Error(Exception("Friend request already sent"))
            }

            // Send friend request instead of auto-adding
            val requestId = "${currentUserId}_${friendId}"

            // Get user names for the request
            val currentUserName = currentUserDoc.getString("name") ?: "Unknown User"
            val friendName = friendDoc.getString("name") ?: "Unknown User"

            val request = hashMapOf<String, Any>(
                "fromUserId" to currentUserId,
                "toUserId" to friendId,
                "fromUserName" to currentUserName,
                "toUserName" to friendName,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("friend_requests")
                .document(requestId)
                .set(request)
                .await()

            // Add request to user's friendRequests list using FieldValue
            firestore.collection("users")
                .document(friendId)
                .update("friendRequests", FieldValue.arrayUnion(requestId))
                .await()

            Result.Success("Friend request sent successfully!")

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUserFriendCode(userId: String): Result<String> {
        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            val friendCode = document.getString("friendCode")
            if (friendCode.isNullOrEmpty()) {
                Result.Error(Exception("Friend code not found"))
            } else {
                Result.Success(friendCode)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getFriends(userId: String): Result<List<User>> {
        return try {
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            val friendIds = userDoc.get("friends") as? List<String> ?: emptyList()

            if (friendIds.isEmpty()) {
                return Result.Success(emptyList())
            }

            val friends = mutableListOf<User>()
            for (friendId in friendIds) {
                val friendDoc = firestore.collection("users")
                    .document(friendId)
                    .get()
                    .await()

                val friend = friendDoc.toObject(User::class.java)
                if (friend != null) {
                    friends.add(friend)
                }
            }

            Result.Success(friends)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchUserByFriendCode(friendCode: String): Result<User?> {
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("friendCode", friendCode)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Result.Success(null)
            } else {
                val user = querySnapshot.documents[0].toObject(User::class.java)
                Result.Success(user)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // Friend request system
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String): Result<String> {
        return try {
            // Get user names for the request
            val fromUserDoc = firestore.collection("users")
                .document(fromUserId)
                .get()
                .await()

            val toUserDoc = firestore.collection("users")
                .document(toUserId)
                .get()
                .await()

            val fromUserName = fromUserDoc.getString("name") ?: "Unknown User"
            val toUserName = toUserDoc.getString("name") ?: "Unknown User"

            val request = hashMapOf<String, Any>(
                "fromUserId" to fromUserId,
                "toUserId" to toUserId,
                "fromUserName" to fromUserName,
                "toUserName" to toUserName,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            val requestId = "${fromUserId}_${toUserId}"
            firestore.collection("friend_requests")
                .document(requestId)
                .set(request)
                .await()

            // Add to user's friendRequests list using FieldValue
            firestore.collection("users")
                .document(toUserId)
                .update("friendRequests", FieldValue.arrayUnion(requestId))
                .await()

            Result.Success("Friend request sent")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getFriendRequests(userId: String): Result<List<FriendRequest>> {
        return try {
            // Get incoming requests where user is the receiver
            val querySnapshot = firestore.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val requests = mutableListOf<FriendRequest>()
            for (document in querySnapshot.documents) {
                val request = FriendRequest(
                    id = document.id,
                    fromUserId = document.getString("fromUserId") ?: "",
                    toUserId = document.getString("toUserId") ?: "",
                    fromUserName = document.getString("fromUserName") ?: "Unknown User",
                    toUserName = document.getString("toUserName") ?: "Unknown User",
                    status = document.getString("status") ?: "pending",
                    createdAt = document.getLong("createdAt") ?: 0
                )
                requests.add(request)
            }

            Result.Success(requests)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun acceptFriendRequest(requestId: String, currentUserId: String): Result<String> {
        return try {
            // Get the request
            val requestDoc = firestore.collection("friend_requests")
                .document(requestId)
                .get()
                .await()

            if (!requestDoc.exists()) {
                return Result.Error(Exception("Friend request not found"))
            }

            val fromUserId = requestDoc.getString("fromUserId") ?: ""
            val toUserId = requestDoc.getString("toUserId") ?: ""

            // Verify current user is the receiver
            if (currentUserId != toUserId) {
                return Result.Error(Exception("Not authorized to accept this request"))
            }

            // Update both users' friends lists
            // Add friend to current user's friends list
            firestore.collection("users")
                .document(currentUserId)
                .update("friends", FieldValue.arrayUnion(fromUserId))
                .await()

            // Add current user to requester's friends list
            firestore.collection("users")
                .document(fromUserId)
                .update("friends", FieldValue.arrayUnion(currentUserId))
                .await()

            // Update request status to accepted
            firestore.collection("friend_requests")
                .document(requestId)
                .update("status", "accepted")
                .await()

            // Remove request from user's friendRequests list
            firestore.collection("users")
                .document(currentUserId)
                .update("friendRequests", FieldValue.arrayRemove(requestId))
                .await()

            Result.Success("Friend request accepted")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun declineFriendRequest(requestId: String, currentUserId: String): Result<String> {
        return try {
            // Get the request
            val requestDoc = firestore.collection("friend_requests")
                .document(requestId)
                .get()
                .await()

            if (!requestDoc.exists()) {
                return Result.Error(Exception("Friend request not found"))
            }

            val toUserId = requestDoc.getString("toUserId") ?: ""

            // Verify current user is the receiver
            if (currentUserId != toUserId) {
                return Result.Error(Exception("Not authorized to decline this request"))
            }

            // Update request status to declined
            firestore.collection("friend_requests")
                .document(requestId)
                .update("status", "declined")
                .await()

            // Remove request from user's friendRequests list
            firestore.collection("users")
                .document(currentUserId)
                .update("friendRequests", FieldValue.arrayRemove(requestId))
                .await()

            Result.Success("Friend request declined")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun cancelFriendRequest(requestId: String, currentUserId: String): Result<String> {
        return try {
            // Get the request
            val requestDoc = firestore.collection("friend_requests")
                .document(requestId)
                .get()
                .await()

            if (!requestDoc.exists()) {
                return Result.Error(Exception("Friend request not found"))
            }

            val fromUserId = requestDoc.getString("fromUserId") ?: ""
            val toUserId = requestDoc.getString("toUserId") ?: ""

            // Verify current user is the sender
            if (currentUserId != fromUserId) {
                return Result.Error(Exception("Not authorized to cancel this request"))
            }

            // Delete the request
            firestore.collection("friend_requests")
                .document(requestId)
                .delete()
                .await()

            // Remove request from receiver's friendRequests list
            firestore.collection("users")
                .document(toUserId)
                .update("friendRequests", FieldValue.arrayRemove(requestId))
                .await()

            Result.Success("Friend request cancelled")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun removeFriend(currentUserId: String, friendId: String): Result<String> {
        return try {
            // Remove friend from current user's friends list
            firestore.collection("users")
                .document(currentUserId)
                .update("friends", FieldValue.arrayRemove(friendId))
                .await()

            // Remove current user from friend's friends list
            firestore.collection("users")
                .document(friendId)
                .update("friends", FieldValue.arrayRemove(currentUserId))
                .await()

            Result.Success("Friend removed successfully")
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getSentFriendRequests(userId: String): Result<List<FriendRequest>> {
        return try {
            val querySnapshot = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            val requests = mutableListOf<FriendRequest>()
            for (document in querySnapshot.documents) {
                val request = FriendRequest(
                    id = document.id,
                    fromUserId = document.getString("fromUserId") ?: "",
                    toUserId = document.getString("toUserId") ?: "",
                    fromUserName = document.getString("fromUserName") ?: "Unknown User",
                    toUserName = document.getString("toUserName") ?: "Unknown User",
                    status = document.getString("status") ?: "pending",
                    createdAt = document.getLong("createdAt") ?: 0
                )
                requests.add(request)
            }

            Result.Success(requests)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // Helper function to get user by ID
    suspend fun getUserById(userId: String): Result<User?> {
        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val user = document.toObject(User::class.java)
                Result.Success(user)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // Check if friend request already exists
    suspend fun checkExistingFriendRequest(fromUserId: String, toUserId: String): Boolean {
        return try {
            val querySnapshot = firestore.collection("friend_requests")
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .await()

            !querySnapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    // Get pending friend request count
    suspend fun getPendingRequestCount(userId: String): Result<Int> {
        return try {
            val querySnapshot = firestore.collection("friend_requests")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            Result.Success(querySnapshot.size())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}