package com.whatschat.app.data.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

private const val TAG = "WebRtcClient"

/**
 * Thin wrapper around a single WebRTC peer connection (audio, optionally
 * video). Firestore (via [com.whatschat.app.data.repository.CallRepository])
 * carries the SDP offer/answer and ICE candidates produced here; this class
 * never talks to the network directly except through the WebRTC engine.
 */
class WebRtcClient(
    private val context: Context,
    private val enableVideo: Boolean,
    private val listener: Listener
) {
    interface Listener {
        fun onLocalIceCandidate(candidate: IceCandidate)
        fun onConnected()
        fun onDisconnected()
        fun onRemoteVideoTrack(track: VideoTrack) {}
    }

    /** Shared EGL context — the UI must init its SurfaceViewRenderers with [eglBase]'s context. */
    val eglBase: EglBase = EglBase.create()

    private val peerConnectionFactory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val localAudioTrack: AudioTrack
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    var localVideoTrack: VideoTrack? = null
        private set

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    /** A camera-level failure here (as opposed to a signaling/SDP issue) previously failed totally silently. */
    private val cameraEventsLogger = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(errorDescription: String) { Log.w(TAG, "Camera error: $errorDescription") }
        override fun onCameraDisconnected() { Log.w(TAG, "Camera disconnected") }
        override fun onCameraFreezed(errorDescription: String) { Log.w(TAG, "Camera freezed: $errorDescription") }
        override fun onCameraOpening(cameraName: String) { Log.d(TAG, "Camera opening: $cameraName") }
        override fun onFirstFrameAvailable() { Log.d(TAG, "Camera first frame available") }
        override fun onCameraClosed() { Log.d(TAG, "Camera closed") }
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        // Software (not hardware-accelerated) codecs on purpose: hardware
        // encoder/decoder support varies wildly across Android vendors/chipsets,
        // and a hardware codec that silently fails to init is a well-known way
        // to end up with a call that connects and carries audio fine but never
        // produces any video — with no error surfaced anywhere. Software VP8
        // works identically on every device at the cost of more CPU/battery,
        // which is a good trade for a low-resolution 1:1 call.
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .createPeerConnectionFactory()
        audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("whatschat-audio", audioSource)
    }

    fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    listener.onLocalIceCandidate(candidate)
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "ICE connection state: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> listener.onConnected()
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.CLOSED -> listener.onDisconnected()
                        else -> Unit
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onAddStream(stream: MediaStream) = Unit
                override fun onRemoveStream(stream: MediaStream) = Unit
                override fun onDataChannel(channel: DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                    val track = receiver.track()
                    Log.d(TAG, "onAddTrack: kind=${track?.kind()} id=${track?.id()}")
                    if (track is VideoTrack) listener.onRemoteVideoTrack(track)
                }
            }
        )
        peerConnection?.addTrack(localAudioTrack, listOf("whatschat-stream"))
        if (enableVideo) startLocalVideo()
    }

    private fun startLocalVideo() {
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
        if (deviceName == null) {
            Log.w(TAG, "startLocalVideo: no camera device found")
            return
        }

        val capturer = enumerator.createCapturer(deviceName, cameraEventsLogger)
        if (capturer == null) {
            Log.w(TAG, "startLocalVideo: failed to create a capturer for $deviceName")
            return
        }
        videoCapturer = capturer

        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        surfaceTextureHelper = helper

        val source = peerConnectionFactory.createVideoSource(capturer.isScreencast)
        videoSource = source
        capturer.initialize(helper, context, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        val track = peerConnectionFactory.createVideoTrack("whatschat-video", source)
        localVideoTrack = track
        val sender = peerConnection?.addTrack(track, listOf("whatschat-stream"))
        Log.d(TAG, "startLocalVideo: local video track added, sender=$sender")
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun createOffer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                logVideoNegotiation("local offer", sdp)
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onCreated(sdp)
            }
        }, MediaConstraints())
    }

    fun createAnswer(onCreated: (SessionDescription) -> Unit) {
        peerConnection?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                logVideoNegotiation("local answer", sdp)
                peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                onCreated(sdp)
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        logVideoNegotiation("remote ${sdp.type}", sdp)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                peerConnection?.transceivers?.forEach { t ->
                    Log.d(
                        TAG,
                        "transceiver mid=${t.mid} media=${t.mediaType} " +
                            "direction=${t.direction} currentDirection=${t.currentDirection}"
                    )
                }
            }
        }, sdp)
    }

    /** Logs whether a video m-line is present and what direction it negotiated — the only
     *  way (without a debugger) to tell an SDP-negotiation failure apart from a rendering one. */
    private fun logVideoNegotiation(label: String, sdp: SessionDescription) {
        val hasVideo = sdp.description.contains("m=video")
        val directionLine = sdp.description
            .lineSequence()
            .dropWhile { !it.startsWith("m=video") }
            .firstOrNull { it.startsWith("a=sendrecv") || it.startsWith("a=sendonly") || it.startsWith("a=recvonly") || it.startsWith("a=inactive") }
        Log.d(TAG, "$label: hasVideoLine=$hasVideo direction=${directionLine ?: "none"}")
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack.setEnabled(!muted)
    }

    fun close() {
        videoCapturer?.let { runCatching { it.stopCapture() } }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        videoSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        audioSource.dispose()
        peerConnectionFactory.dispose()
        eglBase.release()
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) { Log.w(TAG, "SDP create failed: $error") }
        override fun onSetFailure(error: String) { Log.w(TAG, "SDP set failed: $error") }
    }
}
