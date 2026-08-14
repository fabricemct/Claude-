package com.whatschat.app.data.model

enum class MessageType { TEXT, IMAGE, AUDIO }

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val audioUrl: String = "",
    val audioDurationMs: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = 0L
)
