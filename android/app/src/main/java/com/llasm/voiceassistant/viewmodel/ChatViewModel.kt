package com.llasm.voiceassistant.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llasm.voiceassistant.data.ChatMessage
import com.llasm.voiceassistant.data.MessageType
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
    
    private var voiceService: VoiceService? = null
    private var userManager: UserManager? = null
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
    
    fun clearMessages() {
        _messages.value = emptyList()
    }
    
    fun testConnection() {
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                val apiService = NetworkModule.getApiService()
                val response = apiService.healthCheck()
                if (response.isSuccessful) {
                    _error.value = "✅ 连接成功！后端服务运行正常"
                } else {
                    _error.value = "❌ 连接失败: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                _error.value = "❌ 连接失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun initializeVoiceService(context: Context) {
        voiceService = VoiceService(context)
    }
    
    fun initializeUserManager(context: Context) {
        if (userManager == null) {
            userManager = UserManager(context)
            userManager?.initialize()
        }
    }
    
    fun startVoiceRecording() {
        if (_isRecording.value) return
        
        voiceService?.startVoiceRecording(
            onRecordingStarted = {
                _isRecording.value = true
            },
            onRecordingStopped = {
                _isRecording.value = false
            },
            onError = { error ->
                _isRecording.value = false
                _error.value = "录音失败: ${error.message}"
            }
        )
    }
    
    fun stopVoiceRecording() {
        if (!_isRecording.value) return
        
        voiceService?.stopVoiceRecording(
            onTranscriptionComplete = { transcription ->
                _isRecording.value = false
                if (transcription.isNotBlank()) {
                    sendMessage(transcription)
                }
            },
            onError = { error ->
                _isRecording.value = false
                _error.value = "语音识别失败: ${error.message}"
            }
        )
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
    
    override fun onCleared() {
        super.onCleared()
        currentRequestJob?.cancel()
        voiceService?.release()
    }
}
