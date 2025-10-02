package com.llasm.storycontrol.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

class TTSService(private val context: Context) {
    
    companion object {
        private const val TAG = "StoryTTSService"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var playbackJob: Job? = null
    
    /**
     * 播放预录制的故事音频
     */
    fun playStoryAudio(
        storyId: String,
        storyText: String,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            Log.d(TAG, "开始播放故事音频: $storyId")
            
            if (isPlaying) {
                Log.w(TAG, "音频正在播放中，停止当前播放")
                stopPlayback()
            }
            
            // 从assets加载预录制的音频文件
            val audioData = loadPreRecordedAudio(storyId)
            if (audioData == null) {
                Log.e(TAG, "预录制音频文件不存在: $storyId")
                onError("音频文件不存在，请检查预录制状态")
                return
            }
            
            // 检查是否是有效的音频文件（简单检查文件大小）
            if (audioData.size < 1000) { // 小于1KB可能是占位符文件
                Log.e(TAG, "音频文件太小，可能是占位符文件: $storyId")
                onError("音频文件无效，请使用预录制的真实音频文件")
                return
            }
            
            // 保存到临时文件并播放
            val tempFile = saveAudioToTempFile(audioData, storyId)
            if (tempFile == null) {
                Log.e(TAG, "保存临时音频文件失败: $storyId")
                onError("保存音频文件失败")
                return
            }
            
            // 创建MediaPlayer并播放
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
                    // 删除临时文件
                    tempFile.delete()
                }
                setOnErrorListener { mediaPlayer, what, extra ->
                    Log.e(TAG, "音频播放错误: what=$what, extra=$extra")
                    this@TTSService.isPlaying = false
                    onError("音频播放失败，请检查音频文件格式")
                    cleanup()
                    // 删除临时文件
                    tempFile.delete()
                    true
                }
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "播放故事音频失败", e)
            isPlaying = false
            onError("播放失败: ${e.message}")
        }
    }
    
    /**
     * 从assets加载预录制的音频文件
     */
    private fun loadPreRecordedAudio(storyId: String): ByteArray? {
        return try {
            val assetPath = "story_audio/$storyId.mp3"
            Log.d(TAG, "尝试加载预录制音频文件: $assetPath")
            context.assets.open(assetPath).readBytes()
        } catch (e: Exception) {
            Log.w(TAG, "加载预录制音频文件失败: $storyId", e)
            null
        }
    }
    
    /**
     * 保存音频数据到临时文件
     */
    private fun saveAudioToTempFile(audioData: ByteArray, storyId: String): File? {
        return try {
            val tempFile = File.createTempFile("story_audio_$storyId", ".mp3", context.cacheDir)
            tempFile.writeBytes(audioData)
            Log.d(TAG, "音频文件保存成功: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "保存音频文件失败", e)
            null
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        try {
            playbackJob?.cancel()
            playbackJob = null
            
            if (isPlaying) {
                Log.d(TAG, "停止音频播放")
                mediaPlayer?.stop()
                isPlaying = false
            }
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
     * 检查预录制音频文件是否存在
     */
    fun isAudioFileExists(storyId: String): Boolean {
        return try {
            val assetPath = "story_audio/$storyId.mp3"
            context.assets.open(assetPath).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取所有可用的音频文件列表
     */
    fun getAllAvailableAudioFiles(): List<String> {
        return try {
            val audioFiles = mutableListOf<String>()
            val assetFiles = context.assets.list("story_audio")
            assetFiles?.forEach { fileName ->
                if (fileName.endsWith(".mp3")) {
                    audioFiles.add(fileName.removeSuffix(".mp3"))
                }
            }
            audioFiles
        } catch (e: Exception) {
            Log.e(TAG, "获取音频文件列表失败", e)
            emptyList()
        }
    }
}