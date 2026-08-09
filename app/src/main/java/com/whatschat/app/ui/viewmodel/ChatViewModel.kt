package com.whatschat.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.Message
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatId: String,
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUser?.uid

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.observeMessages(chatId).collect { _messages.value = it }
        }
    }

    fun sendText(text: String) {
        val uid = currentUid ?: return
        if (text.isBlank()) return
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

    class Factory(private val chatId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(chatId) as T
    }
}
