package com.example.crimicam.data.repository

import com.example.crimicam.data.model.User
import com.example.crimicam.domain.repository.IAuthRepository
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor() : IAuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun signUp(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                val user = User(
                    uid = firebaseUser.uid,
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

    override suspend fun login(email: String, password: String): Result<FirebaseUser> {
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

    override fun logout() {
        auth.signOut()
    }

    override fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    override fun isUserLoggedIn(): Boolean {
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
        }
    }
}