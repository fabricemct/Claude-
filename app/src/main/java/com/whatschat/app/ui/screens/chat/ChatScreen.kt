package com.whatschat.app.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.whatschat.app.data.audio.PcmResampler
import com.whatschat.app.data.audio.VoiceEffect
import com.whatschat.app.data.audio.VoiceRecorder
import com.whatschat.app.data.audio.WavFile
import com.whatschat.app.data.model.Message
import com.whatschat.app.data.model.MessageType
import com.whatschat.app.data.ocr.PhotoTextRecognizer
import com.whatschat.app.data.translate.AppLanguage
import com.whatschat.app.data.translate.VoiceTranslator
import com.whatschat.app.ui.components.COMMON_EMOJIS
import com.whatschat.app.ui.components.EmojiGridDialog
import com.whatschat.app.ui.components.STICKER_EMOJIS
import com.whatschat.app.ui.components.Avatar
import com.whatschat.app.ui.theme.WaBubbleIncoming
import com.whatschat.app.ui.theme.WaBubbleOutgoing
import com.whatschat.app.ui.theme.WaBubbleText
import com.whatschat.app.ui.theme.WaBubbleTimestamp
import com.whatschat.app.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.unit.sp
import com.whatschat.app.data.translate.SpeechToText

/** How the "Translate" flow gets its input text and sends its output. */
private enum class TranslateMode { TYPE_TO_VOICE, SPEAK_TO_VOICE, SPEAK_TO_TEXT }

