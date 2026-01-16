package com.llasm.storycontrol.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.llasm.storycontrol.network.StoryApiService
import com.llasm.storycontrol.network.*
import com.llasm.storycontrol.config.ServerConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * 阅读进度管理器
 * 负责管理本地阅读进度状态和与后端同步
 */
class ReadingProgressManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "ReadingProgressManager"
        private const val PREFS_NAME = "reading_progress_prefs"
        private const val KEY_CURRENT_SESSION_ID = "current_session_id"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        @Volatile
        private var INSTANCE: ReadingProgressManager? = null
        
        fun getInstance(context: Context): ReadingProgressManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadingProgressManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // 初始化UserManager
        UserManager.init(context)
        android.util.Log.e("ReadingProgressManager", "ReadingProgressManager初始化完成，UserManager已初始化")
    }
    
    // 当前阅读会话
    private val _currentSession = MutableStateFlow<ReadingSession?>(null)
    val currentSession: StateFlow<ReadingSession?> = _currentSession.asStateFlow()
    
    // 阅读进度列表
    private val _readingProgress = MutableStateFlow<List<ReadingProgress>>(emptyList())
    val readingProgress: StateFlow<List<ReadingProgress>> = _readingProgress.asStateFlow()
    
    // 阅读统计
    private val _readingStatistics = MutableStateFlow<ReadingStatistics?>(null)
    val readingStatistics: StateFlow<ReadingStatistics?> = _readingStatistics.asStateFlow()
    
    // 同步状态
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    // 本地缓存
    private val localProgressCache = ConcurrentHashMap<String, ReadingProgress>()
    
    /**
     * 开始阅读会话
     */
    suspend fun startReadingSession(
        userId: String,
        storyId: String,
        storyTitle: String,
        deviceInfo: String = ""
    ): Result<ReadingSession> = withContext(Dispatchers.IO) {
        try {
            _isSyncing.value = true
            
            val result = StoryApiService.startReadingSession(
                userId = userId,
                storyId = storyId,
                storyTitle = storyTitle,
                deviceInfo = deviceInfo
            )
            
            when (result) {
                is ApiResult.Success -> {
                    val session = ReadingSession(
                        sessionId = result.data.sessionId,
                        userId = userId,
                        storyId = storyId,
                        storyTitle = storyTitle,
            startTime = System.currentTimeMillis(),
                        isActive = true
                    )
                    
                    _currentSession.value = session
                    saveCurrentSessionId(session.sessionId)
                    
                    // 记录开始阅读交互
                    logStoryInteraction(
                        userId = userId,
                        storyId = storyId,
                        interactionType = "start_reading",
                        sessionId = session.sessionId,
                        deviceInfo = deviceInfo
                    )
                    
                    Log.d(TAG, "阅读会话开始成功: ${session.sessionId}")
                    Result.success(session)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "开始阅读会话失败: ${result.message}")
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开始阅读会话异常", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * 结束阅读会话
     */
    suspend fun endReadingSession(charactersRead: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = _currentSession.value ?: return@withContext Result.failure(Exception("没有活跃的阅读会话"))
            
            _isSyncing.value = true
            
            val result = StoryApiService.endReadingSession(
                sessionId = session.sessionId,
                charactersRead = charactersRead
            )
            
            when (result) {
                is ApiResult.Success -> {
                    // 记录结束阅读交互
                    logStoryInteraction(
                        userId = session.userId,
                        storyId = session.storyId,
                        interactionType = "complete_reading",
                        sessionId = session.sessionId
                    )
                    
                    _currentSession.value = null
                    clearCurrentSessionId()
                    
                    Log.d(TAG, "阅读会话结束成功: ${session.sessionId}")
                    Result.success(Unit)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "结束阅读会话失败: ${result.message}")
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "结束阅读会话异常", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * 更新阅读进度
     */
    suspend fun updateReadingProgress(
        userId: String,
        storyId: String,
        storyTitle: String,
        currentPosition: Int,
        totalLength: Int,
        deviceInfo: String = ""
    ): Result<ReadingProgressResponse> = withContext(Dispatchers.IO) {
        try {
            val session = _currentSession.value
            
            _isSyncing.value = true
            
            val result = StoryApiService.updateReadingProgress(
                userId = userId,
                storyId = storyId,
                storyTitle = storyTitle,
                currentPosition = currentPosition,
                totalLength = totalLength,
                sessionId = session?.sessionId,
                deviceInfo = deviceInfo
            )
            
            when (result) {
                is ApiResult.Success -> {
                    // 更新本地缓存
                    val progress = ReadingProgress(
                        storyId = storyId,
                        storyTitle = storyTitle,
                        currentPosition = currentPosition,
                        totalLength = totalLength,
                        readingProgress = result.data.progressPercentage.toFloat(),
                        isCompleted = result.data.isCompleted,
                        startTime = null,
                        lastReadTime = null,
                        completionTime = null,
                        readingDurationSeconds = 0
                    )
                    localProgressCache[storyId] = progress
                    
                    Log.d(TAG, "阅读进度更新成功: ${result.data.progressPercentage}%")
                    Result.success(result.data)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "更新阅读进度失败: ${result.message}")
                    Result.failure(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新阅读进度异常", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * 获取阅读进度
     */
    suspend fun getReadingProgress(
        userId: String,
        storyId: String? = null
    ): Result<List<ReadingProgress>> = withContext(Dispatchers.IO) {
        try {
            _isSyncing.value = true
            
            // 验证用户ID
            if (userId == "anonymous_user" || userId.isEmpty()) {
                Log.e(TAG, "获取阅读进度失败: 用户未登录")
                return@withContext Result.failure(Exception("用户未登录，请先登录"))
            }
            
            val result = StoryApiService.getReadingProgress(userId, storyId)
            
            when (result) {
                is ApiResult.Success -> {
                    // 转换网络响应为本地数据类
                    val localProgressList = result.data.progress.map { networkProgress ->
                        ReadingProgress(
                            storyId = networkProgress.storyId,
                            storyTitle = networkProgress.storyTitle,
                            currentPosition = networkProgress.currentPosition,
                            totalLength = networkProgress.totalLength,
                            readingProgress = networkProgress.readingProgress.toFloat(),
                            isCompleted = networkProgress.isCompleted,
                            startTime = networkProgress.startTime?.toLongOrNull(),
                            lastReadTime = networkProgress.lastReadTime?.toLongOrNull(),
                            completionTime = networkProgress.completionTime?.toLongOrNull(),
                            readingDurationSeconds = networkProgress.readingDurationSeconds.toLong()
                        )
                    }
                    
                    _readingProgress.value = localProgressList
                    
                    // 更新本地缓存
                    localProgressList.forEach { progress ->
                        localProgressCache[progress.storyId] = progress
                    }
                    
                    Log.d(TAG, "获取阅读进度成功: ${result.data.count} 条记录")
                    Result.success(localProgressList)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "获取阅读进度失败: ${result.message}")
                    // 如果是身份验证失败，提供更友好的错误信息
                    val errorMessage = if (result.message.contains("用户身份验证失败")) {
                        "用户身份验证失败，请重新登录"
                    } else {
                        result.message
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取阅读进度异常", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * 获取阅读统计
     */
    suspend fun getReadingStatistics(
        userId: String,
        days: Int = 30
    ): Result<ReadingStatistics> = withContext(Dispatchers.IO) {
        try {
            _isSyncing.value = true
            
            // 验证用户ID
            if (userId == "anonymous_user" || userId.isEmpty()) {
                Log.e(TAG, "获取阅读统计失败: 用户未登录")
                return@withContext Result.failure(Exception("用户未登录，请先登录"))
            }
            
            val result = StoryApiService.getReadingStatistics(userId, days)
            
            when (result) {
                is ApiResult.Success -> {
                    _readingStatistics.value = result.data.statistics
                    Log.d(TAG, "获取阅读统计成功")
                    Result.success(result.data.statistics)
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "获取阅读统计失败: ${result.message}")
                    // 如果是身份验证失败，提供更友好的错误信息
                    val errorMessage = if (result.message.contains("用户身份验证失败")) {
                        "用户身份验证失败，请重新登录"
                    } else {
                        result.message
                    }
                    Result.failure(Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取阅读统计异常", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
    
    /**
     * 记录故事交互
     */
    private suspend fun logStoryInteraction(
        userId: String,
        storyId: String,
        interactionType: String,
        sessionId: String? = null,
        deviceInfo: String = "",
        interactionData: Map<String, Any>? = null
    ) {
        try {
            android.util.Log.d(TAG, "发送交互记录到服务器: userId=$userId, storyId=$storyId, type=$interactionType")
            val result = StoryApiService.logStoryInteraction(
                userId = userId,
                storyId = storyId,
                interactionType = interactionType,
                interactionData = interactionData,
                sessionId = sessionId,
                deviceInfo = deviceInfo
            )
            
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d(TAG, "记录故事交互成功: $interactionType")
                }
                is ApiResult.Error -> {
                    android.util.Log.e(TAG, "记录故事交互失败: ${result.message}, type=$interactionType, userId=$userId")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "记录故事交互异常: type=$interactionType", e)
        }
    }
    
    /**
     * 记录故事交互
     */
    suspend fun recordStoryInteraction(
        storyId: String,
        interactionType: String,
        interactionData: Map<String, Any>? = null
    ) {
        val userId = getCurrentUserId()
        // 确保用户ID不为空
        if (userId == null || userId.isEmpty() || userId == "anonymous_user") {
            android.util.Log.w(TAG, "用户未登录，无法记录交互: $interactionType, storyId: $storyId")
            // 即使用户未登录，也尝试记录（服务器端会验证）
            // 但这里先返回，避免记录无效数据
            return
        }
        
        android.util.Log.d(TAG, "记录故事交互: userId=$userId, storyId=$storyId, type=$interactionType")
        val session = _currentSession.value
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = interactionType,
            sessionId = session?.sessionId,
            deviceInfo = "Android",
            interactionData = interactionData
        )
    }
    
    /**
     * 暂停阅读
     */
    suspend fun pauseReading(userId: String, storyId: String) {
        val session = _currentSession.value
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = "pause_reading",
            sessionId = session?.sessionId
        )
    }
    
    /**
     * 恢复阅读
     */
    suspend fun resumeReading(userId: String, storyId: String) {
        val session = _currentSession.value
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = "resume_reading",
            sessionId = session?.sessionId
        )
    }
    
    /**
     * 添加书签
     */
    suspend fun addBookmark(
        userId: String,
        storyId: String,
        position: Int,
        note: String? = null
    ) {
        val session = _currentSession.value
        val interactionData = mapOf(
            "position" to position,
            "note" to (note ?: "")
        )
        
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = "bookmark",
            sessionId = session?.sessionId,
            interactionData = interactionData
        )
    }
    
    /**
     * 分享故事
     */
    suspend fun shareStory(
        userId: String,
        storyId: String,
        shareType: String = "text"
    ) {
        val session = _currentSession.value
        val interactionData = mapOf(
            "share_type" to shareType
        )
        
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = "share",
            sessionId = session?.sessionId,
            interactionData = interactionData
        )
    }
    
    /**
     * 评分故事
     */
    suspend fun rateStory(
        userId: String,
        storyId: String,
        rating: Int,
        comment: String? = null
    ) {
        val session = _currentSession.value
        val interactionData = mapOf(
            "rating" to rating,
            "comment" to (comment ?: "")
        )
        
        logStoryInteraction(
            userId = userId,
            storyId = storyId,
            interactionType = "rate",
            sessionId = session?.sessionId,
            interactionData = interactionData
        )
    }
    
    /**
     * 获取本地缓存的进度
     */
    fun getLocalProgress(storyId: String): ReadingProgress? {
        return localProgressCache[storyId]
    }
    
    /**
     * 保存当前会话ID
     */
    private fun saveCurrentSessionId(sessionId: String) {
        prefs.edit().putString(KEY_CURRENT_SESSION_ID, sessionId).apply()
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return prefs.getString(KEY_CURRENT_SESSION_ID, null)
    }
    
    /**
     * 清除当前会话ID
     */
    private fun clearCurrentSessionId() {
        prefs.edit().remove(KEY_CURRENT_SESSION_ID).apply()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
    
    // ==================== 兼容性方法 ====================
    // 为了保持与现有代码的兼容性，提供一些旧的方法
    
    /**
     * 文本阅读状态（兼容性）
     */
    val textReadingState: StateFlow<TextReadingState> = MutableStateFlow(TextReadingState())
    
    /**
     * 音频阅读状态（兼容性）
     */
    val audioReadingState: StateFlow<AudioReadingState> = MutableStateFlow(AudioReadingState())
    
    /**
     * 开始文本阅读（兼容性）
     */
    suspend fun startTextReading(storyId: String, content: String, audioDurationMs: Long = 0L) {
        // 检查故事是否已经完成，如果已完成则不重新开始阅读
        val isAlreadyCompleted = isStoryCompleted(storyId)
        if (isAlreadyCompleted) {
            android.util.Log.d("ReadingProgressManager", "故事已完成，不重新开始文本阅读: $storyId")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        android.util.Log.d("ReadingProgressManager", "开始文本阅读: $storyId")
        (textReadingState as MutableStateFlow).value = TextReadingState(
            isReading = true,
            currentPosition = 0,
            totalLength = content.length,
            progress = 0f,
            readingDuration = 0L,
            startTime = currentTime,
            lastScrollTime = currentTime,
            isIdle = false,
            minimumReadingTime = 0L
        )
        android.util.Log.d("ReadingProgressManager", "文本阅读状态已设置")
    }
    
    /**
     * 恢复文本阅读（从空闲状态恢复）
     */
    suspend fun resumeTextReading(storyId: String, content: String) {
        val currentState = (textReadingState as MutableStateFlow).value
        if (!currentState.isReading) {
            val currentTime = System.currentTimeMillis()
            android.util.Log.d("ReadingProgressManager", "恢复文本阅读: $storyId")
            (textReadingState as MutableStateFlow).value = currentState.copy(
                isReading = true,
                startTime = if (currentState.startTime == 0L) currentTime else currentState.startTime,
                lastScrollTime = currentTime,
                isIdle = false
            )
        }
    }
    
    /**
     * 更新文本阅读进度（优化版 - 确保进度不会后退）
     * 仅在用户滑动时更新进度，不滑动时不更新
     * 重要：此方法只更新进度，不会自动完成故事
     * 只有用户点击完成按钮才能标记故事为已完成
     */
    suspend fun updateTextReadingProgress(storyId: String, position: Int, totalLength: Int, storyTitle: String = "故事标题", isUserScroll: Boolean = true) {
        // 如果不是用户滚动，直接返回，不更新任何进度
        if (!isUserScroll) {
            return
        }
        
        val currentState = (textReadingState as MutableStateFlow).value
        // 如果阅读状态未激活，尝试恢复阅读状态
        if (!currentState.isReading) {
            android.util.Log.d("ReadingProgressManager", "文本阅读状态未激活，尝试恢复阅读状态")
            resumeTextReading(storyId, "")
            // 如果恢复后仍然未激活，则跳过更新
            val newState = (textReadingState as MutableStateFlow).value
            if (!newState.isReading) {
                android.util.Log.d("ReadingProgressManager", "无法恢复阅读状态，跳过更新")
                return
            }
        }
        
        // 如果已经空闲，但用户正在滚动，则恢复阅读状态
        if (currentState.isIdle && isUserScroll) {
            android.util.Log.d("ReadingProgressManager", "检测到用户滚动，恢复阅读状态")
            resumeTextReading(storyId, "")
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 进度只基于滑动位置，不基于时间
        val positionProgress = if (totalLength > 0) {
            (position.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
        } else 0f
        
        // 进度只基于位置，不依赖时间
        val finalProgress = maxOf(positionProgress, currentState.progress).coerceIn(0f, 1f)
        
        // 更新状态（仅在用户滚动时更新）
            (textReadingState as MutableStateFlow).value = TextReadingState(
            isReading = true,
            currentPosition = position,
            totalLength = totalLength,
            progress = finalProgress,
            readingDuration = 0L,
            startTime = currentState.startTime,
            lastScrollTime = currentTime,
            isIdle = false,
            minimumReadingTime = 0L
        )
        
        // 同步到数据库（仅在用户滚动时，当位置变化或进度有增长时）
        if (position != currentState.currentPosition || finalProgress > currentState.progress) {
            syncProgressToDatabase(storyId, position, totalLength, finalProgress, storyTitle)
        }
    }
    
    /**
     * 开始音频阅读（兼容性）
     */
    suspend fun startAudioReading(storyId: String, content: String) {
        // 检查故事是否已经完成，如果已完成则不重新开始音频阅读
        val isAlreadyCompleted = isStoryCompleted(storyId)
        if (isAlreadyCompleted) {
            android.util.Log.d("ReadingProgressManager", "故事已完成，不重新开始音频阅读: $storyId")
            return
        }
        
        android.util.Log.d("ReadingProgressManager", "开始音频阅读: $storyId")
        (audioReadingState as MutableStateFlow).value = AudioReadingState(
            isPlaying = true,
            currentPosition = 0,
            totalLength = content.length,
            progress = 0f
        )
    }
    
    /**
     * 更新音频阅读状态（兼容性）
     */
    suspend fun updateAudioReadingState(storyId: String, isPlaying: Boolean, position: Int) {
        val currentState = (audioReadingState as MutableStateFlow).value
        android.util.Log.d("ReadingProgressManager", "更新音频阅读状态: $storyId, 播放: $isPlaying, 位置: $position")
        (audioReadingState as MutableStateFlow).value = currentState.copy(
            isPlaying = isPlaying,
            currentPosition = position
        )
    }
    
    /**
     * 更新音频进度（兼容性）
     */
    suspend fun updateAudioProgress(storyId: String, position: Int, totalLength: Int) {
        val currentState = (audioReadingState as MutableStateFlow).value
        // 确保位置不会后退，只允许前进，但不能超过总长度
        val newPosition = maxOf(position, currentState.currentPosition).coerceAtMost(totalLength)
        val progress = if (totalLength > 0) {
            (newPosition.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
        } else 0f
        
        android.util.Log.d("ReadingProgressManager", "更新音频进度: $storyId, 位置: $newPosition (原: $position), 总长度: $totalLength, 进度: ${(progress * 100).toInt()}%")
        (audioReadingState as MutableStateFlow).value = AudioReadingState(
            isPlaying = true,
            currentPosition = newPosition,
            totalLength = totalLength,
            progress = progress
        )
    }
    
    /**
     * 完成阅读（兼容性）
     * 重要：此方法只能由用户点击完成按钮触发，不能自动调用
     * 用户点击完成按钮是标记故事完成阅读的唯一标准，没有之一
     * @param storyId 故事ID
     * @param storyTitle 故事标题
     * @param completionMode 完成方式：'text'（文字模式）、'audio'（音频模式）
     */
    suspend fun completeReading(storyId: String, storyTitle: String = "故事标题", completionMode: String = "text") {
        android.util.Log.d("ReadingProgressManager", "完成阅读: $storyId, 完成方式: $completionMode")
        
        // 更新本地缓存
        val currentProgress = localProgressCache[storyId] ?: ReadingProgress(
            storyId = storyId,
            storyTitle = storyTitle,
            currentPosition = 0,
            totalLength = 0,
            readingProgress = 1.0f,
            isCompleted = true,
            startTime = null,
            lastReadTime = null,
            completionTime = null,
            readingDurationSeconds = 0
        )
        
        localProgressCache[storyId] = currentProgress.copy(
            isCompleted = true,
            readingProgress = 1.0f,
            completionTime = System.currentTimeMillis()
        )
        
        // 更新StateFlow
        _readingProgress.value = localProgressCache.values.toList()
        
        // 保持完成状态，不重置为初始状态
        val currentTextState = (textReadingState as MutableStateFlow).value
        val currentAudioState = (audioReadingState as MutableStateFlow).value
        
        // 更新为完成状态，但保持阅读信息
        (textReadingState as MutableStateFlow).value = currentTextState.copy(
            isReading = false, // 停止阅读状态
            progress = 1.0f, // 确保进度为100%
            readingDuration = currentTextState.readingDuration // 保持阅读时长
        )
        
        if (currentAudioState.isPlaying) {
            (audioReadingState as MutableStateFlow).value = currentAudioState.copy(
                isPlaying = false, // 停止播放
                progress = 1.0f // 确保进度为100%
            )
        }
        
        // 同步完成状态到数据库（传递正确的完成方式）
        syncCompletionToDatabase(storyId, storyTitle, completionMode)
        
        // 重新从数据库加载数据以确保统计信息正确
        loadReadingProgressFromDatabase()
        
        android.util.Log.d("ReadingProgressManager", "完成阅读处理完成: $storyId, 完成方式: $completionMode, 本地缓存完成状态: ${localProgressCache[storyId]?.isCompleted}")
        
        android.util.Log.d("ReadingProgressManager", "阅读完成状态已更新: $storyId")
    }
    
    /**
     * 检查故事是否完成（兼容性）
     */
    fun isStoryCompleted(storyId: String): Boolean {
        val result = localProgressCache[storyId]?.isCompleted ?: false
        android.util.Log.d("ReadingProgressManager", "检查故事完成状态: $storyId, 缓存数据=${localProgressCache[storyId]}, 结果=$result")
        return result
    }
    
    /**
     * 获取文本阅读状态
     */
    fun getTextReadingState(storyId: String): TextReadingState? {
        return localProgressCache[storyId]?.textReadingState
    }
    
    /**
     * 获取已完成故事数量（兼容性）
     */
    fun getCompletedStoriesCount(): Int {
        return readingProgress.value.count { it.isCompleted }
    }
    
    /**
     * 同步进度到数据库
     */
    private suspend fun syncProgressToDatabase(storyId: String, position: Int, totalLength: Int, progress: Float, storyTitle: String = "故事标题") {
        try {
            val userId = getCurrentUserId() ?: return
            
            // 同步进度到数据库
            
            // 更新阅读进度
            val result = updateReadingProgress(
                userId = userId,
                storyId = storyId,
                storyTitle = storyTitle,
                currentPosition = position,
                totalLength = totalLength
            )
            
            if (result.isSuccess) {
                android.util.Log.d("ReadingProgressManager", "进度同步成功: $storyId")
            } else {
                val error = result.exceptionOrNull()?.message
                android.util.Log.e("ReadingProgressManager", "进度同步失败: $storyId, 错误: $error")
                
                // 如果是用户身份验证失败，提示用户重新登录
                if (error?.contains("用户身份验证失败") == true) {
                    android.util.Log.e("ReadingProgressManager", "用户身份验证失败，请检查登录状态")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "同步进度到数据库异常", e)
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private fun getCurrentUserId(): String? {
        return try {
            // 检查UserManager是否已初始化
            val isLoggedIn = UserManager.isLoggedIn()
            
            // 从UserManager获取当前用户ID
            val userData = UserManager.getUserData()
            val userId = userData?.userId ?: "anonymous_user"
            
            if (userId == "anonymous_user") {
                android.util.Log.w("ReadingProgressManager", "警告: 使用匿名用户ID，可能用户未登录")
            }
            
            userId
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "获取用户ID失败", e)
            "anonymous_user"
        }
    }
    
    /**
     * 完成故事阅读API调用
     */
    private suspend fun completeStoryReading(
        userId: String,
        storyId: String,
        storyTitle: String,
        completionMode: String,
        deviceInfo: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 使用现有的StoryApiService来完成阅读
            val result = StoryApiService.completeStoryReading(
                userId = userId,
                storyId = storyId,
                storyTitle = storyTitle,
                completionMode = completionMode,
                deviceInfo = deviceInfo
            )
            
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d("ReadingProgressManager", "完成阅读API调用成功: $storyId")
                    Result.success(Unit)
                }
                is ApiResult.Error -> {
                    android.util.Log.e("ReadingProgressManager", "完成阅读API调用失败: ${result.message}")
                    Result.failure(Exception("完成阅读API调用失败: ${result.message}"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "完成阅读API调用异常", e)
            Result.failure(e)
        }
    }

    /**
     * 同步完成阅读到数据库
     */
    private suspend fun syncCompletionToDatabase(storyId: String, storyTitle: String = "故事标题", completionMode: String = "text") {
        try {
            val userId = getCurrentUserId() ?: return
            
            android.util.Log.e("ReadingProgressManager", "开始同步完成阅读: 用户=$userId, 故事=$storyId, 模式=$completionMode")
            
            // 使用新的完成阅读API
            val result = completeStoryReading(
                userId = userId,
                        storyId = storyId,
                storyTitle = storyTitle,
                completionMode = completionMode,
                deviceInfo = "Android App"
            )
            
            if (result.isSuccess) {
                android.util.Log.d("ReadingProgressManager", "完成阅读同步成功: $storyId")
                // 同步成功后，确保本地缓存也更新
                localProgressCache[storyId]?.let { progress ->
                    localProgressCache[storyId] = progress.copy(
                        isCompleted = true,
                        readingProgress = 1.0f,
                        completionTime = System.currentTimeMillis()
                    )
                    _readingProgress.value = localProgressCache.values.toList()
                }
            } else {
                val error = result.exceptionOrNull()?.message
                android.util.Log.e("ReadingProgressManager", "完成阅读同步失败: $storyId, 错误: $error")
                
                // 如果是用户身份验证失败，提示用户重新登录
                if (error?.contains("用户身份验证失败") == true) {
                    android.util.Log.e("ReadingProgressManager", "用户身份验证失败，请检查登录状态")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "同步完成阅读到数据库异常", e)
        }
    }
    
    /**
     * 从数据库加载阅读进度
     */
    suspend fun loadReadingProgressFromDatabase() {
        try {
            val userId = getCurrentUserId()
            
            // 检查用户是否已登录
            if (userId == null || userId == "anonymous_user" || userId.isEmpty()) {
                android.util.Log.w("ReadingProgressManager", "用户未登录，跳过加载阅读进度和统计")
                return
            }
            
            // 从服务器获取阅读进度
            val progressResult = getReadingProgress(userId)
            if (progressResult.isSuccess) {
                val progressList = progressResult.getOrNull() ?: emptyList()
                _readingProgress.value = progressList
                
                // 更新本地缓存
                progressList.forEach { progress ->
                    localProgressCache[progress.storyId] = progress
                }
                
                android.util.Log.d("ReadingProgressManager", "阅读进度加载成功: ${progressList.size} 条记录")
            } else {
                val error = progressResult.exceptionOrNull()?.message ?: "未知错误"
                android.util.Log.e("ReadingProgressManager", "阅读进度加载失败: $error")
                // 如果是身份验证失败，不显示错误，因为可能是用户未登录
                if (!error.contains("用户身份验证失败") && !error.contains("用户未登录")) {
                    android.util.Log.e("ReadingProgressManager", "阅读进度加载失败: $error")
                }
            }
            
            // 获取阅读统计
            val statisticsResult = getReadingStatistics(userId)
            if (statisticsResult.isSuccess) {
                _readingStatistics.value = statisticsResult.getOrNull()
                android.util.Log.d("ReadingProgressManager", "阅读统计加载成功")
            } else {
                val error = statisticsResult.exceptionOrNull()?.message ?: "未知错误"
                android.util.Log.e("ReadingProgressManager", "阅读统计加载失败: $error")
                // 如果是身份验证失败，不显示错误，因为可能是用户未登录
                if (!error.contains("用户身份验证失败") && !error.contains("用户未登录")) {
                    android.util.Log.e("ReadingProgressManager", "阅读统计加载失败: $error")
                }
            }
            
            android.util.Log.d("ReadingProgressManager", "已更新_readingProgress StateFlow，当前完成故事数量: ${_readingProgress.value.count { it.isCompleted }}")
            
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "从数据库加载阅读进度异常", e)
    }
}

    /**
     * 初始化时加载数据
     */
    fun initialize() {
        scope.launch {
            loadReadingProgressFromDatabase()
        }
    }
    
    /**
     * 等待阅读进度加载完成（用于同步等待）
     */
    suspend fun waitForProgressLoaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            if (userId == null || userId == "anonymous_user" || userId.isEmpty()) {
                return@withContext false
            }
            
            // 如果已经有缓存数据，直接返回
            if (localProgressCache.isNotEmpty()) {
                return@withContext true
            }
            
            // 等待加载完成
            val progressResult = getReadingProgress(userId)
            progressResult.isSuccess
        } catch (e: Exception) {
            android.util.Log.e("ReadingProgressManager", "等待阅读进度加载失败", e)
            false
        }
    }
}

/**
 * 文本阅读状态（兼容性）
 */
data class TextReadingState(
    val isReading: Boolean = false,
    val currentPosition: Int = 0,
    val totalLength: Int = 0,
    val progress: Float = 0f,
    val readingDuration: Long = 0L, // 阅读时长（毫秒）
    val startTime: Long = 0L, // 开始阅读时间
    val lastScrollTime: Long = 0L, // 最后滚动时间
    val isIdle: Boolean = false, // 是否处于空闲状态（5秒内无滚动）
    val minimumReadingTime: Long = 30000L // 最少阅读时长（30秒）
)

/**
 * 音频阅读状态（兼容性）
 */
data class AudioReadingState(
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val totalLength: Int = 0,
    val progress: Float = 0f
)

/**
 * 阅读进度数据类
 */
data class ReadingProgress(
    val storyId: String,
    val storyTitle: String,
    val currentPosition: Int = 0,
    val totalLength: Int = 0,
    val readingProgress: Float = 0f,
    val isCompleted: Boolean = false,
    val startTime: Long? = null,
    val lastReadTime: Long? = null,
    val completionTime: Long? = null,
    val readingDurationSeconds: Long = 0,
    val textReadingState: TextReadingState? = null,
    val audioReadingState: AudioReadingState? = null
)

/**
 * 阅读会话数据类
 */
data class ReadingSession(
    val sessionId: String,
    val userId: String,
    val storyId: String,
    val storyTitle: String,
    val startTime: Long,
    val isActive: Boolean
)
