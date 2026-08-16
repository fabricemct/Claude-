package com.whatschat.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.Call
import com.whatschat.app.data.model.CallStatus
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.CallRepository
import com.whatschat.app.data.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

enum class CallPhase { CONNECTING, RINGING, CONNECTED, ENDED }

/**
 * Drives a single 1:1 call (audio, optionally video). When [existingCallId]
 * is null this device is the caller (it creates the call doc and sends the
 * offer); otherwise it's the callee answering an incoming call that a caller
 * already created. [isVideoCall] must be known upfront by both sides (the
 * caller from which button they tapped, the callee from the incoming call's
 * `isVideo` field) since it decides whether the camera is opened at all.
 */
class CallViewModel(
    application: Application,
    private val otherUid: String,
    private val existingCallId: String?,
    val isVideoCall: Boolean,
    private val authRepository: AuthRepository = AuthRepository(),
    private val callRepository: CallRepository = CallRepository()
) : AndroidViewModel(application) {

    private val myUid = authRepository.currentUser?.uid.orEmpty()
    val isCaller = existingCallId == null
    private var callId: String? = existingCallId
    private var remoteDescriptionSet = false

    private val _phase = MutableStateFlow(if (isCaller) CallPhase.CONNECTING else CallPhase.RINGING)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _videoEnabled = MutableStateFlow(isVideoCall)
    val videoEnabled: StateFlow<Boolean> = _videoEnabled.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _call = MutableStateFlow<Call?>(null)
    val call: StateFlow<Call?> = _call.asStateFlow()

    private val localRole = if (isCaller) "callerCandidates" else "calleeCandidates"
    private val remoteRole = if (isCaller) "calleeCandidates" else "callerCandidates"

    private val webRtcClient = WebRtcClient(
        application,
        isVideoCall,
        object : WebRtcClient.Listener {
            override fun onLocalIceCandidate(candidate: org.webrtc.IceCandidate) {
                val id = callId ?: return
                viewModelScope.launch { callRepository.addIceCandidate(id, localRole, candidate) }
            }

            override fun onConnected() {
                _phase.value = CallPhase.CONNECTED
            }

            override fun onDisconnected() {
                if (_phase.value != CallPhase.ENDED) _phase.value = CallPhase.ENDED
            }

            override fun onRemoteVideoTrack(track: VideoTrack) {
                _remoteVideoTrack.value = track
            }
        }
    )

    val eglBaseContext: EglBase.Context get() = webRtcClient.eglBase.eglBaseContext
    val localVideoTrack: VideoTrack? get() = webRtcClient.localVideoTrack

    init {
        webRtcClient.createPeerConnection()
        if (isCaller) {
            viewModelScope.launch {
                val id = callRepository.createCall(myUid, otherUid, isVideoCall)
                callId = id
                observeCall(id)
                observeRemoteCandidates(id)
                webRtcClient.createOffer { sdp ->
                    viewModelScope.launch { callRepository.setOffer(id, sdp.description) }
                }
            }
        } else {
            val id = requireNotNull(existingCallId)
            observeCall(id)
            observeRemoteCandidates(id)
        }
    }

    private fun observeCall(id: String) {
        viewModelScope.launch {
            callRepository.observeCall(id).collect { current ->
                _call.value = current
                if (current == null) return@collect

                if (isCaller && !remoteDescriptionSet && current.answerSdp.isNotBlank()) {
                    remoteDescriptionSet = true
                    webRtcClient.setRemoteDescription(
                        SessionDescription(SessionDescription.Type.ANSWER, current.answerSdp)
                    )
                }

                if (current.status == CallStatus.DECLINED.name || current.status == CallStatus.ENDED.name) {
                    _phase.value = CallPhase.ENDED
                }
            }
        }
    }

    private fun observeRemoteCandidates(id: String) {
        viewModelScope.launch {
            callRepository.observeIceCandidates(id, remoteRole).collect { candidate ->
                webRtcClient.addIceCandidate(candidate)
            }
        }
    }

    /** Callee accepts the ringing call once its offer SDP is known. */
    fun accept() {
        val id = callId ?: return
        val offerSdp = _call.value?.offerSdp
        if (offerSdp.isNullOrBlank() || remoteDescriptionSet) return
        remoteDescriptionSet = true
        _phase.value = CallPhase.CONNECTING
        webRtcClient.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        webRtcClient.createAnswer { sdp ->
            viewModelScope.launch { callRepository.setAnswer(id, sdp.description) }
        }
    }

    fun decline() {
        val id = callId ?: return
        viewModelScope.launch { callRepository.updateStatus(id, CallStatus.DECLINED) }
        _phase.value = CallPhase.ENDED
    }

    fun toggleMute() {
        val newMuted = !_muted.value
        webRtcClient.setMuted(newMuted)
        _muted.value = newMuted
    }

    fun toggleVideo() {
        if (!isVideoCall) return
        val newEnabled = !_videoEnabled.value
        webRtcClient.setVideoEnabled(newEnabled)
        _videoEnabled.value = newEnabled
    }

    fun switchCamera() {
        webRtcClient.switchCamera()
    }

    fun hangUp() {
        val id = callId
        if (id != null) {
            viewModelScope.launch { callRepository.updateStatus(id, CallStatus.ENDED) }
        }
        _phase.value = CallPhase.ENDED
    }

    override fun onCleared() {
        super.onCleared()
        webRtcClient.close()
    }

    class Factory(
        private val application: Application,
        private val otherUid: String,
        private val existingCallId: String?,
        private val isVideoCall: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CallViewModel(application, otherUid, existingCallId, isVideoCall) as T
    }
}
