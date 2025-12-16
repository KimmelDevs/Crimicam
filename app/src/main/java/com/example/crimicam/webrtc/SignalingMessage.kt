
package com.example.crimicam.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

data class SignalingMessage(
    val type: String, // "offer", "answer", "ice-candidate"
    val sessionId: String,
    val senderId: String,
    val receiverId: String? = null, // null for broadcaster, specific for viewer
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class WebRTCSignalingService {
    private val firestore = FirebaseFirestore.getInstance()
    private val signalingCollection = firestore.collection("webrtc_signaling")

    companion object {
        private const val TAG = "WebRTCSignaling"
    }

    // Broadcaster sends offer
    suspend fun sendOffer(
        sessionId: String,
        senderId: String,
        sdp: SessionDescription
    ) {
        val message = SignalingMessage(
            type = "offer",
            sessionId = sessionId,
            senderId = senderId,
            sdp = sdp.description
        )

        signalingCollection
            .document("${sessionId}_offer")
            .set(message)
            .await()

        Log.d(TAG, "Offer sent for session: $sessionId")
    }

    // Viewer sends answer
    suspend fun sendAnswer(
        sessionId: String,
        senderId: String,
        receiverId: String,
        sdp: SessionDescription
    ) {
        val message = SignalingMessage(
            type = "answer",
            sessionId = sessionId,
            senderId = senderId,
            receiverId = receiverId,
            sdp = sdp.description
        )

        signalingCollection
            .add(message)
            .await()

        Log.d(TAG, "Answer sent for session: $sessionId")
    }

    // Send ICE candidate
    suspend fun sendIceCandidate(
        sessionId: String,
        senderId: String,
        receiverId: String?,
        candidate: IceCandidate
    ) {
        val message = SignalingMessage(
            type = "ice-candidate",
            sessionId = sessionId,
            senderId = senderId,
            receiverId = receiverId,
            candidate = candidate.sdp,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex
        )

        signalingCollection
            .add(message)
            .await()
    }

    // Listen for offer (viewer)
    fun observeOffer(sessionId: String): Flow<SessionDescription?> = callbackFlow {
        val listener = signalingCollection
            .document("${sessionId}_offer")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing offer", error)
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val sdp = doc.getString("sdp")
                    if (sdp != null) {
                        val sessionDesc = SessionDescription(
                            SessionDescription.Type.OFFER,
                            sdp
                        )
                        trySend(sessionDesc)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen for answers (broadcaster)
    fun observeAnswers(
        sessionId: String,
        senderId: String
    ): Flow<Pair<String, SessionDescription>> = callbackFlow {
        val listener = signalingCollection
            .whereEqualTo("sessionId", sessionId)
            .whereEqualTo("type", "answer")
            .whereEqualTo("receiverId", senderId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing answers", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val sdp = doc.getString("sdp")
                    val viewerId = doc.getString("senderId")

                    if (sdp != null && viewerId != null) {
                        val sessionDesc = SessionDescription(
                            SessionDescription.Type.ANSWER,
                            sdp
                        )
                        trySend(Pair(viewerId, sessionDesc))
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Listen for ICE candidates
    fun observeIceCandidates(
        sessionId: String,
        receiverId: String
    ): Flow<IceCandidate> = callbackFlow {
        val listener = signalingCollection
            .whereEqualTo("sessionId", sessionId)
            .whereEqualTo("type", "ice-candidate")
            .whereEqualTo("receiverId", receiverId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing ICE candidates", error)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    val candidate = doc.getString("candidate")
                    val sdpMid = doc.getString("sdpMid")
                    val sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt()

                    if (candidate != null && sdpMid != null && sdpMLineIndex != null) {
                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                        trySend(iceCandidate)
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // Clean up old signaling messages
    suspend fun cleanupSession(sessionId: String) {
        signalingCollection
            .whereEqualTo("sessionId", sessionId)
            .get()
            .await()
            .documents
            .forEach { it.reference.delete() }
    }
}