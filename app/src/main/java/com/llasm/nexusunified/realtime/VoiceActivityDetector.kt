package com.llasm.nexusunified.realtime

import android.util.Log
import kotlin.math.*

/**
 * 语音活动检测器 (Voice Activity Detection)
 * 实现实时语音检测，支持多种检测算法
 */
class VoiceActivityDetector {
    companion object {
        private const val TAG = "VoiceActivityDetector"
        
        // 基础检测阈值 - 最稳定可靠的配置
        private const val DEFAULT_RMS_THRESHOLD = 200.0  // 进一步提高RMS阈值，确保稳定
        private const val DEFAULT_AMPLITUDE_THRESHOLD = 1500  // 进一步提高振幅阈值
        private const val DEFAULT_SILENCE_THRESHOLD = 400  // 进一步提高静音阈值
        private const val DEFAULT_VOICE_RATIO_THRESHOLD = 0.025  // 进一步提高语音比例阈值
        
        // 高级检测参数 - 最稳定的检测配置
        private const val MIN_VOICE_DURATION_MS = 800L      // 最小语音持续时间（进一步增加）
        private const val MAX_SILENCE_DURATION_MS = 3000L   // 最大静音持续时间（进一步增加）
        private const val VOICE_START_DELAY_MS = 500L       // 语音开始延迟（进一步增加）
        private const val VOICE_END_DELAY_MS = 1500L        // 语音结束延迟（进一步增加）
        
        // 滑动窗口参数
        private const val WINDOW_SIZE_MS = 100L             // 分析窗口大小
        private const val OVERLAP_MS = 50L                  // 窗口重叠
    }
    
    // 检测状态
    private var isVoiceActive = false
    private var voiceStartTime = 0L
    private var lastVoiceTime = 0L
    private var silenceStartTime = 0L
    
    // 保活机制检测
    private var keepAliveSilenceCount = 0
    private var lastValidVoiceTime = 0L
    private val maxKeepAliveSilenceCount = 10
    
    // 音频缓冲区
    private val audioBuffer = mutableListOf<ByteArray>()
    private val sampleRate = 16000
    private val windowSize = (sampleRate * WINDOW_SIZE_MS / 1000).toInt()
    private val overlapSize = (sampleRate * OVERLAP_MS / 1000).toInt()
    
    // 检测参数
    private var rmsThreshold = DEFAULT_RMS_THRESHOLD
    private var amplitudeThreshold = DEFAULT_AMPLITUDE_THRESHOLD
    private var silenceThreshold = DEFAULT_SILENCE_THRESHOLD
    private var voiceRatioThreshold = DEFAULT_VOICE_RATIO_THRESHOLD
    
    // 自适应阈值
    private val rmsHistory = mutableListOf<Double>()
    private val amplitudeHistory = mutableListOf<Int>()
    private val maxHistorySize = 50
    
    // 回调接口
    private var onVoiceStart: (() -> Unit)? = null
    private var onVoiceEnd: (() -> Unit)? = null
    private var onVoiceActivity: ((Boolean) -> Unit)? = null
    
    /**
     * 设置语音开始回调
     */
    fun setOnVoiceStart(callback: () -> Unit) {
        onVoiceStart = callback
    }
    
    /**
     * 设置语音结束回调
     */
    fun setOnVoiceEnd(callback: () -> Unit) {
        onVoiceEnd = callback
    }
    
    /**
     * 设置语音活动状态回调
     */
    fun setOnVoiceActivity(callback: (Boolean) -> Unit) {
        onVoiceActivity = callback
    }
    
    /**
     * 设置检测阈值
     */
    fun setThresholds(
        rms: Double = DEFAULT_RMS_THRESHOLD,
        amplitude: Int = DEFAULT_AMPLITUDE_THRESHOLD,
        silence: Int = DEFAULT_SILENCE_THRESHOLD,
        voiceRatio: Double = DEFAULT_VOICE_RATIO_THRESHOLD
    ) {
        rmsThreshold = rms
        amplitudeThreshold = amplitude
        silenceThreshold = silence
        voiceRatioThreshold = voiceRatio
        Log.d(TAG, "VAD阈值更新 - RMS: $rms, 振幅: $amplitude, 静音: $silence, 比例: $voiceRatio")
    }
    
