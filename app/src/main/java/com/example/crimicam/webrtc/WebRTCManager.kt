package com.example.crimicam.webrtc

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WebRTCManager(private val context: Context) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val _connectionState = MutableStateFlow<RTCConnectionState>(RTCConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RTCConnectionState> = _connectionState

    companion object {
        private const val TAG = "WebRTCManager"
        // SFU configuration
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // Add your TURN server for production
            // PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
            //     .setUsername("username")
            //     .setPassword("password")
            //     .createIceServer()
        )
    }

    enum class RTCConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }

    fun initialize() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "WebRTC initialized")
    }

    fun startStreaming(
        sessionId: String,
        onIceCandidate: (IceCandidate) -> Unit,
        onOfferCreated: (SessionDescription) -> Unit
    ) {
        createPeerConnection(sessionId, onIceCandidate)
        setupLocalMedia()
        createOffer(onOfferCreated)
    }

    fun startViewing(
        sessionId: String,
        onIceCandidate: (IceCandidate) -> Unit,
        videoSink: VideoSink
    ): PeerConnection? {
        val pc = createPeerConnection(sessionId, onIceCandidate)

        // Add transceiver for receiving video
        pc?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )

        // Set up remote video renderer
        pc?.transceivers?.forEach { transceiver ->
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                track.addSink(videoSink)
            }
        }

        return pc
    }

    private fun createPeerConnection(
        sessionId: String,
        onIceCandidate: (IceCandidate) -> Unit
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE Candidate: ${candidate.sdp}")
                onIceCandidate(candidate)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                Log.d(TAG, "Connection state: $newState")
                _connectionState.value = when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> RTCConnectionState.CONNECTED
                    PeerConnection.PeerConnectionState.CONNECTING -> RTCConnectionState.CONNECTING
                    PeerConnection.PeerConnectionState.FAILED -> RTCConnectionState.FAILED
                    else -> RTCConnectionState.DISCONNECTED
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE Connection: $state")
            }

            override fun onAddStream(stream: MediaStream) {
                Log.d(TAG, "Stream added: ${stream.id}")
            }

            override fun onRemoveStream(stream: MediaStream) {
                Log.d(TAG, "Stream removed: ${stream.id}")
            }

            override fun onDataChannel(dc: DataChannel) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }

    private fun setupLocalMedia() {
        // Create video source
        val videoSource = peerConnectionFactory?.createVideoSource(false)

        // Create video capturer
        val enumerator = Camera2Enumerator(context)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.first()

        videoCapturer = enumerator.createCapturer(cameraName, null)

        // Create surface texture helper
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)

        // Initialize capturer
        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        // Create video track
        localVideoTrack = peerConnectionFactory?.createVideoTrack("video_track", videoSource)

        // Create audio source and track
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)

        // Add tracks to peer connection
        peerConnection?.addTrack(localVideoTrack, listOf("stream_id"))
        peerConnection?.addTrack(localAudioTrack, listOf("stream_id"))

        Log.d(TAG, "Local media setup complete")
    }

    private fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set")
                        onOfferCreated(sdp)
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "Set local description failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription) {}
                    override fun onCreateFailure(p0: String) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e(TAG, "Create offer failed: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription, onSuccess: () -> Unit = {}) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set")
                onSuccess()
            }
            override fun onSetFailure(error: String) {
                Log.e(TAG, "Set remote description failed: $error")
            }
            override fun onCreateSuccess(p0: SessionDescription) {}
            override fun onCreateFailure(p0: String) {}
        }, sdp)
    }

    fun createAnswer(onAnswerCreated: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set (answer)")
                        onAnswerCreated(sdp)
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "Set local description failed: $error")
                    }
                    override fun onCreateSuccess(p0: SessionDescription) {}
                    override fun onCreateFailure(p0: String) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String) {
                Log.e(TAG, "Create answer failed: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }, constraints)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun release() {
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
    }
}