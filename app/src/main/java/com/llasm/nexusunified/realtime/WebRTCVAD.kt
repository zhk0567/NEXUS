package com.llasm.nexusunified.realtime

import android.util.Log
import kotlin.math.*

/**
 * WebRTC风格VAD实现
 * 基于WebRTC VAD算法的Android原生实现
 * 提供稳定的语音活动检测功能
 */
class WebRTCVAD : VADInterface {
    companion object {
        private const val TAG = "WebRTCVAD"
        
        // WebRTC VAD模式 (0-3, 从最宽松到最严格)
        const val MODE_VERY_AGGRESSIVE = 0  // 最宽松
        const val MODE_AGGRESSIVE = 1       // 宽松
        const val MODE_NORMAL = 2           // 正常
        const val MODE_QUALITY = 3          // 最严格
        
        // 支持的采样率
        const val SAMPLE_RATE_8KHZ = 8000
        const val SAMPLE_RATE_16KHZ = 16000
        const val SAMPLE_RATE_32KHZ = 32000
        const val SAMPLE_RATE_48KHZ = 48000
        
        // 帧长度 (ms)
        const val FRAME_LENGTH_10MS = 10
        const val FRAME_LENGTH_20MS = 20
        const val FRAME_LENGTH_30MS = 30
    }
    
    // WebRTC风格VAD状态
    private var vadInitialized = false
    private var isInitialized = false
    private var sampleRate = SAMPLE_RATE_16KHZ
    private var frameLengthMs = FRAME_LENGTH_20MS
    private var mode = MODE_QUALITY // 默认使用最严格模式
    
    // 统计信息
    private var totalFrames = 0
    private var voiceFrames = 0
    private var silenceFrames = 0
    
    /**
     * 初始化WebRTC风格VAD
     */
    override fun initialize(): Boolean {
        return try {
            this.sampleRate = SAMPLE_RATE_16KHZ
            this.frameLengthMs = FRAME_LENGTH_20MS
            this.mode = MODE_QUALITY
            
            vadInitialized = true
            isInitialized = true
            
            Log.d(TAG, "WebRTC风格VAD初始化成功 - 采样率: ${sampleRate}Hz, 帧长: ${frameLengthMs}ms, 模式: $mode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC风格VAD初始化异常", e)
            false
        }
    }
    
    /**
     * 自定义初始化方法（支持参数）
     */
    fun initialize(sampleRate: Int = SAMPLE_RATE_16KHZ, 
                  frameLengthMs: Int = FRAME_LENGTH_20MS,
                  mode: Int = MODE_QUALITY): Boolean {
        return try {
            this.sampleRate = sampleRate
            this.frameLengthMs = frameLengthMs
            this.mode = mode
            
            vadInitialized = true
            isInitialized = true
            
            Log.d(TAG, "WebRTC风格VAD初始化成功 - 采样率: ${sampleRate}Hz, 帧长: ${frameLengthMs}ms, 模式: $mode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC风格VAD初始化异常", e)
            false
        }
    }
    
