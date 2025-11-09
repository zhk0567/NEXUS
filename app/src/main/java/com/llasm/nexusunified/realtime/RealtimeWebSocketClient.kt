package com.llasm.nexusunified.realtime

import android.util.Log
import java.util.UUID
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject
import org.json.JSONArray

/**
 * 实时语音对话WebSocket客户端
 * 基于字节跳动语音服务的WebSocket协议实现
 */
class RealtimeWebSocketClient(
    private val onMessage: (String) -> Unit,
    private val onAudioData: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit,
    private val onResponseComplete: () -> Unit = {},  // 添加响应完成回调
    private val onTranscriptionResult: (String) -> Unit = {},  // 添加语音识别结果回调
    private val onTextOutput: (String) -> Unit = {},  // 添加豆包文字输出回调
    private var voiceId: String = "zh_female_vv_jupiter_bigtts"  // 添加音色ID参数
) {
    companion object {
        private const val TAG = "RealtimeWebSocketClient"
        
        // 重连配置
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val CONNECTION_TIMEOUT_MS = 10000L
        private const val READ_TIMEOUT_MS = 30000L
        private const val WRITE_TIMEOUT_MS = 10000L
        
        // 保活配置
        private const val KEEPALIVE_INTERVAL_MS = 5000L  // 5秒保活间隔
        
        // 语音识别超时配置
        private const val ASR_TIMEOUT_MS = 5000L  // 5秒语音识别超时
        
        // WebSocket连接配置
        private const val BASE_URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
        private const val APP_ID = "9065017641"
        private const val ACCESS_KEY = "2AmQpw1aTtuIaRdMcrPX7K4PChZWus82"
        private const val RESOURCE_ID = "volc.speech.dialog"
        private const val APP_KEY = "1-QSPcc75MckNFBAJqQK63KJTNhbDu0d"
        
        // 协议常量
        private const val PROTOCOL_VERSION = 0b0001
        private const val DEFAULT_HEADER_SIZE = 0b0001
        
        // Message Type
        private const val CLIENT_FULL_REQUEST = 0b0001
        private const val CLIENT_AUDIO_ONLY_REQUEST = 0b0010
        private const val SERVER_FULL_RESPONSE = 0b1001
        private const val SERVER_ACK = 0b1011
        private const val SERVER_ERROR_RESPONSE = 0b1111
        
        // Message Type Specific Flags
        private const val NO_SEQUENCE = 0b0000
        private const val POS_SEQUENCE = 0b0001
        private const val NEG_SEQUENCE = 0b0010
        private const val NEG_SEQUENCE_1 = 0b0011
        private const val MSG_WITH_EVENT = 0b0100
        
        // Message Serialization
        private const val NO_SERIALIZATION = 0b0000
        private const val JSON = 0b0001
        private const val THRIFT = 0b0011
        private const val CUSTOM_TYPE = 0b1111
        
        // Message Compression
        private const val NO_COMPRESSION = 0b0000
        private const val GZIP = 0b0001
        private const val CUSTOM_COMPRESSION = 0b1111
    }
    
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var sessionId: String = ""
    private var logId: String = ""
    private var isConnected = false
    private var retryCount = 0
    private var isReconnecting = false
    private var lastAudioSendTime = 0L
    private var keepaliveJob: Job? = null
    
    // 音频处理状态
    private var lastAudioData: ByteArray? = null
    private var hasSentEndSignal = false
    
    // 语音识别超时检测
    private var asrStartTime = 0L
    private var asrTimeoutJob: Job? = null
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        sessionId = UUID.randomUUID().toString()
        Log.d(TAG, "初始化WebSocket客户端，会话ID: $sessionId")
    }
    
    /**
     * 连接到WebSocket服务器
     */
    suspend fun connect() {
        if (isReconnecting) {
            Log.w(TAG, "正在重连中，跳过重复连接请求")
            return
        }
        
        try {
            Log.d(TAG, "开始连接WebSocket服务器... (尝试 ${retryCount + 1}/$MAX_RETRY_COUNT)")
            
            client = OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)  // 30秒心跳
                .retryOnConnectionFailure(true)
                .build()
            
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("X-Api-App-ID", APP_ID)
                .addHeader("X-Api-Access-Key", ACCESS_KEY)
                .addHeader("X-Api-Resource-Id", RESOURCE_ID)
                .addHeader("X-Api-App-Key", APP_KEY)
                .addHeader("X-Api-Connect-Id", sessionId)
                .build()
            
            webSocket = client?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket连接已建立")
                    isConnected = true
                    logId = response.header("X-Tt-Logid") ?: ""
                    Log.d(TAG, "服务器响应日志ID: $logId")
                    
                    scope.launch(Dispatchers.Main) {
                        onMessage("🔗 WebSocket连接已建立")
                        onMessage("📋 服务器日志ID: $logId")
                        onConnected()
                        onMessage("📤 发送StartConnection请求...")
                        startConnection()
                        // 保活机制在startSession后启动
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "收到文本消息: $text")
                    scope.launch(Dispatchers.Main) {
                        onMessage(text)
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d(TAG, "收到二进制消息，大小: ${bytes.size}")
                    scope.launch(Dispatchers.Main) {
                        onMessage("📥 收到服务器响应: ${bytes.size} 字节")
                        handleBinaryMessage(bytes.toByteArray())
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket正在关闭: $code - $reason")
                    isConnected = false
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket连接已关闭: $code - $reason")
                    isConnected = false
                    stopAsrTimeoutDetection()  // 停止超时检测
                    scope.launch(Dispatchers.Main) {
                        onDisconnected()
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket连接失败", t)
                    isConnected = false
                    keepaliveJob?.cancel()  // 停止保活
                    stopAsrTimeoutDetection()  // 停止超时检测
                    
                    scope.launch(Dispatchers.Main) {
                        onMessage("❌ WebSocket连接失败: ${t.javaClass.simpleName}")
                        onMessage("❌ 错误详情: ${t.message}")
                        if (response != null) {
                            onMessage("❌ 响应码: ${response.code}")
                        }
                        onError("连接失败: ${t.message}")
                    }
                    
                    // 自动重连
                    if (retryCount < MAX_RETRY_COUNT) {
                        retryCount++
                        Log.d(TAG, "准备重连，延迟 ${RETRY_DELAY_MS}ms")
                        scope.launch {
                            delay(RETRY_DELAY_MS)
                            reconnect()
                        }
                    } else {
                        Log.e(TAG, "达到最大重试次数，停止重连")
                        scope.launch(Dispatchers.Main) {
                            onError("连接失败，已达到最大重试次数")
                        }
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "连接WebSocket时出错", e)
            onError("连接失败: ${e.message}")
            
            // 连接异常时也尝试重连
            if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                scope.launch {
                    delay(RETRY_DELAY_MS)
                    reconnect()
                }
            }
        }
    }
    
    /**
     * 重连方法
     */
    private suspend fun reconnect() {
        if (isReconnecting) return
        
        isReconnecting = true
        try {
            Log.d(TAG, "开始重连...")
            disconnect()  // 先断开现有连接
            delay(1000)   // 等待1秒
            connect()     // 重新连接
        } catch (e: Exception) {
            Log.e(TAG, "重连失败", e)
            onError("重连失败: ${e.message}")
        } finally {
            isReconnecting = false
        }
    }
    
    /**
     * 开始连接请求
     */
    private suspend fun startConnection() {
        try {
            val header = generateHeader()
            val payload = "{}"
            val compressedPayload = gzipCompress(payload.toByteArray())
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(1, 4)) // event
                write(intToBytes(compressedPayload.size, 4)) // payload size
                write(compressedPayload)
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            Log.d(TAG, "发送开始连接请求")
            onMessage("📤 StartConnection请求已发送 (${request.size} 字节)")
            
            // 按照Python版本的流程，发送StartConnection后立即发送StartSession
            delay(100) // 短暂延迟
            startSession()
            
        } catch (e: Exception) {
            Log.e(TAG, "发送开始连接请求失败", e)
            onMessage("❌ 发送StartConnection失败: ${e.message}")
            onError("发送连接请求失败: ${e.message}")
        }
    }
    
    /**
     * 启动会话
     */
    suspend fun startSession() {
        try {
            val sessionConfig = JSONObject().apply {
                put("asr", JSONObject().apply {
                    put("extra", JSONObject().apply {
                        put("end_smooth_window_ms", 500)  // 减少平滑窗口时间
                        put("eos_silence_timeout", 1000)  // 设置1秒静音超时
                        put("vad_backtrack_silence_time_ms", 200)  // 减少回溯时间
                    })
                })
                put("tts", JSONObject().apply {
                    put("speaker", voiceId)
                    put("audio_config", JSONObject().apply {
                        put("channel", 1)
                        put("format", "pcm_s16le")  // 使用16位格式，符合官方文档
                        put("sample_rate", 24000)
                    })
                    put("text_config", JSONObject().apply {
                        put("enable", true)  // 启用文本输出
                    })
                })
                put("dialog", JSONObject().apply {
                    put("bot_name", "豆包")
                    put("system_role", "你是一个智能的AI助手，名字叫豆包。你使用活泼灵动的女声，性格开朗，热爱生活。你的说话风格简洁明了，语速适中，语调自然。你可以帮助用户解答问题、聊天、提供建议等。请用友好、专业的语气与用户交流。")
                    put("speaking_style", "你的说话风格简洁明了，语速适中，语调自然，能够进行智能对话。")
                    put("location", JSONObject().apply {
                        put("city", "北京")
                    })
                    put("extra", JSONObject().apply {
                        put("strict_audit", false)
                        put("audit_response", "我会以友好、专业的方式与您交流。")
                    })
                })
            }
            
            val header = generateHeader()
            val payload = sessionConfig.toString()
            val compressedPayload = gzipCompress(payload.toByteArray())
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(100, 4)) // event
                write(intToBytes(sessionId.length, 4)) // session id length
                write(sessionId.toByteArray()) // session id
                write(intToBytes(compressedPayload.size, 4)) // payload size
                write(compressedPayload)
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            hasSentEndSignal = false // 重置结束信号标志
            Log.d(TAG, "发送启动会话请求")
            onMessage("📤 StartSession请求已发送 (${request.size} 字节)")
            
            // 按照Python版本的流程，发送StartSession后启动保活机制
            delay(100) // 短暂延迟
            startKeepalive()
            onMessage("🎉 豆包语音对话已开始，可以录音了！")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送启动会话请求失败", e)
            onMessage("❌ 发送StartSession失败: ${e.message}")
            onError("发送会话请求失败: ${e.message}")
        }
    }
    
    /**
     * 发送Hello消息
     */
    suspend fun sendHello() {
        try {
            val helloPayload = JSONObject().apply {
                put("content", "你好，我是豆包，有什么可以帮助你的？")
            }
            
            val header = generateHeader()
            val payload = helloPayload.toString()
            val compressedPayload = gzipCompress(payload.toByteArray())
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(300, 4)) // event
                write(intToBytes(sessionId.length, 4)) // session id length
                write(sessionId.toByteArray()) // session id
                write(intToBytes(compressedPayload.size, 4)) // payload size
                write(compressedPayload)
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            Log.d(TAG, "发送Hello消息")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送Hello消息失败", e)
            onError("发送Hello消息失败: ${e.message}")
        }
    }
    
    /**
     * 发送文本查询
     */
    suspend fun sendTextQuery(content: String) {
        try {
            val queryPayload = JSONObject().apply {
                put("content", content)
            }
            
            val header = generateHeader()
            val payload = queryPayload.toString()
            val compressedPayload = gzipCompress(payload.toByteArray())
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(501, 4)) // event
                write(intToBytes(sessionId.length, 4)) // session id length
                write(sessionId.toByteArray()) // session id
                write(intToBytes(compressedPayload.size, 4)) // payload size
                write(compressedPayload)
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            Log.d(TAG, "发送文本查询: $content")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送文本查询失败", e)
            onError("发送文本查询失败: ${e.message}")
        }
    }
    
    /**
     * 发送音频数据
     */
    suspend fun sendAudioData(audioData: ByteArray, showLog: Boolean = true, updateLastSendTime: Boolean = true) {
        if (!isConnected) {
            Log.w(TAG, "WebSocket未连接，跳过音频数据发送")
            onMessage("❌ WebSocket未连接，无法发送音频")
            return
        }
        
        try {
            // 保存音频数据
            lastAudioData = audioData
            
            // 按照Python代码的方式发送单个音频块
            val header = generateHeader(
                messageType = CLIENT_AUDIO_ONLY_REQUEST,
                messageTypeSpecificFlags = MSG_WITH_EVENT,  // 添加事件标志
                serialMethod = NO_SERIALIZATION
            )
            
            // 根据火山引擎文档，音频数据需要压缩
            val compressedAudio = gzipCompress(audioData)
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(200, 4)) // event
                write(intToBytes(sessionId.length, 4)) // session id length
                write(sessionId.toByteArray()) // session id
                write(intToBytes(compressedAudio.size, 4)) // payload size
                write(compressedAudio) // 写入压缩后的数据
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            if (updateLastSendTime) {
                lastAudioSendTime = System.currentTimeMillis()  // 更新最后发送时间
            }
            
            if (showLog) {
                Log.d(TAG, "发送音频块，原始大小: ${audioData.size} 字节，压缩后: ${compressedAudio.size} 字节")
                onMessage("📤 发送音频块: ${audioData.size}→${compressedAudio.size} 字节")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送音频数据失败", e)
            onMessage("❌ 发送音频失败: ${e.message}")
            onError("发送音频数据失败: ${e.message}")
            
            // 发送失败时检查连接状态
            if (!isConnected) {
                Log.w(TAG, "发送失败，连接已断开，尝试重连")
                onMessage("🔄 连接断开，尝试重连...")
                if (retryCount < MAX_RETRY_COUNT) {
                    retryCount++
                    scope.launch {
                        delay(RETRY_DELAY_MS)
                        reconnect()
                    }
                }
            }
        }
    }
    
    /**
     * 启动语音识别超时检测
     */
    private fun startAsrTimeoutDetection() {
        asrTimeoutJob?.cancel()
        asrStartTime = System.currentTimeMillis()
        
        asrTimeoutJob = scope.launch {
            delay(ASR_TIMEOUT_MS)
            
            // 检查是否还在等待语音识别结果
            if (!hasSentEndSignal && System.currentTimeMillis() - asrStartTime >= ASR_TIMEOUT_MS) {
                Log.w(TAG, "语音识别超时，强制发送结束信号")
                sendEndSignal()
                onMessage("⏰ 语音识别超时，强制结束")
            }
        }
    }
    
    /**
     * 停止语音识别超时检测
     */
    private fun stopAsrTimeoutDetection() {
        asrTimeoutJob?.cancel()
        asrTimeoutJob = null
    }
    
    /**
     * 发送静音音频块作为结束标记
     */
    suspend fun sendSilenceChunks() {
        if (!isConnected) {
            Log.w(TAG, "WebSocket未连接，跳过静音块发送")
            return
        }
        
        try {
            val silenceChunk = ByteArray(3200) // 16000Hz * 0.2秒 = 3200字节
            // 静音数据已经是全零，不需要额外处理
            
            // 启动语音识别超时检测
            startAsrTimeoutDetection()
            
            // 发送5个静音块作为结束标记（按照Python代码）
            repeat(5) {
                sendAudioData(silenceChunk, showLog = false) // 静音数据不显示日志
                delay(50) // 小延迟避免发送过快
            }
            
            Log.d(TAG, "发送静音块完成")
            
            // 不在这里发送结束信号，等待语音识别完成后再发送
            
        } catch (e: Exception) {
            Log.e(TAG, "发送静音块失败", e)
        }
    }
    
    /**
     * 发送结束信号
     */
    private suspend fun sendEndSignal() {
        // 防止重复发送结束信号
        if (hasSentEndSignal) {
            Log.d(TAG, "结束信号已发送，跳过重复发送")
            return
        }
        
        // 停止超时检测
        stopAsrTimeoutDetection()
        
        try {
            // 根据火山引擎文档，结束信号应该使用特定的payload格式
            val payload = JSONObject().apply {
                put("event", "end")
                put("timestamp", System.currentTimeMillis())
            }
            val compressedPayload = gzipCompress(payload.toString().toByteArray())
            
            val header = generateHeader(
                messageType = CLIENT_AUDIO_ONLY_REQUEST,
                messageTypeSpecificFlags = MSG_WITH_EVENT,
                serialMethod = JSON
            )
            
            val request = ByteArrayOutputStream().apply {
                write(header)
                write(intToBytes(300, 4)) // event - 结束信号
                write(intToBytes(sessionId.length, 4)) // session id length
                write(sessionId.toByteArray()) // session id
                write(intToBytes(compressedPayload.size, 4)) // payload size
                write(compressedPayload) // 写入压缩后的payload
            }.toByteArray()
            
            webSocket?.send(ByteString.of(*request))
            hasSentEndSignal = true
            Log.d(TAG, "发送结束信号: ${payload.toString()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送结束信号失败", e)
        }
    }
    
    /**
     * 启动保活机制
     */
    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            Log.d(TAG, "🔇 启动保活机制，间隔: ${KEEPALIVE_INTERVAL_MS}ms")
            while (isConnected) {
                try {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastAudio = currentTime - lastAudioSendTime
                    
                    if (timeSinceLastAudio > KEEPALIVE_INTERVAL_MS) {
                        Log.d(TAG, "🔇 发送保活音频，距离上次音频: ${timeSinceLastAudio}ms")
                        sendKeepaliveAudio()
                    }
                    
                    // 动态调整检查间隔
                    val checkInterval = if (timeSinceLastAudio > KEEPALIVE_INTERVAL_MS) {
                        1000L // 需要保活时，每秒检查一次
                    } else {
                        maxOf(1000L, KEEPALIVE_INTERVAL_MS - timeSinceLastAudio) // 根据剩余时间调整
                    }
                    delay(checkInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "保活检查失败", e)
                    break
                }
            }
            Log.d(TAG, "🔇 保活机制已停止")
        }
    }
    
    /**
     * 发送保活静音音频
     */
    private suspend fun sendKeepaliveAudio() {
        if (!isConnected) return
        
        try {
            val silenceChunk = ByteArray(3200) // 16000Hz * 0.2秒 = 3200字节
            sendAudioData(silenceChunk, showLog = false, updateLastSendTime = false) // 保活静音数据不显示日志，不更新发送时间
            // 保活机制静默运行，不输出日志
        } catch (e: Exception) {
            Log.e(TAG, "发送保活音频失败", e)
        }
    }
    
    /**
     * 处理二进制消息
     */
    private fun handleBinaryMessage(data: ByteArray) {
        try {
            onMessage("🔍 解析服务器响应: ${data.size} 字节")
            val response = parseResponse(data)
            onMessage("📋 解析结果: ${response.keys.joinToString()}")
            
            // 收到任何响应都取消超时检测
            lastAudioData = null
            
            when (response["message_type"]) {
                "SERVER_ACK" -> {
                    onMessage("📥 收到SERVER_ACK响应")
                    val audioData = response["payload_msg"] as? ByteArray
                    if (audioData != null) {
                        onMessage("🔊 播放音频数据: ${audioData.size} 字节")
                        onAudioData(audioData)
                    } else {
                        onMessage("⚠️ SERVER_ACK无音频数据")
                    }
                    
                    // 检查SERVER_ACK中是否包含文本信息
                    val payload = response["payload_msg"]
                    if (payload != null) {
                        val payloadStr = payload.toString()
                        Log.d(TAG, "SERVER_ACK payload: $payloadStr")
                        if (payloadStr.length > 3 && (
                            payloadStr.matches(Regex(".*[\\u4e00-\\u9fa5].*")) || // 包含中文字符
                            payloadStr.contains("你好") || 
                            payloadStr.contains("谢谢") || 
                            payloadStr.contains("帮助") ||
                            payloadStr.contains("问题") ||
                            payloadStr.contains("回答") ||
                            payloadStr.contains("AI") ||
                            payloadStr.contains("助手")
                        )) {
                            onMessage("🤖 在SERVER_ACK中检测到AI回复: $payloadStr")
                            Log.d(TAG, "从SERVER_ACK检测到AI回复: $payloadStr")
                            onTextOutput(payloadStr)
                        }
                    }
                }
                "SERVER_FULL_RESPONSE" -> {
                    val event = response["event"] as? Int
                    val payload = response["payload_msg"]
                    onMessage("📥 收到SERVER_FULL_RESPONSE，事件: $event")
                    
                    // 记录所有收到的消息用于调试
                    Log.d(TAG, "收到WebSocket消息 - 事件类型: $event, payload类型: ${payload?.javaClass?.simpleName}")
                    if (payload is JSONObject) {
                        Log.d(TAG, "payload字段: ${payload.keys()}")
                        Log.d(TAG, "完整payload: ${payload.toString()}")
                        
                        // 通用AI回复检查 - 只记录日志，不重复处理
                        val possibleFields = listOf("content", "text", "message", "response", "result", "answer", "reply", "data", "transcript", "transcription", "output", "ai_response", "ai_output")
                        for (field in possibleFields) {
                            if (payload.has(field)) {
                                val content = payload.getString(field)
                                Log.d(TAG, "发现字段'$field': $content")
                                // 只记录日志，不调用onTextOutput，避免重复处理
                                if (content.length > 1 && (
                                    content.matches(Regex(".*[\\u4e00-\\u9fa5].*")) || // 包含中文字符
                                    content.contains("你好") || 
                                    content.contains("谢谢") || 
                                    content.contains("帮助") ||
                                    content.contains("问题") ||
                                    content.contains("回答") ||
                                    content.contains("AI") ||
                                    content.contains("助手") ||
                                    content.contains("散步") ||
                                    content.contains("晚上") ||
                                    content.contains("好") ||
                                    content.contains("！") ||
                                    content.contains("？") ||
                                    content.contains("。")
                                )) {
                                    onMessage("🤖 通用检测到AI回复: $content")
                                    Log.d(TAG, "从事件${event ?: "未知"}的字段'$field'检测到AI回复: $content")
                                    // 不在这里调用onTextOutput，避免重复处理
                                    break
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "payload内容: $payload")
                    }
                    
                when (event) {
                    1 -> {
                        // StartConnection响应 - 连接建立成功
                        Log.d(TAG, "StartConnection响应成功")
                        onMessage("✅ StartConnection响应成功")
                    }
                    100 -> {
                        // 会话启动成功
                        Log.d(TAG, "会话启动成功")
                        onMessage("✅ StartSession响应成功")
                    }
                    101 -> {
                        // 会话启动失败
                        Log.e(TAG, "会话启动失败")
                        onMessage("❌ StartSession响应失败")
                        onError("会话启动失败")
                    }
                        450 -> {
                            Log.d(TAG, "AI开始响应，清空音频缓存")
                            onMessage("🤖 AI开始响应...")
                            
                            // 立即发送结束信号，强制停止语音识别
                            if (!hasSentEndSignal) {
                                Log.d(TAG, "AI开始响应，立即发送结束信号")
                                scope.launch {
                                    sendEndSignal()
                                }
                                onMessage("⏳ 等待AI回复...")
                            }
                        }
                        200 -> {
                            // 语音识别结果 - 只记录日志，不重复处理
                            if (payload is JSONObject && payload.has("text")) {
                                val text = payload.getString("text")
                                onMessage("🎤 语音识别: $text")
                                Log.d(TAG, "=== 语音识别结果处理 (消息200) ===")
                                Log.d(TAG, "语音识别结果: '$text'")
                                Log.d(TAG, "文本长度: ${text.length}")
                                // 不在这里调用onTranscriptionResult，避免重复处理
                                Log.d(TAG, "=== 语音识别结果处理完成 (消息200) ===")
                            }
                            // 检查是否包含AI回复 - 只记录日志，不重复处理
                            if (payload is JSONObject) {
                                val possibleFields = listOf("content", "text", "message", "response", "result", "answer")
                                for (field in possibleFields) {
                                    if (payload.has(field)) {
                                        val content = payload.getString(field)
                                        if (content.length > 1 && content.matches(Regex(".*[\\u4e00-\\u9fa5].*"))) {
                                            onMessage("🤖 检测到AI回复: $content")
                                            Log.d(TAG, "从消息类型200的字段'$field'检测到AI回复: $content")
                                            // 不在这里调用onTextOutput，避免重复处理
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        201 -> {
                            // 部分语音识别结果（保留日志，但不处理字幕）
                            if (payload is JSONObject && payload.has("text")) {
                                val text = payload.getString("text")
                                onMessage("🎤 部分识别: $text")
                            }
                            // 检查是否包含AI回复 - 只记录日志，不重复处理
                            if (payload is JSONObject) {
                                val possibleFields = listOf("content", "text", "message", "response", "result", "answer")
                                for (field in possibleFields) {
                                    if (payload.has(field)) {
                                        val content = payload.getString(field)
                                        if (content.length > 1 && content.matches(Regex(".*[\\u4e00-\\u9fa5].*"))) {
                                            onMessage("🤖 检测到AI回复: $content")
                                            Log.d(TAG, "从消息类型201的字段'$field'检测到AI回复: $content")
                                            // 不在这里调用onTextOutput，避免重复处理
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        202 -> {
                            // 识别状态
                            if (payload is JSONObject && payload.has("status")) {
                                val status = payload.getString("status")
                                onMessage("📊 识别状态: $status")
                            }
                        }
                        451 -> {
                            // 语音识别结果（包括部分和最终结果）
                            if (payload is JSONObject) {
                                // 检查results字段中的识别结果
                                if (payload.has("results")) {
                                    val results = payload.getJSONArray("results")
                                    if (results.length() > 0) {
                                        val firstResult = results.getJSONObject(0)
                                        if (firstResult.has("text")) {
                                            val text = firstResult.getString("text")
                                            val isInterim = firstResult.optBoolean("is_interim", true)
                                            
                                            // 只处理非临时结果，避免重复记录
                                            if (!isInterim && text.isNotEmpty()) {
                                                Log.d(TAG, "=== 最终语音识别结果 ===")
                                                Log.d(TAG, "最终识别结果: '$text'")
                                                onMessage("🎤 语音识别完成: $text")
                                                onTranscriptionResult(text)
                                                
                                                // 发送结束信号
                                                if (!hasSentEndSignal) {
                                                    GlobalScope.launch {
                                                        delay(200) // 等待0.2秒确保结果完整
                                                        if (!hasSentEndSignal) {
                                                            Log.d(TAG, "发送结束信号")
                                                            sendEndSignal()
                                                            onMessage("⏳ 等待AI回复...")
                                                        }
                                                    }
                                                }
                                            } else if (isInterim) {
                                                // 临时结果只显示，不记录到数据库
                                                onMessage("🎤 识别中: $text")
                                                Log.d(TAG, "临时识别结果: '$text'")
                                            }
                                        }
                                    }
                                }
                                
                                // 检查是否包含AI回复 - 只记录日志，不重复处理
                                val possibleFields = listOf("content", "text", "message", "response", "result", "answer")
                                for (field in possibleFields) {
                                    if (payload.has(field)) {
                                        val content = payload.getString(field)
                                        if (content.length > 1 && content.matches(Regex(".*[\\u4e00-\\u9fa5].*"))) {
                                            onMessage("🤖 检测到AI回复: $content")
                                            Log.d(TAG, "从消息类型451的字段'$field'检测到AI回复: $content")
                                            // 不在这里调用onTextOutput，避免重复处理
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        300 -> {
                            // 其他响应
                            if (payload is JSONObject) {
                                // 检查是否包含AI回复内容
                                val possibleFields = listOf("content", "text", "message", "response", "result")
                                for (field in possibleFields) {
                                    if (payload.has(field)) {
                                        val content = payload.getString(field)
                                        if (content.isNotEmpty() && content.length > 1) {
                                            onMessage("📝 服务器消息: $content")
                                            Log.d(TAG, "=== 消息类型300 AI回复处理 ===")
                                            Log.d(TAG, "AI回复内容: '$content'")
                                            Log.d(TAG, "内容长度: ${content.length}")
                                            onTextOutput(content)
                                            Log.d(TAG, "=== onTextOutput调用完成 (消息类型300) ===")
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        350 -> {
                            // TTS开始信号
                            onMessage("🎵 TTS开始")
                        }
                        351 -> {
                            // TTS结束信号
                            onMessage("🎵 TTS结束")
                            // 音频播放完成，通知UI响应完成
                            onResponseComplete()
                        }
                        550 -> {
                            // AI回复内容
                            if (payload is JSONObject) {
                                val possibleFields = listOf("content", "text", "message", "response", "result")
                                for (field in possibleFields) {
                                    if (payload.has(field)) {
                                        val content = payload.getString(field)
                                        if (content.isNotEmpty() && content.length > 1) {
                                            onMessage("🤖 AI回复: $content")
                                            Log.d(TAG, "=== 消息类型550 AI回复处理 ===")
                                            Log.d(TAG, "AI回复内容: '$content'")
                                            Log.d(TAG, "内容长度: ${content.length}")
                                            onTextOutput(content)
                                            Log.d(TAG, "=== onTextOutput调用完成 (消息类型550) ===")
                                            break
                                        }
                                    }
                                }
                            }
                            // 不立即调用onResponseComplete，等待音频播放完成
                        }
                        459 -> {
                            // 对话结束信号，但不立即调用onResponseComplete
                            onMessage("✅ 对话结束信号")
                            // 不在这里调用onResponseComplete，等待AI实际回复
                        }
                    }
                }
                "SERVER_ERROR" -> {
                    val errorCode = response["code"] as? Int
                    val errorMsg = response["payload_msg"]
                    onMessage("❌ 收到SERVER_ERROR，代码: $errorCode")
                    
                    // 检查错误消息中是否包含AI回复
                    val errorStr = errorMsg.toString()
                    Log.d(TAG, "SERVER_ERROR内容: $errorStr")
                    if (errorStr.length > 3 && (
                        errorStr.matches(Regex(".*[\\u4e00-\\u9fa5].*")) || // 包含中文字符
                        errorStr.contains("你好") || 
                        errorStr.contains("谢谢") || 
                        errorStr.contains("帮助") ||
                        errorStr.contains("问题") ||
                        errorStr.contains("回答") ||
                        errorStr.contains("AI") ||
                        errorStr.contains("助手")
                    )) {
                        onMessage("🤖 在SERVER_ERROR中检测到AI回复: $errorStr")
                        Log.d(TAG, "从SERVER_ERROR检测到AI回复: $errorStr")
                        onTextOutput(errorStr)
                    } else {
                        // 根据Python代码处理特定错误
                        if (errorStr.contains("DialogAudioIdleeTimeoutError") || errorCode == 52000042) {
                            Log.w(TAG, "DialogAudioIdleeTimeoutError - 服务端超过10秒没有收到query音频")
                            onMessage("⏰ 连接超时，请重新开始录音")
                            onError("💡 连接超时，请重新开始录音")
                            // 不退出程序，允许用户继续操作
                        } else {
                            onMessage("❌ 服务器错误: $errorMsg")
                            onError("服务器错误 (代码: $errorCode): $errorMsg")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理二进制消息失败", e)
            onMessage("❌ 解析响应失败: ${e.message}")
            onError("处理服务器响应失败: ${e.message}")
        }
    }
    
    /**
     * 解析服务器响应
     */
    private fun parseResponse(data: ByteArray): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        if (data.isEmpty()) return result
        
        val protocolVersion = (data[0].toInt() and 0xFF) shr 4
        val headerSize = data[0].toInt() and 0x0F
        val messageType = (data[1].toInt() and 0xFF) shr 4
        val messageTypeSpecificFlags = data[1].toInt() and 0x0F
        val serializationMethod = (data[2].toInt() and 0xFF) shr 4
        val messageCompression = data[2].toInt() and 0x0F
        val reserved = data[3].toInt() and 0xFF
        
        val headerExtensions = data.sliceArray(4 until headerSize * 4)
        val payload = data.sliceArray(headerSize * 4 until data.size)
        
        when (messageType) {
            SERVER_FULL_RESPONSE, SERVER_ACK -> {
                result["message_type"] = if (messageType == SERVER_ACK) "SERVER_ACK" else "SERVER_FULL_RESPONSE"
                
                var start = 0
                if (messageTypeSpecificFlags and NEG_SEQUENCE > 0) {
                    result["seq"] = bytesToInt(payload.sliceArray(0..3), ByteOrder.BIG_ENDIAN)
                    start += 4
                }
                if (messageTypeSpecificFlags and MSG_WITH_EVENT > 0) {
                    result["event"] = bytesToInt(payload.sliceArray(start until start + 4), ByteOrder.BIG_ENDIAN)
                    start += 4
                }
                
                val remainingPayload = payload.sliceArray(start until payload.size)
                val sessionIdSize = bytesToIntSigned(remainingPayload.sliceArray(0..3), ByteOrder.BIG_ENDIAN)
                val sessionId = String(remainingPayload.sliceArray(4 until 4 + sessionIdSize))
                result["session_id"] = sessionId
                
                val payloadSize = bytesToInt(remainingPayload.sliceArray(4 + sessionIdSize until 8 + sessionIdSize), ByteOrder.BIG_ENDIAN)
                val payloadMsg = remainingPayload.sliceArray(8 + sessionIdSize until 8 + sessionIdSize + payloadSize)
                
                if (messageCompression == GZIP) {
                    val decompressed = gzipDecompress(payloadMsg)
                    if (serializationMethod == JSON) {
                        result["payload_msg"] = JSONObject(String(decompressed))
                    } else {
                        result["payload_msg"] = decompressed
                    }
                } else {
                    if (serializationMethod == JSON) {
                        result["payload_msg"] = JSONObject(String(payloadMsg))
                    } else {
                        result["payload_msg"] = payloadMsg
                    }
                }
                result["payload_size"] = payloadSize
            }
            SERVER_ERROR_RESPONSE -> {
                result["message_type"] = "SERVER_ERROR"
                result["code"] = bytesToInt(payload.sliceArray(0..3), ByteOrder.BIG_ENDIAN)
                val payloadSize = bytesToInt(payload.sliceArray(4..7), ByteOrder.BIG_ENDIAN)
                val payloadMsg = payload.sliceArray(8 until 8 + payloadSize)
                result["payload_msg"] = String(payloadMsg)
            }
        }
        
        return result
    }
    
    /**
     * 生成协议头
     */
    private fun generateHeader(
        version: Int = PROTOCOL_VERSION,
        messageType: Int = CLIENT_FULL_REQUEST,
        messageTypeSpecificFlags: Int = MSG_WITH_EVENT,
        serialMethod: Int = JSON,
        compressionType: Int = GZIP,
        reservedData: Int = 0x00,
        extensionHeader: ByteArray = ByteArray(0)
    ): ByteArray {
        val headerSize = extensionHeader.size / 4 + 1
        return byteArrayOf(
            ((version shl 4) or headerSize).toByte(),
            ((messageType shl 4) or messageTypeSpecificFlags).toByte(),
            ((serialMethod shl 4) or compressionType).toByte(),
            reservedData.toByte()
        ) + extensionHeader
    }
    
    /**
     * 整数转字节数组
     */
    private fun intToBytes(value: Int, size: Int): ByteArray {
        val buffer = ByteBuffer.allocate(size)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(value)
        return buffer.array()
    }
    
    /**
     * 字节数组转整数
     */
    private fun bytesToInt(bytes: ByteArray, order: ByteOrder): Int {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(order)
        return buffer.int
    }
    
    private fun bytesToIntSigned(bytes: ByteArray, order: ByteOrder): Int {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(order)
        return buffer.int  // 在Java中，int本身就是有符号的
    }
    
    /**
     * GZIP压缩
     */
    private fun gzipCompress(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val gzipOutputStream = GZIPOutputStream(outputStream)
        gzipOutputStream.write(data)
        gzipOutputStream.close()
        return outputStream.toByteArray()
    }
    
    /**
     * GZIP解压缩
     */
    private fun gzipDecompress(data: ByteArray): ByteArray {
        val inputStream = GZIPInputStream(data.inputStream())
        return inputStream.readBytes()
    }
    
    /**
     * 关闭连接
     */
    fun disconnect() {
        try {
            isConnected = false
            keepaliveJob?.cancel()  // 停止保活
            lastAudioData = null  // 取消超时检测
            webSocket?.close(1000, "正常关闭")
            client?.dispatcher?.executorService?.shutdown()
            scope.cancel()
            Log.d(TAG, "WebSocket连接已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭WebSocket连接时出错", e)
        }
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * 更新音色ID
     */
    suspend fun updateVoiceId(newVoiceId: String) {
        voiceId = newVoiceId
        Log.d(TAG, "音色ID已更新: $voiceId")
        
        // 如果已连接，重新启动会话以应用新音色
        if (isConnected) {
            try {
                startSession()
                Log.d(TAG, "会话已重新启动，应用新音色")
            } catch (e: Exception) {
                Log.e(TAG, "重新启动会话失败", e)
            }
        }
    }
}
