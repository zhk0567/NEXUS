package com.llasm.nexusunified

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.llasm.nexusunified.realtime.RealtimeWebSocketClient
import com.llasm.nexusunified.realtime.RealtimeAudioManager
import com.llasm.nexusunified.service.AIService
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.ui.SettingsManager
import com.llasm.nexusunified.ui.VoiceCallScreen
import com.llasm.nexusunified.ui.ConversationItem
import kotlinx.coroutines.*
import java.util.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VoiceCallComposeActivity : ComponentActivity() {
    
    private enum class Role {
        USER,        // 用户提问内容
        ASSISTANT,   // 助手机器人回复内容
        LOG,         // 日志信息
    }

    private data class Message(
        val role: Role,
        var text: String,
        var confirmed: Boolean
    )

    companion object {
        private const val TAG = "VoiceCallComposeActivity"
        private const val MAX_DIALOG_MESSAGE_COUNT = 20
        private const val PERMISSION_REQUEST_CODE = 1
        private val DIALOG_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    // 实时语音组件
    private var webSocketClient: RealtimeWebSocketClient? = null
    private var audioManager: RealtimeAudioManager? = null
    private var aiService: AIService? = null
    
    // 状态管理
    private var isRecording by mutableStateOf(false)
    private var isConnected by mutableStateOf(false)
    private var isWaitingForResponse by mutableStateOf(false)
    private var conversationHistory by mutableStateOf<List<ConversationItem>>(emptyList())
    
    // 音频处理状态
    private var currentAudioData: ByteArray? = null
    
    // 录音时间记录
    private var recordingStartTime = 0L

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // HTTP客户端用于数据库记录
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // 会话ID
    private val sessionId = "voice_call_${System.currentTimeMillis()}"
    
    // 去重机制
    private val recentUserInputs = mutableSetOf<String>()
    private val recentAIOutputs = mutableSetOf<String>()
    private val maxRecentSize = 10
    
    // 防止重复记录的时间戳
    private var lastUserInputTime = 0L
    private var lastAIOutputTime = 0L
    private val minIntervalMs = 500L // 最小间隔0.5秒，降低严格程度
    
    // 累积用户输入文本，避免分片记录
    private var accumulatedUserInput = ""
    
    // 对话配对机制
    private var pendingUserInput: String? = null
    private var pendingAIResponse: String? = null
    private val maxPairingDelayMs = 5000L // 最大配对延迟5秒

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "VoiceCallComposeActivity onCreate")
        super.onCreate(savedInstanceState)
        
        // 强制使用外放扬声器
        val systemAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        systemAudioManager.mode = AudioManager.MODE_NORMAL
        systemAudioManager.isSpeakerphoneOn = true
        systemAudioManager.isBluetoothScoOn = false
        systemAudioManager.isWiredHeadsetOn = false
        
        Log.d(TAG, "onCreate: 已设置使用外放扬声器")

        initAIService()
        requestPermissions()
        
        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager }
            val themeColors = settingsManager.getThemeColors()
            val fontStyle = settingsManager.getFontStyle()
            
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = themeColors.background
                ) {
                   VoiceCallScreen(
                       isConnected = isConnected,
                       isCalling = isRecording, // 使用现有的录音状态作为通话状态
                       isWaitingForResponse = isWaitingForResponse,
                       conversationHistory = conversationHistory,
                       onHangup = { hangup() },
                       onStartCall = { startRecording() }, // 开始录音作为开始通话
                       onEndCall = { stopRecording() }, // 停止录音作为结束通话
                       onSettings = { /* 设置功能 */ },
                       themeColors = themeColors,
                       fontStyle = fontStyle
                   )
                }
            }
        }
    }
    
    private fun initAIService() {
        try {
            aiService = AIService(this)
            Log.d(TAG, "AI服务初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "AI服务初始化失败", e)
        }
    }
    
    
    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, DIALOG_PERMISSIONS, PERMISSION_REQUEST_CODE)
        } else {
            initializeVoiceComponents()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeVoiceComponents()
            } else {
                Log.e(TAG, "录音权限被拒绝")
                finish()
            }
        }
    }
    
    private fun initializeVoiceComponents() {
        scope.launch {
            try {
                // 初始化WebSocket客户端
                webSocketClient = RealtimeWebSocketClient(
                    onMessage = { message -> handleWebSocketMessage(message) },
                    onAudioData = { audioData -> 
                        // 播放AI回复的音频
                        audioManager?.playAudio(audioData)
                    },
                    onError = { error -> handleWebSocketError(error) },
                    onConnected = { 
                        isConnected = true
                        Log.d(TAG, "WebSocket连接成功")
                    },
                    onDisconnected = { 
                        isConnected = false
                        Log.d(TAG, "WebSocket连接断开")
                    },
                    onTranscriptionResult = { text ->
                        // 处理语音识别结果
                        if (text.isNotEmpty() && text.length > 2) {
                            Log.d(TAG, "🎤 用户: $text")
                            // 记录用户输入到数据库
                            recordConversation("user", text)
                        }
                    },
                    onTextOutput = { text ->
                        // 处理AI文本输出
                        if (text.isNotEmpty() && text.length > 1) {
                            Log.d(TAG, "🤖 AI: $text")
                            handleTextResponse(text)
                        }
                    },
                    onResponseComplete = {
                        // AI响应完成，重置状态
                        isWaitingForResponse = false
                        Log.d(TAG, "✅ AI响应结束，准备下一轮对话")
                    },
                    voiceId = "zh_female_vv_jupiter_bigtts" // 使用默认音色
                )
                
                // 初始化音频管理器
                audioManager = RealtimeAudioManager(
                    context = this@VoiceCallComposeActivity,
                    onAudioData = { _ ->
                        // 音频数据回调（暂时不使用，我们通过getCurrentAudioData获取）
                    },
                    onError = { error -> handleAudioError(error) }
                )
                
                // 连接WebSocket
                webSocketClient?.connect()
                
            } catch (e: Exception) {
                Log.e(TAG, "初始化语音组件失败", e)
            }
        }
    }
    
    private fun handleWebSocketMessage(message: String) {
        scope.launch {
            try {
                // 检查是否是纯文本消息（不是JSON）
                if (message.startsWith("{") && message.endsWith("}")) {
                    // 尝试解析JSON
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    when (type) {
                        "audio_response" -> {
                            val audioData = json.optString("audio_data")
                            if (audioData.isNotEmpty()) {
                                playAudioResponse(audioData)
                            }
                        }
                        "text_response" -> {
                            val text = json.optString("text")
                            if (text.isNotEmpty()) {
                                handleTextResponse(text)
                            }
                        }
                        "status" -> {
                            val status = json.optString("status")
                            Log.d(TAG, "收到状态更新: $status")
                        }
                    }
                } else {
                    // 处理纯文本消息（可能是日志或状态信息）
                    Log.d(TAG, "收到WebSocket消息: $message")
                }
            } catch (e: Exception) {
                // 如果JSON解析失败，可能是纯文本消息
                Log.d(TAG, "收到WebSocket消息: $message")
            }
        }
    }
    
    private fun handleWebSocketError(error: String) {
        Log.e(TAG, "WebSocket错误: $error")
    }
    
    
    private fun handleAudioError(error: String) {
        Log.e(TAG, "音频错误: $error")
    }
    
    private fun playAudioResponse(audioData: String) {
        scope.launch {
            try {
                // 将Base64字符串转换为ByteArray
                val audioBytes = android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
                audioManager?.playAudio(audioBytes)
            } catch (e: Exception) {
                Log.e(TAG, "播放音频失败", e)
            }
        }
    }
    
    private fun handleTextResponse(text: String) {
        scope.launch {
            try {
                isWaitingForResponse = false
                
                // 添加到对话历史
                val newItem = ConversationItem(
                    role = "assistant",
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                conversationHistory = conversationHistory + newItem
                
                // 不记录AI回复到数据库，避免分片记录问题
                Log.d(TAG, "AI回复: $text")
            } catch (e: Exception) {
                Log.e(TAG, "处理文本回复失败", e)
            }
        }
    }
    
    private fun startRecording() {
        if (isConnected && !isRecording && !isWaitingForResponse) {
            scope.launch {
                try {
                    isRecording = true
                    recordingStartTime = System.currentTimeMillis()
                    audioManager?.startRecording()
                    Log.d(TAG, "开始录音")
                } catch (e: Exception) {
                    Log.e(TAG, "开始录音失败", e)
                    isRecording = false
                }
            }
        }
    }
    
    private fun stopRecording() {
        if (isRecording) {
            scope.launch {
                try {
                    val recordingDuration = System.currentTimeMillis() - recordingStartTime
                    audioManager?.stopRecording()
                    isRecording = false
                    isWaitingForResponse = true
                    
                    // 获取录音数据并发送
                    val audioData = audioManager?.getCurrentAudioData()
                    if (audioData != null) {
                        sendAudioToAI(audioData)
                    } else {
                        Log.e(TAG, "获取录音数据失败")
                        isWaitingForResponse = false
                    }
                    
                    Log.d(TAG, "停止录音，等待AI回复 (录音时长: ${String.format("%.1f", recordingDuration / 1000.0)} 秒)")
                } catch (e: Exception) {
                    Log.e(TAG, "停止录音失败", e)
                    isRecording = false
                    isWaitingForResponse = false
                }
            }
        }
    }
    
    private fun sendAudioToAI(audioData: ByteArray) {
        scope.launch {
            try {
                // 按照Python代码分块发送
                val chunkSize = 3200 // 16000Hz * 0.2秒 = 3200字节
                
                // 确保音频数据长度是chunkSize的整数倍
                val paddingNeeded = (chunkSize - (audioData.size % chunkSize)) % chunkSize
                val paddedAudioData = if (paddingNeeded > 0) {
                    audioData + ByteArray(paddingNeeded) // 添加静音填充
                } else {
                    audioData
                }
                
                // 发送所有音频块
                for (i in 0 until paddedAudioData.size step chunkSize) {
                    val chunk = paddedAudioData.sliceArray(i until i + chunkSize)
                    webSocketClient?.sendAudioData(chunk)
                    delay(10) // 小延迟避免发送过快
                }
                
                // 发送静音块作为结束标记
                webSocketClient?.sendSilenceChunks()
                
                Log.d(TAG, "📤 语音已发送，等待AI回复...")
                
            } catch (e: Exception) {
                Log.e(TAG, "发送语音失败", e)
                isWaitingForResponse = false
            }
        }
    }
    
    private fun hangup() {
        scope.launch {
            try {
                // 停止录音和播放
                audioManager?.stopRecording()
                audioManager?.stopPlayback()
                
                // 断开WebSocket连接
                webSocketClient?.disconnect()
                
                // 退出电话模式
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "挂断失败", e)
                finish()
            }
        }
    }
    
    private fun toggleSubtitle() {
        // 字幕功能实现
        Log.d(TAG, "切换字幕显示")
    }
    
    private fun recordConversation(role: String, text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                
                // 防止重复记录
                when (role) {
                    "user" -> {
                        if (currentTime - lastUserInputTime < minIntervalMs) return@launch
                        if (recentUserInputs.contains(text)) return@launch
                        lastUserInputTime = currentTime
                        recentUserInputs.add(text)
                        if (recentUserInputs.size > maxRecentSize) {
                            recentUserInputs.remove(recentUserInputs.first())
                        }
                    }
                    "assistant" -> {
                        if (currentTime - lastAIOutputTime < minIntervalMs) return@launch
                        if (recentAIOutputs.contains(text)) return@launch
                        lastAIOutputTime = currentTime
                        recentAIOutputs.add(text)
                        if (recentAIOutputs.size > maxRecentSize) {
                            recentAIOutputs.remove(recentAIOutputs.first())
                        }
                    }
                }
                
                // 获取真实的用户ID和会话ID
                val userId = com.llasm.nexusunified.data.UserManager.getUserId() ?: "android_user_${System.currentTimeMillis()}"
                val sessionId = com.llasm.nexusunified.data.UserManager.getSessionId() ?: "android_session_${System.currentTimeMillis()}"
                
                val requestBody = JSONObject().apply {
                    put("user_id", userId)
                    put("interaction_type", "voice_call")
                    put("content", if (role == "user") text else "")
                    put("response", if (role == "assistant") text else "")
                    put("session_id", sessionId)
                    put("success", true)
                }.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(ServerConfig.getApiUrl(ServerConfig.Endpoints.INTERACTIONS_LOG))
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ 电话模式对话记录成功: $role")
                } else {
                    Log.e(TAG, "❌ 电话模式对话记录失败: ${response.code}")
                }
                response.close()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 记录对话失败", e)
            }
        }
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        webSocketClient?.disconnect()
        audioManager?.stopRecording()
        audioManager?.stopPlayback()
    }
}
