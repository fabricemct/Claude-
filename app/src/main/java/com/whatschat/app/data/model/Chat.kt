package com.whatschat.app.data.model

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val typingUid: String = "",
    val typingUpdatedAt: Long = 0L,

    // Client-side only: the other participant's profile, resolved after loading.
    val otherUser: User? = null
)
