// WebRTC Signaling Repository - Fixed to use your Result class
package com.example.crimicam.data.repository

import android.util.Log
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WebRTCSignalingRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val sessionsCollection = firestore.collection("webrtc_sessions")
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "WebRTCRepository"
    }

    /**
     * Create a new streaming session
     */
    suspend fun createSession(session: WebRTCSession): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not authenticated"))

            val sessionData = session.copy(userId = userId)
            val docRef = sessionsCollection.add(sessionData).await()

            Log.d(TAG, "Session created: ${docRef.id}")
            Result.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            Result.Error(e)
        }
    }

    /**
     * Update session streaming status
     */
    suspend fun updateSessionStatus(sessionId: String, isStreaming: Boolean): Result<Unit> {
        return try {
            sessionsCollection.document(sessionId)
                .update(
                    mapOf(
                        "isStreaming" to isStreaming,
                        "lastHeartbeat" to System.currentTimeMillis()
                    )
                )
                .await()

            Log.d(TAG, "Session status updated: $sessionId -> $isStreaming")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update session status", e)
            Result.Error(e)
        }
    }

    /**
     * Update session heartbeat
     */
    suspend fun updateHeartbeat(sessionId: String, timestamp: Long): Result<Unit> {
        return try {
            sessionsCollection.document(sessionId)
                .update("lastHeartbeat", timestamp)
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update heartbeat", e)
            Result.Error(e)
        }
    }

    /**
     * Update session location
     */
    suspend fun updateSessionLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double
    ): Result<Unit> {
        return try {
            sessionsCollection.document(sessionId)
                .update(
                    mapOf(
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "lastHeartbeat" to System.currentTimeMillis()
                    )
                )
                .await()

            Log.d(TAG, "Session location updated: $sessionId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update session location", e)
            Result.Error(e)
        }
    }

    /**
     * Observe all active streaming sessions for the current user
     */
    fun observeAllSessions(): Flow<List<WebRTCSession>> = callbackFlow {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.e(TAG, "❌ User not authenticated in observeAllSessions")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📡 Setting up Firestore listener")
        Log.d(TAG, "  Collection: webrtc_sessions")
        Log.d(TAG, "  Filtering by userId: $userId")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val listener = sessionsCollection
            .whereEqualTo("userId", userId)
            .orderBy("lastHeartbeat", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Firestore listener error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                Log.d(TAG, "📦 Firestore snapshot received")
                Log.d(TAG, "  Documents count: ${snapshots?.size() ?: 0}")

                val sessions = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        Log.d(TAG, "  📄 Document ID: ${doc.id}")
                        Log.d(TAG, "    Data: ${doc.data}")

                        val session = doc.toObject(WebRTCSession::class.java)?.copy(id = doc.id)

                        if (session != null) {
                            Log.d(TAG, "    ✅ Parsed successfully")
                            Log.d(TAG, "      Device: ${session.deviceName}")
                            Log.d(TAG, "      Streaming: ${session.isStreaming}")
                            Log.d(TAG, "      Heartbeat: ${session.lastHeartbeat}")
                        } else {
                            Log.w(TAG, "    ⚠️ Failed to parse document")
                        }

                        session
                    } catch (e: Exception) {
                        Log.e(TAG, "    ❌ Error parsing document ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "✅ Successfully parsed ${sessions.size} sessions")
                trySend(sessions)
            }

        awaitClose {
            Log.d(TAG, "🔌 Closing Firestore session observer")
            listener.remove()
        }
    }

    /**
     * Get a specific session
     */
    suspend fun getSession(sessionId: String): Result<WebRTCSession> {
        return try {
            val doc = sessionsCollection.document(sessionId).get().await()
            val session = doc.toObject(WebRTCSession::class.java)?.copy(id = doc.id)

            if (session != null) {
                Result.Success(session)
            } else {
                Result.Error(Exception("Session not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session", e)
            Result.Error(e)
        }
    }

    /**
     * Delete a session
     */
    suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            sessionsCollection.document(sessionId).delete().await()
            Log.d(TAG, "Session deleted: $sessionId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session", e)
            Result.Error(e)
        }
    }

    /**
     * Clean up stale sessions (no heartbeat for 30 seconds)
     */
    suspend fun cleanupStaleSessions(): Result<Int> {
        return try {
            val thirtySecondsAgo = System.currentTimeMillis() - 30000

            val staleSessions = sessionsCollection
                .whereLessThan("lastHeartbeat", thirtySecondsAgo)
                .get()
                .await()

            var count = 0
            staleSessions.documents.forEach { doc ->
                doc.reference.update("isStreaming", false).await()
                count++
            }

            Log.d(TAG, "Cleaned up $count stale sessions")
            Result.Success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup stale sessions", e)
            Result.Error(e)
        }
    }
}