package com.llasm.voiceassistant.data

import java.util.Date

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Date = Date(),
    val messageType: MessageType = MessageType.TEXT
)

enum class MessageType {
    TEXT, VOICE
}
