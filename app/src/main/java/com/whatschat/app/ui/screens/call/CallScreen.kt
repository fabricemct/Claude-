package com.whatschat.app.ui.screens.call

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.viewmodel.CallPhase
import com.whatschat.app.ui.viewmodel.CallViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    otherUid: String,
    otherUserName: String,
    otherUserPhoto: String,
    existingCallId: String?,
    isVideoCall: Boolean,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current

    val requiredPermissions = remember(isVideoCall) {
        if (isVideoCall) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> permissionsGranted = results.values.all { it } }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) permissionLauncher.launch(requiredPermissions)
    }

    if (!permissionsGranted) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (isVideoCall) "Microphone and camera access are needed for video calls."
                    else "Microphone access is needed to make voice calls."
                )
            }
        }
        return
    }

    // The WebRTC engine (and the microphone/camera it opens) is only created
    // once permissions are confirmed, so the ViewModel is instantiated here.
    val application = context.applicationContext as Application
    val viewModel: CallViewModel = viewModel(
        factory = CallViewModel.Factory(application, otherUid, existingCallId, isVideoCall)
    )

    val phase by viewModel.phase.collectAsState()
    val muted by viewModel.muted.collectAsState()
    val videoEnabled by viewModel.videoEnabled.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()

    LaunchedEffect(phase) {
        if (phase == CallPhase.ENDED) onCallEnded()
    }

    val statusText = when (phase) {
        CallPhase.CONNECTING -> if (viewModel.isCaller) "Calling..." else "Connecting..."
        CallPhase.RINGING -> "Incoming call"
        CallPhase.CONNECTED -> "Connected"
        CallPhase.ENDED -> "Call ended"
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isVideoCall && remoteVideoTrack != null) {
                VideoRendererView(
                    track = remoteVideoTrack,
                    eglBaseContext = viewModel.eglBaseContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Avatar(photoUrl = otherUserPhoto, name = otherUserName, size = 120.dp)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = otherUserName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(text = statusText, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }

            if (isVideoCall && videoEnabled && viewModel.localVideoTrack != null) {
                VideoRendererView(
                    track = viewModel.localVideoTrack,
                    eglBaseContext = viewModel.eglBaseContext,
                    mirror = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 100.dp, height = 140.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // The callee already chose to accept from the incoming-call prompt before
                // this screen ever opened (see WhatsChatNavGraph), so there's no separate
                // in-call accept/decline step — just the usual mute/video/hang-up controls,
                // which double as "hang up" if you change your mind while it's still
                // connecting.
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FilledIconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (muted) "Unmute" else "Mute"
                        )
                    }
                    if (isVideoCall) {
                        FilledIconButton(
                            onClick = { viewModel.toggleVideo() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                if (videoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                contentDescription = if (videoEnabled) "Turn off camera" else "Turn on camera"
                            )
                        }
                        FilledIconButton(
                            onClick = { viewModel.switchCamera() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Switch camera")
                        }
                    }
                    FilledIconButton(
                        onClick = { viewModel.hangUp() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Hang up")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRendererView(
    track: VideoTrack?,
    eglBaseContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { viewContext ->
            SurfaceViewRenderer(viewContext).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                renderer = this
            }
        },
        modifier = modifier,
        onRelease = { it.release() }
    )

    DisposableEffect(track, renderer) {
        val currentRenderer = renderer
        if (currentRenderer != null) track?.addSink(currentRenderer)
        onDispose {
            if (currentRenderer != null) track?.removeSink(currentRenderer)
        }
    }
}
