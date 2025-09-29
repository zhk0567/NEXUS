package com.llasm.nexusunified.realtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.Collections
import java.nio.ByteOrder

/**
 * 实时音频管理器 - 简化版本
 * 负责音频录制和播放，支持点击式录音控制
 */
class RealtimeAudioManager(
    private val context: Context,
    private val onAudioData: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "RealtimeAudioManager"
        
        // 音频配置 - 严格按照火山引擎文档1.1产品约束
        private const val SAMPLE_RATE = 16000  // 16kHz采样率（录制）
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO  // 单声道
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16位位深
        private const val CHUNK_SIZE = 3200  // 每个音频块大小 (16000Hz * 0.2秒 = 3200字节)
        
        // 输出音频配置 - 服务端返回24kHz PCM格式
        private const val OUTPUT_SAMPLE_RATE = 24000  // 24kHz采样率
        private const val OUTPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO  // 单声道
        private const val OUTPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16位深度
        private const val OUTPUT_CHUNK_SIZE = 9600  // 每个音频块大小 (24000Hz * 0.2秒 * 2字节 = 9600字节)
        
        // 内存管理
        private const val MAX_AUDIO_BUFFER_SIZE = 50  // 最大音频缓冲区数量
    }
    
    // 音频录制
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    // 音频播放
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackJob: Job? = null
    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    
    // 音频缓冲区
    private val audioBuffer = mutableListOf<ByteArray>()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        initializeAudio()
    }
    
    /**
     * 初始化音频系统
     */
    private fun initializeAudio() {
        try {
            initializeAudioRecord()
            initializeAudioTrack()
            Log.d(TAG, "音频系统初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "音频系统初始化失败", e)
            onError("音频系统初始化失败: ${e.message}")
        }
    }
    
    /**
     * 初始化录音器
     */
    private fun initializeAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("AudioRecord初始化失败")
        }
        
        Log.d(TAG, "音频录制初始化成功")
    }
    
    /**
     * 初始化播放器
     */
    private fun initializeAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            OUTPUT_CHANNEL_CONFIG,
            OUTPUT_AUDIO_FORMAT
        )
        
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)  // 使用媒体播放，确保通过外放扬声器
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        
        val audioFormat = AudioFormat.Builder()
            .setSampleRate(OUTPUT_SAMPLE_RATE)
            .setEncoding(OUTPUT_AUDIO_FORMAT)
            .setChannelMask(OUTPUT_CHANNEL_CONFIG)
            .build()
        
        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            throw RuntimeException("AudioTrack初始化失败")
        }
        
        Log.d(TAG, "音频播放初始化成功")
    }
    
    /**
     * 开始录音
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "已经在录音中")
            return
        }
        
        try {
            audioRecord?.startRecording()
            isRecording = true
            audioBuffer.clear()
            
            // 启动录音循环
            recordingJob = scope.launch {
                val buffer = ShortArray(CHUNK_SIZE / 2)
                val byteBuffer = ByteBuffer.allocate(CHUNK_SIZE)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                
                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        // 转换为字节数组
                        byteBuffer.clear()
                        for (sample in buffer) {
                            byteBuffer.putShort(sample)
                        }
                        val audioData = byteBuffer.array().copyOf(bytesRead * 2)
                        
                        // 保存到缓冲区（同步访问）
                        synchronized(audioBuffer) {
                            audioBuffer.add(audioData)
                            
                            // 限制缓冲区大小
                            if (audioBuffer.size > MAX_AUDIO_BUFFER_SIZE) {
                                audioBuffer.removeAt(0)
                            }
                        }
                        
                        Log.d(TAG, "录音中... 缓冲区大小: ${audioBuffer.size}")
                    }
                }
            }
            
            Log.d(TAG, "开始录音")
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            onError("开始录音失败: ${e.message}")
        }
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "当前未在录音")
            return
        }
        
        try {
            isRecording = false
            recordingJob?.cancel()
            audioRecord?.stop()
            
            synchronized(audioBuffer) {
                Log.d(TAG, "停止录音，缓冲区大小: ${audioBuffer.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            onError("停止录音失败: ${e.message}")
        }
    }
    
    /**
     * 获取当前录音数据
     */
    fun getCurrentAudioData(): ByteArray? {
        synchronized(audioBuffer) {
            return if (audioBuffer.isNotEmpty()) {
                val allData = audioBuffer.fold(ByteArray(0)) { acc, data ->
                    acc + data
                }
                audioBuffer.clear()
                Log.d(TAG, "获取录音数据: ${allData.size} 字节")
                allData
            } else {
                Log.w(TAG, "录音缓冲区为空")
                null
            }
        }
    }
    
    /**
     * 播放音频数据
     */
    fun playAudio(audioData: ByteArray) {
        // 添加到播放队列
        audioQueue.add(audioData)
        Log.d(TAG, "添加音频到队列: ${audioData.size} 字节，队列大小: ${audioQueue.size}")
        
        // 如果当前没有播放，开始播放
        if (!isPlaying) {
            startPlayback()
        }
    }
    
    /**
     * 开始播放队列中的音频
     */
    private fun startPlayback() {
        if (isPlaying || audioQueue.isEmpty()) {
            return
        }
        
        try {
            isPlaying = true
            audioTrack?.play()
            
            playbackJob = scope.launch {
                try {
                    // 播放队列中的所有音频
                    while (audioQueue.isNotEmpty()) {
                        val audioData = audioQueue.removeAt(0)
                        Log.d(TAG, "播放音频块: ${audioData.size} 字节，剩余队列: ${audioQueue.size}")
                        playAudioData(audioData)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "播放音频失败", e)
                    onError("播放音频失败: ${e.message}")
                } finally {
                    isPlaying = false
                    audioTrack?.stop()
                    Log.d(TAG, "音频播放完成")
                }
            }
            
            Log.d(TAG, "开始播放音频队列")
            
        } catch (e: Exception) {
            Log.e(TAG, "开始播放失败", e)
            onError("开始播放失败: ${e.message}")
            isPlaying = false
        }
    }
    
    /**
     * 播放单个音频数据块
     */
    private suspend fun playAudioData(audioData: ByteArray) {
        try {
            val bytesWritten = audioTrack?.write(audioData, 0, audioData.size) ?: 0
            if (bytesWritten < 0) {
                Log.e(TAG, "音频写入失败: $bytesWritten")
                return
            }
            
            // 不添加延迟，让AudioTrack自然播放
            // AudioTrack会自动处理播放时序
            Log.d(TAG, "音频数据已写入: ${audioData.size} 字节")
        } catch (e: Exception) {
            Log.e(TAG, "播放音频数据失败", e)
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        try {
            isPlaying = false
            playbackJob?.cancel()
            audioTrack?.stop()
            audioQueue.clear()
            Log.d(TAG, "停止播放")
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
        }
    }
    
    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            isRecording = false
            isPlaying = false
            recordingJob?.cancel()
            playbackJob?.cancel()
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            audioBuffer.clear()
            audioQueue.clear()
            
            scope.cancel()
            Log.d(TAG, "音频管理器已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放音频管理器失败", e)
        }
    }
}