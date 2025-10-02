package com.llasm.nexusunified.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.llasm.nexusunified.config.ServerConfig
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TTSService private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSService"
        // 使用真机连接时的电脑IP地址
        private val BACKEND_URL = ServerConfig.CURRENT_SERVER
        private const val TTS_ENDPOINT = "/api/tts"
        
        // 超时设置 - 支持长文本
        private const val CONNECT_TIMEOUT = 10000 // 10秒
        private const val READ_TIMEOUT = 60000 // 60秒
        
        // 缓存设置
        private const val MAX_CACHE_SIZE = 50
        private val audioCache = mutableMapOf<String, ByteArray>()
        
        @Volatile
        private var INSTANCE: TTSService? = null
        
        fun getInstance(context: Context): TTSService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isInitialized = true // edge-tts通过API调用，不需要本地初始化
    private var playbackJob: Job? = null
    
    /**
     * 从edge-tts API获取音频数据（带缓存和超时优化）
     */
    private suspend fun fetchAudioFromAPI(text: String, voice: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存
                val cacheKey = "${text}_${voice}"
                audioCache[cacheKey]?.let { cachedAudio ->
                    return@withContext cachedAudio
                }
                val startTime = System.currentTimeMillis()
                
                val url = URL("$BACKEND_URL$TTS_ENDPOINT")
                val connection = url.openConnection() as HttpURLConnection
                
                // 设置超时
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Connection", "keep-alive")
                connection.doOutput = true
                
                // 构建请求JSON - 转义特殊字符
                val escapedText = text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                val escapedVoice = voice.replace("\"", "\\\"")
                val requestBody = """
                    {
                        "text": "$escapedText",
                        "voice": "$escapedVoice"
                    }
                """.trimIndent()
                
                // 发送请求
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toByteArray())
                }
                
                // 读取响应
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val audioData = connection.inputStream.readBytes()
                    
                    // 缓存音频数据
                    if (audioData.isNotEmpty()) {
                        cacheAudio(cacheKey, audioData)
                    }
                    
                    audioData
                } else {
                    Log.e(TAG, "API请求失败，状态码: $responseCode")
                    null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "获取音频数据失败", e)
                null
            }
        }
    }
    
    /**
     * 缓存音频数据
     */
    private fun cacheAudio(key: String, audioData: ByteArray) {
        try {
            // 限制缓存大小
            if (audioCache.size >= MAX_CACHE_SIZE) {
                val firstKey = audioCache.keys.first()
                audioCache.remove(firstKey)
            }
            
            audioCache[key] = audioData
        } catch (e: Exception) {
            Log.w(TAG, "缓存音频失败: ${e.message}")
        }
    }
    
    /**
     * 保存音频数据到临时文件
     */
    private fun saveAudioToTempFile(audioData: ByteArray): File? {
        return try {
            val tempFile = File.createTempFile("tts_audio", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { outputStream ->
                outputStream.write(audioData)
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "保存音频文件失败", e)
            null
        }
    }
    
    /**
     * 文字转语音并播放
     */
    suspend fun textToSpeechAndPlay(
        text: String,
        voice: String = "zh-CN-XiaoxiaoNeural",
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // 如果文本过长，截取前500个字符以提高速度
        val processedText = if (text.length > 500) {
            text.substring(0, 500) + "..."
        } else {
            text
        }
        try {
            Log.d(TAG, "开始TTS处理: $processedText, 音色: $voice")
            
            withContext(Dispatchers.Main) {
                if (isPlaying) {
                    Log.w(TAG, "TTS正在播放中，停止当前播放")
                    stopPlayback()
                }
                
                // 从API获取音频数据
                val audioData = fetchAudioFromAPI(processedText, voice)
                if (audioData == null || audioData.isEmpty()) {
                    Log.e(TAG, "获取音频数据失败")
                    onError("获取音频数据失败，请检查网络连接")
                    return@withContext
                }
                
                // 保存到临时文件
                val tempFile = saveAudioToTempFile(audioData)
                if (tempFile == null) {
                    Log.e(TAG, "保存音频文件失败")
                    onError("保存音频文件失败")
                    return@withContext
                }
                
                // 创建MediaPlayer并播放
                try {
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setOnPreparedListener { mediaPlayer ->
                            Log.d(TAG, "音频准备完成，开始播放")
                            this@TTSService.isPlaying = true
                            onPlayStart()
                            start()
                        }
                        setOnCompletionListener { mediaPlayer ->
                            Log.d(TAG, "音频播放完成")
                            this@TTSService.isPlaying = false
                            onPlayComplete()
                            cleanup()
                        }
                        setOnErrorListener { mediaPlayer, what, extra ->
                            Log.e(TAG, "音频播放错误: what=$what, extra=$extra")
                            this@TTSService.isPlaying = false
                            onError("音频播放失败")
                            cleanup()
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建MediaPlayer失败", e)
                    isPlaying = false
                    onError("创建播放器失败: ${e.message}")
                    cleanup()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "TTS处理异常", e)
            isPlaying = false
            onError("TTS处理异常: ${e.message}")
        }
    }
    
    /**
     * 测试音色播放 - 使用预置音频文件实现即时播放
     */
    suspend fun testVoice(
        voiceId: String,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            Log.d(TAG, "开始播放预置音色: $voiceId")
            
            withContext(Dispatchers.Main) {
                if (isPlaying) {
                    Log.w(TAG, "TTS正在播放中，停止当前播放")
                    stopPlayback()
                }
                
                // 尝试从assets加载预置音频文件
                val audioData = loadPrebuiltAudio(voiceId)
                if (audioData != null) {
                    Log.d(TAG, "使用预置音频文件，大小: ${audioData.size} 字节")
                    // 保存到临时文件并播放
                    val tempFile = saveAudioToTempFile(audioData)
                    if (tempFile != null) {
                        playPrebuiltAudio(tempFile, onPlayStart, onPlayComplete, onError)
                    } else {
                        onError("保存预置音频文件失败")
                    }
                } else {
                    Log.w(TAG, "预置音频文件不存在，回退到API生成")
                    // 回退到API生成
                    val testText = getVoiceTestText(voiceId)
                    textToSpeechAndPlay(testText, voiceId, onPlayStart, onPlayComplete, onError)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试音色播放失败", e)
            onError("测试音色播放失败: ${e.message}")
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        try {
            Log.d(TAG, "停止TTS播放")
            playbackJob?.cancel()
            playbackJob = null
            
            // 强制停止播放，不管isPlaying状态
            mediaPlayer?.stop()
            isPlaying = false
            cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
        }
    }
    
    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "清理MediaPlayer失败", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            Log.d(TAG, "释放TTS服务资源")
            stopPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "释放TTS服务失败", e)
        }
    }
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 检查TTS是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * 预加载常用TTS音频 - 激进策略
     */
    fun preloadCommonAudio() {
        val commonTexts = listOf(
            "你好！请问有什么可以帮您？",
            "你好，请问有什么可以帮您？",
            "好的，我明白了",
            "请稍等",
            "正在处理中",
            "操作完成",
            "谢谢",
            "不客气",
            "再见",
            "好的",
            "明白",
            "了解",
            "收到",
            "确认",
            "完成"
        )
        
        val commonVoices = listOf(
            "zh-CN-XiaoyiNeural",
            "zh-CN-XiaoxiaoNeural"
        )
        
        // 在后台预加载
        CoroutineScope(Dispatchers.IO).launch {
            commonVoices.forEach { voice ->
                commonTexts.forEach { text ->
                    try {
                        val cacheKey = "${text}_${voice}"
                        if (!audioCache.containsKey(cacheKey)) {
                            fetchAudioFromAPI(text, voice)
                            delay(200) // 减少延迟，加快预加载
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "预加载TTS音频失败: $text, ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * 获取音色显示名称
     */
    private fun getVoiceDisplayName(voiceId: String): String {
        return when (voiceId) {
            "zh-CN-XiaoxiaoNeural" -> "晓晓"
            "zh-CN-YunxiNeural" -> "云希"
            "zh-CN-YunyangNeural" -> "云扬"
            "zh-CN-XiaoyiNeural" -> "小艺"
            "zh-CN-YunjianNeural" -> "云健"
            else -> "未知音色"
        }
    }
    
    /**
     * 从assets加载预置音频文件
     */
    private fun loadPrebuiltAudio(voiceId: String): ByteArray? {
        return try {
            val assetPath = "voice_samples/$voiceId.mp3"
            Log.d(TAG, "尝试加载预置音频文件: $assetPath")
            context.assets.open(assetPath).readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "加载预置音频文件失败: $voiceId", e)
            null
        }
    }
    
    /**
     * 播放预置音频文件
     */
    private fun playPrebuiltAudio(
        tempFile: File,
        onPlayStart: () -> Unit,
        onPlayComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            Log.d(TAG, "开始播放预置音频文件: ${tempFile.absolutePath}")
            
            // 创建MediaPlayer并播放
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(tempFile.absolutePath)
                    
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "预置音频准备完成，开始播放")
                        try {
                            mp.start()
                            this@TTSService.isPlaying = true
                            Log.d(TAG, "预置音频播放已启动")
                            onPlayStart()
                            
                            // 启动播放完成检测
                            startPlaybackMonitoring(onPlayComplete, onError, tempFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "预置音频播放启动失败", e)
                            this@TTSService.isPlaying = false
                            onError("预置音频播放启动失败: ${e.message}")
                        }
                    }
                    
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "预置音频播放错误: what=$what, extra=$extra")
                        this@TTSService.isPlaying = false
                        onError("预置音频播放失败: what=$what, extra=$extra")
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "MediaPlayer释放失败", e)
                        }
                        this@TTSService.mediaPlayer = null
                        // 删除临时文件
                        tempFile.delete()
                        true
                    }
                    
                    Log.d(TAG, "开始异步准备预置音频...")
                    prepareAsync()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "预置音频MediaPlayer设置失败", e)
                    this@TTSService.isPlaying = false
                    onError("预置音频MediaPlayer设置失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "播放预置音频失败", e)
            onError("播放预置音频失败: ${e.message}")
        }
    }
    
    /**
     * 启动播放完成检测
     */
    private fun startPlaybackMonitoring(
        onPlayComplete: () -> Unit,
        onError: (String) -> Unit,
        tempFile: File
    ) {
        playbackJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // 等待播放开始
                delay(1000)
                
                // 监控播放状态
                while (isPlaying && mediaPlayer != null) {
                    try {
                        if (mediaPlayer?.isPlaying == false) {
                            Log.d(TAG, "检测到播放已停止")
                            isPlaying = false
                            onPlayComplete()
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "播放状态检测失败", e)
                        isPlaying = false
                        onError("播放状态检测失败: ${e.message}")
                        break
                    }
                    delay(200) // 每200ms检查一次
                }
                
                // 清理资源
                try {
                    mediaPlayer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPlayer释放失败", e)
                }
                mediaPlayer = null
                tempFile.delete()
                
            } catch (e: Exception) {
                Log.e(TAG, "播放监控失败", e)
                isPlaying = false
                onError("播放监控失败: ${e.message}")
            }
        }
    }
    
    /**
     * 获取音色测试文本
     */
    private fun getVoiceTestText(voiceId: String): String {
        return when (voiceId) {
            "zh-CN-XiaoxiaoNeural" -> "您好，我是晓晓，我的声音温柔甜美，清澈动听，适合日常对话和轻松交流。"
            "zh-CN-YunxiNeural" -> "您好，我是云希，我的声音知性优雅，成熟稳重，适合商务场景和专业对话。"
            "zh-CN-YunyangNeural" -> "您好，我是云扬，我的声音成熟稳重，浑厚有力，适合正式场合和重要播报。"
            "zh-CN-XiaoyiNeural" -> "您好，我是小艺，我的声音活泼可爱，清脆悦耳，适合轻松对话和娱乐内容。"
            "zh-CN-YunjianNeural" -> "您好，我是云健，我的声音磁性低沉，富有魅力，适合朗读和播报。"
            else -> "这是音色测试，您可以听到不同的声音效果。"
        }
    }
}