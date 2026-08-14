package com.whatschat.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.whatschat.app.data.audio.PcmResampler
import com.whatschat.app.data.audio.VoiceEffect
import com.whatschat.app.data.audio.VoiceRecorder
import com.whatschat.app.data.audio.WavFile
import com.whatschat.app.data.model.Message
import com.whatschat.app.data.model.MessageType
import com.whatschat.app.ui.components.COMMON_EMOJIS
import com.whatschat.app.ui.components.EmojiGridDialog
import com.whatschat.app.ui.components.STICKER_EMOJIS
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.theme.WaBubbleIncoming
import com.whatschat.app.ui.theme.WaBubbleOutgoing
import com.whatschat.app.ui.viewmodel.ChatViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUserName: String,
    otherUserPhoto: String,
    onBack: () -> Unit,
    onStartCall: (otherUid: String, otherUserName: String, otherUserPhoto: String) -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(chatId))
) {
    val messages by viewModel.messages.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val otherUid = remember(chatId) { chatId.split("_").firstOrNull { it != viewModel.currentUid }.orEmpty() }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendImage(uri)
    }

    var showEmojiPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }

    val voiceRecorder = remember { VoiceRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    var pendingVoicePcm by remember { mutableStateOf<ByteArray?>(null) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) {
            voiceRecorder.start()
            isRecording = true
        }
    }

    fun toggleRecording() {
        if (isRecording) {
            val pcm = voiceRecorder.stop()
            isRecording = false
            if (pcm.isNotEmpty()) pendingVoicePcm = pcm
        } else if (micGranted) {
            voiceRecorder.start()
            isRecording = true
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (pendingVoicePcm != null) {
        AlertDialog(
            onDismissRequest = { pendingVoicePcm = null },
            title = { Text("Choose a voice") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    VoiceEffect.entries.forEach { effect ->
                        TextButton(onClick = {
                            val pcm = pendingVoicePcm ?: return@TextButton
                            val resampled = PcmResampler.resample(pcm, VoiceRecorder.SAMPLE_RATE, effect)
                            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav")
                            WavFile.write(file, resampled, VoiceRecorder.SAMPLE_RATE)
                            val durationMs = (resampled.size / 2).toLong() * 1000L / VoiceRecorder.SAMPLE_RATE
                            viewModel.sendAudio(file, durationMs)
                            pendingVoicePcm = null
                        }) {
                            Text("${effect.emoji} ${effect.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingVoicePcm = null }) { Text("Cancel") }
            }
        )
    }

    if (showEmojiPicker) {
        EmojiGridDialog(
            title = "Emoji",
            emojis = COMMON_EMOJIS,
            fontSize = 26.sp,
            onDismiss = { showEmojiPicker = false },
            onEmojiSelected = { emoji -> text += emoji }
        )
    }

    if (showStickerPicker) {
        EmojiGridDialog(
            title = "Stickers",
            emojis = STICKER_EMOJIS,
            fontSize = 34.sp,
            onDismiss = { showStickerPicker = false },
            onEmojiSelected = { emoji -> viewModel.sendSticker(emoji) }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(photoUrl = otherUserPhoto, name = otherUserName, size = 36.dp)
                        Text(
                            text = otherUserName,
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall(otherUid, otherUserName, otherUserPhoto) },
                        enabled = otherUid.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Voice call")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEmojiPicker = true }) {
                    Icon(Icons.Filled.EmojiEmotions, contentDescription = "Emoji")
                }
                IconButton(onClick = { showStickerPicker = true }) {
                    Icon(Icons.Filled.Star, contentDescription = "Stickers")
                }
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Filled.Image, contentDescription = "Send image")
                }
                IconButton(onClick = { toggleRecording() }) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Record voice message",
                        tint = if (isRecording) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (isRecording) "Recording... tap mic to stop" else "Message") }
                )
                IconButton(onClick = {
                    viewModel.sendText(text)
                    text = ""
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.messageId }) { message ->
                MessageBubble(message = message, isOwn = message.senderId == viewModel.currentUid)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isOwn: Boolean) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    if (message.type == MessageType.STICKER) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
            Text(text = message.text, fontSize = 56.sp, modifier = Modifier.padding(4.dp))
        }
        return
    }

    val bubbleColor = if (isOwn) WaBubbleOutgoing else WaBubbleIncoming

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .padding(8.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.IMAGE -> AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Image message",
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    MessageType.AUDIO -> AudioMessageBubble(
                        audioUrl = message.audioUrl,
                        durationMs = message.audioDurationMs
                    )
                    MessageType.TEXT -> Text(text = message.text)
                    MessageType.STICKER -> Unit // handled by the early return above
                }
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun AudioMessageBubble(audioUrl: String, durationMs: Long) {
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var mediaPlayer by remember(audioUrl) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            val player = mediaPlayer
            when {
                player == null -> {
                    mediaPlayer = runCatching {
                        MediaPlayer().apply {
                            setDataSource(audioUrl)
                            setOnCompletionListener { isPlaying = false }
                            setOnPreparedListener {
                                it.start()
                                isPlaying = true
                            }
                            setOnErrorListener { _, _, _ -> isPlaying = false; true }
                            prepareAsync()
                        }
                    }.getOrNull()
                }
                player.isPlaying -> {
                    player.pause()
                    isPlaying = false
                }
                else -> {
                    player.start()
                    isPlaying = true
                }
            }
        }) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play voice message"
            )
        }
        Text(text = formatDuration(durationMs), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
