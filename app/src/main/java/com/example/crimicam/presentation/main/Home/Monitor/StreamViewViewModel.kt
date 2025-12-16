package com.example.crimicam.presentation.main.Home.Monitor

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.model.WebRTCSession
import com.example.crimicam.data.repository.WebRTCSignalingRepository
import com.example.crimicam.util.Result
import com.example.crimicam.webrtc.WebRTCManager
import com.example.crimicam.webrtc.WebRTCSignalingService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

data class StreamViewerState(
    val session: WebRTCSession? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val connectionState: WebRTCManager.RTCConnectionState = WebRTCManager.RTCConnectionState.DISCONNECTED,
    val isConnected: Boolean = false
)

class StreamViewerViewModel(
    private val repository: WebRTCSignalingRepository = WebRTCSignalingRepository(),
    private val signalingService: WebRTCSignalingService = WebRTCSignalingService()
) : ViewModel() {

    private val _state = MutableStateFlow(StreamViewerState())
    val state: StateFlow<StreamViewerState> = _state.asStateFlow()

    private var webRtcManager: WebRTCManager? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private val auth = FirebaseAuth.getInstance()
    private var currentSessionId: String? = null

    companion object {
        private const val TAG = "StreamViewerVM"
    }

    fun connectToStream(context: Context, sessionId: String) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true)
                currentSessionId = sessionId

                val userId = auth.currentUser?.uid ?: run {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Please sign in to view stream"
                    )
                    return@launch
                }

                // Load session info
                when (val result = repository.getSession(sessionId)) {
                    is Result.Success -> {
                        val session = result.data
                        _state.value = _state.value.copy(session = session)

                        // Initialize WebRTC if not already done
                        if (webRtcManager == null) {
                            webRtcManager = WebRTCManager(context).apply {
                                initialize()
                            }
                        }

                        // Observe connection state
                        observeConnectionState()

                        // Start viewing with video sink
                        remoteRenderer?.let { renderer ->
                            webRtcManager?.startViewing(
                                sessionId = sessionId,
                                onIceCandidate = { candidate ->
                                    handleIceCandidate(candidate, sessionId, userId, session.userId)
                                },
                                videoSink = renderer
                            )
                        }

                        // Observe offer from broadcaster
                        observeOffer(sessionId, userId, session.userId)

                        // Observe ICE candidates from broadcaster
                        observeIceCandidates(sessionId, userId)

                        Log.d(TAG, "Started viewing stream: $sessionId")
                    }
                    is Result.Error -> {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            errorMessage = "Stream not found: ${result.exception.message}"
                        )
                    }
                    is Result.Loading -> {
                        // Keep loading state
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to stream", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Connection failed: ${e.message}"
                )
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            webRtcManager?.connectionState?.collect { state ->
                _state.value = _state.value.copy(
                    connectionState = state,
                    isConnected = state == WebRTCManager.RTCConnectionState.CONNECTED,
                    isLoading = state == WebRTCManager.RTCConnectionState.CONNECTING
                )
            }
        }
    }

    private fun observeOffer(sessionId: String, viewerId: String, broadcasterId: String) {
        viewModelScope.launch {
            signalingService.observeOffer(sessionId).collect { offer ->
                offer?.let {
                    Log.d(TAG, "Received offer from broadcaster")
                    _state.value = _state.value.copy(
                        connectionState = WebRTCManager.RTCConnectionState.CONNECTING
                    )

                    webRtcManager?.setRemoteDescription(it) {
                        // Create answer
                        webRtcManager?.createAnswer { answer ->
                            viewModelScope.launch {
                                try {
                                    signalingService.sendAnswer(
                                        sessionId = sessionId,
                                        senderId = viewerId,
                                        receiverId = broadcasterId,
                                        sdp = answer
                                    )
                                    Log.d(TAG, "Answer sent to broadcaster")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to send answer", e)
                                    _state.value = _state.value.copy(
                                        errorMessage = "Failed to send answer"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeIceCandidates(sessionId: String, receiverId: String) {
        viewModelScope.launch {
            signalingService.observeIceCandidates(sessionId, receiverId).collect { candidate ->
                Log.d(TAG, "Received ICE candidate")
                webRtcManager?.addIceCandidate(candidate)
            }
        }
    }

    private fun handleIceCandidate(
        candidate: IceCandidate,
        sessionId: String,
        senderId: String,
        receiverId: String
    ) {
        viewModelScope.launch {
            try {
                signalingService.sendIceCandidate(
                    sessionId = sessionId,
                    senderId = senderId,
                    receiverId = receiverId,
                    candidate = candidate
                )
                Log.d(TAG, "ICE candidate sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send ICE candidate", e)
            }
        }
    }

    fun attachVideoRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        renderer.init(EglBase.create().eglBaseContext, null)
        renderer.setMirror(false)
        renderer.setEnableHardwareScaler(true)
        Log.d(TAG, "Video renderer attached")
    }

    fun disconnect() {
        currentSessionId?.let { sessionId ->
            viewModelScope.launch {
                try {
                    // Cleanup signaling data for this viewer
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        // Could add cleanup logic here if needed
                        Log.d(TAG, "Disconnecting from session: $sessionId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during disconnect", e)
                }
            }
        }

        webRtcManager?.release()
        remoteRenderer?.release()
        webRtcManager = null
        remoteRenderer = null
        currentSessionId = null

        _state.value = _state.value.copy(
            isConnected = false,
            connectionState = WebRTCManager.RTCConnectionState.DISCONNECTED
        )

        Log.d(TAG, "Disconnected and cleaned up")
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}