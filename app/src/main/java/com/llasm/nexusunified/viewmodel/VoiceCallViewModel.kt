package com.llasm.nexusunified.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.realtime.RealtimeWebSocketClient
import com.llasm.nexusunified.realtime.RealtimeAudioManager
import com.llasm.nexusunified.service.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class VoiceCallViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "VoiceCallViewModel"
        private const val MAX_DIALOG_MESSAGE_COUNT = 20
        private const val RESPONSE_TIMEOUT_MS = 30000L // 30秒超时
    }
    
    // 状态管理
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isWaitingForResponse = MutableStateFlow(false)
    val isWaitingForResponse: StateFlow<Boolean> = _isWaitingForResponse.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private val _isSubtitlesEnabled = MutableStateFlow(false)
    val isSubtitlesEnabled: StateFlow<Boolean> = _isSubtitlesEnabled.asStateFlow()
    
    // 对话字幕数据类
    data class SubtitleMessage(
        val isUser: Boolean,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    // 字幕相关状态
    private val _currentUserQuestion = MutableStateFlow("")
    val currentUserQuestion: StateFlow<String> = _currentUserQuestion.asStateFlow()
    
    private val _currentAIAnswer = MutableStateFlow("对话字幕已开启，等待对话...")
    val currentAIAnswer: StateFlow<String> = _currentAIAnswer.asStateFlow()
    
    private val _subtitleHistory = MutableStateFlow<List<SubtitleMessage>>(emptyList())
    val subtitleHistory: StateFlow<List<SubtitleMessage>> = _subtitleHistory.asStateFlow()
    
    // 消息状态
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _currentMessage = MutableStateFlow("")
    val currentMessage: StateFlow<String> = _currentMessage.asStateFlow()
    
    // 服务实例
    private var aiService: AIService? = null
    private var audioManager: RealtimeAudioManager? = null
    private var webSocketClient: RealtimeWebSocketClient? = null
    private var context: Context? = null
    
    // 录音状态
    private var isRecordingInProgress = false
    
    // 响应等待超时机制
    private var responseTimeoutJob: Job? = null
    
    init {
    }
    
    /**
     * 初始化服务
     */
    fun initializeServices(context: Context) {
        try {
            // 保存Context
            this.context = context
            
            // 初始化AI服务
            aiService = AIService(context)
            
            
            // 初始化音频管理器
            audioManager = RealtimeAudioManager(
                context = context,
                onAudioData = { audioData ->
                    // 音频数据回调
                    viewModelScope.launch {
                        webSocketClient?.sendAudioData(audioData)
                    }
                },
                onError = { error ->
                    Log.e(TAG, "音频错误: $error")
                    _currentMessage.value = "❌ 音频错误: $error"
                },
                onPlaybackComplete = {
                    // 播放完成回调（暂时不使用）
                }
            )
            
            // 发送初始状态
            
        } catch (e: Exception) {
            Log.e(TAG, "服务初始化失败", e)
        }
    }
    
    /**
     * 开始录音（点击录音按钮）
     */
    fun startRecording() {
        if (isRecordingInProgress) {
            Log.w(TAG, "已经在录音中")
            return
        }
        
        if (!_isConnected.value) {
            Log.w(TAG, "WebSocket未连接")
            return
        }
        
        if (_isPaused.value) {
            Log.w(TAG, "通话已暂停")
            return
        }
        
        if (_isWaitingForResponse.value) {
            Log.w(TAG, "正在等待AI回复")
            return
        }
        
        try {
            
            // 检查音频管理器是否已初始化
            if (audioManager == null) {
                Log.e(TAG, "音频管理器未初始化")
                _currentMessage.value = "❌ 音频管理器未初始化"
                return
            }
            
            // 开始录音
            audioManager?.startRecording()
            isRecordingInProgress = true
            _isRecording.value = true
            _currentMessage.value = "🎤 正在录音... 再次点击停止录音"
            
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            _currentMessage.value = "❌ 开始录音失败: ${e.message}"
        }
    }
    
    /**
     * 停止录音（再次点击录音按钮）
     */
    fun stopRecording() {
        if (!isRecordingInProgress) {
            Log.w(TAG, "当前未在录音")
            return
        }
        
        try {
            
            // 停止录音
            audioManager?.stopRecording()
            isRecordingInProgress = false
            _isRecording.value = false
            _isWaitingForResponse.value = true
            _currentMessage.value = "⏳ 语音已发送，等待AI回复…"
            
            // 启动响应超时检测
            startResponseTimeout()
            
            // 获取录音数据并发送
            val audioData = audioManager?.getCurrentAudioData()
            if (audioData != null && audioData.isNotEmpty()) {
                viewModelScope.launch {
                    webSocketClient?.sendAudioData(audioData)
                }
            } else {
                Log.w(TAG, "录音数据为空")
                _currentMessage.value = "❌ 录音数据为空"
                _isWaitingForResponse.value = false
                stopResponseTimeout()
            }
            
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            _currentMessage.value = "❌ 停止录音失败: ${e.message}"
            _isWaitingForResponse.value = false
            stopResponseTimeout()
        }
    }
    
    /**
     * 切换录音状态（点击录音按钮）
     */
    fun toggleRecording() {
        if (isRecordingInProgress) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * 暂停通话
     */
    fun pauseCall() {
        try {
            
            // 停止录音
            if (isRecordingInProgress) {
                stopRecording()
            }
            
            // 停止播放
            audioManager?.stopPlayback()
            _isPlaying.value = false
            
            // 设置暂停状态
            _isPaused.value = true
            _currentMessage.value = "⏸️ 通话已暂停"
            
            
        } catch (e: Exception) {
            Log.e(TAG, "暂停通话失败", e)
        }
    }
    
    /**
     * 恢复通话
     */
    fun resumeCall() {
        try {
            
            // 重置状态
            _isPaused.value = false
            _isWaitingForResponse.value = false
            stopResponseTimeout()
            _currentMessage.value = "🎤 点击开始录音"
            
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复通话失败", e)
        }
    }
    
    /**
     * 挂断通话
     */
    fun hangupCall() {
        try {
            
            // 停止录音
            if (isRecordingInProgress) {
                stopRecording()
            }
            
            // 停止播放
            audioManager?.stopPlayback()
            _isPlaying.value = false
            
            // 断开WebSocket连接
            webSocketClient?.disconnect()
            _isConnected.value = false
            
            // 重置所有状态
            _isRecording.value = false
            _isWaitingForResponse.value = false
            stopResponseTimeout()
            _isPaused.value = false
            _currentMessage.value = "📞 通话已结束"
            
            
        } catch (e: Exception) {
            Log.e(TAG, "挂断通话失败", e)
        }
    }
    
    /**
     * 切换字幕显示
     */
    fun toggleSubtitles() {
        val newState = !_isSubtitlesEnabled.value
        _isSubtitlesEnabled.value = newState
        
        if (newState) {
            _currentMessage.value = "📝 字幕已开启"
        } else {
            _currentMessage.value = "📝 字幕已关闭"
        }
        
    }
    
    /**
     * 连接WebSocket
     */
    fun connectWebSocket() {
        try {
            
            webSocketClient = RealtimeWebSocketClient(
                onConnected = {
                    _isConnected.value = true
                    _currentMessage.value = "🎉 小美语音对话已开始，点击开始录音"
                },
                onDisconnected = {
                    _isConnected.value = false
                    _currentMessage.value = "❌ 连接已断开"
                },
                onError = { error ->
                    _currentMessage.value = "❌ 连接错误: $error"
                    Log.e(TAG, "WebSocket错误: $error")
                },
                onTranscriptionResult = { text ->
                    _currentUserQuestion.value = text
                },
                onTextOutput = { text ->
                    _currentAIAnswer.value = text
                    // 收到文本输出，说明响应已开始，停止超时检测
                    stopResponseTimeout()
                },
                onAudioData = { audioData ->
                    _isPlaying.value = true
                    audioManager?.playAudio(audioData)
                    // 收到音频数据，说明响应已开始，停止超时检测
                    stopResponseTimeout()
                },
                onResponseComplete = {
                    _isWaitingForResponse.value = false
                    stopResponseTimeout()
                    _isPlaying.value = false
                    _currentMessage.value = "🎤 点击开始录音"
                },
                onMessage = { message ->
                    _currentMessage.value = message
                }
            )
            
            viewModelScope.launch {
                webSocketClient?.connect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "连接WebSocket失败", e)
            _currentMessage.value = "❌ 连接失败: ${e.message}"
        }
    }
    
    /**
     * 断开WebSocket连接
     */
    fun disconnectWebSocket() {
        try {
            webSocketClient?.disconnect()
            _isConnected.value = false
        } catch (e: Exception) {
            Log.e(TAG, "断开WebSocket失败", e)
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        try {
            
            // 停止录音
            if (isRecordingInProgress) {
                stopRecording()
            }
            
            // 停止播放
            audioManager?.stopPlayback()
            
            // 断开连接
            webSocketClient?.disconnect()
            
            // 释放资源
            audioManager?.release()
            
            // 重置状态
            _isConnected.value = false
            _isRecording.value = false
            _isWaitingForResponse.value = false
            stopResponseTimeout()
            _isPlaying.value = false
            _isPaused.value = false
            
            
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        }
    }
    
    /**
     * 启动响应超时检测
     * 如果超过一定时间未完成处理，自动恢复初始状态
     */
    private fun startResponseTimeout() {
        // 取消之前的超时任务
        responseTimeoutJob?.cancel()
        
        responseTimeoutJob = viewModelScope.launch {
            delay(RESPONSE_TIMEOUT_MS)
            
            // 检查是否仍在等待响应
            if (_isWaitingForResponse.value) {
                Log.w(TAG, "⏰ 响应超时（${RESPONSE_TIMEOUT_MS / 1000}秒），自动恢复初始状态")
                
                // 恢复初始状态
                _isWaitingForResponse.value = false
                _isRecording.value = false
                _isPlaying.value = false
                _currentMessage.value = "⏰ 响应超时，已自动恢复。可以重新开始录音"
                
                Log.d(TAG, "✅ 已自动恢复初始状态，可以重新开始录音")
            }
        }
    }
    
    /**
     * 停止响应超时检测
     */
    private fun stopResponseTimeout() {
        responseTimeoutJob?.cancel()
        responseTimeoutJob = null
    }
}