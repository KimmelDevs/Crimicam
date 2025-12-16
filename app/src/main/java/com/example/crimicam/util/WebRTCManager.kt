package com.example.crimicam.util

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume

class WebRTCManager(private val context: Context) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null

    private val _connectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.IceConnectionState> = _connectionState

    private val iceCandidatesList = mutableListOf<IceCandidate>()

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(iceCandidate: IceCandidate?) {
            iceCandidate?.let {
                iceCandidatesList.add(it)
                onIceCandidateCallback?.invoke(it)
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            newState?.let {
                _connectionState.value = it
            }
        }

        override fun onAddStream(mediaStream: MediaStream?) {
            mediaStream?.let {
                onRemoteStreamCallback?.invoke(it)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            // Handle ICE candidates removal
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onRemoveStream(mediaStream: MediaStream?) {}
        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    }

    var onIceCandidateCallback: ((IceCandidate) -> Unit)? = null
    var onRemoteStreamCallback: ((MediaStream) -> Unit)? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            peerConnectionObserver
        )

        return peerConnection
    }

    fun startLocalVideoCapture(isFrontCamera: Boolean = true): VideoTrack? {
        val videoSource = peerConnectionFactory?.createVideoSource(false)

        videoCapturer = createCameraCapturer(isFrontCamera)
        videoCapturer?.let { capturer ->
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread",
                EglBase.create().eglBaseContext
            )
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer.startCapture(1280, 720, 30)
        }

        localVideoTrack = peerConnectionFactory?.createVideoTrack("localVideo", videoSource)

        // Add video track to peer connection
        peerConnection?.addTrack(localVideoTrack, listOf("stream"))

        return localVideoTrack
    }

    private fun createCameraCapturer(isFrontCamera: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (isFrontCamera && enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            } else if (!isFrontCamera && enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }

        return null
    }

    suspend fun createOffer(): String? {
        return try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            }

            val offer = peerConnection?.createOfferAsync(constraints)
            peerConnection?.setLocalDescriptionAsync(offer)

            offer?.description
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createAnswer(): String? {
        return try {
            val constraints = MediaConstraints()
            val answer = peerConnection?.createAnswerAsync(constraints)
            peerConnection?.setLocalDescriptionAsync(answer)

            answer?.description
        } catch (e: Exception) {
            null
        }
    }

    suspend fun setRemoteDescription(sdp: String, type: SessionDescription.Type) {
        try {
            val sessionDescription = SessionDescription(type, sdp)
            peerConnection?.setRemoteDescriptionAsync(sessionDescription)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun stopCapture() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
    }

    fun release() {
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
    }

    fun getIceCandidates(): List<IceCandidate> = iceCandidatesList.toList()
}

// Extension functions for coroutines support
private suspend fun PeerConnection.createOfferAsync(constraints: MediaConstraints): SessionDescription? {
    return suspendCancellableCoroutine { continuation ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                continuation.resume(sessionDescription)
            }
            override fun onCreateFailure(error: String?) {
                continuation.resume(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
}

private suspend fun PeerConnection.createAnswerAsync(constraints: MediaConstraints): SessionDescription? {
    return suspendCancellableCoroutine { continuation ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                continuation.resume(sessionDescription)
            }
            override fun onCreateFailure(error: String?) {
                continuation.resume(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
}

private suspend fun PeerConnection.setLocalDescriptionAsync(sessionDescription: SessionDescription?) {
    return suspendCancellableCoroutine { continuation ->
        setLocalDescription(object : SdpObserver {
            override fun onSetSuccess() {
                continuation.resume(Unit)
            }
            override fun onSetFailure(error: String?) {
                continuation.resume(Unit)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }
}

private suspend fun PeerConnection.setRemoteDescriptionAsync(sessionDescription: SessionDescription) {
    return suspendCancellableCoroutine { continuation ->
        setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                continuation.resume(Unit)
            }
            override fun onSetFailure(error: String?) {
                continuation.resume(Unit)
            }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sessionDescription)
    }
}