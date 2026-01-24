package com.llasm.storycontrol.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
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
    private var isPaused = false
    private var playbackJob: Job? = null
    private var progressUpdateJob: Job? = null
    private var onProgressUpdate: (currentPosition: Int, duration: Int) -> Unit = { _, _ -> }
    
    /**
     * 播放预录制的故事音频
     */
    fun playStoryAudio(
        storyId: String,
        storyText: String,
        storyTitle: String? = null,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
        onProgressUpdate: (currentPosition: Int, duration: Int) -> Unit = { _, _ -> }
    ) {
        try {
            Log.d(TAG, "开始播放故事音频: $storyId")
            
            // 保存进度更新回调
            this.onProgressUpdate = onProgressUpdate
            
            if (isPlaying || isPaused) {
                Log.w(TAG, "音频正在播放或暂停中，停止当前播放")
                stopPlayback()
            }
            
            // 从assets加载预录制的音频文件（使用标题）
            val audioData = loadPreRecordedAudio(storyId, storyTitle)
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
                // 设置音频属性，确保音频能正常播放
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                // 设置音量（确保不是静音）
                setVolume(1.0f, 1.0f)
                 setOnPreparedListener { mediaPlayer ->
                     Log.d(TAG, "音频准备完成，开始播放")
                     this@TTSService.isPlaying = true
                     this@TTSService.isPaused = false
                     onPlayStart()
                     start()
                     
                     // 启动进度更新协程
                     startProgressUpdates()
                 }
                 setOnCompletionListener { mediaPlayer ->
                     Log.d(TAG, "音频播放完成")
                     this@TTSService.isPlaying = false
                     this@TTSService.isPaused = false
                     stopProgressUpdates()
                     onPlayComplete()
                     cleanup()
                     // 删除临时文件
                     tempFile.delete()
                 }
                 setOnErrorListener { mediaPlayer, what, extra ->
                     Log.e(TAG, "音频播放错误: what=$what, extra=$extra")
                     this@TTSService.isPlaying = false
                     this@TTSService.isPaused = false
                     stopProgressUpdates()
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
     * 优先使用标题查找，如果失败则使用日期格式
     * 支持多种文件名匹配策略
     */
    private fun loadPreRecordedAudio(storyId: String, storyTitle: String? = null): ByteArray? {
        // 获取所有可用的音频文件列表
        val availableFiles = try {
            context.assets.list("story_audio")?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取音频文件列表失败", e)
            emptyList()
        }
        
        // 策略1: 使用原始标题精确匹配
        if (!storyTitle.isNullOrBlank()) {
            val exactMatch = availableFiles.find { it.equals("$storyTitle.mp3", ignoreCase = true) }
            if (exactMatch != null) {
                return try {
                    val assetPath = "story_audio/$exactMatch"
                    Log.d(TAG, "精确匹配音频文件: $assetPath")
                    context.assets.open(assetPath).readBytes()
                } catch (e: Exception) {
                    Log.w(TAG, "加载精确匹配音频文件失败", e)
                    null
                }
            }
        }
        
        // 策略2: 清理标题后匹配（处理常见特殊字符）
        if (!storyTitle.isNullOrBlank()) {
            val cleanedTitle = storyTitle
                .replace(Regex("[<>:\"/\\\\|?*]"), "_")  // 标准特殊字符
                .replace(Regex("""[：""''""]"""), "_")  // 中文标点符号
                .replace(Regex("--+"), "-")  // 多个破折号合并
                .trim()
            
            val cleanedMatch = availableFiles.find { fileName ->
                val fileNameWithoutExt = fileName.removeSuffix(".mp3")
                fileNameWithoutExt.equals(cleanedTitle, ignoreCase = true) ||
                fileNameWithoutExt.contains(cleanedTitle, ignoreCase = true) ||
                cleanedTitle.contains(fileNameWithoutExt, ignoreCase = true)
            }
            
            if (cleanedMatch != null) {
                return try {
                    val assetPath = "story_audio/$cleanedMatch"
                    Log.d(TAG, "清理后匹配音频文件: $assetPath (清理后标题: $cleanedTitle)")
                    context.assets.open(assetPath).readBytes()
                } catch (e: Exception) {
                    Log.w(TAG, "加载清理后匹配音频文件失败", e)
                    null
                }
            }
        }
        
        // 策略3: 模糊匹配（包含关系）
        if (!storyTitle.isNullOrBlank()) {
            val fuzzyMatch = availableFiles.find { fileName ->
                val fileNameWithoutExt = fileName.removeSuffix(".mp3")
                // 检查标题是否包含文件名的主要部分，或文件名包含标题的主要部分
                val titleWords = storyTitle.split(Regex("""[\s\-：：""''""]""")).filter { word: String -> word.length > 1 }
                titleWords.any { word: String ->
                    fileNameWithoutExt.contains(word, ignoreCase = true) ||
                    word.contains(fileNameWithoutExt.take(5), ignoreCase = true)
                }
            }
            
            if (fuzzyMatch != null) {
                return try {
                    val assetPath = "story_audio/$fuzzyMatch"
                    Log.d(TAG, "模糊匹配音频文件: $assetPath (原始标题: $storyTitle)")
                    context.assets.open(assetPath).readBytes()
                } catch (e: Exception) {
                    Log.w(TAG, "加载模糊匹配音频文件失败", e)
                    null
                }
            }
        }
        
        // 策略4: 使用storyId匹配（日期格式）
        if (storyId.startsWith("2025-01-")) {
            val dateId = storyId.replace("2025-01-", "2024-01-")
            val dateMatch = availableFiles.find { it.startsWith(dateId, ignoreCase = true) }
            if (dateMatch != null) {
                return try {
                    val assetPath = "story_audio/$dateMatch"
                    Log.d(TAG, "日期格式匹配音频文件: $assetPath")
                    context.assets.open(assetPath).readBytes()
                } catch (e: Exception) {
                    Log.w(TAG, "加载日期格式匹配音频文件失败", e)
                    null
                }
            }
        }
        
        // 策略5: 直接使用storyId
        val storyIdMatch = availableFiles.find { it.startsWith(storyId, ignoreCase = true) }
        if (storyIdMatch != null) {
            return try {
                val assetPath = "story_audio/$storyIdMatch"
                Log.d(TAG, "StoryID匹配音频文件: $assetPath")
                context.assets.open(assetPath).readBytes()
            } catch (e: Exception) {
                Log.w(TAG, "加载StoryID匹配音频文件失败", e)
                null
            }
        }
        
        Log.e(TAG, "无法找到匹配的音频文件: storyId=$storyId, title=$storyTitle")
        Log.d(TAG, "可用音频文件列表: ${availableFiles.take(10).joinToString(", ")}")
        return null
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
     * 启动进度更新协程
     */
    private fun startProgressUpdates() {
        stopProgressUpdates() // 先停止之前的更新
        progressUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isPlaying && !isPaused) {
                try {
                    val currentPosition = mediaPlayer?.currentPosition ?: 0
                    val duration = mediaPlayer?.duration ?: 0
                    onProgressUpdate(currentPosition, duration)
                    delay(100) // 每100ms更新一次
                } catch (e: Exception) {
                    Log.e(TAG, "进度更新失败", e)
                    break
                }
            }
        }
    }
    
    /**
     * 停止进度更新协程
     */
    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }
    
    /**
     * 暂停播放
     */
    fun pausePlayback() {
        try {
            if (isPlaying && !isPaused) {
                Log.d(TAG, "暂停音频播放")
                mediaPlayer?.pause()
                isPaused = true
                stopProgressUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停播放失败", e)
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumePlayback() {
        try {
            if (isPaused) {
                Log.d(TAG, "恢复音频播放")
                mediaPlayer?.start()
                isPaused = false
                startProgressUpdates()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复播放失败", e)
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        try {
            playbackJob?.cancel()
            playbackJob = null
            stopProgressUpdates()
            
            if (isPlaying || isPaused) {
                Log.d(TAG, "停止音频播放")
                mediaPlayer?.stop()
                isPlaying = false
                isPaused = false
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
     * 检查是否暂停
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * 检查预录制音频文件是否存在
     */
    fun isAudioFileExists(storyId: String, storyTitle: String? = null): Boolean {
        return loadPreRecordedAudio(storyId, storyTitle) != null
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
    
    /**
     * 播放预录制的故事音频（支持阅读进度管理）
     */
    fun playStoryAudioWithProgress(
        storyId: String,
        storyText: String,
        storyTitle: String? = null,
        onPlayStart: () -> Unit = {},
        onPlayComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
        onProgressUpdate: (currentPosition: Int, duration: Int) -> Unit = { _, _ -> }
    ) {
        playStoryAudio(storyId, storyText, storyTitle, onPlayStart, onPlayComplete, onError, onProgressUpdate)
    }
    
    /**
     * 获取音频时长（不播放，仅获取时长信息）
     */
    fun getAudioDuration(storyId: String, storyTitle: String? = null, onDurationReady: (Int) -> Unit) {
        try {
            Log.d(TAG, "获取音频时长: $storyId (标题: $storyTitle)")
            
            // 从assets加载预录制的音频文件
            val audioData = loadPreRecordedAudio(storyId, storyTitle)
            if (audioData == null) {
                Log.e(TAG, "预录制音频文件不存在: $storyId")
                onDurationReady(0)
                return
            }
            
            // 检查是否是有效的音频文件
            if (audioData.size < 1000) {
                Log.e(TAG, "音频文件太小，可能是占位符文件: $storyId")
                onDurationReady(0)
                return
            }
            
            // 保存到临时文件并获取时长
            val tempFile = saveAudioToTempFile(audioData, storyId)
            if (tempFile == null) {
                Log.e(TAG, "保存临时音频文件失败: $storyId")
                onDurationReady(0)
                return
            }
            
            // 创建MediaPlayer仅用于获取时长
            val tempMediaPlayer = MediaPlayer().apply {
                // 设置音频属性
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener { mediaPlayer ->
                    val duration = mediaPlayer.duration
                    Log.d(TAG, "音频时长获取成功: ${duration}ms")
                    onDurationReady(duration)
                    // 清理资源
                    mediaPlayer.release()
                    tempFile.delete()
                }
                setOnErrorListener { mediaPlayer, what, extra ->
                    Log.e(TAG, "获取音频时长失败: what=$what, extra=$extra")
                    onDurationReady(0)
                    mediaPlayer.release()
                    tempFile.delete()
                    true
                }
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取音频时长失败", e)
            onDurationReady(0)
        }
    }
}