/** Common phrases useful when traveling, grouped by topic for the "Quick travel phrases" picker. */
private val TRAVEL_PHRASES: Map<String, List<String>> = linkedMapOf(
    "Basics" to listOf(
        "Hello", "Good morning", "Good evening", "Goodbye", "Thank you",
        "Thank you very much", "You're welcome", "Please", "Yes", "No",
        "Excuse me", "Sorry", "My name is...", "What is your name?",
        "Nice to meet you", "Do you speak English?", "I don't understand",
        "Can you repeat that, please?", "Can you speak more slowly?",
        "Can you help me?", "I don't speak [language]"
    ),
    "Directions" to listOf(
        "Where is the bathroom?", "Where is the nearest hotel?",
        "Where is the train station?", "Where is the bus stop?",
        "How do I get to the city center?", "Is it far from here?",
        "Can you show me on the map?", "Turn left", "Turn right",
        "Go straight ahead", "I am lost", "Where am I?"
    ),
    "Transportation" to listOf(
        "One ticket, please", "Two tickets, please", "How much is a ticket?",
        "What time does it leave?", "What time does it arrive?",
        "Is this seat taken?", "Where can I get a taxi?",
        "Please take me to this address", "Please stop here",
        "How much is the fare?", "Where can I rent a car?"
    ),
    "Accommodation" to listOf(
        "I have a reservation", "Do you have any rooms available?",
        "How much is a room per night?", "What time is check-out?",
        "Can I have the Wi-Fi password?", "The room key, please",
        "Can you call me a taxi?", "Is breakfast included?"
    ),
    "Food & Dining" to listOf(
        "A table for two, please", "Can I see the menu?",
        "What do you recommend?", "I am allergic to...",
        "I am vegetarian", "I am vegan", "Water, please",
        "The check, please", "It was delicious", "Cheers!",
        "Can I get this to go?", "Is there a vegetarian option?"
    ),
    "Shopping" to listOf(
        "How much does this cost?", "That's too expensive",
        "Can you lower the price?", "Do you accept credit cards?",
        "I'm just looking, thank you", "Can I try this on?",
        "Do you have a smaller size?", "Do you have a bigger size?",
        "I'll take it", "Can I have a receipt?"
    ),
    "Emergencies & Health" to listOf(
        "I need a doctor", "Call an ambulance", "Call the police",
        "It's an emergency", "I feel sick", "I lost my passport",
        "I lost my wallet", "Where is the nearest hospital?",
        "Where is the nearest pharmacy?", "I need help",
        "I am allergic to penicillin", "My phone was stolen"
    ),
    "Numbers & Time" to listOf(
        "What time is it?", "One", "Two", "Three", "Ten", "Twenty",
        "One hundred", "Today", "Tomorrow", "Yesterday",
        "What day is it today?"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    otherUserName: String,
    otherUserPhoto: String,
    onBack: () -> Unit,
    onStartCall: (otherUid: String, otherUserName: String, otherUserPhoto: String, isVideo: Boolean) -> Unit,
    onOpenFilters: (chatId: String) -> Unit,
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
    var showTools by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val voiceTranslator = remember { VoiceTranslator(context.applicationContext) }
    val speechToText = remember { SpeechToText(context.applicationContext) }
    var showTranslateModeChooser by remember { mutableStateOf(false) }
    var translateMode by remember { mutableStateOf(TranslateMode.TYPE_TO_VOICE) }
    // For the two "Speak" modes: which language you're about to speak, asked
    // explicitly instead of guessing from the phone's system language — that
    // guess breaks as soon as the phone's own language doesn't match what
    // you're actually saying (e.g. a French speaker with a German phone).
    var speakSourceLanguage by remember { mutableStateOf<AppLanguage?>(null) }
    var showSpeakSourcePicker by remember { mutableStateOf(false) }
    var showTranslatePicker by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var translateError by remember { mutableStateOf<String?>(null) }

    // Quick travel phrasebook: pick a common phrase, then a language, get it
    // translated with a "listen" and "send to chat" option.
    var showPhrasebook by remember { mutableStateOf(false) }
    var phrasebookPhrase by remember { mutableStateOf<String?>(null) }
    var showPhrasebookLanguagePicker by remember { mutableStateOf(false) }
    var phrasebookLoading by remember { mutableStateOf(false) }
    var phrasebookResult by remember { mutableStateOf<String?>(null) }
    var phrasebookResultLanguage by remember { mutableStateOf<AppLanguage?>(null) }
    var phrasebookError by remember { mutableStateOf<String?>(null) }

    // Per-message translate/listen: tapping the small icon on a text message
    // shows a "Translate" (pick a language, translation appears under the
    // original) and "Listen" (read the message aloud in its own language)
    // menu, independent of the composer's own translate flow above.
    var messageTranslations by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var translateTargetMessageId by remember { mutableStateOf<String?>(null) }
    var showMessageLanguagePicker by remember { mutableStateOf(false) }
    var messageActionError by remember { mutableStateOf<String?>(null) }

    // Per-contact settings: a preferred language for this conversation (used
    // as the default target everywhere in this chat) and whether incoming
    // messages should be translated into it automatically.
    val chat by viewModel.chat.collectAsState()
    val otherUserProfile by viewModel.otherUser.collectAsState()
    val preferredLanguage = remember(chat) {
        val code = chat?.preferredLanguages?.get(viewModel.currentUid)
        AppLanguage.entries.firstOrNull { it.mlKitCode == code }
    }
    val autoTranslateOn = chat?.autoTranslateEnabled?.get(viewModel.currentUid) == true
    var showConversationSettings by remember { mutableStateOf(false) }
    var autoTranslatedMessageIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(messages, autoTranslateOn, preferredLanguage) {
        val target = preferredLanguage
        if (!autoTranslateOn || target == null) return@LaunchedEffect
        messages.filter { message ->
            message.type == MessageType.TEXT &&
                message.senderId != viewModel.currentUid &&
                message.messageId !in messageTranslations &&
                message.messageId !in autoTranslatedMessageIds
        }.forEach { message ->
            autoTranslatedMessageIds = autoTranslatedMessageIds + message.messageId
            scope.launch {
                runCatching { voiceTranslator.translate(message.text, target) }
                    .onSuccess { messageTranslations = messageTranslations + (message.messageId to it) }
            }
        }
    }

    // Photo translation: photograph a menu/sign/document, extract its text
    // on-device (ML Kit text recognition), then translate it — no cloud AI.
    var showPhotoTranslateLanguagePicker by remember { mutableStateOf(false) }
    var photoTranslateLanguage by remember { mutableStateOf<AppLanguage?>(null) }
    var photoTranslateLoading by remember { mutableStateOf(false) }
    var photoTranslateResult by remember { mutableStateOf<String?>(null) }
    var photoTranslateError by remember { mutableStateOf<String?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val photoTranslateCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val language = photoTranslateLanguage
        if (bitmap != null && language != null) {
            photoTranslateLoading = true
            scope.launch {
                runCatching {
                    val lines = PhotoTextRecognizer.recognizeBlocks(bitmap)
                    if (lines.isEmpty()) {
                        throw IllegalStateException("No text found in the photo — try getting closer or a clearer angle.")
                    }
                    // Translating and showing plain text (one line per recognized line, in
                    // reading order) instead of redrawing it over the photo — trying to fit
                    // a translation back into the original text's box on a real menu (mixed
                    // fonts, prices, columns) produced an unreadable mess.
                    lines.joinToString("\n") { line -> voiceTranslator.translate(line.text, language) }
                }.onSuccess { photoTranslateResult = it }
                    .onFailure { photoTranslateError = it.message ?: "Something went wrong." }
                photoTranslateLoading = false
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        if (granted) photoTranslateCameraLauncher.launch(null)
    }

    val voiceRecorder = remember { VoiceRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    // Voice messages send the instant you stop recording — no extra dialog in
    // the way. The effect applied is whatever was last picked from the (opt-in)
    // voice-effect picker, defaulting to a plain, unmodified recording.
    var selectedVoiceEffect by remember { mutableStateOf(VoiceEffect.NORMAL) }
    var showVoiceEffectPicker by remember { mutableStateOf(false) }
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
    // A separate launcher from the one above: granting mic access here should
    // just unlock the speak-to-translate flow, not also start a plain voice
    // message recording as a side effect.
    val speechPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    fun toggleRecording() {
        if (isRecording) {
            val pcm = voiceRecorder.stop()
            isRecording = false
            if (pcm.isNotEmpty()) {
                val resampled = PcmResampler.resample(pcm, VoiceRecorder.SAMPLE_RATE, selectedVoiceEffect)
                val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav")
                WavFile.write(file, resampled, VoiceRecorder.SAMPLE_RATE)
                val durationMs = (resampled.size / 2).toLong() * 1000L / VoiceRecorder.SAMPLE_RATE
                viewModel.sendAudio(file, durationMs)
            }
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

    // Long-pressing an own message starts multi-select; tapping other own messages
    // while active adds/removes them, and the trash icon in the contextual app bar
    // deletes the whole selection at once.
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val inSelectionMode = selectedMessageIds.isNotEmpty()

    // Tapping an image message (a photo, or a translated-photo result) opens it full-screen
    // instead of doing nothing, which is all that happened before.
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    fun toggleSelection(messageId: String) {
        selectedMessageIds =
            if (messageId in selectedMessageIds) selectedMessageIds - messageId else selectedMessageIds + messageId
    }

    if (showVoiceEffectPicker) {
        AlertDialog(
            onDismissRequest = { showVoiceEffectPicker = false },
            title = { Text("Voice for your next recording") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    VoiceEffect.entries.forEach { effect ->
                        TextButton(onClick = {
                            selectedVoiceEffect = effect
                            showVoiceEffectPicker = false
                        }) {
                            Text(
                                "${effect.emoji} ${effect.label}" +
                                    if (effect == selectedVoiceEffect) " ✓" else ""
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVoiceEffectPicker = false }) { Text("Cancel") }
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

    if (showTranslateModeChooser) {
        AlertDialog(
            onDismissRequest = { showTranslateModeChooser = false },
            title = { Text("Translate") },
            text = {
                Column {
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            translateMode = TranslateMode.TYPE_TO_VOICE
                            showTranslateModeChooser = false
                            showTranslatePicker = true
                        }
                    ) { Text("⌨️ Type text → send as voice") }
                    TextButton(onClick = {
                        showTranslateModeChooser = false
                        if (!micGranted) {
                            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            translateMode = TranslateMode.SPEAK_TO_VOICE
                            showSpeakSourcePicker = true
                        }
                    }) { Text("🎤 Speak → send as voice") }
                    TextButton(onClick = {
                        showTranslateModeChooser = false
                        if (!micGranted) {
                            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            translateMode = TranslateMode.SPEAK_TO_TEXT
                            showSpeakSourcePicker = true
                        }
                    }) { Text("🎤 Speak → send as text") }
                    TextButton(onClick = {
                        showTranslateModeChooser = false
                        showPhrasebook = true
                    }) { Text("📖 Quick travel phrases") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTranslateModeChooser = false }) { Text("Cancel") }
            }
        )
    }

    if (showSpeakSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSpeakSourcePicker = false },
            title = { Text("What language will you speak?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = {
                            speakSourceLanguage = language
                            showSpeakSourcePicker = false
                            showTranslatePicker = true
                        }) {
                            Text("${language.flag} ${language.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSpeakSourcePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showPhrasebook) {
        AlertDialog(
            onDismissRequest = { showPhrasebook = false },
            title = { Text("Quick travel phrases") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TRAVEL_PHRASES.forEach { (category, phrases) ->
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        phrases.forEach { phrase ->
                            TextButton(onClick = {
                                phrasebookPhrase = phrase
                                showPhrasebook = false
                                showPhrasebookLanguagePicker = true
                            }) {
                                Text(phrase)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhrasebook = false }) { Text("Cancel") }
            }
        )
    }

    if (showPhrasebookLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showPhrasebookLanguagePicker = false },
            title = { Text("Translate into...") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = {
                            val phrase = phrasebookPhrase
                            showPhrasebookLanguagePicker = false
                            if (phrase != null) {
                                phrasebookLoading = true
                                phrasebookResultLanguage = language
                                scope.launch {
                                    runCatching { voiceTranslator.translate(phrase, language) }
                                        .onSuccess { phrasebookResult = it }
                                        .onFailure { phrasebookError = it.message ?: "Something went wrong." }
                                    phrasebookLoading = false
                                }
                            }
                        }) {
                            Text("${language.flag} ${language.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhrasebookLanguagePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (phrasebookLoading || phrasebookResult != null || phrasebookError != null) {
        AlertDialog(
            onDismissRequest = {
                if (!phrasebookLoading) {
                    phrasebookResult = null
                    phrasebookError = null
                }
            },
            title = { Text(if (phrasebookError != null) "Couldn't translate" else phrasebookPhrase.orEmpty()) },
            text = {
                when {
                    phrasebookLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Translating...", modifier = Modifier.padding(start = 12.dp))
                    }
                    phrasebookError != null -> Text(phrasebookError.orEmpty())
                    else -> Text(phrasebookResult.orEmpty())
                }
            },
            confirmButton = {
                if (phrasebookResult != null) {
                    TextButton(onClick = {
                        viewModel.sendText(phrasebookResult.orEmpty())
                        phrasebookResult = null
                    }) { Text("Send to chat") }
                }
            },
            dismissButton = {
                if (!phrasebookLoading) {
                    Row {
                        if (phrasebookResult != null) {
                            TextButton(onClick = {
                                val result = phrasebookResult.orEmpty()
                                val language = phrasebookResultLanguage
                                scope.launch {
                                    runCatching { voiceTranslator.speakNow(result, language?.ttsLocale) }
                                        .onFailure { phrasebookError = it.message ?: "Something went wrong." }
                                }
                            }) { Text("🔊 Listen") }
                        }
                        TextButton(onClick = {
                            phrasebookResult = null
                            phrasebookError = null
                        }) { Text("Close") }
                    }
                }
            }
        )
    }

    if (showTranslatePicker || isListening || translating || translateError != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isListening && !translating) {
                    showTranslatePicker = false
                    translateError = null
                }
            },
            title = {
                Text(
                    when {
                        translateError != null -> "Translation failed"
                        isListening -> "Listening..."
                        translating -> "Translating..."
                        else -> "Translate into..."
                    }
                )
            },
            text = {
                when {
                    translateError != null -> Text(translateError.orEmpty())
                    isListening -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Speak now...", modifier = Modifier.padding(start = 12.dp))
                    }
                    translating -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Translating...", modifier = Modifier.padding(start = 12.dp))
                    }
                    else -> Column {
                        AppLanguage.entries.forEach { language ->
                            TextButton(onClick = {
                                val mode = translateMode
                                showTranslatePicker = false
                                scope.launch {
                                    runCatching {
                                        val sourceText = if (mode == TranslateMode.TYPE_TO_VOICE) {
                                            text
                                        } else {
                                            isListening = true
                                            val spoken = speechToText.listen(
                                                speakSourceLanguage?.ttsLocale ?: Locale.getDefault()
                                            )
                                            isListening = false
                                            spoken
                                        }
                                        translating = true
                                        val translated = voiceTranslator.translate(sourceText, language)
                                        if (mode == TranslateMode.SPEAK_TO_TEXT) {
                                            viewModel.sendText(translated)
                                        } else {
                                            val file = File(context.cacheDir, "translate_${System.currentTimeMillis()}.wav")
                                            voiceTranslator.speakToFile(translated, language.ttsLocale, file)
                                            val durationMs = WavFile.readDurationMs(file)
                                            viewModel.sendAudio(file, durationMs)
                                        }
                                    }.onSuccess {
                                        if (mode == TranslateMode.TYPE_TO_VOICE) {
                                            text = ""
                                            viewModel.onComposerTextChanged("")
                                        }
                                    }.onFailure {
                                        translateError = it.message ?: "Something went wrong."
                                    }
                                    isListening = false
                                    translating = false
                                }
                            }) {
                                Text("${language.flag} ${language.label}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!isListening && !translating) {
                    TextButton(onClick = {
                        showTranslatePicker = false
                        translateError = null
                    }) { Text("Cancel") }
                }
            }
        )
    }

    if (showMessageLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showMessageLanguagePicker = false },
            title = { Text("Translate this message") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = {
                            val msgId = translateTargetMessageId
                            val original = messages.firstOrNull { it.messageId == msgId }?.text
                            showMessageLanguagePicker = false
                            if (msgId != null && original != null) {
                                scope.launch {
                                    runCatching { voiceTranslator.translate(original, language) }
                                        .onSuccess { messageTranslations = messageTranslations + (msgId to it) }
                                        .onFailure { messageActionError = it.message ?: "Something went wrong." }
                                }
                            }
                        }) {
                            Text("${language.flag} ${language.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMessageLanguagePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (messageActionError != null) {
        AlertDialog(
            onDismissRequest = { messageActionError = null },
            title = { Text("Couldn't do that") },
            text = { Text(messageActionError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { messageActionError = null }) { Text("OK") }
            }
        )
    }

    if (showConversationSettings) {
        AlertDialog(
            onDismissRequest = { showConversationSettings = false },
            title = { Text("Conversation settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Preferred language for $otherUserName",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = { viewModel.setPreferredLanguage(language.mlKitCode) }) {
                            Text(
                                "${language.flag} ${language.label}" +
                                    if (language == preferredLanguage) " ✓" else ""
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text("Auto-translate incoming messages", modifier = Modifier.weight(1f))
                        Switch(
                            checked = autoTranslateOn,
                            onCheckedChange = { checked -> viewModel.setAutoTranslate(checked) },
                            enabled = preferredLanguage != null
                        )
                    }
                    if (preferredLanguage == null) {
                        Text(
                            "Pick a language above first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showConversationSettings = false }) { Text("Close") }
            }
        )
    }

    if (showPhotoTranslateLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showPhotoTranslateLanguagePicker = false },
            title = { Text("Translate a photo") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Pick a language, then take a photo of the text (menu, sign, document).",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = {
                            photoTranslateLanguage = language
                            showPhotoTranslateLanguagePicker = false
                            if (cameraGranted) {
                                photoTranslateCameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Text("${language.flag} ${language.label}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoTranslateLanguagePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (photoTranslateLoading || photoTranslateResult != null || photoTranslateError != null) {
        AlertDialog(
            onDismissRequest = {
                if (!photoTranslateLoading) {
                    photoTranslateResult = null
                    photoTranslateError = null
                }
            },
            title = { Text(if (photoTranslateError != null) "Couldn't translate" else "Translated text") },
            text = {
                when {
                    photoTranslateLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Reading and translating...", modifier = Modifier.padding(start = 12.dp))
                    }
                    photoTranslateError != null -> Text(photoTranslateError.orEmpty())
                    else -> Text(
                        photoTranslateResult.orEmpty(),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                if (photoTranslateResult != null) {
                    TextButton(onClick = {
                        viewModel.sendText(photoTranslateResult.orEmpty())
                        photoTranslateResult = null
                    }) { Text("Send to chat") }
                }
            },
            dismissButton = {
                if (!photoTranslateLoading) {
                    TextButton(onClick = {
                        photoTranslateResult = null
                        photoTranslateError = null
                    }) { Text("Close") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text("${selectedMessageIds.size} selected") },
                    navigationIcon = {
                        TooltipIconButton(
                            icon = Icons.Filled.Close,
                            description = "Cancel selection",
                            onClick = { selectedMessageIds = emptySet() }
                        )
                    },
                    actions = {
                        TooltipIconButton(
                            icon = Icons.Filled.Delete,
                            description = "Delete selected",
                            onClick = { showDeleteConfirm = true }
                        )
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        val otherIsTyping by viewModel.otherIsTyping.collectAsState()
                        val localTime = otherUserProfile?.timeZoneId?.takeIf { it.isNotBlank() }
                            ?.let { formatLocalTime(it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(photoUrl = otherUserPhoto, name = otherUserName, size = 36.dp)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(text = otherUserName, fontWeight = FontWeight.SemiBold)
                                when {
                                    otherIsTyping -> TypingIndicatorText()
                                    localTime != null -> Text(
                                        text = "🕒 $localTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        TooltipIconButton(icon = Icons.Filled.ArrowBack, description = "Back", onClick = onBack)
                    },
                    actions = {
                        TooltipIconButton(
                            icon = Icons.Filled.Call,
                            description = "Voice call",
                            onClick = { onStartCall(otherUid, otherUserName, otherUserPhoto, false) },
                            enabled = otherUid.isNotBlank()
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.Videocam,
                            description = "Video call",
                            onClick = { onStartCall(otherUid, otherUserName, otherUserPhoto, true) },
                            enabled = otherUid.isNotBlank()
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.Face,
                            description = "Selfie filters",
                            onClick = { onOpenFilters(chatId) }
                        )
                        TooltipIconButton(
                            icon = Icons.Rounded.Public,
                            description = "Conversation settings: preferred language, auto-translate",
                            onClick = { showConversationSettings = true }
                        )
                    }
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showTools) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TooltipIconButton(
                            icon = Icons.Filled.EmojiEmotions,
                            description = "Emoji",
                            onClick = { showEmojiPicker = true },
                            tint = Color(0xFFFFC107)
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.Star,
                            description = "Stickers",
                            onClick = { showStickerPicker = true },
                            tint = Color(0xFFFF9800)
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.Image,
                            description = "Send a photo",
                            onClick = { imagePicker.launch("image/*") },
                            tint = Color(0xFF2196F3)
                        )
                        TooltipIconButton(
                            icon = Icons.Rounded.Translate,
                            description = "Translate (type or speak, send as voice or text)",
                            onClick = { showTranslateModeChooser = true },
                            tint = Color(0xFF4CAF50)
                        )
                        TooltipIconButton(
                            icon = Icons.Rounded.DocumentScanner,
                            description = "Translate text from a photo (menu, sign, document)",
                            onClick = { showPhotoTranslateLanguagePicker = true },
                            tint = Color(0xFF00ACC1)
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.Mic,
                            description = if (isRecording) "Stop and send" else "Record a voice message",
                            onClick = { toggleRecording() },
                            tint = if (isRecording) Color(0xFFE53935) else Color(0xFF9C27B0)
                        )
                        TooltipIconButton(
                            icon = Icons.Filled.TheaterComedy,
                            description = "Voice for your next recording: ${selectedVoiceEffect.emoji} ${selectedVoiceEffect.label}",
                            onClick = { showVoiceEffectPicker = true },
                            enabled = !isRecording,
                            tint = Color(0xFFFF5722)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TooltipIconButton(
                        icon = if (showTools) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        description = if (showTools) "Hide options" else "More options (emoji, stickers, photo, translate, voice)",
                        onClick = { showTools = !showTools }
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            viewModel.onComposerTextChanged(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text(if (isRecording) "Recording... tap mic to stop" else "Message") }
                    )
                    TooltipIconButton(
                        icon = Icons.Filled.Send,
                        description = "Send",
                        onClick = {
                            viewModel.sendText(text)
                            text = ""
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (showDeleteConfirm) {
            val count = selectedMessageIds.size
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(if (count > 1) "Delete $count messages?" else "Delete message?") },
                text = {
                    Text(
                        "This removes " + (if (count > 1) "them" else "it") +
                            " for everyone in this conversation."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteMessages(selectedMessageIds)
                        selectedMessageIds = emptySet()
                        showDeleteConfirm = false
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.messageId }) { message ->
                val isOwn = message.senderId == viewModel.currentUid
                val isSelected = message.messageId in selectedMessageIds
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                        )
                ) {
                    MessageBubble(
                        message = message,
                        isOwn = isOwn,
                        translatedText = messageTranslations[message.messageId],
                        onLongPress = { if (isOwn) toggleSelection(message.messageId) },
                        onTap = {
                            when {
                                inSelectionMode && isOwn -> toggleSelection(message.messageId)
                                message.type == MessageType.IMAGE && message.imageUrl.isNotBlank() ->
                                    fullScreenImageUrl = message.imageUrl
                            }
                        },
                        onTranslateClick = {
                            translateTargetMessageId = message.messageId
                            showMessageLanguagePicker = true
                        },
                        onListenClick = {
                            scope.launch {
                                runCatching { voiceTranslator.speakNow(message.text) }
                                    .onFailure { messageActionError = it.message ?: "Something went wrong." }
                            }
                        }
                    )
                }
            }
        }
    }

    val viewedImageUrl = fullScreenImageUrl
    if (viewedImageUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(onClick = { fullScreenImageUrl = null })
            ) {
                AsyncImage(
                    model = viewedImageUrl,
                    contentDescription = "Full-screen image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    translatedText: String?,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onTranslateClick: () -> Unit,
    onListenClick: () -> Unit
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    if (message.type == MessageType.STICKER) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
            Text(
                text = message.text,
                fontSize = 56.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            )
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
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(8.dp)
        ) {
            // Bubbles are always a light color by design, regardless of dark mode or
            // dynamic (Material You) theming, so their text must stay a fixed dark
            // color too — otherwise dark-theme's light text becomes unreadable on it.
            CompositionLocalProvider(LocalContentColor provides WaBubbleText) {
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
                        MessageType.TEXT -> {
                            Text(text = message.text, color = WaBubbleText)
                            if (translatedText != null) {
                                Text(
                                    text = translatedText,
                                    color = WaBubbleText,
                                    fontStyle = FontStyle.Italic,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        MessageType.STICKER -> Unit // handled by the early return above
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        // Aligned as a whole (not filling the bubble's width) so short
                        // messages keep a compact bubble instead of always stretching wide.
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp)
                    ) {
                        if (message.type == MessageType.TEXT) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Translate,
                                        contentDescription = "Translate or listen to this message",
                                        tint = WaBubbleTimestamp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Translate") },
                                        onClick = {
                                            menuExpanded = false
                                            onTranslateClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Listen") },
                                        onClick = {
                                            menuExpanded = false
                                            onListenClick()
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = WaBubbleTimestamp
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatLocalTime(timeZoneId: String): String {
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    format.timeZone = TimeZone.getTimeZone(timeZoneId)
    return format.format(Date())
}

/** An [IconButton] that reveals what it does via a long-press tooltip, using [description] both as the a11y label and the tooltip text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = description, tint = tint)
        }
    }
}

@Composable
private fun TypingIndicatorText() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(450)
            dotCount = (dotCount % 3) + 1
        }
    }
    Text(
        text = "typing" + ".".repeat(dotCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

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
