package com.llasm.nexusunified.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.realtime.RealtimeWebSocketClient
import com.llasm.nexusunified.realtime.RealtimeAudioManager
import com.llasm.nexusunified.service.AIService
import com.llasm.nexusunified.network.MonitorClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class VoiceCallViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "VoiceCallViewModel"
        private const val MAX_DIALOG_MESSAGE_COUNT = 20
        private const val MIN_RECORDING_TIME_MS = 4000L // 4秒最小录音时间
    }
    
    // 状态管理
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isWaitingForResponse = MutableStateFlow(false)
    val isWaitingForResponse: StateFlow<Boolean> = _isWaitingForResponse.asStateFlow()
    
    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()
    
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
    
    private val _currentUserQuestion = MutableStateFlow("")
    val currentUserQuestion: StateFlow<String> = _currentUserQuestion.asStateFlow()
    
    private val _currentAIAnswer = MutableStateFlow("")
    val currentAIAnswer: StateFlow<String> = _currentAIAnswer.asStateFlow()
    
    // 用于累积AI回答的临时变量
    private var _accumulatedAIAnswer = ""
    
    private val _subtitleHistory = MutableStateFlow<List<SubtitleMessage>>(emptyList())
    val subtitleHistory: StateFlow<List<SubtitleMessage>> = _subtitleHistory.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _statusText = MutableStateFlow("正在连接...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()
    
    private val _hintText = MutableStateFlow("正在建立连接...")
    val hintText: StateFlow<String> = _hintText.asStateFlow()
    
    // 组件
    private var webSocketClient: RealtimeWebSocketClient? = null
    private var audioManager: RealtimeAudioManager? = null
    private var aiService: AIService? = null
    private var monitorClient: MonitorClient? = null
    private var context: Context? = null
    
    // 录音状态
    private var recordingStartTime = 0L
    private var currentAudioData: ByteArray? = null
    
    fun initialize(context: Context) {
        try {
            // 保存context引用
            this.context = context
            
            // 初始化AI服务
            aiService = AIService(context)
            Log.d(TAG, "AI服务初始化成功")
            
            // 初始化监控客户端
            monitorClient = MonitorClient(context)
            Log.d(TAG, "监控客户端初始化成功")
            
            // 发送初始状态
            sendInitialStatus()
            
            // 初始化音频组件
            initializeAudioComponents(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            _statusText.value = "初始化失败: ${e.message}"
        }
    }
    
    private fun initializeAudioComponents(context: Context) {
        try {
            // 初始化音频管理器
            audioManager = RealtimeAudioManager(
                context = context,
                onAudioData = { _ ->
                    // 音频数据回调（暂时不使用）
                },
                onError = { error ->
                    viewModelScope.launch {
                        addLogMessage("❌ 音频错误: $error")
                    }
                },
                onVoiceActivity = { active ->
                    viewModelScope.launch {
                        // 如果正在播放音频，忽略VAD检测
                        if (_isPlaying.value) {
                            addLogMessage("🔊 正在播放音频，忽略VAD检测")
                            return@launch
                        }
                        
                        _isVoiceActive.value = active
                        updateVADStatus()
                        if (active) {
                            addLogMessage("🎤 VAD检测到语音活动")
                        } else {
                            addLogMessage("🔇 VAD检测到静音")
                            // 当VAD检测到语音结束时，立即停止录音
                            if (_isRecording.value && !_isWaitingForResponse.value) {
                                val recordingDuration = System.currentTimeMillis() - recordingStartTime
                                if (recordingDuration >= MIN_RECORDING_TIME_MS) {
                                    addLogMessage("语音结束，立即停止录音并发送音频数据...")
                                    // 立即停止录音并发送音频数据
                                    stopRecordingAndSend()
                                } else {
                                    val remainingTime = (MIN_RECORDING_TIME_MS - recordingDuration) / 1000.0
                                    addLogMessage("⚠️ 录音时间不足4秒，还需 ${String.format("%.1f", remainingTime)} 秒，继续录音...")
                                    // 不停止录音，继续等待
                                }
                            }
                        }
                    }
                }
            )
            
            if (!audioManager!!.initializeRecording()) {
                addLogMessage("❌ 音频录制初始化失败")
                return
            }
            
            if (!audioManager!!.initializePlayback()) {
                addLogMessage("❌ 音频播放初始化失败")
                return
            }
            
            // 初始化WebSocket客户端
            webSocketClient = RealtimeWebSocketClient(
                onMessage = { message ->
                    viewModelScope.launch {
                        addLogMessage(message)
                    }
                },
                onAudioData = { audioData ->
                    viewModelScope.launch {
                        // 设置播放状态
                        _isPlaying.value = true
                        addLogMessage("🔊 开始播放AI回复音频")
                        
                        // 播放AI回复的音频
                        audioManager?.playAudioData(audioData)
                        
                        // 播放完成后重置状态
                        delay(100) // 给播放器一点时间启动
                        while (audioManager?.isPlaying() == true) {
                            delay(100)
                        }
                        _isPlaying.value = false
                        addLogMessage("🔊 AI回复音频播放完成")
                    }
                },
                onTranscriptionResult = { text ->
                    Log.d(TAG, "=== onTranscriptionResult回调被调用 ===")
                    Log.d(TAG, "接收到的语音识别文本: '$text'")
                    Log.d(TAG, "文本长度: ${text.length}")
                    viewModelScope.launch {
                        Log.d(TAG, "在协程中调用updateUserQuestion")
                        // 更新用户问题字幕
                        updateUserQuestion(text)
                        Log.d(TAG, "updateUserQuestion调用完成")
                    }
                },
                onTextOutput = { text ->
                    Log.d(TAG, "=== onTextOutput回调被调用 ===")
                    Log.d(TAG, "接收到的AI回答文本: '$text'")
                    Log.d(TAG, "文本长度: ${text.length}")
                    viewModelScope.launch {
                        Log.d(TAG, "在协程中调用updateSubtitle")
                        // 更新AI回答字幕
                        updateSubtitle(text)
                        Log.d(TAG, "updateSubtitle调用完成")
                    }
                },
                onError = { error ->
                    viewModelScope.launch {
                        addLogMessage("❌ 连接错误: $error")
                        _isConnected.value = false
                        _isWaitingForResponse.value = false
                        _statusText.value = "准备就绪，点击开始录音"
                        updateButtonStates()
                    }
                },
                onConnected = {
                    viewModelScope.launch {
                        addLogMessage("✅ 已连接到AI语音服务")
                        _isConnected.value = true
                        updateButtonStates()
                        
                        // 连接成功后自动开始录音
                        autoStartRecording()
                    }
                },
                onDisconnected = {
                    viewModelScope.launch {
                        addLogMessage("❌ 连接已断开")
                        _isConnected.value = false
                        updateButtonStates()
                    }
                },
                onResponseComplete = {
                    viewModelScope.launch {
                        // AI响应完成，重置状态
                        _isWaitingForResponse.value = false
                        _isPlaying.value = false
                        _statusText.value = "准备下一轮对话"
                        updateButtonStates()
                        addLogMessage("✅ AI响应结束")
                        
                        // 完成当前对话，添加到历史记录
                        completeUserQuestion()
                        completeAIAnswer()
                        
                        // 清空当前显示，准备下一轮对话
                        _currentUserQuestion.value = ""
                        _currentAIAnswer.value = ""
                        
                        // 清理状态
                        currentAudioData = null
                        
                        // AI回复完成后，延迟3秒再恢复VAD
                        delay(3000)
                        if (_isConnected.value && !_isWaitingForResponse.value && !_isPlaying.value && !_isPaused.value) {
                            audioManager?.resumeVAD()
                            
                            // 自动开始下一轮录音
                            addLogMessage("🎤 准备下一轮对话，自动开始录音...")
                            startRecording()
                        }
                    }
                },
            )
            
            // 连接到WebSocket服务器
            viewModelScope.launch {
                try {
                    webSocketClient?.connect()
                } catch (e: Exception) {
                    addLogMessage("❌ 连接失败: ${e.message}")
                }
            }
            
            addLogMessage("🔧 正在初始化语音服务...")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化音频组件失败", e)
            addLogMessage("❌ 初始化失败: ${e.message}")
        }
    }
    
    private fun sendInitialStatus() {
        try {
            monitorClient?.sendAppStatus(
                appVersion = "1.0.0",
                isActive = true,
                currentScreen = "VoiceCall",
                lastActivity = "应用启动",
                memoryUsage = 0.0,
                cpuUsage = 0.0,
                networkStatus = "unknown",
                apiCallsCount = 0,
                errorCount = 0
            )
            Log.d(TAG, "初始状态已发送到监控后端")
        } catch (e: Exception) {
            Log.e(TAG, "发送初始状态失败", e)
        }
    }
    
    fun startRecording() {
        if (_isRecording.value) {
            addLogMessage("⚠️ 当前正在录音中")
            return
        }
        
        if (!_isConnected.value) {
            addLogMessage("❌ 未连接到服务器，请稍后再试")
            return
        }
        
        if (_isPaused.value) {
            addLogMessage("⚠️ 通话已暂停，请先恢复通话")
            return
        }
        
        if (_isWaitingForResponse.value) {
            addLogMessage("⚠️ 正在等待AI回复，请稍后再试")
            return
        }
        
        try {
            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()
            updateButtonStates()
            _statusText.value = "正在录音...请说话"
            addLogMessage("🎤 开始录音...")
            
            // 开始录音
            audioManager?.startRecording()
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            addLogMessage("❌ 开始录音失败: ${e.message}")
            _isRecording.value = false
            updateButtonStates()
        }
    }
    
    /**
     * 停止录音并发送音频数据
     */
    private fun stopRecordingAndSend() {
        if (!_isRecording.value) {
            addLogMessage("⚠️ 当前未在录音")
            return
        }
        
        try {
            // 检查录音时间是否达到最小要求
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            if (recordingDuration < MIN_RECORDING_TIME_MS) {
                val remainingTime = (MIN_RECORDING_TIME_MS - recordingDuration) / 1000.0
                addLogMessage("⚠️ 录音时间不足4秒，还需 ${String.format("%.1f", remainingTime)} 秒")
                return
            }
            
            // 先获取音频数据，再停止录音（跳过录音状态检查）
            val audioData = audioManager?.getCurrentAudioData(checkRecording = false)
            
            // 然后停止录音
            audioManager?.stopRecording()
            _isRecording.value = false
            _isVoiceActive.value = false
            _isWaitingForResponse.value = true
            updateButtonStates()
            _statusText.value = "处理中..."
            addLogMessage("⏹️ 录音已停止，正在发送音频数据... (录音时长: ${String.format("%.1f", recordingDuration / 1000.0)} 秒)")
            
            if (audioData == null) {
                addLogMessage("❌ 获取音频数据失败，请重试")
                _isWaitingForResponse.value = false
                updateButtonStates()
                return
            }
            
            addLogMessage("✅ 音频数据获取成功，正在发送...")
            
            // 保存当前音频数据
            currentAudioData = audioData
            
            // 发送音频数据到AI
            sendAudioToAI(audioData)
            
            addLogMessage("📤 音频数据已发送，等待AI回复...")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音并发送失败", e)
            addLogMessage("❌ 停止录音并发送失败: ${e.message}")
            _isWaitingForResponse.value = false
            updateButtonStates()
        }
    }
    
    /**
     * 发送当前轮次的录音（不结束整个录音流程）
     */
    private fun sendCurrentRecording() {
        if (!_isRecording.value) {
            addLogMessage("⚠️ 当前未在录音")
            return
        }
        
        try {
            // 检查录音时间是否达到最小要求
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            if (recordingDuration < MIN_RECORDING_TIME_MS) {
                val remainingTime = (MIN_RECORDING_TIME_MS - recordingDuration) / 1000.0
                addLogMessage("⚠️ 录音时间不足4秒，还需 ${String.format("%.1f", remainingTime)} 秒")
                return
            }
            
            // 暂停VAD但保持录音状态
            audioManager?.pauseVAD()
            _isWaitingForResponse.value = true
            updateButtonStates()
            _statusText.value = "处理中..."
            addLogMessage("⏹️ 发送当前轮次录音... (录音时长: ${String.format("%.1f", recordingDuration / 1000.0)} 秒)")
            
            // 获取当前音频数据（不停止录音）
            val audioData = audioManager?.getCurrentAudioData()
            
            if (audioData == null) {
                addLogMessage("❌ 获取音频数据失败，请重试")
                _isWaitingForResponse.value = false
                audioManager?.resumeVAD()
                updateButtonStates()
                return
            }
            
            addLogMessage("✅ 音频数据获取成功，正在发送...")
            
            // 保存当前音频数据
            currentAudioData = audioData
            
            // 重置录音开始时间，为下一轮录音做准备
            recordingStartTime = System.currentTimeMillis()
            
            // 发送音频数据到AI
            sendAudioToAI(audioData)
            
            // 立即恢复VAD检测，为下一轮录音做准备
            audioManager?.resumeVAD()
            addLogMessage("🎤 VAD已恢复，等待下一轮语音...")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送当前录音失败", e)
            addLogMessage("❌ 发送当前录音失败: ${e.message}")
            _isWaitingForResponse.value = false
            audioManager?.resumeVAD()
            updateButtonStates()
        }
    }
    
    fun stopRecording() {
        if (!_isRecording.value) {
            addLogMessage("⚠️ 当前未在录音")
            return
        }
        
        try {
            // 检查录音时间是否达到最小要求
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            if (recordingDuration < MIN_RECORDING_TIME_MS) {
                val remainingTime = (MIN_RECORDING_TIME_MS - recordingDuration) / 1000.0
                addLogMessage("⚠️ 录音时间不足4秒，还需 ${String.format("%.1f", remainingTime)} 秒")
                return
            }
            
            // 先获取音频数据，再停止录音
            val audioData = audioManager?.getCurrentAudioData(checkRecording = false)
            
            // 然后停止录音
            audioManager?.stopRecording()
            
            // 更新状态
            _isRecording.value = false
            _isVoiceActive.value = false
            _isWaitingForResponse.value = true
            audioManager?.pauseVAD()
            updateButtonStates()
            _statusText.value = "处理中..."
            addLogMessage("⏹️ 录音已停止，正在处理... (录音时长: ${String.format("%.1f", recordingDuration / 1000.0)} 秒)")
            
            if (audioData == null) {
                addLogMessage("❌ 录音失败，请重试")
                _isWaitingForResponse.value = false
                audioManager?.resumeVAD()
                _statusText.value = "准备就绪，点击开始录音"
                updateButtonStates()
                return
            }
            
            addLogMessage("✅ 录音成功，正在发送...")
            
            // 保存当前音频数据
            currentAudioData = audioData
            
            // 发送音频数据到AI
            sendAudioToAI(audioData)
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            addLogMessage("❌ 停止录音失败: ${e.message}")
            _isRecording.value = false
            _isWaitingForResponse.value = false
            audioManager?.resumeVAD()
            updateButtonStates()
        }
    }
    
    private fun sendAudioToAI(audioData: ByteArray) {
        viewModelScope.launch {
            try {
                _isWaitingForResponse.value = true
                
                // 按照Python代码分块发送
                val chunkSize = 3200 // 16000Hz * 0.2秒 = 3200字节
                
                // 确保音频数据长度是chunkSize的整数倍
                val paddingNeeded = (chunkSize - (audioData.size % chunkSize)) % chunkSize
                val paddedAudioData = if (paddingNeeded > 0) {
                    audioData + ByteArray(paddingNeeded)
                } else {
                    audioData
                }
                
                // 发送所有音频块
                for (i in 0 until paddedAudioData.size step chunkSize) {
                    val chunk = paddedAudioData.sliceArray(i until i + chunkSize)
                    webSocketClient?.sendAudioData(chunk)
                    delay(10)
                }
                
                // 发送静音块作为结束标记
                webSocketClient?.sendSilenceChunks()
                
                addLogMessage("📤 语音已发送，等待AI回复...")
                
            } catch (e: Exception) {
                addLogMessage("❌ 发送语音失败: ${e.message}")
                _isWaitingForResponse.value = false
                audioManager?.resumeVAD()
                updateButtonStates()
            }
        }
    }
    
    fun hangup() {
        addLogMessage("📞 挂断电话")
        
        // 重置所有状态
        _isRecording.value = false
        _isWaitingForResponse.value = false
        _isPlaying.value = false
        _isVoiceActive.value = false
        _isConnected.value = false
        _isPaused.value = false
        
        // 停止录音和播放
        audioManager?.stopRecording()
        audioManager?.stopPlayback()
        audioManager?.pauseVAD()
        
        // 断开WebSocket连接
        webSocketClient?.disconnect()
        webSocketClient = null
        
        // 清理状态
        currentAudioData = null
        
        // 清理音频管理器
        audioManager?.release()
        audioManager = null
        
        addLogMessage("✅ 电话已挂断，所有资源已清理，返回首页")
    }
    
    /**
     * 暂停通话（保持静默状态和服务器连接）
     */
    fun pauseCall() {
        addLogMessage("⏸️ 暂停通话")
        
        _isPaused.value = true
        _isRecording.value = false
        _isWaitingForResponse.value = false
        _isPlaying.value = false
        
        // 暂停录音但保持连接
        audioManager?.pauseRecording()
        audioManager?.pauseVAD()
        
        updateButtonStates()
        _statusText.value = "通话已暂停"
        _hintText.value = "点击继续按钮恢复通话"
        
        addLogMessage("✅ 通话已暂停，录音已停止，服务器连接保持")
    }
    
    /**
     * 恢复通话
     */
    fun resumeCall() {
        addLogMessage("▶️ 恢复通话")
        
        _isPaused.value = false
        
        // 恢复VAD检测
        audioManager?.resumeVAD()
        
        updateButtonStates()
        _statusText.value = "准备就绪"
        _hintText.value = "点击开始语音对话"
        
        addLogMessage("✅ 通话已恢复")
        
        // 自动开始录音
        viewModelScope.launch {
            delay(500) // 延迟500ms确保状态更新完成
            if (_isConnected.value && !_isRecording.value && !_isWaitingForResponse.value && !_isPlaying.value && !_isPaused.value) {
                addLogMessage("🎤 自动开始录音...")
                startRecording()
            }
        }
    }
    
    fun toggleSubtitles() {
        _isSubtitlesEnabled.value = !_isSubtitlesEnabled.value
        Log.d(TAG, "toggleSubtitles被调用，新状态: ${_isSubtitlesEnabled.value}")
        if (_isSubtitlesEnabled.value) {
            addLogMessage("📝 对话字幕已开启")
            _currentUserQuestion.value = ""
            _currentAIAnswer.value = "对话字幕已开启，等待对话..."
            Log.d(TAG, "字幕已开启，当前用户问题: '${_currentUserQuestion.value}', AI回答: '${_currentAIAnswer.value}'")
        } else {
            addLogMessage("📝 对话字幕已关闭")
            _currentUserQuestion.value = ""
            _currentAIAnswer.value = ""
            Log.d(TAG, "字幕已关闭，清空所有字幕内容")
        }
    }
    
    /**
     * 更新AI回答字幕内容（累积模式）
     */
    private fun updateSubtitle(text: String) {
        Log.d(TAG, "=== updateSubtitle被调用 ===")
        Log.d(TAG, "传入AI回答文本片段: '$text'")
        Log.d(TAG, "文本长度: ${text.length}")
        Log.d(TAG, "字幕开启状态: ${_isSubtitlesEnabled.value}")
        Log.d(TAG, "当前累积的AI回答: '$_accumulatedAIAnswer'")
        
        if (_isSubtitlesEnabled.value && text.isNotEmpty()) {
            Log.d(TAG, "条件满足，开始累积AI回答字幕")
            
            // 累积AI回答文本
            _accumulatedAIAnswer += text
            _currentAIAnswer.value = _accumulatedAIAnswer
            
            addLogMessage("📝 AI回答字幕累积: $text")
            Log.d(TAG, "AI回答字幕已累积为: '$_accumulatedAIAnswer'")
            Log.d(TAG, "=== updateSubtitle完成 ===")
        } else {
            Log.w(TAG, "字幕更新被跳过")
            Log.w(TAG, "原因 - 字幕开启状态: ${_isSubtitlesEnabled.value}, 文本长度: ${text.length}")
            Log.d(TAG, "=== updateSubtitle跳过 ===")
        }
    }
    
    /**
     * 完成AI回答，添加到历史记录
     */
    private fun completeAIAnswer() {
        if (_accumulatedAIAnswer.isNotEmpty()) {
            Log.d(TAG, "=== completeAIAnswer被调用 ===")
            Log.d(TAG, "完成AI回答: '$_accumulatedAIAnswer'")
            
            // 添加到字幕历史记录
            val currentHistory = _subtitleHistory.value.toMutableList()
            currentHistory.add(SubtitleMessage(isUser = false, content = _accumulatedAIAnswer))
            // 只保留最近20条字幕
            if (currentHistory.size > 20) {
                currentHistory.removeAt(0)
            }
            _subtitleHistory.value = currentHistory
            Log.d(TAG, "AI回答已添加到历史记录，当前数量: ${currentHistory.size}")
            
            // 清空累积的AI回答，准备下一轮
            _accumulatedAIAnswer = ""
            Log.d(TAG, "=== completeAIAnswer完成 ===")
        }
    }
    
    /**
     * 更新用户问题字幕内容
     */
    private fun updateUserQuestion(text: String) {
        Log.d(TAG, "=== updateUserQuestion被调用 ===")
        Log.d(TAG, "传入用户问题文本: '$text'")
        Log.d(TAG, "文本长度: ${text.length}")
        Log.d(TAG, "字幕开启状态: ${_isSubtitlesEnabled.value}")
        Log.d(TAG, "当前用户问题: '${_currentUserQuestion.value}'")
        
        if (_isSubtitlesEnabled.value && text.isNotEmpty()) {
            Log.d(TAG, "条件满足，开始更新用户问题字幕")
            _currentUserQuestion.value = text
            addLogMessage("📝 用户问题字幕更新: $text")
            Log.d(TAG, "用户问题字幕已更新为: '$text'")
            Log.d(TAG, "=== updateUserQuestion完成 ===")
        } else {
            Log.w(TAG, "用户问题字幕更新被跳过")
            Log.w(TAG, "原因 - 字幕开启状态: ${_isSubtitlesEnabled.value}, 文本长度: ${text.length}")
            Log.d(TAG, "=== updateUserQuestion跳过 ===")
        }
    }
    
    /**
     * 完成用户问题，添加到历史记录
     */
    private fun completeUserQuestion() {
        if (_currentUserQuestion.value.isNotEmpty()) {
            Log.d(TAG, "=== completeUserQuestion被调用 ===")
            Log.d(TAG, "完成用户问题: '${_currentUserQuestion.value}'")
            
            // 添加到字幕历史记录
            val currentHistory = _subtitleHistory.value.toMutableList()
            currentHistory.add(SubtitleMessage(isUser = true, content = _currentUserQuestion.value))
            // 只保留最近20条字幕
            if (currentHistory.size > 20) {
                currentHistory.removeAt(0)
            }
            _subtitleHistory.value = currentHistory
            Log.d(TAG, "用户问题已添加到历史记录，当前数量: ${currentHistory.size}")
            Log.d(TAG, "=== completeUserQuestion完成 ===")
        }
    }
    
    /**
     * 清空字幕
     */
    private fun clearSubtitle() {
        _currentUserQuestion.value = ""
        _currentAIAnswer.value = ""
        _accumulatedAIAnswer = ""
        addLogMessage("📝 字幕已清空")
    }
    
    private fun updateButtonStates() {
        when {
            !_isConnected.value -> {
                _statusText.value = "正在连接..."
                _hintText.value = "正在建立连接..."
            }
            _isPaused.value -> {
                _statusText.value = "通话已暂停"
                _hintText.value = "点击继续按钮恢复通话"
            }
            _isRecording.value -> {
                val vadStatus = if (_isVoiceActive.value) " (检测到语音)" else " (静音中)"
                _statusText.value = "正在录音$vadStatus"
                _hintText.value = "请说话..."
            }
            _isWaitingForResponse.value -> {
                _statusText.value = "等待AI回复"
                _hintText.value = "AI正在处理中..."
            }
            else -> {
                _statusText.value = "准备就绪"
                _hintText.value = "点击开始语音对话"
            }
        }
    }
    
    private fun updateVADStatus() {
        if (_isRecording.value) {
            val vadStatus = if (_isVoiceActive.value) " (检测到语音)" else " (静音中)"
            _statusText.value = "正在录音$vadStatus"
        }
    }
    
    private fun autoStartRecording() {
        viewModelScope.launch {
            // 延迟1秒后自动开始录音，确保连接稳定
            delay(1000)
            
            if (_isConnected.value && !_isRecording.value && !_isWaitingForResponse.value) {
                addLogMessage("🎤 自动开始录音...")
                startRecording()
            }
        }
    }
    
    
    private fun addLogMessage(message: String) {
        val logMessage = ChatMessage(
            content = message,
            isUser = false
        )
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(logMessage)
        
        // 限制消息数量
        if (currentMessages.size > MAX_DIALOG_MESSAGE_COUNT) {
            currentMessages.removeAt(0)
        }
        
        _messages.value = currentMessages
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // 停止录音
        if (_isRecording.value) {
            audioManager?.stopRecording()
        }
        
        // 停止播放
        audioManager?.stopPlayback()
        
        // 断开WebSocket连接
        webSocketClient?.disconnect()
        
        // 释放音频资源
        audioManager?.release()
        
        // 清理状态
        currentAudioData = null
    }
}
