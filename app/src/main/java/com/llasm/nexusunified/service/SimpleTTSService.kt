package com.llasm.nexusunified.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.llasm.nexusunified.config.ServerConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SimpleTTSService(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleTTSService"
        private val API_URL = ServerConfig.getApiUrl("tts")
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var playbackJob: Job? = null
    
    /**
     * 简单文字转语音并播放
     */
    suspend fun textToSpeechAndPlay(
        text: String,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            Log.d(TAG, "开始简单TTS处理: $text")
            
            withContext(Dispatchers.IO) {
                // 发送文本到后端进行TTS
                val audioData = textToSpeech(text)
                
                if (audioData != null && audioData.isNotEmpty()) {
                    Log.d(TAG, "简单TTS生成成功，开始播放")
                    // 播放音频
                    playAudio(audioData, onPlayStart, onPlayComplete, onError)
                } else {
                    Log.e(TAG, "简单TTS生成失败：音频数据为空")
                    onError("简单TTS生成失败")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "简单TTS播放失败", e)
            onError("简单TTS播放失败: ${e.message}")
        }
    }
    
    /**
     * 文字转语音
     */
    private suspend fun textToSpeech(text: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("text", text)
                }
                
                val requestBody = json.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val audioData = response.body?.bytes()
                    Log.d(TAG, "简单TTS生成成功，音频大小: ${audioData?.size ?: 0} bytes")
                    return@withContext audioData
                } else {
                    Log.e(TAG, "简单TTS API请求失败: ${response.code}")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "简单TTS请求失败", e)
                return@withContext null
            }
        }
    }
    
    /**
     * 播放音频数据
     */
    private fun playAudio(
        audioData: ByteArray,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            Log.d(TAG, "开始播放简单音频，数据大小: ${audioData.size} bytes")
            
            // 停止当前播放
            stopPlayback()
            
            // 将音频数据保存到临时文件
            val tempFile = File.createTempFile("simple_tts_", ".mp3", context.cacheDir)
            tempFile.writeBytes(audioData)
            
            Log.d(TAG, "保存简单音频文件: ${tempFile.absolutePath}, 大小: ${audioData.size} bytes")
            
            // 创建MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(tempFile.absolutePath)
                    
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "简单音频准备完成，开始播放")
                        try {
                            mp.start()
                            this@SimpleTTSService.isPlaying = true
                            Log.d(TAG, "简单音频播放已启动")
                            onPlayStart()
                            
                            // 启动播放完成检测
                            startPlaybackMonitoring(onPlayComplete, onError, tempFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "简单音频播放启动失败", e)
                            this@SimpleTTSService.isPlaying = false
                            onError("简单音频播放启动失败: ${e.message}")
                        }
                    }
                    
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "简单音频播放错误: what=$what, extra=$extra")
                        this@SimpleTTSService.isPlaying = false
                        onError("简单音频播放失败: what=$what, extra=$extra")
                        try {
                            mp.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "MediaPlayer释放失败", e)
                        }
                        this@SimpleTTSService.mediaPlayer = null
                        // 删除临时文件
                        tempFile.delete()
                        true
                    }
                    
                    Log.d(TAG, "开始异步准备简单音频...")
                    prepareAsync()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "MediaPlayer设置失败", e)
                    this@SimpleTTSService.isPlaying = false
                    onError("MediaPlayer设置失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "播放简单音频失败", e)
            onError("播放简单音频失败: ${e.message}")
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
     * 停止播放
     */
    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        
        if (isPlaying) {
            mediaPlayer?.stop()
            isPlaying = false
            Log.d(TAG, "停止简单TTS播放")
        }
        
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopPlayback()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
}
