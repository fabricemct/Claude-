package com.whatschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.Chat
import com.whatschat.app.data.model.User
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.ChatRepository
import com.whatschat.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    val currentUid: String? get() = authRepository.currentUser?.uid

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _otherUsers = MutableStateFlow<List<User>>(emptyList())
    val otherUsers: StateFlow<List<User>> = _otherUsers.asStateFlow()

    init {
        val uid = currentUid
        if (uid != null) {
            viewModelScope.launch {
                chatRepository.observeChats(uid).collect { _chats.value = it }
            }
            viewModelScope.launch {
                userRepository.observeOtherUsers(uid).collect { _otherUsers.value = it }
            }
        }
    }

    suspend fun startChatWith(otherUid: String): String? {
        val uid = currentUid ?: return null
        return chatRepository.getOrCreateChat(uid, otherUid)
    }
}
