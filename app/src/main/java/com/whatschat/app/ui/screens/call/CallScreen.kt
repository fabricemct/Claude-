package com.whatschat.app.ui.screens.call

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.viewmodel.CallPhase
import com.whatschat.app.ui.viewmodel.CallViewModel

@Composable
fun CallScreen(
    otherUid: String,
    otherUserName: String,
    otherUserPhoto: String,
    existingCallId: String?,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    LaunchedEffect(Unit) {
        if (!micGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    if (!micGranted) {
        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Microphone access is needed to make voice calls.")
            }
        }
        return
    }

    // The WebRTC engine (and the microphone it opens) is only created once
    // permission is confirmed, so the ViewModel is instantiated here.
    val application = context.applicationContext as Application
    val viewModel: CallViewModel = viewModel(
        factory = CallViewModel.Factory(application, otherUid, existingCallId)
    )

    val phase by viewModel.phase.collectAsState()
    val muted by viewModel.muted.collectAsState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Avatar(photoUrl = otherUserPhoto, name = otherUserName, size = 120.dp)

            Text(
                text = otherUserName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(64.dp))

            if (phase == CallPhase.RINGING && !viewModel.isCaller) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    FilledIconButton(
                        onClick = { viewModel.decline() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Decline")
                    }
                    FilledIconButton(
                        onClick = { viewModel.accept() },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF43A047)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Accept")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    FilledIconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (muted) "Unmute" else "Mute"
                        )
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
