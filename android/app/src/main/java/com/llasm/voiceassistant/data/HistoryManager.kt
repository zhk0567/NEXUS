package com.llasm.voiceassistant.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class ConversationHistory(
    val id: String,
    val title: String,
    val preview: String,
    val messages: List<ChatMessage>,
    val timestamp: Long,
    val messageCount: Int
)

class HistoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("conversation_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _historyList = MutableStateFlow<List<ConversationHistory>>(emptyList())
    val historyList: StateFlow<List<ConversationHistory>> = _historyList.asStateFlow()
    
    init {
        loadHistory()
    }
    
    fun saveConversation(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        
        val conversationId = generateConversationId()
        val title = generateTitle(messages)
        val preview = generatePreview(messages)
        val timestamp = System.currentTimeMillis()
        
        val conversation = ConversationHistory(
            id = conversationId,
            title = title,
            preview = preview,
            messages = messages,
            timestamp = timestamp,
            messageCount = messages.size
        )
        
        val currentHistory = _historyList.value.toMutableList()
        currentHistory.add(0, conversation) // 添加到列表开头
        
        // 限制历史记录数量，最多保存50个对话
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _historyList.value = currentHistory
        saveToPreferences()
    }
    
    fun loadConversation(conversationId: String): List<ChatMessage>? {
        return _historyList.value.find { it.id == conversationId }?.messages
    }
    
    fun updateConversation(id: String, messages: List<ChatMessage>) {
        val currentHistory = _historyList.value.toMutableList()
        val index = currentHistory.indexOfFirst { it.id == id }
        
        if (index != -1) {
            // 更新现有记录
            val updatedHistory = currentHistory[index].copy(
                title = generateTitle(messages),
                preview = generatePreview(messages),
                timestamp = System.currentTimeMillis(), // 更新时间戳
                messageCount = messages.size,
                messages = messages
            )
            
            // 移除旧记录，添加更新后的记录到列表顶部
            currentHistory.removeAt(index)
            currentHistory.add(0, updatedHistory)
            
            _historyList.value = currentHistory
            saveToPreferences()
        }
    }
    
    fun deleteConversation(conversationId: String) {
        val currentHistory = _historyList.value.toMutableList()
        currentHistory.removeAll { it.id == conversationId }
        _historyList.value = currentHistory
        saveToPreferences()
    }
    
    fun clearAllHistory() {
        _historyList.value = emptyList()
        saveToPreferences()
    }
    
    private fun generateConversationId(): String {
        return "conv_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    }
    
    private fun generateTitle(messages: List<ChatMessage>): String {
        // 使用第一条用户消息作为标题
        val firstUserMessage = messages.firstOrNull { it.isUser }
        return if (firstUserMessage != null) {
            val content = firstUserMessage.content
            if (content.length > 20) {
                content.substring(0, 20) + "..."
            } else {
                content
            }
        } else {
            "新对话"
        }
    }
    
    private fun generatePreview(messages: List<ChatMessage>): String {
        // 使用最后一条AI消息作为预览
        val lastAiMessage = messages.lastOrNull { !it.isUser }
        return if (lastAiMessage != null) {
            val content = lastAiMessage.content
            if (content.length > 30) {
                content.substring(0, 30) + "..."
            } else {
                content
            }
        } else {
            "开始对话"
        }
    }
    
    private fun loadHistory() {
        val historyJson = prefs.getString("history_list", null)
        if (historyJson != null) {
            try {
                val type = object : TypeToken<List<ConversationHistory>>() {}.type
                val history: List<ConversationHistory> = gson.fromJson(historyJson, type)
                _historyList.value = history
            } catch (e: Exception) {
                _historyList.value = emptyList()
            }
        }
    }
    
    private fun saveToPreferences() {
        val historyJson = gson.toJson(_historyList.value)
        prefs.edit().putString("history_list", historyJson).apply()
    }
    
    fun getFormattedDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}
