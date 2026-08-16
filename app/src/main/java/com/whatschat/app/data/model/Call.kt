package com.whatschat.app.data.model

enum class CallStatus { RINGING, ACCEPTED, DECLINED, ENDED }

data class Call(
    val callId: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val status: String = CallStatus.RINGING.name,
    val offerSdp: String = "",
    val answerSdp: String = "",
    val isVideo: Boolean = false,
    val createdAt: Long = 0L
)
