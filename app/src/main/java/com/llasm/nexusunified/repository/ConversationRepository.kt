package com.llasm.nexusunified.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.llasm.nexusunified.data.Conversation
import com.llasm.nexusunified.data.ChatMessage
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 对话存储管理类
 */
class ConversationRepository(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()
    
    init {
        loadConversations()
    }
    
    /**
     * 加载所有对话
     */
    private fun loadConversations() {
        val conversationsJson = sharedPreferences.getString("conversations", null)
        if (conversationsJson != null) {
            try {
                val type = object : TypeToken<List<Conversation>>() {}.type
                val conversations = gson.fromJson<List<Conversation>>(conversationsJson, type)
                _conversations.value = conversations ?: emptyList()
            } catch (e: Exception) {
                _conversations.value = emptyList()
            }
        }
    }
    
    /**
     * 保存对话列表
     */
    private fun saveConversations() {
        val conversationsJson = gson.toJson(_conversations.value)
        sharedPreferences.edit()
            .putString("conversations", conversationsJson)
            .apply()
    }
    
    /**
     * 创建新对话
     */
    fun createNewConversation(): Conversation {
        val conversation = Conversation.createNew()
        val updatedList = _conversations.value.toMutableList()
        updatedList.add(0, conversation) // 添加到列表顶部
        _conversations.value = updatedList
        saveConversations()
        return conversation
    }
    
    /**
     * 开始新对话（清空当前对话）
     */
    fun startNewConversation(): Conversation {
        _currentConversationId.value = null
        return createNewConversation()
    }
    
    /**
     * 选择对话
     */
    fun selectConversation(conversationId: String) {
        _currentConversationId.value = conversationId
    }
    
    /**
     * 获取当前对话
     */
    fun getCurrentConversation(): Conversation? {
        val currentId = _currentConversationId.value
        return if (currentId != null) {
            _conversations.value.find { it.id == currentId }
        } else {
            null
        }
    }
    
    /**
     * 添加消息到当前对话
     */
    fun addMessageToCurrentConversation(message: ChatMessage) {
        val currentId = _currentConversationId.value
        if (currentId != null) {
            val conversation = _conversations.value.find { it.id == currentId }
            if (conversation != null) {
                val updatedMessages = conversation.messages + message
                val updatedConversation = conversation.copy(
                    messages = updatedMessages,
                    updatedAt = Date(),
                    title = generateTitleFromMessages(updatedMessages)
                )
                
                val updatedList = _conversations.value.toMutableList()
                val index = updatedList.indexOfFirst { it.id == currentId }
                if (index != -1) {
                    updatedList[index] = updatedConversation
                    _conversations.value = updatedList
                    saveConversations()
                }
            }
        }
    }
    
    /**
     * 更新对话消息列表
     */
    fun updateCurrentConversationMessages(messages: List<ChatMessage>) {
        val currentId = _currentConversationId.value
        android.util.Log.d("ConversationRepository", "更新对话消息: currentId=$currentId, messagesCount=${messages.size}")
        
        if (currentId != null) {
            val conversation = _conversations.value.find { it.id == currentId }
            if (conversation != null) {
                val updatedConversation = conversation.copy(
                    messages = messages,
                    updatedAt = Date(),
                    title = generateTitleFromMessages(messages)
                )
                
                val updatedList = _conversations.value.toMutableList()
                val index = updatedList.indexOfFirst { it.id == currentId }
                if (index != -1) {
                    updatedList[index] = updatedConversation
                    _conversations.value = updatedList
                    saveConversations()
                    android.util.Log.d("ConversationRepository", "对话消息已保存: ${updatedConversation.title}")
                } else {
                    android.util.Log.w("ConversationRepository", "未找到对话索引: $currentId")
                }
            } else {
                android.util.Log.w("ConversationRepository", "未找到当前对话: $currentId")
            }
        } else {
            android.util.Log.w("ConversationRepository", "当前对话ID为空")
        }
    }
    
    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        val updatedList = _conversations.value.toMutableList()
        updatedList.removeAll { it.id == conversationId }
        _conversations.value = updatedList
        saveConversations()
        
        // 如果删除的是当前对话，清空当前对话
        if (_currentConversationId.value == conversationId) {
            _currentConversationId.value = null
        }
    }
    
    /**
     * 根据消息生成对话标题
     */
    private fun generateTitleFromMessages(messages: List<ChatMessage>): String {
        val firstUserMessage = messages.find { it.isUser }
        return if (firstUserMessage != null) {
            val content = firstUserMessage.content.trim()
            if (content.length > 20) {
                content.substring(0, 20) + "..."
            } else {
                content
            }
        } else {
            "新对话"
        }
    }
    
    /**
     * 获取对话的消息列表
     */
    fun getConversationMessages(conversationId: String): List<ChatMessage> {
        return _conversations.value.find { it.id == conversationId }?.messages ?: emptyList()
    }
}
