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
import kotlin.math.*

/**
 * 实时音频管理器
 * 负责音频录制和播放，符合火山引擎文档要求
 * 集成VAD语音活动检测功能
 */
class RealtimeAudioManager(
    private val context: Context,
    private val onAudioData: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
    private val onVoiceActivity: ((Boolean) -> Unit)? = null
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
        private const val OUTPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT  // 16位深度（符合官方文档）
        private const val OUTPUT_CHUNK_SIZE = 9600  // 每个音频块大小 (24000Hz * 0.2秒 * 2字节 = 9600字节)
        
        // 语音检测阈值 - 最稳定可靠的配置
        private const val RMS_THRESHOLD = 250.0  // 进一步提高RMS阈值
        private const val AMPLITUDE_THRESHOLD = 2000  // 进一步提高振幅阈值
        private const val SILENCE_THRESHOLD = 500  // 进一步提高静音阈值
        private const val VOICE_RATIO_THRESHOLD = 0.03  // 进一步提高语音比例阈值到3%
        
        // 内存管理
        private const val MAX_AUDIO_BUFFER_SIZE = 50  // 最大音频缓冲区数量
        private const val MAX_AUDIO_QUEUE_SIZE = 20   // 最大播放队列数量
        private const val BUFFER_CLEANUP_INTERVAL = 10000L  // 10秒清理一次
        
        // 录音时间限制
        private const val MIN_RECORDING_TIME_MS = 4000L // 4秒最小录音时间（进一步增加）
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    // 线程安全的状态变量
    @Volatile
    private var isRecording = false
    @Volatile
    private var isPlaying = false
    @Volatile
    private var vadEnabled = true
    @Volatile
    private var isVoiceActive = false
    
    private var recordingJob: Job? = null
    private var playingJob: Job? = null
    
    // 录音时间管理
    private var recordingStartTime = 0L
    
    // 音频缓冲区 - 使用同步集合
    private val audioBuffer = Collections.synchronizedList(mutableListOf<ByteArray>())
    private val audioQueue = Collections.synchronizedList(mutableListOf<ByteArray>())
    
    // VAD语音活动检测器
    // VAD检测器 - 默认使用WebRTC VAD
    private var vadDetector: VADInterface = VADFactory.createVAD(VADType.WEBRTC)
    
    // 同步锁
    private val recordingLock = Any()
    private val playingLock = Any()
    private val bufferLock = Any()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 缓冲区清理定时器
    private var cleanupJob: Job? = null
    
    init {
        // 初始化VAD检测器
        initializeVAD()
        
        // 启动缓冲区清理定时器
        startBufferCleanup()
    }
    
    /**
     * 初始化VAD检测器
     */
    private fun initializeVAD() {
        if (!vadDetector.initialize()) {
            Log.e(TAG, "VAD检测器初始化失败")
            return
        }
        
        Log.d(TAG, "VAD检测器初始化完成 - 类型: ${if (vadDetector is WebRTCVAD) "WebRTC" else "Custom"}")
    }
    
    /**
     * 启动缓冲区清理定时器
     */
    private fun startBufferCleanup() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(BUFFER_CLEANUP_INTERVAL)
                cleanupBuffers()
            }
        }
    }
    
    /**
     * 清理音频缓冲区
     */
    private fun cleanupBuffers() {
        try {
            // 清理录音缓冲区
            if (audioBuffer.size > MAX_AUDIO_BUFFER_SIZE) {
                val removeCount = audioBuffer.size - MAX_AUDIO_BUFFER_SIZE
                repeat(removeCount) {
                    if (audioBuffer.isNotEmpty()) {
                        audioBuffer.removeAt(0)
                    }
                }
                Log.d(TAG, "清理录音缓冲区，移除 $removeCount 个数据块")
            }
            
            // 清理播放队列
            if (audioQueue.size > MAX_AUDIO_QUEUE_SIZE) {
                val removeCount = audioQueue.size - MAX_AUDIO_QUEUE_SIZE
                repeat(removeCount) {
                    if (audioQueue.isNotEmpty()) {
                        audioQueue.removeAt(0)
                    }
                }
                Log.d(TAG, "清理播放队列，移除 $removeCount 个数据块")
            }
            
            // 记录当前缓冲区状态
            if (audioBuffer.size > MAX_AUDIO_BUFFER_SIZE * 0.8 || audioQueue.size > MAX_AUDIO_QUEUE_SIZE * 0.8) {
                Log.w(TAG, "缓冲区使用率较高 - 录音: ${audioBuffer.size}/$MAX_AUDIO_BUFFER_SIZE, 播放: ${audioQueue.size}/$MAX_AUDIO_QUEUE_SIZE")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理缓冲区失败", e)
        }
    }
    
    /**
     * 启用/禁用VAD检测
     */
    fun setVADEnabled(enabled: Boolean) {
        vadEnabled = enabled
        if (!enabled) {
            vadDetector.reset()
            isVoiceActive = false
        }
        Log.d(TAG, "VAD检测${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 暂停VAD检测（在AI回答期间）
     */
    fun pauseVAD() {
        vadEnabled = false
        Log.d(TAG, "VAD检测已暂停")
    }
    
    /**
     * 恢复VAD检测
     */
    fun resumeVAD() {
        vadEnabled = true
        Log.d(TAG, "VAD检测已恢复")
    }
    
    /**
     * 设置VAD阈值
     */
    fun setVADThresholds(
        rms: Double = RMS_THRESHOLD,
        amplitude: Int = AMPLITUDE_THRESHOLD,
        silence: Int = SILENCE_THRESHOLD,
        voiceRatio: Double = VOICE_RATIO_THRESHOLD
    ) {
        if (vadDetector is CustomVAD) {
            // 只有自定义VAD支持阈值设置
            Log.d(TAG, "设置自定义VAD阈值")
        } else {
            Log.d(TAG, "WebRTC VAD使用模式设置，忽略阈值参数")
        }
    }
    
    /**
     * 获取VAD状态
     */
    fun isVoiceActive(): Boolean = isVoiceActive
    
    /**
     * 获取VAD统计信息
     */
    fun getVADStatistics(): Any {
        val detector = vadDetector
        return when (detector) {
            is WebRTCVAD -> detector.getStatistics()
            is CustomVAD -> "Custom VAD Statistics"
            else -> "Unknown VAD Type"
        }
    }
    
    /**
     * 切换VAD类型
     */
    fun switchVADType(type: VADType) {
        val wasEnabled = vadEnabled
        vadDetector.release()
        vadDetector = VADFactory.createVAD(type)
        
        if (wasEnabled) {
            vadDetector.initialize()
        }
        
        Log.d(TAG, "VAD类型已切换为: $type")
    }
    
    /**
     * 获取当前VAD类型
     */
    fun getCurrentVADType(): VADType {
        return when (vadDetector) {
            is WebRTCVAD -> VADType.WEBRTC
            is CustomVAD -> VADType.CUSTOM
            else -> VADType.WEBRTC
        }
    }
    
    /**
     * 初始化音频录制
     */
    fun initializeRecording(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(bufferSize, CHUNK_SIZE * 2)
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "音频录制初始化失败")
                return false
            }
            
            Log.d(TAG, "音频录制初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化音频录制失败", e)
            onError("初始化音频录制失败: ${e.message}")
            false
        }
    }
    
    /**
     * 初始化音频播放
     */
    fun initializePlayback(): Boolean {
        return try {
            val bufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                OUTPUT_AUDIO_FORMAT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(OUTPUT_AUDIO_FORMAT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(OUTPUT_CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferSize, OUTPUT_SAMPLE_RATE * 2 * 2))  // 24kHz * 2字节 * 2秒
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "音频播放初始化失败")
                return false
            }
            
            Log.d(TAG, "音频播放初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "初始化音频播放失败", e)
            onError("初始化音频播放失败: ${e.message}")
            false
        }
    }
    
    /**
     * 开始录音
     */
    fun startRecording() {
        synchronized(recordingLock) {
            if (isRecording) {
                Log.w(TAG, "已经在录音中")
                return
            }
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("音频录制未初始化")
                return
            }
            
            try {
                audioRecord?.startRecording()
                isRecording = true
                recordingStartTime = System.currentTimeMillis() // 记录录音开始时间
                synchronized(bufferLock) {
                    audioBuffer.clear()
                }
                
                recordingJob = scope.launch {
                    val buffer = ByteArray(CHUNK_SIZE)
                    
                    while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        
                        if (bytesRead > 0) {
                            val audioData = buffer.copyOf(bytesRead)
                            
                            // 检查缓冲区大小，防止内存泄漏
                            synchronized(bufferLock) {
                                if (audioBuffer.size >= MAX_AUDIO_BUFFER_SIZE) {
                                    // 移除最旧的数据
                                    if (audioBuffer.isNotEmpty()) {
                                        audioBuffer.removeAt(0)
                                    }
                                }
                                audioBuffer.add(audioData)
                            }
                            
                            // VAD实时检测 - 录音4秒后才启用VAD
                            if (vadEnabled) {
                                val recordingDuration = System.currentTimeMillis() - recordingStartTime
                                if (recordingDuration >= MIN_RECORDING_TIME_MS) {
                                    val vadResult = vadDetector.processAudioData(audioData)
                                    if (vadResult.isVoiceActive != isVoiceActive) {
                                        isVoiceActive = vadResult.isVoiceActive
                                        Log.d(TAG, "VAD状态变化 - 语音活动: $isVoiceActive, 置信度: ${vadResult.confidence}")
                                        // 只有在VAD启用时才触发回调
                                        if (vadEnabled) {
                                            onVoiceActivity?.invoke(isVoiceActive)
                                        }
                                    }
                                } else {
                                    // 录音前4秒内，强制设置为语音活动状态，避免过早触发静音检测
                                    if (!isVoiceActive) {
                                        isVoiceActive = true
                                        Log.d(TAG, "录音前4秒内，强制设置为语音活动状态")
                                        // 只有在VAD启用时才触发回调
                                        if (vadEnabled) {
                                            onVoiceActivity?.invoke(true)
                                        }
                                    }
                                }
                            }
                            
                            // 传统音频分析
                            val analysis = analyzeAudioData(audioData)
                            Log.d(TAG, "录音中... RMS: ${analysis.rms}, 语音比例: ${analysis.voiceRatio}, VAD: $isVoiceActive")
                        }
                    }
                }
                
                Log.d(TAG, "开始录音")
            } catch (e: Exception) {
                Log.e(TAG, "开始录音失败", e)
                onError("开始录音失败: ${e.message}")
                isRecording = false
            }
        }
    }
    
    /**
     * 获取当前录音数据（不停止录音）
     */
    fun getCurrentAudioData(): ByteArray? {
        return getCurrentAudioData(checkRecording = true)
    }
    
    /**
     * 获取当前录音数据（可选择是否检查录音状态）
     */
    fun getCurrentAudioData(checkRecording: Boolean = true): ByteArray? {
        synchronized(recordingLock) {
            if (checkRecording && !isRecording) {
                Log.w(TAG, "当前未在录音，无法获取音频数据")
                return null
            }
            
            try {
                // 获取当前缓冲区中的所有音频数据
                val combinedAudio = ByteArray(audioBuffer.sumOf { it.size })
                var offset = 0
                
                for (audioData in audioBuffer) {
                    System.arraycopy(audioData, 0, combinedAudio, offset, audioData.size)
                    offset += audioData.size
                }
                
                // 清空缓冲区，为下一轮录音做准备
                synchronized(bufferLock) {
                    audioBuffer.clear()
                }
                
                Log.d(TAG, "获取当前音频数据成功，大小: ${combinedAudio.size} 字节，缓冲区已清空")
                return combinedAudio
                
            } catch (e: Exception) {
                Log.e(TAG, "获取当前音频数据失败", e)
                return null
            }
        }
    }
    
    /**
     * 暂停录音（不返回音频数据）
     */
    fun pauseRecording() {
        synchronized(recordingLock) {
            if (!isRecording) {
                Log.w(TAG, "当前未在录音，无需暂停")
                return
            }
            
            try {
                isRecording = false
                recordingJob?.cancel()
                audioRecord?.stop()
                
                // 清空音频缓冲区
                synchronized(bufferLock) {
                    audioBuffer.clear()
                }
                
                Log.d(TAG, "录音已暂停")
                
            } catch (e: Exception) {
                Log.e(TAG, "暂停录音失败", e)
            }
        }
    }
    
    /**
     * 停止录音并返回音频数据
     */
    fun stopRecording(): ByteArray? {
        synchronized(recordingLock) {
            if (!isRecording) {
                Log.w(TAG, "当前未在录音")
                return null
            }
            
            try {
                isRecording = false
                recordingJob?.cancel()
                audioRecord?.stop()
                
                // 合并所有音频数据
                val bufferData: List<ByteArray>
                synchronized(bufferLock) {
                    if (audioBuffer.isEmpty()) {
                        Log.w(TAG, "录音缓冲区为空")
                        return null
                    }
                    bufferData = audioBuffer.toList()
                    audioBuffer.clear()
                }
                
                val totalSize = bufferData.sumOf { it.size }
                val combinedAudio = ByteArray(totalSize)
                var offset = 0
                
                for (audioData in bufferData) {
                    System.arraycopy(audioData, 0, combinedAudio, offset, audioData.size)
                    offset += audioData.size
                }
                
                // 分析完整音频数据
                val analysis = analyzeAudioData(combinedAudio)
                val vadStats = getVADStatistics()
                
                Log.d(TAG, "录音完成 - 时长: ${analysis.durationMs}ms, 语音检测: ${analysis.isVoiceDetected}")
                Log.d(TAG, "音频分析 - RMS: ${analysis.rms}, 最大振幅: ${analysis.maxAmplitude}, 语音比例: ${analysis.voiceRatio}")
                Log.d(TAG, "VAD统计 - $vadStats")
                
                // 使用VAD和传统检测的综合判断
                val voiceDetected = if (vadEnabled) {
                    // VAD检测结果通过isVoiceActive状态获取
                    isVoiceActive || analysis.isVoiceDetected
                } else {
                    analysis.isVoiceDetected
                }
                
                if (!voiceDetected) {
                    Log.w(TAG, "语音检测失败 - RMS: ${analysis.rms}, 最大振幅: ${analysis.maxAmplitude}, 语音比例: ${analysis.voiceRatio}, VAD: $isVoiceActive")
                    onError("未检测到有效语音，请重新录音")
                    return null
                }
                
                if (analysis.durationMs < 2000) {
                    Log.w(TAG, "录音时间太短: ${analysis.durationMs}ms")
                    onError("录音时间太短，建议至少2秒")
                    return null
                }
                
                // 确保音频数据长度是chunk_size的整数倍
                val paddingNeeded = (CHUNK_SIZE - (combinedAudio.size % CHUNK_SIZE)) % CHUNK_SIZE
                if (paddingNeeded > 0) {
                    val paddedAudio = combinedAudio + ByteArray(paddingNeeded)
                    Log.d(TAG, "音频数据填充: 添加 $paddingNeeded 字节静音数据")
                    return paddedAudio
                }
                
                Log.d(TAG, "录音停止，返回音频数据: ${combinedAudio.size} 字节")
                return combinedAudio
                
            } catch (e: Exception) {
                Log.e(TAG, "停止录音失败", e)
                onError("停止录音失败: ${e.message}")
                return null
            }
        }
    }
    
    
    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        synchronized(playingLock) {
            try {
                isPlaying = false
                playingJob?.cancel()
                audioTrack?.stop()
                audioTrack?.flush()
                Log.d(TAG, "音频播放已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止播放失败: ${e.message}")
            }
        }
    }
    
    /**
     * 播放音频数据
     */
    fun playAudioData(audioData: ByteArray) {
        synchronized(playingLock) {
            if (isPlaying) {
                Log.w(TAG, "正在播放中，添加到队列")
                
                // 检查播放队列大小，防止内存泄漏
                synchronized(bufferLock) {
                    if (audioQueue.size >= MAX_AUDIO_QUEUE_SIZE) {
                        // 移除最旧的数据
                        if (audioQueue.isNotEmpty()) {
                            audioQueue.removeAt(0)
                        }
                    }
                    audioQueue.add(audioData)
                }
                return
            }
            
            try {
                if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "音频播放未初始化，尝试重新初始化")
                    if (!initializePlayback()) {
                        onError("音频播放初始化失败")
                        return
                    }
                }
                
                // 验证音频数据
                if (audioData.isEmpty()) {
                    Log.w(TAG, "音频数据为空，跳过播放")
                    return
                }
                
                // 验证音频数据大小是否合理
                if (audioData.size > OUTPUT_SAMPLE_RATE * 2 * 10) { // 最大10秒音频
                    Log.w(TAG, "音频数据过大，截断处理")
                    val maxSize = OUTPUT_SAMPLE_RATE * 2 * 10
                    val truncatedData = audioData.sliceArray(0 until maxSize)
                    playAudioDataInternal(truncatedData)
                    return
                }
                
                playAudioDataInternal(audioData)
                
            } catch (e: Exception) {
                Log.e(TAG, "播放音频数据失败", e)
                onError("播放音频数据失败: ${e.message}")
                isPlaying = false
            }
        }
    }
    
    /**
     * 内部播放音频数据方法
     */
    private fun playAudioDataInternal(audioData: ByteArray) {
        synchronized(playingLock) {
            try {
                isPlaying = true
                audioTrack?.play()
                
                playingJob = scope.launch {
                    try {
                        // 分块播放，确保16位PCM数据正确播放
                        val chunkSize = 2400  // 24kHz * 0.05秒 * 2字节 = 2400字节
                        var offset = 0
                        
                        while (offset < audioData.size && isPlaying) {
                            val currentChunkSize = minOf(chunkSize, audioData.size - offset)
                            val chunk = audioData.sliceArray(offset until offset + currentChunkSize)
                            
                            val bytesWritten = audioTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                            
                            if (bytesWritten != chunk.size) {
                                Log.w(TAG, "音频块写入不完整: $bytesWritten/${chunk.size}")
                            }
                            
                            offset += currentChunkSize
                            
                            // 精确延迟，确保播放速度正确
                            delay(50)  // 0.05秒延迟
                        }
                    
                        Log.d(TAG, "音频播放完成: ${audioData.size} 字节")
                        
                        // 播放队列中的音频数据
                        while (audioQueue.isNotEmpty() && isPlaying) {
                            val queuedAudio: ByteArray
                            synchronized(bufferLock) {
                                queuedAudio = audioQueue.removeAt(0)
                            }
                            var queuedOffset = 0
                            
                            while (queuedOffset < queuedAudio.size && isPlaying) {
                                val currentChunkSize = minOf(chunkSize, queuedAudio.size - queuedOffset)
                                val chunk = queuedAudio.sliceArray(queuedOffset until queuedOffset + currentChunkSize)
                                
                                val queuedBytesWritten = audioTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                                
                                if (queuedBytesWritten != chunk.size) {
                                    Log.w(TAG, "队列音频块写入不完整: $queuedBytesWritten/${chunk.size}")
                                }
                                
                                queuedOffset += currentChunkSize
                                delay(10)
                            }
                        }
                    
                    } catch (e: Exception) {
                        Log.e(TAG, "播放音频数据失败", e)
                        onError("播放音频数据失败: ${e.message}")
                    } finally {
                        isPlaying = false
                    }
                }
                
                Log.d(TAG, "开始播放音频数据: ${audioData.size} 字节")
            } catch (e: Exception) {
                Log.e(TAG, "播放音频数据失败", e)
                onError("播放音频数据失败: ${e.message}")
                isPlaying = false
            }
        }
    }
    
    
    /**
     * 分析音频数据
     */
    private fun analyzeAudioData(audioData: ByteArray): AudioAnalysis {
        if (audioData.isEmpty()) {
            return AudioAnalysis(
                maxAmplitude = 0,
                avgAmplitude = 0.0,
                rms = 0.0,
                voiceRatio = 0.0,
                isVoiceDetected = false,
                sampleCount = 0,
                durationMs = 0.0
            )
        }
        
        // 验证音频数据长度是否为偶数（16位音频）
        if (audioData.size % 2 != 0) {
            Log.w(TAG, "音频数据长度不是偶数，可能格式不正确")
            return AudioAnalysis(
                maxAmplitude = 0,
                avgAmplitude = 0.0,
                rms = 0.0,
                voiceRatio = 0.0,
                isVoiceDetected = false,
                sampleCount = 0,
                durationMs = 0.0
            )
        }
        
        // 将字节数据转换为16位整数（小端序）
        val samples = ShortArray(audioData.size / 2)
        val buffer = ByteBuffer.wrap(audioData)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().get(samples)
        
        // 计算音频统计信息
        val maxAmplitude = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        val avgAmplitude = samples.map { abs(it.toInt()) }.average()
        val rms = sqrt(samples.map { it.toDouble() * it.toDouble() }.average())
        
        // 语音检测算法
        val rmsVoice = rms > RMS_THRESHOLD
        val amplitudeVoice = maxAmplitude > AMPLITUDE_THRESHOLD
        
        val nonSilentSamples = samples.count { abs(it.toInt()) > SILENCE_THRESHOLD }
        val voiceRatio = nonSilentSamples.toDouble() / samples.size
        val ratioVoice = voiceRatio > VOICE_RATIO_THRESHOLD
        
        // 连续非静音段检测
        var consecutiveCount = 0
        var maxConsecutive = 0
        for (sample in samples) {
            if (abs(sample.toInt()) > SILENCE_THRESHOLD) {
                consecutiveCount++
                maxConsecutive = maxOf(maxConsecutive, consecutiveCount)
            } else {
                consecutiveCount = 0
            }
        }
        val consecutiveVoice = maxConsecutive > 5
        
        // 综合判断
        val isVoiceDetected = rmsVoice || amplitudeVoice || ratioVoice || consecutiveVoice
        
        return AudioAnalysis(
            maxAmplitude = maxAmplitude,
            avgAmplitude = avgAmplitude,
            rms = rms,
            voiceRatio = voiceRatio,
            isVoiceDetected = isVoiceDetected,
            sampleCount = samples.size,
            durationMs = samples.size.toDouble() / SAMPLE_RATE * 1000
        )
    }
    
    /**
     * 测试麦克风
     */
    fun testMicrophone(durationMs: Long = 3000): AudioAnalysis? {
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("音频录制未初始化")
            return null
        }
        
        return try {
            audioRecord?.startRecording()
            val buffer = ByteArray(CHUNK_SIZE)
            val testData = mutableListOf<ByteArray>()
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < durationMs) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    testData.add(buffer.copyOf(bytesRead))
                }
            }
            
            audioRecord?.stop()
            
            val totalSize = testData.sumOf { it.size }
            val combinedData = ByteArray(totalSize)
            var offset = 0
            
            for (data in testData) {
                System.arraycopy(data, 0, combinedData, offset, data.size)
                offset += data.size
            }
            
            analyzeAudioData(combinedData)
        } catch (e: Exception) {
            Log.e(TAG, "测试麦克风失败", e)
            onError("测试麦克风失败: ${e.message}")
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            isRecording = false
            isPlaying = false
            recordingJob?.cancel()
            playingJob?.cancel()
            cleanupJob?.cancel()  // 取消清理定时器
            
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            audioBuffer.clear()
            audioQueue.clear()
            
            // 重置VAD检测器
            vadDetector.reset()
            isVoiceActive = false
            
            scope.cancel()
            Log.d(TAG, "音频管理器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放音频管理器资源失败", e)
        }
    }
    
    /**
     * 检查是否正在录音
     */
    fun isRecording(): Boolean = isRecording
}

/**
 * 音频分析结果
 */
data class AudioAnalysis(
    val maxAmplitude: Int,
    val avgAmplitude: Double,
    val rms: Double,
    val voiceRatio: Double,
    val isVoiceDetected: Boolean,
    val sampleCount: Int,
    val durationMs: Double
)