    /**
     * 处理音频数据
     */
    fun processAudioData(audioData: ByteArray): VADResult {
        val currentTime = System.currentTimeMillis()
        
        // 添加到缓冲区
        audioBuffer.add(audioData)
        
        // 保持缓冲区大小合理
        if (audioBuffer.size > 100) {
            audioBuffer.removeAt(0)
        }
        
        // 分析当前音频块
        val analysis = analyzeAudioChunk(audioData)
        
        // 更新历史记录
        updateHistory(analysis)
        
        // 自适应阈值调整
        adjustThresholds()
        
        // 检测语音活动
        val voiceDetected = detectVoiceActivity(analysis)
        
        // 更新状态
        updateVoiceState(voiceDetected, currentTime)
        
        return VADResult(
            isVoiceActive = isVoiceActive,
            voiceDetected = voiceDetected,
            analysis = analysis,
            confidence = calculateConfidence(analysis)
        )
    }
    
    /**
     * 分析音频块
     */
    private fun analyzeAudioChunk(audioData: ByteArray): AudioAnalysis {
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
        
        // 验证音频数据长度
        if (audioData.size % 2 != 0) {
            Log.w(TAG, "音频数据长度不是偶数")
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
        val maxAmplitude = samples.maxOfOrNull { abs(it.toInt()) } ?: 0
        val avgAmplitude = samples.map { abs(it.toInt()) }.average()
        val rms = sqrt(samples.map { it.toDouble() * it.toDouble() }.average())
        
        // 计算语音比例
        val nonSilentSamples = samples.count { abs(it.toInt()) > silenceThreshold }
        val voiceRatio = nonSilentSamples.toDouble() / samples.size
        
        // 计算频谱特征
        val spectralCentroid = calculateSpectralCentroid(samples)
        val zeroCrossingRate = calculateZeroCrossingRate(samples)
        
        // 综合语音检测
        val isVoiceDetected = detectVoice(analysis = AudioAnalysis(
            maxAmplitude = maxAmplitude,
            avgAmplitude = avgAmplitude,
            rms = rms,
            voiceRatio = voiceRatio,
            isVoiceDetected = false,
            sampleCount = samples.size,
            durationMs = samples.size.toDouble() / sampleRate * 1000
        ), spectralCentroid = spectralCentroid, zeroCrossingRate = zeroCrossingRate)
        
        return AudioAnalysis(
            maxAmplitude = maxAmplitude,
            avgAmplitude = avgAmplitude,
            rms = rms,
            voiceRatio = voiceRatio,
            isVoiceDetected = isVoiceDetected,
            sampleCount = samples.size,
            durationMs = samples.size.toDouble() / sampleRate * 1000
        )
    }
    
    /**
     * 计算频谱重心
     */
    private fun calculateSpectralCentroid(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        
        // 简单的频谱重心计算（基于幅度）
        var weightedSum = 0.0
        var magnitudeSum = 0.0
        
        for (i in samples.indices) {
            val magnitude = abs(samples[i].toDouble())
            weightedSum += i * magnitude
            magnitudeSum += magnitude
        }
        
        return if (magnitudeSum > 0) weightedSum / magnitudeSum else 0.0
    }
    
    /**
     * 计算过零率
     */
    private fun calculateZeroCrossingRate(samples: ShortArray): Double {
        if (samples.size < 2) return 0.0
        
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0) != (samples[i - 1] >= 0)) {
                crossings++
            }
        }
        
        return crossings.toDouble() / (samples.size - 1)
    }
    
    /**
     * 检测语音活动
     */
    private fun detectVoice(analysis: AudioAnalysis, spectralCentroid: Double, zeroCrossingRate: Double): Boolean {
        // 基础阈值检测
        val rmsVoice = analysis.rms > rmsThreshold
        val amplitudeVoice = analysis.maxAmplitude > amplitudeThreshold
        val ratioVoice = analysis.voiceRatio > voiceRatioThreshold
        
        // 频谱特征检测
        val spectralVoice = spectralCentroid > 0.1 && spectralCentroid < 0.8
        val zcrVoice = zeroCrossingRate > 0.01 && zeroCrossingRate < 0.3
        
        // 连续非静音段检测
        val consecutiveVoice = detectConsecutiveVoice(analysis)
        
        // 综合判断
        val basicVoice = rmsVoice || amplitudeVoice || ratioVoice
        val advancedVoice = spectralVoice && zcrVoice
        
        return basicVoice && (advancedVoice || consecutiveVoice)
    }
    
    /**
     * 检测连续语音段
     */
    private fun detectConsecutiveVoice(analysis: AudioAnalysis): Boolean {
        // 这里可以实现更复杂的连续语音检测逻辑
        // 目前使用简单的阈值判断
        return analysis.voiceRatio > voiceRatioThreshold * 2
    }
    
    /**
     * 检测语音活动状态 - 优化版本
     */
    private fun detectVoiceActivity(analysis: AudioAnalysis): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // 检测保活机制的静音数据
        if (isKeepAliveSilence(analysis)) {
            keepAliveSilenceCount++
            Log.v(TAG, "检测到保活静音数据，计数: $keepAliveSilenceCount")
            return false
        } else {
            keepAliveSilenceCount = 0
        }
        
        // 如果连续检测到太多保活静音，可能需要调整检测策略
        if (keepAliveSilenceCount > maxKeepAliveSilenceCount) {
            Log.w(TAG, "检测到大量保活静音数据，可能需要调整检测策略")
            keepAliveSilenceCount = 0
        }
        
        // 多级检测策略
        val basicVoice = detectBasicVoice(analysis)
        val advancedVoice = detectAdvancedVoice(analysis)
        val contextualVoice = detectContextualVoice(analysis, currentTime)
        
        // 综合判断：需要满足基础条件，并且有高级特征或上下文支持
        val voiceDetected = basicVoice && (advancedVoice || contextualVoice)
        
        // 更新最后有效语音时间
        if (voiceDetected) {
            lastValidVoiceTime = currentTime
        }
        
        return voiceDetected
    }
    
    /**
     * 基础语音检测 - 最严格模式
     */
    private fun detectBasicVoice(analysis: AudioAnalysis): Boolean {
        val rmsVoice = analysis.rms > rmsThreshold
        val amplitudeVoice = analysis.maxAmplitude > amplitudeThreshold
        val ratioVoice = analysis.voiceRatio > voiceRatioThreshold
        
        // 最严格的检测：必须同时满足所有条件
        val strictCondition = rmsVoice && amplitudeVoice && ratioVoice
        
        // 超高比例条件：需要极高的语音比例和振幅
        val ultraHighRatioCondition = analysis.voiceRatio > voiceRatioThreshold * 4 && 
                                    analysis.maxAmplitude > amplitudeThreshold * 1.2 &&
                                    analysis.rms > rmsThreshold * 1.1
        
        return strictCondition || ultraHighRatioCondition
    }
    
    /**
     * 高级语音检测
     */
    private fun detectAdvancedVoice(analysis: AudioAnalysis): Boolean {
        // 连续语音段检测
        val consecutiveVoice = detectConsecutiveVoice(analysis)
        
        // 频谱特征检测
        val spectralVoice = detectSpectralFeatures(analysis)
        
        // 能量分布检测
        val energyVoice = detectEnergyDistribution(analysis)
        
        return consecutiveVoice || spectralVoice || energyVoice
    }
    
    /**
     * 上下文语音检测 - 保守模式
     */
    private fun detectContextualVoice(analysis: AudioAnalysis, currentTime: Long): Boolean {
        // 如果最近有语音活动，适度降低检测阈值
        val timeSinceLastVoice = currentTime - lastValidVoiceTime
        val isRecentContext = timeSinceLastVoice < 1500 // 1.5秒内有语音（进一步缩短时间）
        
        if (isRecentContext) {
            // 在语音上下文中，使用仍然严格的检测条件
            val relaxedRms = analysis.rms > rmsThreshold * 0.9  // 进一步提高阈值
            val relaxedAmplitude = analysis.maxAmplitude > amplitudeThreshold * 0.9  // 进一步提高阈值
            val relaxedRatio = analysis.voiceRatio > voiceRatioThreshold * 0.95  // 进一步提高阈值
            
            // 需要同时满足所有条件
            return relaxedRms && relaxedAmplitude && relaxedRatio
        }
        
        return false
    }
    
    /**
     * 能量分布检测
     */
    private fun detectEnergyDistribution(analysis: AudioAnalysis): Boolean {
        // 检测能量是否集中在语音频率范围内
        // 这里使用简化的能量分布检测
        val energyRatio = analysis.rms / (analysis.avgAmplitude + 1.0)
        return energyRatio > 0.3 && energyRatio < 2.0
    }
    
    /**
     * 检测是否为保活机制的静音数据 - 优化版本
     */
    private fun isKeepAliveSilence(analysis: AudioAnalysis): Boolean {
        // 检测特征：
        // 1. RMS值极低但非零（保活机制通常发送微小的非零数据）
        // 2. 振幅分布异常均匀（真实语音有变化，保活数据很规律）
        // 3. 频谱特征异常（保活数据频谱特征单一）
        // 4. 持续时间特征（保活数据通常很规律地出现）
        
        val isVeryLowRms = analysis.rms > 0 && analysis.rms < 20.0
        val isUniformAmplitude = analysis.maxAmplitude < 150 && analysis.avgAmplitude < 30
        val isLowRatio = analysis.voiceRatio < 0.003
        val isShortDuration = analysis.durationMs < 250 // 保活数据通常很短
        
        // 检查振幅变化率（保活数据变化很小）
        val amplitudeVariation = if (analysis.avgAmplitude > 0) {
            (analysis.maxAmplitude - analysis.avgAmplitude) / analysis.avgAmplitude
        } else 0.0
        val isLowVariation = amplitudeVariation < 0.5
        
        // 检查是否在最近时间内有有效语音
        val currentTime = System.currentTimeMillis()
        val timeSinceLastVoice = currentTime - lastValidVoiceTime
        val isRecentSilence = timeSinceLastVoice < 8000 // 8秒内有语音
        
        // 如果同时满足这些条件，很可能是保活机制的静音数据
        val isKeepAlivePattern = isVeryLowRms && isUniformAmplitude && isLowRatio && 
                                isShortDuration && isLowVariation
        
        // 只有在最近有语音的情况下才认为是保活数据
        return isKeepAlivePattern && isRecentSilence
    }
    
    /**
     * 检测频谱特征
     */
    private fun detectSpectralFeatures(analysis: AudioAnalysis): Boolean {
        // 这里可以添加更复杂的频谱分析
        // 目前使用简单的能量分布检测
        return analysis.rms > rmsThreshold * 1.5 && analysis.voiceRatio > voiceRatioThreshold * 2
    }
    
    /**
     * 更新历史记录
     */
    private fun updateHistory(analysis: AudioAnalysis) {
        rmsHistory.add(analysis.rms)
        amplitudeHistory.add(analysis.maxAmplitude)
        
        if (rmsHistory.size > maxHistorySize) {
            rmsHistory.removeAt(0)
        }
        if (amplitudeHistory.size > maxHistorySize) {
            amplitudeHistory.removeAt(0)
        }
    }
    
    /**
     * 自适应调整阈值 - 最保守版本
     */
    private fun adjustThresholds() {
        if (rmsHistory.size < 30) return // 需要更多历史数据
        
        // 基于历史数据调整RMS阈值
        val avgRms = rmsHistory.average()
        val stdRms = sqrt(rmsHistory.map { (it - avgRms) * (it - avgRms) }.average())
        
        // 使用最保守的阈值调整策略
        val adaptiveRmsThreshold = avgRms + stdRms * 0.5
        rmsThreshold = maxOf(DEFAULT_RMS_THRESHOLD * 0.9, minOf(DEFAULT_RMS_THRESHOLD * 2.0, adaptiveRmsThreshold))
        
        // 基于历史数据调整振幅阈值
        val avgAmplitude = amplitudeHistory.average()
        val stdAmplitude = sqrt(amplitudeHistory.map { (it - avgAmplitude) * (it - avgAmplitude) }.average())
        
        val adaptiveAmplitudeThreshold = avgAmplitude + stdAmplitude * 0.5
        amplitudeThreshold = maxOf((DEFAULT_AMPLITUDE_THRESHOLD * 0.9).toInt(), minOf((DEFAULT_AMPLITUDE_THRESHOLD * 2.0).toInt(), adaptiveAmplitudeThreshold.toInt()))
        
        // 动态调整语音比例阈值
        val recentVoiceRatios = rmsHistory.takeLast(15).map { if (it > rmsThreshold) 1.0 else 0.0 }
        val voiceActivityRate = recentVoiceRatios.average()
        
        if (voiceActivityRate > 0.8) {
            // 如果最近语音活动频繁，提高阈值
            voiceRatioThreshold = minOf(DEFAULT_VOICE_RATIO_THRESHOLD * 1.5, 0.03)
        } else if (voiceActivityRate < 0.1) {
            // 如果最近语音活动较少，稍微降低阈值
            voiceRatioThreshold = maxOf(DEFAULT_VOICE_RATIO_THRESHOLD * 0.9, 0.01)
        }
        
        Log.v(TAG, "自适应阈值 - RMS: $rmsThreshold, 振幅: $amplitudeThreshold, 比例: $voiceRatioThreshold")
    }
    
    /**
     * 更新语音状态
     */
    private fun updateVoiceState(voiceDetected: Boolean, currentTime: Long) {
        when {
            voiceDetected && !isVoiceActive -> {
                // 语音开始 - 需要连续检测到语音才确认开始
                if (voiceStartTime == 0L) {
                    voiceStartTime = currentTime
                } else if (currentTime - voiceStartTime > VOICE_START_DELAY_MS) {
                    isVoiceActive = true
                    lastVoiceTime = currentTime
                    silenceStartTime = 0L
                    
                    Log.d(TAG, "语音开始检测")
                    onVoiceStart?.invoke()
                    onVoiceActivity?.invoke(true)
                }
            }
            
            voiceDetected && isVoiceActive -> {
                // 语音持续
                lastVoiceTime = currentTime
                silenceStartTime = 0L
                voiceStartTime = 0L // 重置开始时间
            }
            
            !voiceDetected && isVoiceActive -> {
                // 可能的语音结束
                if (silenceStartTime == 0L) {
                    silenceStartTime = currentTime
                } else if (currentTime - silenceStartTime > VOICE_END_DELAY_MS) {
                    // 确认语音结束
                    val voiceDuration = if (lastVoiceTime > 0) lastVoiceTime - voiceStartTime else 0
                    if (voiceDuration >= MIN_VOICE_DURATION_MS) {
                        Log.d(TAG, "语音结束检测，持续时间: ${voiceDuration}ms")
                        onVoiceEnd?.invoke()
                    }
                    
                    isVoiceActive = false
                    voiceStartTime = 0L
                    lastVoiceTime = 0L
                    silenceStartTime = 0L
                    onVoiceActivity?.invoke(false)
                }
            }
            
            !voiceDetected && !isVoiceActive -> {
                // 重置状态
                voiceStartTime = 0L
                lastVoiceTime = 0L
                silenceStartTime = 0L
            }
        }
    }
    
    /**
     * 计算检测置信度
     */
    private fun calculateConfidence(analysis: AudioAnalysis): Float {
        var confidence = 0f
        
        // RMS置信度
        if (analysis.rms > rmsThreshold) {
            confidence += 0.3f
        }
        
        // 振幅置信度
        if (analysis.maxAmplitude > amplitudeThreshold) {
            confidence += 0.3f
        }
        
        // 语音比例置信度
        if (analysis.voiceRatio > voiceRatioThreshold) {
            confidence += 0.2f
        }
        
        // 持续时间置信度
        if (analysis.durationMs > MIN_VOICE_DURATION_MS) {
            confidence += 0.2f
        }
        
        return minOf(1f, confidence)
    }
    
    /**
     * 重置检测器状态
     */
    fun reset() {
        isVoiceActive = false
        voiceStartTime = 0L
        lastVoiceTime = 0L
        silenceStartTime = 0L
        audioBuffer.clear()
        rmsHistory.clear()
        amplitudeHistory.clear()
        Log.d(TAG, "VAD检测器已重置")
    }
    
    /**
     * 获取当前状态
     */
    fun isVoiceActive(): Boolean = isVoiceActive
    
    /**
     * 获取检测统计信息
     */
    fun getStatistics(): VADStatistics {
        return VADStatistics(
            isVoiceActive = isVoiceActive,
            voiceStartTime = voiceStartTime,
            lastVoiceTime = lastVoiceTime,
            silenceStartTime = silenceStartTime,
            bufferSize = audioBuffer.size,
            avgRms = rmsHistory.average(),
            avgAmplitude = amplitudeHistory.average(),
            rmsThreshold = rmsThreshold,
            amplitudeThreshold = amplitudeThreshold
        )
    }
}

/**
 * VAD检测结果
 */
data class VADResult(
    val isVoiceActive: Boolean,
    val voiceDetected: Boolean,
    val analysis: AudioAnalysis,
    val confidence: Float
)

/**
 * VAD统计信息
 */
data class VADStatistics(
    val isVoiceActive: Boolean,
    val voiceStartTime: Long,
    val lastVoiceTime: Long,
    val silenceStartTime: Long,
    val bufferSize: Int,
    val avgRms: Double,
    val avgAmplitude: Double,
    val rmsThreshold: Double,
    val amplitudeThreshold: Int
)
