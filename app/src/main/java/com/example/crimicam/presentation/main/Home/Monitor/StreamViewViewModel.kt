package com.example.crimicam.presentation.main.Home.Monitor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.util.Result
import com.example.crimicam.util.WebRTCManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

data class StreamViewerState(
    val session: WebRTCSession? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val connectionState: String = "DISCONNECTED"
)

class StreamViewerViewModel(
    private val repository: WebRTCSignalingRepository = WebRTCSignalingRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(StreamViewerState())
    val state: StateFlow<StreamViewerState> = _state.asStateFlow()

    private var webRTCManager: WebRTCManager? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    fun connectToStream(context: Context, sessionId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // Initialize WebRTC
            webRTCManager = WebRTCManager(context)

            // Setup callbacks
            webRTCManager?.onRemoteStreamCallback = { mediaStream ->
                mediaStream.videoTracks.firstOrNull()?.let { remoteVideoTrack ->
                    remoteRenderer?.let { renderer ->
                        remoteVideoTrack.addSink(renderer)
                    }
                }
                _state.value = _state.value.copy(
                    connectionState = "CONNECTED",
                    isLoading = false
                )
            }

            webRTCManager?.onIceCandidateCallback = { iceCandidate ->
                viewModelScope.launch {
                    repository.addIceCandidate(sessionId, iceCandidate)
                }
            }

            // Create peer connection with STUN servers
            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
            webRTCManager?.createPeerConnection(iceServers)

            // Observe session changes
            observeSession(sessionId)
        }
    }

    private fun observeSession(sessionId: String) {
        viewModelScope.launch {
            repository.observeSession(sessionId).collect { session ->
                if (session == null) {
                    _state.value = _state.value.copy(
                        errorMessage = "Stream not found",
                        isLoading = false
                    )
                    return@collect
                }

                _state.value = _state.value.copy(
                    session = session,
                    isLoading = false
                )

                // Handle offer from broadcaster
                if (session.offer != null && _state.value.connectionState == "DISCONNECTED") {
                    handleOffer(session.offer, sessionId)
                }

                // Handle ICE candidates
                session.iceCandidates.forEach { candidateString ->
                    val parts = candidateString.split(",")
                    if (parts.size == 3) {
                        val iceCandidate = IceCandidate(
                            parts[0], // sdpMid
                            parts[1].toIntOrNull() ?: 0, // sdpMLineIndex
                            parts[2] // sdp
                        )
                        webRTCManager?.addIceCandidate(iceCandidate)
                    }
                }
            }
        }
    }

    private suspend fun handleOffer(offer: String, sessionId: String) {
        _state.value = _state.value.copy(connectionState = "CONNECTING")

        // Set remote description (offer)
        webRTCManager?.setRemoteDescription(offer, SessionDescription.Type.OFFER)

        // Create answer
        val answer = webRTCManager?.createAnswer()
        if (answer != null) {
            // Send answer back to broadcaster
            repository.setAnswer(sessionId, answer)
        } else {
            _state.value = _state.value.copy(
                errorMessage = "Failed to create answer",
                connectionState = "DISCONNECTED"
            )
        }
    }

    fun attachVideoRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        renderer.init(EglBase.create().eglBaseContext, null)
        renderer.setMirror(false)
    }

    fun disconnect() {
        webRTCManager?.release()
        remoteRenderer?.release()
        webRTCManager = null
        remoteRenderer = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}