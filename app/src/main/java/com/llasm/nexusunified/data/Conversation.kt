package com.llasm.nexusunified.data

import java.util.Date

/**
 * 对话数据模型
 */
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val createdAt: Date,
    val updatedAt: Date,
    val sessionId: String? = null  // 每个历史对话对应的session_id
) {
    companion object {
        fun createNew(title: String = "新对话", sessionId: String? = null): Conversation {
            val now = Date()
            return Conversation(
                id = generateId(),
                title = title,
                messages = emptyList(),
                createdAt = now,
                updatedAt = now,
                sessionId = sessionId
            )
        }
        
        private fun generateId(): String {
            return "conv_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
    }
}
