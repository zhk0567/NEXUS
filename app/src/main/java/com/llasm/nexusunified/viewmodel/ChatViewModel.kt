package com.llasm.nexusunified.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.data.Conversation
import com.llasm.nexusunified.repository.ConversationRepository
import com.llasm.nexusunified.network.NetworkModule
import com.llasm.nexusunified.service.AIService
import com.llasm.nexusunified.service.StreamingAIService
import com.llasm.nexusunified.service.TTSService
import com.llasm.nexusunified.service.SimpleTTSService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class ChatViewModel : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 管理每个消息的播放状态
    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()
    
    // TTS加载状态
    private val _isTTSLoading = MutableStateFlow(false)
    val isTTSLoading: StateFlow<Boolean> = _isTTSLoading.asStateFlow()
    
    // 管理每个消息的TTS加载状态
    private val _loadingTTSMessageId = MutableStateFlow<String?>(null)
    val loadingTTSMessageId: StateFlow<String?> = _loadingTTSMessageId.asStateFlow()
    
    // ASR识别状态
    private val _isASRRecognizing = MutableStateFlow(false)
    val isASRRecognizing: StateFlow<Boolean> = _isASRRecognizing.asStateFlow()
    
    // ASR识别文本
    private val _asrRecognizingText = MutableStateFlow("")
    val asrRecognizingText: StateFlow<String> = _asrRecognizingText.asStateFlow()
    
    private var currentRequestJob: Job? = null
    private var aiService: AIService? = null
    private var streamingAIService: StreamingAIService? = null
    private var context: Context? = null
    private var conversationRepository: ConversationRepository? = null
    
    // 流式对话状态
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()
    
    private val _currentStreamingMessage = MutableStateFlow<ChatMessage?>(null)
    val currentStreamingMessage: StateFlow<ChatMessage?> = _currentStreamingMessage.asStateFlow()
    
    // 流式请求状态 - 用于显示"请稍候"提示
    private val _isStreamingRequestStarted = MutableStateFlow(false)
    val isStreamingRequestStarted: StateFlow<Boolean> = _isStreamingRequestStarted.asStateFlow()
    
    fun initializeAIService(context: Context) {
        this.context = context
        aiService = AIService(context)
        streamingAIService = StreamingAIService(context)
        conversationRepository = ConversationRepository(context)
        
        // 预加载常用TTS音频
        val ttsService = TTSService.getInstance(context)
        ttsService.preloadCommonAudio()
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        // 如果已有请求在进行中，先取消
        currentRequestJob?.cancel()
        
        // 如果没有当前对话，创建一个新对话
        if (conversationRepository?.getCurrentConversation() == null) {
            conversationRepository?.startNewConversation()
        }
        
        val userMessage = ChatMessage(
            content = content.trim(),
            isUser = true
        )
        
        _messages.value = _messages.value + userMessage
        _isLoading.value = true
        _error.value = null
        
        currentRequestJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val aiService = aiService
                if (aiService == null) {
                    _error.value = "AI服务未初始化"
                    return@launch
                }
                
                
                // 检查API配置
                val apiConfig = aiService.checkApiConfiguration()
                if (!apiConfig["deepseek_configured"]!!) {
                    _error.value = "DeepSeek API未配置，请在AIService.kt中设置正确的API Key"
                    return@launch
                }
                
                // 转换消息格式
                val conversationHistory = _messages.value.map { message ->
                    com.llasm.nexusunified.service.ChatMessage(
                        content = message.content,
                        isFromUser = message.isUser,
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                val result = aiService.chatWithText(content.trim(), conversationHistory)
                
                if (result.isSuccess) {
                    val chatResponse = result.getOrThrow()
                    val aiMessage = ChatMessage(
                        content = chatResponse.response,
                        isUser = false
                    )
                    _messages.value = _messages.value + aiMessage
                    
                    // 保存消息到历史记录
                    saveCurrentMessagesToConversation()
                    
                    // 聊天成功
                    val duration = (System.currentTimeMillis() - startTime) / 1000.0
                } else {
                    _error.value = "AI对话失败: ${result.exceptionOrNull()?.message}"
                    
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
    
    /**
     * 语音对话 - 语音输入，文字输出
     */
    fun sendVoiceMessage(audioData: ByteArray) {
        if (audioData.isEmpty()) return
        
        // 如果已有请求在进行中，先取消
        currentRequestJob?.cancel()
        
        _isLoading.value = true
        _error.value = null
        
        currentRequestJob = viewModelScope.launch {
            try {
                val aiService = aiService
                if (aiService == null) {
                    _error.value = "AI服务未初始化"
                    return@launch
                }
                
                // 检查API配置
                val apiConfig = aiService.checkApiConfiguration()
                if (!apiConfig["volcano_configured"]!!) {
                    _error.value = "火山引擎API未配置，请在AIService.kt中设置正确的API Key"
                    return@launch
                }
                
                // 1. 语音转文字
                val transcriptionResult = aiService.transcribeAudio(audioData)
                if (transcriptionResult.isFailure) {
                    _error.value = "语音识别失败: ${transcriptionResult.exceptionOrNull()?.message}"
                    return@launch
                }
                
                val transcription = transcriptionResult.getOrThrow()
                
                // 添加用户语音消息（显示识别结果）
                val userMessage = ChatMessage(
                    content = "[语音] ${transcription.transcription}",
                    isUser = true
                )
                _messages.value = _messages.value + userMessage
                
                // 2. 文字对话
                val conversationHistory = _messages.value.map { message ->
                    com.llasm.nexusunified.service.ChatMessage(
                        content = message.content,
                        isFromUser = message.isUser,
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                val chatResult = aiService.chatWithText(transcription.transcription, conversationHistory)
                
                if (chatResult.isSuccess) {
                    val chatResponse = chatResult.getOrThrow()
                    val aiMessage = ChatMessage(
                        content = chatResponse.response,
                        isUser = false
                    )
                    _messages.value = _messages.value + aiMessage
                } else {
                    _error.value = "AI对话失败: ${chatResult.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                if (e.message?.contains("CancellationException") == true) {
                    return@launch
                }
                _error.value = "语音对话失败: ${e.message}"
            } finally {
                _isLoading.value = false
                currentRequestJob = null
            }
        }
    }
    
    /**
     * 端到端语音对话 - 语音输入，语音输出
     */
    fun sendVoiceChat(audioData: ByteArray) {
        if (audioData.isEmpty()) return
        
        // 如果已有请求在进行中，先取消
        currentRequestJob?.cancel()
        
        _isLoading.value = true
        _error.value = null
        
        currentRequestJob = viewModelScope.launch {
            try {
                val aiService = aiService
                if (aiService == null) {
                    _error.value = "AI服务未初始化"
                    return@launch
                }
                
                // 检查API配置
                val apiConfig = aiService.checkApiConfiguration()
                if (!apiConfig["deepseek_configured"]!! || !apiConfig["volcano_configured"]!!) {
                    _error.value = "API未完全配置，请检查AIService.kt中的API Key设置"
                    return@launch
                }
                
                // 端到端语音对话
                val voiceChatResult = aiService.voiceChat(audioData)
                
                if (voiceChatResult.isSuccess) {
                    val voiceResponse = voiceChatResult.getOrThrow()
                    
                    // 添加用户语音消息
                    val userMessage = ChatMessage(
                        content = "[语音] ${voiceResponse.transcription}",
                        isUser = true
                    )
                    _messages.value = _messages.value + userMessage
                    
                    // 添加AI语音回复
                    val aiMessage = ChatMessage(
                        content = "[语音回复] ${voiceResponse.response}",
                        isUser = false
                    )
                    _messages.value = _messages.value + aiMessage
                    
                    // TODO: 播放语音回复
                    // 这里可以添加语音播放功能
                    
                } else {
                    _error.value = "语音对话失败: ${voiceChatResult.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                if (e.message?.contains("CancellationException") == true) {
                    return@launch
                }
                _error.value = "语音对话失败: ${e.message}"
            } finally {
                _isLoading.value = false
                currentRequestJob = null
            }
        }
    }
    
    /**
     * 刷新AI回答 - 重新发送最后一个用户问题
     */
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

        // 更新消息列表，移除旧的AI回答
        _messages.value = messagesWithoutLastAI

        // 重置流式状态
        _isLoading.value = true
        _error.value = null
        _isStreaming.value = true
        _streamingText.value = ""
        _isStreamingRequestStarted.value = true // Set to true when request starts
        _currentStreamingMessage.value = ChatMessage(content = "", isUser = false) // Initialize for streaming

        currentRequestJob = viewModelScope.launch {
            try {
                // 直接使用 lastUserMessage.content 发起流式请求
                val streamingService = streamingAIService
                if (streamingService == null) {
                    _isStreaming.value = false
                    _streamingText.value = ""
                    _isStreamingRequestStarted.value = false
                    _error.value = "流式AI服务未初始化"
                    _currentStreamingMessage.value = null
                    return@launch
                }
                
                // 构建对话历史（排除最后一个AI回复）
                val conversationHistory = messagesWithoutLastAI
                
                streamingService.startStreamingChat(lastUserMessage.content, conversationHistory, object : StreamingAIService.StreamingCallback {
                    override fun onTextUpdate(content: String, fullText: String, sentenceCount: Int) {
                        _streamingText.value = fullText
                        _currentStreamingMessage.value = ChatMessage(
                            content = fullText,
                            isUser = false
                        )
                        if (_isStreamingRequestStarted.value) {
                            _isStreamingRequestStarted.value = false // First text update, so request has started
                        }
                    }
                    override fun onComplete(text: String, sentenceCount: Int) {
                        _isStreaming.value = false
                        _streamingText.value = ""
                        _isStreamingRequestStarted.value = false
                        val finalMessage = ChatMessage(content = text, isUser = false)
                        _messages.value = _messages.value + finalMessage // Add the new AI response
                        _currentStreamingMessage.value = null
                        saveCurrentMessagesToConversation() // Save after complete
                    }
                    override fun onError(message: String) {
                        _isStreaming.value = false
                        _streamingText.value = ""
                        _isStreamingRequestStarted.value = false
                        _error.value = "流式对话失败: $message"
                        _currentStreamingMessage.value = null
                    }
                })
            } catch (e: Exception) {
                _isStreaming.value = false
                _streamingText.value = ""
                _isStreamingRequestStarted.value = false
                _error.value = "流式对话异常: ${e.localizedMessage}"
                _currentStreamingMessage.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun playAudioForMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        
        // 如果正在播放，先停止当前播放
        if (_isPlaying.value) {
            stopAudio()
        }
        
        val currentContext = context
        if (currentContext == null) {
            _error.value = "播放失败: Context未初始化"
            return
        }
        
        viewModelScope.launch {
            try {
                // 设置加载状态
                _isTTSLoading.value = true
                _loadingTTSMessageId.value = messageId
                _error.value = null
                
                // 获取用户选择的音调
                val prefs = currentContext.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
                val selectedVoice = prefs.getString("selected_voice", "zh-CN-XiaoxiaoNeural") ?: "zh-CN-XiaoxiaoNeural"
                
                // 记录TTS播放开始时间
                val playStartTime = System.currentTimeMillis()
                
                // 使用TTS服务播放，使用用户选择的音调
                val ttsService = TTSService.getInstance(currentContext)
                ttsService.textToSpeechAndPlay(
                    text = text,
                    voice = selectedVoice,
                    onPlayStart = {
                        // 清除加载状态，设置播放状态
                        _isTTSLoading.value = false
                        _loadingTTSMessageId.value = null
                        _isPlaying.value = true
                        _playingMessageId.value = messageId
                    },
                    onPlayComplete = {
                        _isPlaying.value = false
                        _playingMessageId.value = null
                        
                        // TTS播放完成，记录到数据库
                        val playDuration = (System.currentTimeMillis() - playStartTime) / 1000.0
                        logTTSInteraction(text, selectedVoice, playDuration, true, actionType = "TTS播放完成")
                    },
                    onError = { error ->
                        _isTTSLoading.value = false
                        _loadingTTSMessageId.value = null
                        _isPlaying.value = false
                        _playingMessageId.value = null
                        _error.value = "播放失败: $error"
                        
                        // TTS播放失败，记录到数据库
                        val playDuration = (System.currentTimeMillis() - playStartTime) / 1000.0
                        logTTSInteraction(text, selectedVoice, playDuration, false, error, "TTS播放失败")
                    }
                )
            } catch (e: Exception) {
                _isTTSLoading.value = false
                _loadingTTSMessageId.value = null
                _isPlaying.value = false
                _playingMessageId.value = null
                _error.value = "播放失败: ${e.message}"
                
                // TTS播放异常，记录到数据库
                logTTSInteraction(text, "unknown", 0.0, false, e.message ?: "Unknown error")
            }
        }
    }
    
    
    fun stopAudio() {
        _isTTSLoading.value = false
        _loadingTTSMessageId.value = null
        _isPlaying.value = false
        _playingMessageId.value = null
        
        // 停止TTS播放
        val currentContext = context
        if (currentContext != null) {
            val ttsService = TTSService.getInstance(currentContext)
            ttsService.stopPlayback()
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
        _error.value = null
    }
    
    fun sendStreamingMessage(content: String) {
        if (content.isBlank()) return
        
        // 如果正在流式对话，先停止
        if (_isStreaming.value) {
            stopStreaming()
        }
        
        // 如果没有当前对话，创建一个新对话
        if (conversationRepository?.getCurrentConversation() == null) {
            conversationRepository?.startNewConversation()
        }
        
        val userMessage = ChatMessage(
            content = content.trim(),
            isUser = true
        )
        
        _messages.value = _messages.value + userMessage
        _isStreaming.value = true
        _isStreamingRequestStarted.value = true  // 标记流式请求已开始
        _streamingText.value = ""
        _error.value = null
        
        // 创建流式AI消息
        val streamingMessage = ChatMessage(
            content = "",
            isUser = false
        )
        _currentStreamingMessage.value = streamingMessage
        
        // 使用真正的StreamingAIService进行流式对话
        val streamingService = streamingAIService
        if (streamingService == null) {
            _isStreaming.value = false
            _streamingText.value = ""
            _error.value = "流式AI服务未初始化"
            _currentStreamingMessage.value = null
            return
        }
        
        // 构建对话历史（排除当前用户消息）
        val conversationHistory = _messages.value
        
        streamingService.startStreamingChat(content, conversationHistory, object : StreamingAIService.StreamingCallback {
            override fun onTextUpdate(content: String, fullText: String, sentenceCount: Int) {
                // 第一次收到文本更新时，重置请求开始状态
                if (_isStreamingRequestStarted.value) {
                    _isStreamingRequestStarted.value = false
                }
                
                // 实时更新流式文本
                _streamingText.value = fullText
                _currentStreamingMessage.value = ChatMessage(
                    content = fullText,
                    isUser = false
                )
            }
            
            override fun onComplete(text: String, sentenceCount: Int) {
                // 流式对话完成
                _isStreaming.value = false
                _isStreamingRequestStarted.value = false
                _streamingText.value = ""
                
                // 将最终消息添加到消息列表
                val finalMessage = ChatMessage(
                    content = text,
                    isUser = false
                )
                _messages.value = _messages.value + finalMessage
                _currentStreamingMessage.value = null
                
                // 保存消息到历史记录
                saveCurrentMessagesToConversation()
            }
            
            override fun onError(message: String) {
                // 流式对话出错
                _isStreaming.value = false
                _isStreamingRequestStarted.value = false
                _streamingText.value = ""
                _error.value = "流式对话失败: $message"
                _currentStreamingMessage.value = null
            }
        })
    }
    
    fun stopStreaming() {
        _isStreaming.value = false
        _isStreamingRequestStarted.value = false
        _streamingText.value = ""
        _currentStreamingMessage.value = null
    }
    
    // ========== 历史对话相关方法 ==========
    
    /**
     * 获取所有对话列表
     */
    fun getAllConversations(): StateFlow<List<Conversation>> {
        return conversationRepository?.conversations ?: MutableStateFlow<List<Conversation>>(emptyList()).asStateFlow()
    }
    
    /**
     * 获取当前对话ID
     */
    fun getCurrentConversationId(): StateFlow<String?> {
        return conversationRepository?.currentConversationId ?: MutableStateFlow<String?>(null).asStateFlow()
    }
    
    /**
     * 开始新对话
     */
    fun startNewConversation() {
        conversationRepository?.startNewConversation()
        _messages.value = emptyList()
        _error.value = null
    }
    
    /**
     * 选择对话
     */
    fun selectConversation(conversationId: String) {
        conversationRepository?.selectConversation(conversationId)
        val conversation = conversationRepository?.getCurrentConversation()
        if (conversation != null) {
            _messages.value = conversation.messages
        }
    }
    
    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        conversationRepository?.deleteConversation(conversationId)
    }
    
    /**
     * 删除单条消息
     */
    fun deleteMessage(messageId: String) {
        val currentMessages = _messages.value.toMutableList()
        val messageToDelete = currentMessages.find { it.id == messageId }
        
        if (messageToDelete != null) {
            currentMessages.removeAll { it.id == messageId }
            _messages.value = currentMessages
            
            // 更新历史记录
            saveCurrentMessagesToConversation()
        }
    }
    
    /**
     * 保存当前消息到对话
     */
    private fun saveCurrentMessagesToConversation() {
        android.util.Log.d("ChatViewModel", "保存消息到历史记录: messagesCount=${_messages.value.size}")
        if (conversationRepository == null) {
            android.util.Log.w("ChatViewModel", "ConversationRepository为空，无法保存历史记录")
            return
        }
        conversationRepository?.updateCurrentConversationMessages(_messages.value)
        android.util.Log.d("ChatViewModel", "历史记录保存完成")
    }
    
    /**
     * 记录TTS交互到数据库
     */
    private fun logTTSInteraction(text: String, voice: String, duration: Double, success: Boolean, errorMessage: String? = null, actionType: String = "TTS播放完成") {
        viewModelScope.launch {
            try {
                val currentContext = context
                if (currentContext == null) {
                    android.util.Log.w("ChatViewModel", "Context为空，无法记录TTS交互")
                    return@launch
                }
                
                // 获取真实的用户ID和会话ID
                val userId = com.llasm.nexusunified.data.UserManager.getUserId() ?: "android_user_${System.currentTimeMillis()}"
                val sessionId = com.llasm.nexusunified.data.UserManager.getSessionId() ?: "android_session_${System.currentTimeMillis()}"
                
                // 构建交互内容
                val interactionContent = "$actionType: $text (音色: $voice)"
                val response = if (success) "${actionType}成功" else "${actionType}失败: $errorMessage"
                
                // 调用后端API记录交互
                val apiService = com.llasm.nexusunified.network.NetworkModule.getApiService()
                val requestBody = com.llasm.nexusunified.network.LogInteractionRequest(
                    user_id = userId,
                    interaction_type = "tts_play",
                    content = interactionContent,
                    response = response,
                    session_id = sessionId,
                    duration_seconds = duration.toInt(),
                    success = success,
                    error_message = errorMessage ?: ""
                )
                
                val result = try {
                    val response = apiService.logInteraction(requestBody)
                    if (response.isSuccessful) {
                        Result.success(response.body())
                    } else {
                        Result.failure(Exception("API调用失败: ${response.code()}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
                
                if (result.isSuccess) {
                    android.util.Log.d("ChatViewModel", "TTS交互记录成功: $interactionContent")
                } else {
                    android.util.Log.w("ChatViewModel", "TTS交互记录失败: ${result.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "记录TTS交互异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 重写sendMessage以自动保存到对话
     */
    fun sendMessageWithHistory(content: String) {
        sendMessage(content)
        // sendMessage内部已经会调用saveCurrentMessagesToConversation()
    }
    
    /**
     * 重写sendStreamingMessage以自动保存到对话
     */
    fun sendStreamingMessageWithHistory(content: String) {
        sendStreamingMessage(content)
        // sendStreamingMessage内部已经会调用saveCurrentMessagesToConversation()
    }
    
    /**
     * 开始ASR识别
     */
    fun startASRRecognition() {
        _isASRRecognizing.value = true
        _asrRecognizingText.value = "正在识别中..."
    }
    
    /**
     * 更新ASR识别文本
     */
    fun updateASRRecognizingText(text: String) {
        _asrRecognizingText.value = text
    }
    
    /**
     * 完成ASR识别
     */
    fun completeASRRecognition() {
        _isASRRecognizing.value = false
        _asrRecognizingText.value = ""
    }
    
    /**
     * 取消ASR识别
     */
    fun cancelASRRecognition() {
        _isASRRecognizing.value = false
        _asrRecognizingText.value = ""
    }
    
    
    override fun onCleared() {
        super.onCleared()
        currentRequestJob?.cancel()
    }
}
