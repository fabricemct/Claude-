package com.whatschat.app.data.model

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSenderId: String = "",
    val typingUid: String = "",
    val typingUpdatedAt: Long = 0L,

    // Both keyed by uid, so each participant's own preference is independent of
    // the other's: preferredLanguages maps a uid to the AppLanguage.mlKitCode
    // they want this contact's messages translated into; autoTranslateEnabled
    // maps a uid to whether that should happen automatically for every
    // incoming message instead of only on request.
    val preferredLanguages: Map<String, String> = emptyMap(),
    val autoTranslateEnabled: Map<String, Boolean> = emptyMap(),

    // Client-side only: the other participant's profile, resolved after loading.
    val otherUser: User? = null
)
