package com.whatschat.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.Message
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TYPING_IDLE_DELAY_MS = 3000L

class ChatViewModel(
    private val chatId: String,
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUser?.uid

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _otherIsTyping = MutableStateFlow(false)
    val otherIsTyping: StateFlow<Boolean> = _otherIsTyping.asStateFlow()

    private var typingIdleJob: Job? = null
    private var typingFlagSet = false

    init {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { _messages.value = it }
        }
        viewModelScope.launch {
            chatRepository.observeTyping(chatId).collect { typingUid ->
                _otherIsTyping.value = typingUid.isNotBlank() && typingUid != currentUid
            }
        }
    }

    /** Call on every keystroke in the composer to keep the other user's "typing..." status live. */
    fun onComposerTextChanged(text: String) {
        val uid = currentUid ?: return
        typingIdleJob?.cancel()
        if (text.isBlank()) {
            setTyping(uid, false)
            return
        }
        if (!typingFlagSet) setTyping(uid, true)
        typingIdleJob = viewModelScope.launch {
            delay(TYPING_IDLE_DELAY_MS)
            setTyping(uid, false)
        }
    }

    private fun setTyping(uid: String, isTyping: Boolean) {
        typingFlagSet = isTyping
        viewModelScope.launch { chatRepository.setTyping(chatId, uid, isTyping) }
    }

    fun sendText(text: String) {
        val uid = currentUid ?: return
        if (text.isBlank()) return
        typingIdleJob?.cancel()
        typingFlagSet = false
        viewModelScope.launch {
            chatRepository.sendTextMessage(chatId, uid, text.trim())
        }
    }

    fun sendImage(uri: Uri) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            chatRepository.sendImageMessage(chatId, uid, uri)
        }
    }

    fun sendAudio(audioFile: File, durationMs: Long) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            chatRepository.sendAudioMessage(chatId, uid, audioFile, durationMs)
            audioFile.delete()
        }
    }

    fun sendSticker(emoji: String) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            chatRepository.sendSticker(chatId, uid, emoji)
        }
    }

    class Factory(private val chatId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(chatId) as T
    }
}
