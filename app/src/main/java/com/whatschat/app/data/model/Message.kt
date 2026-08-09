package com.whatschat.app.data.model

enum class MessageType { TEXT, IMAGE }

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = 0L
)
