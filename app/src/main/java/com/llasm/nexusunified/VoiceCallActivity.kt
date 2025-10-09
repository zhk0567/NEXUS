package com.llasm.nexusunified

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.llasm.nexusunified.realtime.RealtimeWebSocketClient
import com.llasm.nexusunified.realtime.RealtimeAudioManager
import com.llasm.nexusunified.service.AIService
import com.llasm.nexusunified.config.ServerConfig
import kotlinx.coroutines.*
import java.util.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VoiceCallActivity : Activity() {
    
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
        private const val TAG = "VoiceCallActivity"
        private const val MAX_DIALOG_MESSAGE_COUNT = 20
        private const val PERMISSION_REQUEST_CODE = 1
        private val DIALOG_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    // UI组件
    private lateinit var mHangupBtn: Button
    private lateinit var mPauseBtn: Button
    private lateinit var mSubtitleBtn: Button
    private lateinit var mStatusTv: TextView
    private lateinit var mStatusIndicator: View
    private lateinit var mHintTv: TextView
    private lateinit var mResultTv: TextView
    private val mDialogMessages = LinkedList<Message>()
    

    // 实时语音组件
    private var webSocketClient: RealtimeWebSocketClient? = null
    private var audioManager: RealtimeAudioManager? = null
    private var aiService: AIService? = null
    private var isRecording = false
    private var isConnected = false
    private var isWaitingForResponse = false
    
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
    
    // 已移除重复检测机制
    
    // 累积用户输入文本，避免分片记录
    private var accumulatedUserInput = ""
    
    // 对话配对机制
    private var pendingUserInput: String? = null
    private var pendingAIResponse: String? = null
    private val maxPairingDelayMs = 5000L // 最大配对延迟5秒

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "VoiceCallActivity onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_call)

        // 强制使用外放扬声器
        val systemAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        systemAudioManager.mode = AudioManager.MODE_NORMAL
        systemAudioManager.isSpeakerphoneOn = true
        systemAudioManager.isBluetoothScoOn = false
        systemAudioManager.isWiredHeadsetOn = false
        
        Log.d(TAG, "onCreate: 已设置使用外放扬声器")

        initViews()
        initAIService()
        requestPermissions()
    }

    private fun initViews() {
        mHangupBtn = findViewById(R.id.hangup_button)
        mHangupBtn.setOnClickListener { 
            showLogMessage("📞 挂断电话")
            // 停止录音和播放
            audioManager?.stopRecording()
            audioManager?.stopPlayback()
            // 退出电话模式
            finish()
        }

        mPauseBtn = findViewById(R.id.pause_button)
        mPauseBtn.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (isConnected && !isRecording && !isWaitingForResponse) {
                        showLogMessage("🎤 开始录音...")
                        startRecording()
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        showLogMessage("⏹️ 停止录音")
                        stopRecording()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        mSubtitleBtn = findViewById(R.id.subtitle_button)
        mSubtitleBtn.setOnClickListener { 
            showLogMessage("📝 字幕功能（暂未实现）")
        }

        mStatusTv = findViewById(R.id.status_text)
        mStatusIndicator = findViewById(R.id.status_indicator)
        mHintTv = findViewById(R.id.hint_text)
        mResultTv = findViewById(R.id.result_text)
        mResultTv.movementMethod = ScrollingMovementMethod()
        
        // 初始状态
        updateButtonStates()
        mStatusTv.text = "正在连接..."
        mHintTv.text = "正在建立连接..."
        mResultTv.text = "应用已启动，连接成功后请长按录音按钮开始对话..."
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
            initializeComponents()
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
                Log.d(TAG, "录音权限已授予")
                initializeComponents()
            } else {
                Log.w(TAG, "录音权限被拒绝")
                mStatusTv.text = "需要录音权限才能使用语音功能"
                mResultTv.text = "请在设置中授予录音权限，然后重新启动应用"
            }
        }
    }

    private fun initializeComponents() {
        try {
            // 强制使用外放扬声器
            val systemAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            systemAudioManager.mode = AudioManager.MODE_NORMAL
            systemAudioManager.isSpeakerphoneOn = true  // 强制使用外放扬声器
            systemAudioManager.isBluetoothScoOn = false  // 关闭蓝牙音频
            systemAudioManager.isWiredHeadsetOn = false  // 关闭有线耳机
            
            Log.d(TAG, "已设置使用外放扬声器")
            showLogMessage("🔊 已设置使用外放扬声器")
            
            // 初始化音频管理器
            this.audioManager = RealtimeAudioManager(
                context = this,
                onAudioData = { _ ->
                    // 音频数据回调（暂时不使用）
                },
                onError = { error ->
                    runOnUiThread {
                        showLogMessage("❌ 音频错误: $error")
                    }
                },
                onPlaybackComplete = {
                    // 播放完成回调（暂时不使用）
                }
            )

            // 初始化WebSocket客户端
            webSocketClient = RealtimeWebSocketClient(
                onMessage = { message ->
                    runOnUiThread {
                        showLogMessage(message)
                    }
                },
                onAudioData = { audioData ->
                    runOnUiThread {
                        // 播放AI回复的音频
                        audioManager?.playAudio(audioData)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        showLogMessage("❌ 连接错误: $error")
                        isConnected = false
                        isWaitingForResponse = false  // 重置等待状态
                        mStatusTv.text = "准备就绪，长按录音按钮"
                        updateButtonStates()
                    }
                },
                onConnected = {
                    runOnUiThread {
                        showLogMessage("✅ 已连接到AI语音服务")
                        isConnected = true
                        updateButtonStates()
                        
                        // 连接成功后不自动开始录音，等待用户长按
                        showLogMessage("🎤 请长按录音按钮开始说话")
                    }
                },
                onDisconnected = {
                    runOnUiThread {
                        showLogMessage("❌ 连接已断开")
                        isConnected = false
                        updateButtonStates()
                    }
                },
                onTranscriptionResult = { text ->
                    runOnUiThread {
                        // 累积语音识别结果，不立即记录到数据库
                        if (text.isNotEmpty() && text.length > 2) { // 只记录有意义的完整句子
                            showLogMessage("🎤 用户: $text")
                            
                            // 累积用户输入文本
                            if (accumulatedUserInput.isEmpty()) {
                                accumulatedUserInput = text
                            } else {
                                // 如果新文本与累积文本不同，更新累积文本
                                if (text != accumulatedUserInput) {
                                    accumulatedUserInput = text
                                }
                            }
                            
                            // 设置待配对用户输入
                            pendingUserInput = text
                            Log.d(TAG, "📝 累积用户输入: $accumulatedUserInput")
                            
                            // 不立即记录到数据库，等待对话完成时再记录
                        }
                    }
                },
                onTextOutput = { text ->
                    runOnUiThread {
                        // 记录AI文本回复到数据库
                        if (text.isNotEmpty() && text.length > 1) { // 进一步降低长度要求，记录更多AI回复
                            showLogMessage("🤖 AI: $text")
                            Log.d(TAG, "=== 电话模式AI回复处理 ===")
                            Log.d(TAG, "AI回复内容: '$text'")
                            Log.d(TAG, "内容长度: ${text.length}")
                            
                            // 记录完整的对话到数据库（用户输入+AI回复）
                            if (accumulatedUserInput.isNotEmpty()) {
                                logInteractionToDatabase(accumulatedUserInput, text, true)
                                Log.d(TAG, "📝 记录完整对话: 用户='$accumulatedUserInput', AI='$text'")
                                // 清空累积的用户输入
                                accumulatedUserInput = ""
                            } else {
                                // 如果没有累积的用户输入，只记录AI回复
                                logInteractionToDatabase("", text, false)
                            }
                            
                            Log.d(TAG, "=== 电话模式AI回复处理完成 ===")
                        } else {
                            Log.d(TAG, "⚠️ AI回复被过滤: '$text' (长度: ${text.length})")
                        }
                    }
                },
                onResponseComplete = {
                    runOnUiThread {
                        // AI响应完成，重置状态
                        isWaitingForResponse = false
                        mStatusTv.text = "准备下一轮对话"
                        updateButtonStates()
                        showLogMessage("✅ AI响应结束")
                        
                        // 清理状态
                        currentAudioData = null
                        
                        // AI回复完成后，等待用户长按开始下一轮录音
                        showLogMessage("🎤 准备下一轮对话，请长按录音按钮")
                    }
                },
            )

            // 连接到WebSocket服务器
            scope.launch {
                try {
                    webSocketClient?.connect()
                } catch (e: Exception) {
                    runOnUiThread {
                        showLogMessage("❌ 连接失败: ${e.message}")
                    }
                }
            }

            showLogMessage("🔧 正在初始化语音服务...")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化组件失败", e)
            showLogMessage("❌ 初始化失败: ${e.message}")
        }
    }

    private fun startRecording() {
        if (isRecording) {
            showLogMessage("⚠️ 当前正在录音中")
            return
        }

        if (!isConnected) {
            showLogMessage("❌ 未连接到服务器，请稍后再试")
            return
        }

        if (isWaitingForResponse) {
            showLogMessage("⚠️ 正在等待AI回复，请稍后再试")
            return
        }

        try {
            isRecording = true
            recordingStartTime = System.currentTimeMillis() // 记录录音开始时间
            updateButtonStates()
            mStatusTv.text = "正在录音...请说话"
            showLogMessage("🎤 开始录音...")

            // 开始录音
            audioManager?.startRecording()
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            showLogMessage("❌ 开始录音失败: ${e.message}")
            isRecording = false
            updateButtonStates()
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            showLogMessage("⚠️ 当前未在录音")
            return
        }

        try {
            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            
            isRecording = false
            isWaitingForResponse = true  // 立即设置等待状态
            updateButtonStates()
            mStatusTv.text = "处理中..."
            showLogMessage("⏹️ 停止录音，正在处理... (录音时长: ${String.format("%.1f", recordingDuration / 1000.0)} 秒)")

            // 停止录音并获取音频数据
            audioManager?.stopRecording()
            val audioData = audioManager?.getCurrentAudioData()
            
            if (audioData == null) {
                showLogMessage("❌ 录音失败，请重试")
                isWaitingForResponse = false
                mStatusTv.text = "准备就绪，长按录音按钮"
                updateButtonStates()
                return
            }
            
            showLogMessage("✅ 录音成功，正在发送...")

            // 保存当前音频数据
            currentAudioData = audioData
            
            // 发送音频数据到AI
            sendAudioToAI(audioData)
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            showLogMessage("❌ 停止录音失败: ${e.message}")
            isRecording = false
            isWaitingForResponse = false
            updateButtonStates()
        }
    }
    
    /**
     * 发送音频数据到AI
     */
    private fun sendAudioToAI(audioData: ByteArray) {
        scope.launch {
            try {
                isWaitingForResponse = true
                
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
                
                showLogMessage("📤 语音已发送，等待AI回复...")
                
                // 等待语音识别完成，然后通过HTTP API获取AI回复
                delay(500) // 等待0.5秒让语音识别完成
                
                // 通过HTTP API获取AI回复
                getAIResponseViaHTTP()
                
            } catch (e: Exception) {
                runOnUiThread {
                    showLogMessage("❌ 发送语音失败: ${e.message}")
                    isWaitingForResponse = false
                    updateButtonStates()
                }
            }
        }
    }
    
    /**
     * 通过HTTP API获取AI回复
     */
    private fun getAIResponseViaHTTP() {
        scope.launch(Dispatchers.IO) {
            try {
                showLogMessage("🤖 正在获取AI回复...")
                
                // 构建请求 - 使用用户实际说的话
                val userMessage = pendingUserInput ?: "用户语音输入"
                // 获取真实的用户ID
                val userId = com.llasm.nexusunified.data.UserManager.getUserId() ?: ServerConfig.ANDROID_USER_ID
                
                val requestBody = JSONObject().apply {
                    put("message", userMessage)
                    put("user_id", userId)
                    put("session_id", sessionId)
                }.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(ServerConfig.getApiUrl(ServerConfig.Endpoints.CHAT))
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        val aiResponse = jsonResponse.optString("response", "")
                        
                        if (aiResponse.isNotEmpty()) {
                            runOnUiThread {
                                showLogMessage("🤖 AI回复: $aiResponse")
                                
                                // 使用配对机制记录AI回复
                                scope.launch(Dispatchers.IO) {
                                    if (pendingUserInput != null) {
                                        // 配对成功：记录完整的对话
                                        Log.d(TAG, "✅ 配对成功: 用户='$pendingUserInput', AI='$aiResponse'")
                                        recordSingleInteraction(pendingUserInput!!, aiResponse, true)
                                        pendingUserInput = null
                                    } else {
                                        // 没有待配对用户输入，单独记录AI回复
                                        Log.d(TAG, "📝 单独记录AI回复: $aiResponse")
                                        recordSingleInteraction("", aiResponse, false)
                                    }
                                }
                                
                                // 播放AI回复（如果需要）
                                // TODO: 添加TTS播放功能
                                
                                isWaitingForResponse = false
                                updateButtonStates()
                            }
                        } else {
                            runOnUiThread {
                                showLogMessage("❌ AI回复为空")
                                isWaitingForResponse = false
                                updateButtonStates()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        showLogMessage("❌ 获取AI回复失败: ${response.code}")
                        isWaitingForResponse = false
                        updateButtonStates()
                    }
                }
                response.close()
                
            } catch (e: Exception) {
                runOnUiThread {
                    showLogMessage("❌ 获取AI回复异常: ${e.message}")
                    isWaitingForResponse = false
                    updateButtonStates()
                }
            }
        }
    }
    

    private fun updateButtonStates() {
        runOnUiThread {
            // 更新按钮状态
            mHangupBtn.isEnabled = true
            mPauseBtn.isEnabled = isConnected && !isWaitingForResponse
            mSubtitleBtn.isEnabled = true
            
            // 更新录音按钮文本和颜色（长按模式）
            if (isRecording) {
                mPauseBtn.text = "🎤 录音中..."
                mPauseBtn.setBackgroundResource(R.drawable.button_voice_danger)
            } else {
                mPauseBtn.text = "🎤 长按录音"
                mPauseBtn.setBackgroundResource(R.drawable.button_voice_primary)
            }
            
            // 更新状态指示器
            mStatusIndicator.isSelected = isConnected
            
            when {
                !isConnected -> {
                    mStatusTv.text = "正在连接..."
                    mHintTv.text = "正在建立连接..."
                }
                isRecording -> {
                    mStatusTv.text = "正在录音..."
                    mHintTv.text = "请说话，松开停止录音"
                }
                isWaitingForResponse -> {
                    mStatusTv.text = "等待AI回复"
                    mHintTv.text = "AI正在处理中..."
                }
                else -> {
                    mStatusTv.text = "准备就绪"
                    mHintTv.text = "长按录音按钮开始说话"
                }
            }
        }
    }
    
    
    /**
     * 自动开始录音
     */
    

    private fun showUserMessage(data: String) {
        runOnUiThread {
            var message = lastUnconfirmedMessage(Role.USER)
            if (message == null) {
                message = Message(Role.USER, "", false)
                mDialogMessages.addLast(message)
            }
            message.text = data
            updateMessageUI()
        }
    }

    private fun confirmUserMessage() {
        runOnUiThread {
            val message = lastUnconfirmedMessage(Role.USER)
            if (message != null) {
                message.confirmed = true
            }
        }
    }

    private fun showAssistantMessage(data: String) {
        runOnUiThread {
            var message = lastUnconfirmedMessage(Role.ASSISTANT)
            if (message == null) {
                message = Message(Role.ASSISTANT, "", false)
                mDialogMessages.addLast(message)
            }
            message.text += data
            updateMessageUI()
        }
    }

    private fun confirmAssistantMessage() {
        runOnUiThread {
            val message = lastUnconfirmedMessage(Role.ASSISTANT)
            if (message != null) {
                message.confirmed = true
                // AI回复完成，重置状态
                isWaitingForResponse = false
                updateButtonStates()
            }
        }
    }

    private fun showLogMessage(data: String) {
        runOnUiThread {
            // 只显示重要的系统消息
            if (data.contains("✅") || data.contains("❌") || data.contains("🎤") || 
                data.contains("📤") || data.contains("🔄") || data.contains("⏹️")) {
                mDialogMessages.add(Message(Role.LOG, data, true))
                updateMessageUI()
            }
        }
    }

    private fun lastUnconfirmedMessage(role: Role): Message? {
        val it = mDialogMessages.descendingIterator()
        while (it.hasNext()) {
            val current = it.next()
            if (current.role == role) {
                if (!current.confirmed) {
                    return current
                }
                break
            }
        }
        return null
    }

    private fun updateMessageUI() {
        // 刷新消息内容
        if (mDialogMessages.size > MAX_DIALOG_MESSAGE_COUNT) {
            mDialogMessages.removeFirst()
        }

        // 构建消息内容
        val sb = StringBuilder()
        for (message in mDialogMessages) {
            val role = when (message.role) {
                Role.USER -> "[用户]:"
                Role.ASSISTANT -> "[AI]:"
                Role.LOG -> "[系统]:"
            }
            sb.append(role).append(message.text).append("\n")
        }
        mResultTv.text = sb.toString()
        
        // 滚动到底部
        val layout = mResultTv.layout
        if (layout != null) {
            val scrollAmount = layout.getLineTop(mResultTv.lineCount) - mResultTv.height
            if (scrollAmount > 0) {
                mResultTv.scrollTo(0, scrollAmount)
            } else {
                mResultTv.scrollTo(0, 0)
            }
        }
    }
    
    /**
     * 记录对话到数据库
     */
    private fun logInteractionToDatabase(content: String, response: String, isUser: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                // 直接记录对话，不进行重复检测
                Log.d(TAG, "📝 记录对话: ${if (isUser) content else response}")
                
                if (isUser) {
                    // 用户输入：单独记录
                    Log.d(TAG, "📝 记录用户输入: $content")
                    recordSingleInteraction(content, "", true)
                } else {
                    // AI回复：单独记录
                    Log.d(TAG, "📝 记录AI回复: $response")
                    recordSingleInteraction("", response, false)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 记录对话到数据库异常: $e")
            }
        }
    }
    
    // 已移除getRecentUserInput函数
    
    /**
     * 记录单个交互到数据库
     */
    private suspend fun recordSingleInteraction(content: String, response: String, isUser: Boolean) {
        try {
            // 获取真实的用户ID
            val userId = com.llasm.nexusunified.data.UserManager.getUserId() ?: ServerConfig.ANDROID_USER_ID
            
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("interaction_type", "voice_call")
                put("content", content)
                put("response", response)
                put("session_id", sessionId)
                put("success", true)
            }.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(ServerConfig.getApiUrl(ServerConfig.Endpoints.INTERACTIONS_LOG))
                .post(requestBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "✅ 电话模式对话记录成功: ${if (isUser) "用户" else "AI"}")
            } else {
                Log.w(TAG, "❌ 电话模式对话记录失败: ${response.code}")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 电话模式对话记录异常: $e")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "VoiceCallActivity onDestroy")
        
        // 恢复音频设置
        val systemAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        systemAudioManager.isSpeakerphoneOn = false
        systemAudioManager.mode = AudioManager.MODE_NORMAL
        
        Log.d(TAG, "onDestroy: 已恢复音频设置")
        
        // 停止录音
        if (isRecording) {
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
        
        // 取消协程
        scope.cancel()
        
        super.onDestroy()
    }
}
