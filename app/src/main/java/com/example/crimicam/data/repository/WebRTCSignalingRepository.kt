package com.example.crimicam.data.repository

import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate

class WebRTCSignalingRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun getSessionsCollection() = firestore.collection("webrtc_sessions")

    /**
     * Create a new WebRTC session (broadcaster)
     */
    suspend fun createSession(
        deviceName: String,
        offer: String,
        latitude: Double? = null,
        longitude: Double? = null,
        address: String? = null
    ): Result<WebRTCSession> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val deviceId = android.provider.Settings.Secure.ANDROID_ID ?: "unknown"

            val session = WebRTCSession(
                id = userId,
                userId = userId,
                deviceName = deviceName,
                deviceId = deviceId,
                isStreaming = true,
                offer = offer,
                streamStartedAt = System.currentTimeMillis(),
                lastHeartbeat = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                address = address
            )

            getSessionsCollection()
                .document(userId)
                .set(session)
                .await()

            Result.Success(session)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Update session with answer (viewer responds)
     */
    suspend fun setAnswer(sessionId: String, answer: String): Result<Unit> {
        return try {
            getSessionsCollection()
                .document(sessionId)
                .update("answer", answer)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Add ICE candidate
     */
    suspend fun addIceCandidate(sessionId: String, candidate: IceCandidate): Result<Unit> {
        return try {
            val candidateString = "${candidate.sdpMid},${candidate.sdpMLineIndex},${candidate.sdp}"

            getSessionsCollection()
                .document(sessionId)
                .update(
                    "iceCandidates",
                    com.google.firebase.firestore.FieldValue.arrayUnion(candidateString)
                )
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Observe session changes in real-time
     */
    fun observeSession(sessionId: String): Flow<WebRTCSession?> = callbackFlow {
        val listener = getSessionsCollection()
            .document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                val session = snapshot?.toObject(WebRTCSession::class.java)
                trySend(session)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get all active sessions for this user
     */
    fun observeAllSessions(): Flow<List<WebRTCSession>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = getSessionsCollection()
            .whereEqualTo("userId", userId)
            .whereEqualTo("isStreaming", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull {
                    it.toObject(WebRTCSession::class.java)
                } ?: emptyList()

                trySend(sessions)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update heartbeat to keep session alive
     */
    suspend fun updateHeartbeat(sessionId: String): Result<Unit> {
        return try {
            getSessionsCollection()
                .document(sessionId)
                .update("lastHeartbeat", System.currentTimeMillis())
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Stop streaming session
     */
    suspend fun stopSession(sessionId: String): Result<Unit> {
        return try {
            getSessionsCollection()
                .document(sessionId)
                .update("isStreaming", false)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete session completely
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            getSessionsCollection()
                .document(sessionId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}