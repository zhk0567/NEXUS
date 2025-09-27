package com.llasm.nexusunified.realtime

/**
 * VAD接口
 * 支持多种VAD实现
 */
interface VADInterface {
    /**
     * 初始化VAD
     */
    fun initialize(): Boolean
    
    /**
     * 处理音频数据
     */
    fun processAudioData(audioData: ByteArray): VADResult
    
    /**
     * 重置VAD状态
     */
    fun reset()
    
    /**
     * 释放资源
     */
    fun release()
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean
}

/**
 * VAD类型枚举
 */
enum class VADType {
    WEBRTC,     // WebRTC VAD
    CUSTOM      // 自定义VAD
}

/**
 * VAD工厂类
 */
object VADFactory {
    /**
     * 创建VAD实例
     */
    fun createVAD(type: VADType = VADType.WEBRTC): VADInterface {
        return when (type) {
            VADType.WEBRTC -> WebRTCVAD()
            VADType.CUSTOM -> CustomVAD()
        }
    }
}

/**
 * 自定义VAD适配器
 * 将现有的VoiceActivityDetector适配到VADInterface
 */
class CustomVAD : VADInterface {
    private val voiceDetector = VoiceActivityDetector()
    
    override fun initialize(): Boolean {
        return true // 自定义VAD不需要特殊初始化
    }
    
    override fun processAudioData(audioData: ByteArray): VADResult {
        return voiceDetector.processAudioData(audioData)
    }
    
    override fun reset() {
        voiceDetector.reset()
    }
    
    override fun release() {
        // 自定义VAD不需要特殊释放
    }
    
    override fun isInitialized(): Boolean {
        return true
    }
}