    /**
     * 处理音频数据
     * @param audioData 音频数据 (16位PCM)
     * @return VADResult 检测结果
     */
    override fun processAudioData(audioData: ByteArray): VADResult {
        if (!isInitialized) {
            return VADResult(
                isVoiceActive = false,
                voiceDetected = false,
                analysis = AudioAnalysis(
                    maxAmplitude = 0,
                    avgAmplitude = 0.0,
                    rms = 0.0,
                    voiceRatio = 0.0,
                    isVoiceDetected = false,
                    sampleCount = 0,
                    durationMs = 0.0
                ),
                confidence = 0f
            )
        }
        
        try {
            // 创建音频分析结果
            val analysis = analyzeAudioData(audioData)
            
            // 使用WebRTC风格的VAD算法
            val voiceDetected = webRTCVADProcess(analysis)
            
            // 更新统计信息
            totalFrames++
            if (voiceDetected) {
                voiceFrames++
            } else {
                silenceFrames++
            }
            
            // 计算置信度
            val confidence = calculateConfidence(audioData, voiceDetected)
            
            return VADResult(
                isVoiceActive = voiceDetected,
                voiceDetected = voiceDetected,
                analysis = analysis,
                confidence = confidence
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC风格VAD处理音频数据失败", e)
            return VADResult(
                isVoiceActive = false,
                voiceDetected = false,
                analysis = AudioAnalysis(
                    maxAmplitude = 0,
                    avgAmplitude = 0.0,
                    rms = 0.0,
                    voiceRatio = 0.0,
                    isVoiceDetected = false,
                    sampleCount = 0,
                    durationMs = 0.0
                ),
                confidence = 0f
            )
        }
    }
    
    /**
     * WebRTC风格VAD处理算法
     */
    private fun webRTCVADProcess(analysis: AudioAnalysis): Boolean {
        // 基于模式的阈值调整
        val (energyThreshold, spectralThreshold, ratioThreshold) = when (mode) {
            MODE_VERY_AGGRESSIVE -> Triple(50.0, 0.3, 0.01)    // 最宽松
            MODE_AGGRESSIVE -> Triple(100.0, 0.4, 0.015)       // 宽松
            MODE_NORMAL -> Triple(150.0, 0.5, 0.02)            // 正常
            MODE_QUALITY -> Triple(200.0, 0.6, 0.025)          // 最严格
            else -> Triple(150.0, 0.5, 0.02)
        }
        
        // 能量检测
        val energyDetected = analysis.rms > energyThreshold
        
        // 频谱平坦度检测（简化）
        val spectralFlatness = analysis.avgAmplitude / (analysis.maxAmplitude + 1.0)
        val spectralDetected = spectralFlatness > spectralThreshold
        
        // 语音比例检测
        val ratioDetected = analysis.voiceRatio > ratioThreshold
        
        // WebRTC风格综合判断
        return when (mode) {
            MODE_VERY_AGGRESSIVE -> energyDetected || ratioDetected
            MODE_AGGRESSIVE -> energyDetected && (spectralDetected || ratioDetected)
            MODE_NORMAL -> energyDetected && spectralDetected && ratioDetected
            MODE_QUALITY -> energyDetected && spectralDetected && ratioDetected && 
                           analysis.maxAmplitude > 500
            else -> energyDetected && ratioDetected
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
        
        // 转换为16位整数
        val samples = ShortArray(audioData.size / 2)
        val buffer = java.nio.ByteBuffer.wrap(audioData)
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().get(samples)
        
        // 计算统计信息
        val maxAmplitude = samples.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
        val avgAmplitude = samples.map { kotlin.math.abs(it.toInt()) }.average()
        val rms = kotlin.math.sqrt(samples.map { it.toDouble() * it.toDouble() }.average())
        
        // 计算语音比例
        val nonSilentSamples = samples.count { kotlin.math.abs(it.toInt()) > 100 }
        val voiceRatio = nonSilentSamples.toDouble() / samples.size
        
        return AudioAnalysis(
            maxAmplitude = maxAmplitude,
            avgAmplitude = avgAmplitude,
            rms = rms,
            voiceRatio = voiceRatio,
            isVoiceDetected = false, // 由WebRTC VAD决定
            sampleCount = samples.size,
            durationMs = samples.size.toDouble() / sampleRate * 1000
        )
    }
    
    /**
     * 计算置信度
     */
    private fun calculateConfidence(audioData: ByteArray, voiceDetected: Boolean): Float {
        if (totalFrames == 0) return 0f
        
        // 基于历史统计的置信度
        val voiceRatio = voiceFrames.toFloat() / totalFrames
        
        // 基于音频能量的置信度
        val energy = audioData.map { kotlin.math.abs(it.toInt()) }.average()
        val energyConfidence = minOf(1f, (energy / 1000f).toFloat())
        
        // 综合置信度
        return if (voiceDetected) {
            (voiceRatio + energyConfidence) / 2f
        } else {
            (1f - voiceRatio + (1f - energyConfidence)) / 2f
        }
    }
    
    /**
     * 重置VAD状态
     */
    override fun reset() {
        totalFrames = 0
        voiceFrames = 0
        silenceFrames = 0
        Log.d(TAG, "WebRTC风格VAD已重置")
    }
    
    /**
     * 释放资源
     */
    override fun release() {
        try {
            vadInitialized = false
            isInitialized = false
            Log.d(TAG, "WebRTC风格VAD已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放WebRTC风格VAD失败", e)
        }
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): WebRTCVADStatistics {
        return WebRTCVADStatistics(
            isInitialized = isInitialized,
            sampleRate = sampleRate,
            frameLengthMs = frameLengthMs,
            mode = mode,
            totalFrames = totalFrames,
            voiceFrames = voiceFrames,
            silenceFrames = silenceFrames,
            voiceRatio = if (totalFrames > 0) voiceFrames.toFloat() / totalFrames else 0f
        )
    }
    
    /**
     * 检查是否已初始化
     */
    override fun isInitialized(): Boolean = isInitialized
}

/**
 * WebRTC VAD统计信息
 */
data class WebRTCVADStatistics(
    val isInitialized: Boolean,
    val sampleRate: Int,
    val frameLengthMs: Int,
    val mode: Int,
    val totalFrames: Int,
    val voiceFrames: Int,
    val silenceFrames: Int,
    val voiceRatio: Float
)
