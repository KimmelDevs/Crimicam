package com.example.crimicam.data.repository

import com.example.crimicam.data.model.User
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun signUp(name: String, email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                // Save user data to Firestore with name
                val user = User(
                    uid = firebaseUser.uid,
                    name = name,
                    email = email,
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
            // Handle error
            throw e
        }
    }
}