package com.whatschat.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "Hey there! I am using WhatsChat.",
    val lastSeen: Long = 0L,
    val fcmToken: String = ""
)
