package com.whatschat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatschat.app.data.model.Call
import com.whatschat.app.data.model.CallStatus
import com.whatschat.app.data.model.Chat
import com.whatschat.app.data.model.User
import com.whatschat.app.data.repository.AuthRepository
import com.whatschat.app.data.repository.CallRepository
import com.whatschat.app.data.repository.ChatRepository
import com.whatschat.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val callRepository: CallRepository = CallRepository()
) : ViewModel() {

    private val currentUid: String? get() = authRepository.currentUser?.uid

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _otherUsers = MutableStateFlow<List<User>>(emptyList())
    val otherUsers: StateFlow<List<User>> = _otherUsers.asStateFlow()

    private val _incomingCall = MutableStateFlow<Call?>(null)
    val incomingCall: StateFlow<Call?> = _incomingCall.asStateFlow()

    init {
        val uid = currentUid
        if (uid != null) {
            viewModelScope.launch {
                chatRepository.observeChats(uid).collect { _chats.value = it }
            }
            viewModelScope.launch {
                userRepository.observeOtherUsers(uid).collect { _otherUsers.value = it }
            }
            viewModelScope.launch {
                callRepository.observeIncomingCalls(uid).collect { _incomingCall.value = it }
            }
        }
    }

    suspend fun getUser(uid: String) = userRepository.getUser(uid)

    fun dismissIncomingCall() {
        _incomingCall.value = null
    }

    fun declineIncomingCall() {
        val call = _incomingCall.value ?: return
        _incomingCall.value = null
        viewModelScope.launch { callRepository.updateStatus(call.callId, CallStatus.DECLINED) }
    }

    suspend fun startChatWith(otherUid: String): String? {
        val uid = currentUid ?: return null
        return chatRepository.getOrCreateChat(uid, otherUid)
    }
}
