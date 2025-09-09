package com.llasm.voiceassistant.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llasm.voiceassistant.data.ChatMessage
import com.llasm.voiceassistant.data.MessageType
import com.llasm.voiceassistant.data.HistoryManager
import com.llasm.voiceassistant.data.ConversationHistory
import com.llasm.voiceassistant.network.NetworkModule
import com.llasm.voiceassistant.service.VoiceService
import com.llasm.voiceassistant.identity.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class ChatViewModel : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 管理每个消息的播放状态
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()
    
    // 跟踪当前对话来源
    private var currentConversationId: String? = null
    private var isFromHistory: Boolean = false
    
    private var voiceService: VoiceService? = null
    private var userManager: UserManager? = null
    private var historyManager: HistoryManager? = null
    private var currentRequestJob: Job? = null
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        // 如果已有请求在进行中，先取消
        currentRequestJob?.cancel()
        
        val userMessage = ChatMessage(
            content = content.trim(),
            isUser = true
        )
        
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null
        
        currentRequestJob = viewModelScope.launch {
            try {
                val apiService = NetworkModule.getApiService()
                
                // 添加用户身份信息到请求头 - 混合方案
                val headers = mutableMapOf<String, String>()
                
                // 使用统计用户ID（已注册用户用用户ID，未注册用户用设备ID）
                userManager?.getStatisticsUserId()?.let { statisticsUserId ->
                    headers["X-User-ID"] = statisticsUserId
                }
                
                // 始终发送设备ID用于设备关联
                userManager?.getCurrentDeviceId()?.let { deviceId ->
                    headers["X-Device-ID"] = deviceId
                }
                
                // 发送会话ID
                userManager?.getCurrentSessionId()?.let { sessionId ->
                    headers["X-Session-ID"] = sessionId
                }
                
                // 发送用户类型信息
                userManager?.getUserType()?.let { userType ->
                    headers["X-User-Type"] = userType.name
                }
                
                val response = apiService.chatWithAI(mapOf("message" to content.trim()), headers)
                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    if (chatResponse?.success == true) {
                        val aiMessage = ChatMessage(
                            content = chatResponse.response,
                            isUser = false
                        )
                        _messages.value = _messages.value + aiMessage
                    } else {
                        _error.value = "AI回复失败"
                    }
                } else {
                    _error.value = "网络请求失败: ${response.code()}"
                }
            } catch (e: Exception) {
                if (e.message?.contains("CancellationException") == true) {
                    // 请求被取消，不显示错误
                    return@launch
                }
                _error.value = "连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
                currentRequestJob = null
            }
        }
    }
    
    fun cancelCurrentRequest() {
        currentRequestJob?.cancel()
        _isLoading.value = false
        _error.value = "请求已取消"
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun refreshLastAIResponse() {
        val currentMessages = _messages.value
        if (currentMessages.isEmpty()) return
        
        // 找到最后一个用户消息
        val lastUserMessage = currentMessages.lastOrNull { it.isUser }
        if (lastUserMessage == null) return
        
        // 如果已有请求在进行中，先取消
        currentRequestJob?.cancel()
        
        // 移除最后一个AI回答（如果存在）
        val messagesWithoutLastAI = if (currentMessages.isNotEmpty() && !currentMessages.last().isUser) {
            currentMessages.dropLast(1)
        } else {
            currentMessages
        }
        
        _messages.value = messagesWithoutLastAI
        _isLoading.value = true
        _error.value = null
        
        currentRequestJob = viewModelScope.launch {
            try {
                val apiService = NetworkModule.getApiService()
                
                // 添加用户身份信息到请求头 - 混合方案
                val headers = mutableMapOf<String, String>()
                
                // 使用统计用户ID（已注册用户用用户ID，未注册用户用设备ID）
                userManager?.getStatisticsUserId()?.let { statisticsUserId ->
                    headers["X-User-ID"] = statisticsUserId
                }
                
                // 始终发送设备ID用于设备关联
                userManager?.getCurrentDeviceId()?.let { deviceId ->
                    headers["X-Device-ID"] = deviceId
                }
                
                // 发送会话ID
                userManager?.getCurrentSessionId()?.let { sessionId ->
                    headers["X-Session-ID"] = sessionId
                }
                
                // 发送用户类型信息
                userManager?.getUserType()?.let { userType ->
                    headers["X-User-Type"] = userType.name
                }
                
                // 添加刷新标识，让AI知道这是刷新请求
                headers["X-Refresh-Request"] = "true"
                
                // 添加时间戳确保请求的唯一性
                headers["X-Request-Time"] = System.currentTimeMillis().toString()
                
                android.util.Log.d("ChatViewModel", "🔄 刷新请求头: $headers")
                
                val response = apiService.chatWithAI(mapOf("message" to lastUserMessage.content), headers)
                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    if (chatResponse?.success == true) {
                        val aiMessage = ChatMessage(
                            content = chatResponse.response,
                            isUser = false
                        )
                        _messages.value = _messages.value + aiMessage
                    } else {
                        _error.value = "AI回复失败"
                    }
                } else {
                    _error.value = "网络请求失败: ${response.code()}"
                }
            } catch (e: Exception) {
                if (e.message?.contains("CancellationException") == true) {
                    // 请求被取消，不显示错误
                    return@launch
                }
                _error.value = "连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
                currentRequestJob = null
            }
        }
    }
    
    fun playAudio(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                _isPlaying.value = true
                _error.value = null
                
                voiceService?.playTextToSpeech(
                    text = text,
                    onPlaybackComplete = {
                        _isPlaying.value = false
                    },
                    onError = { error ->
                        _isPlaying.value = false
                        _error.value = "播放失败: $error"
                    }
                )
            } catch (e: Exception) {
                _isPlaying.value = false
                _error.value = "播放失败: ${e.message}"
            }
        }
    }
    
    fun playAudioForMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                _playingMessageId.value = messageId
                _error.value = null
                
                voiceService?.playTextToSpeech(
                    text = text,
                    onPlaybackComplete = {
                        _playingMessageId.value = null
                    },
                    onError = { error ->
                        _playingMessageId.value = null
                        _error.value = "播放失败: $error"
                    }
                )
            } catch (e: Exception) {
                _playingMessageId.value = null
                _error.value = "播放失败: ${e.message}"
            }
        }
    }
    
    fun testConnection() {
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Testing connection...")
                val apiService = NetworkModule.getApiService()
                android.util.Log.d("ChatViewModel", "API service created, making health check request...")
                val response = apiService.healthCheck()
                android.util.Log.d("ChatViewModel", "Health check response: ${response.code()}")
                
                if (response.isSuccessful) {
                    _error.value = "✅ 连接成功！后端服务运行正常"
                } else {
                    _error.value = "❌ 连接失败: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Connection test failed", e)
                _error.value = "❌ 连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun initializeVoiceService(context: Context) {
        try {
            voiceService = VoiceService(context)
        } catch (e: Exception) {
            _error.value = "语音服务初始化失败: ${e.message}"
        }
    }
    
    fun initializeUserManager(context: Context) {
        if (userManager == null) {
            userManager = UserManager(context)
            userManager?.initialize()
        }
    }
    
    fun startVoiceRecording() {
        if (_isRecording.value) return
        
        try {
            voiceService?.startVoiceRecording(
                onRecordingStarted = {
                    _isRecording.value = true
                },
                onError = { error ->
                    _isRecording.value = false
                    _error.value = "录音失败: ${error.message}"
                }
            )
        } catch (e: Exception) {
            _isRecording.value = false
            _error.value = "录音启动异常: ${e.message}"
        }
    }
    
    fun stopVoiceRecording() {
        if (!_isRecording.value) return
        
        try {
            voiceService?.stopVoiceRecording(
                onTranscriptionComplete = { transcription ->
                    _isRecording.value = false
                    if (transcription.isNotBlank()) {
                        sendMessage(transcription)
                    }
                },
                onError = { error ->
                    _isRecording.value = false
                    // 检查是否是录音时长不足的错误
                    if (error.message?.contains("录音时间太短") == true) {
                        _error.value = error.message
                    } else {
                        _error.value = "语音识别失败: ${error.message}"
                    }
                }
            )
        } catch (e: Exception) {
            _isRecording.value = false
            _error.value = "录音停止异常: ${e.message}"
        }
    }
    
    fun playTextToSpeech(text: String) {
        if (_isPlaying.value) return
        
        voiceService?.playTextToSpeech(
            text = text,
            onPlaybackComplete = {
                _isPlaying.value = false
            },
            onError = { error ->
                _isPlaying.value = false
                _error.value = "语音播放失败: ${error.message}"
            }
        )
        _isPlaying.value = true
    }
    
    fun initializeHistoryManager(context: Context) {
        historyManager = HistoryManager(context)
    }
    
    fun loadHistoryMessages(messages: List<ChatMessage>) {
        _messages.value = messages
    }
    
    fun deleteHistory(historyId: String) {
        historyManager?.deleteConversation(historyId)
    }
    
    fun saveCurrentConversation() {
        val messages = _messages.value
        if (messages.isNotEmpty()) {
            if (isFromHistory && currentConversationId != null) {
                // 如果是从历史记录打开的，更新现有记录
                historyManager?.updateConversation(currentConversationId!!, messages)
            } else {
                // 如果是新对话，创建新记录
                historyManager?.saveConversation(messages)
            }
        }
    }
    
    fun getHistoryList(): StateFlow<List<ConversationHistory>>? {
        return historyManager?.historyList
    }
    
    fun loadConversationFromHistory(conversationId: String) {
        val messages = historyManager?.loadConversation(conversationId)
        if (messages != null) {
            _messages.value = messages
            // 标记当前对话来自历史记录
            currentConversationId = conversationId
            isFromHistory = true
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        _error.value = null
        // 重置对话来源标记
        currentConversationId = null
        isFromHistory = false
    }
    
    override fun onCleared() {
        super.onCleared()
        currentRequestJob?.cancel()
        voiceService?.release()
    }
}
