package com.llasm.storycontrol.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llasm.storycontrol.data.*
import com.llasm.storycontrol.network.StoryApiService
import com.llasm.storycontrol.ui.LoginDialog
import com.llasm.storycontrol.ui.AccountSettingsPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate

/**
 * 故事屏幕主组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen() {
    val context = LocalContext.current
    
    // 状态管理
    var currentStory by remember { mutableStateOf<Story?>(null) }
    var isStoryCompleted by remember { mutableStateOf(false) }
    var hasStartedTextReading by remember { mutableStateOf(false) }
    var readingStartTime by remember { mutableStateOf(0L) }
    var pausedTime by remember { mutableStateOf(0L) }
    var totalPausedDuration by remember { mutableStateOf(0L) }
    var currentDisplaySeconds by remember { mutableStateOf(0) }
    var uiUpdateTrigger by remember { mutableStateOf(0) }
    var maxProgress by remember { mutableStateOf(0f) }
    var currentDate by remember { mutableStateOf("") }
    var canShowCompleteButton by remember { mutableStateOf(false) }
    
    // 音频播放状态
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var isAudioCompleted by remember { mutableStateOf(false) }
    
    // 页面状态
    var showSettings by remember { mutableStateOf(false) }
    var showSystemSettings by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var showAboutSettings by remember { mutableStateOf(false) }
    var showAudioPlayer by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    
    // 滚动状态
    val scrollState = rememberScrollState()
    
    // 管理器
    val readingProgressManager = ReadingProgressManager.getInstance(context)
    
    // 初始化主题管理器
    LaunchedEffect(Unit) {
        ThemeManager.init(context)
    }
    
    // 初始化阅读进度管理器
    LaunchedEffect(Unit) {
        readingProgressManager.initialize()
    }
    
    // 应用启动时检查登录状态，如果未登录则显示登录对话框
    LaunchedEffect(Unit) {
        // 延迟检查，确保UserManager已初始化
        delay(100)
        val isLoggedIn = UserManager.isLoggedIn()
        android.util.Log.d("StoryScreen", "启动时检查登录状态: $isLoggedIn")
        if (!isLoggedIn) {
            android.util.Log.d("StoryScreen", "用户未登录，显示登录对话框")
            showLoginDialog = true
        } else {
            android.util.Log.d("StoryScreen", "用户已登录，跳过登录对话框")
        }
    }
    
    // 更新当前日期
    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        currentDate = dateFormat.format(Date())
    }
    
    // 主题状态 - 直接使用ThemeManager的状态
    val themeMode = ThemeManager.themeMode
    val fontSize = ThemeManager.fontSize
    val isDarkMode = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
    val themeColors = ThemeManager.getThemeColors(isDarkMode)
    val fontStyle = ThemeManager.getFontStyle()
    
    // 创建StoryRepository实例（复用）
    val storyRepository = remember { StoryRepository() }
    
    // 加载故事（使用30天循环）
    LaunchedEffect(Unit) {
        try {
            // 使用StoryRepository获取今天的故事（30天循环）
            val todayStory = storyRepository.getTodayStory()
            
            if (todayStory != null) {
                currentStory = todayStory
                android.util.Log.d("StoryScreen", "成功加载今天的故事: ${currentStory?.title} (日期: ${currentStory?.date}, ID: ${currentStory?.id})")
            } else {
                android.util.Log.w("StoryScreen", "没有可用的故事")
                // 尝试从API获取作为后备
                try {
                    val result = StoryApiService.getActiveStories()
                    when (result) {
                        is com.llasm.storycontrol.network.ApiResult.Success -> {
                            val stories = result.data.stories
                            if (stories.isNotEmpty()) {
                                val networkStory = stories[0]
                                currentStory = com.llasm.storycontrol.data.Story(
                                    id = networkStory.id,
                                    title = networkStory.title,
                                    content = networkStory.content,
                                    date = java.time.LocalDate.now(),
                                    category = "温馨故事",
                                    isCompleted = false,
                                    completedAt = null,
                                    readingMode = com.llasm.storycontrol.data.ReadingMode.TEXT
                                )
                            }
                        }
                        else -> {
                            android.util.Log.e("StoryScreen", "API加载故事失败")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StoryScreen", "API加载故事异常: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StoryScreen", "加载故事异常: ${e.message}")
        }
    }
    
    // 监听日期变化，每天自动更新故事（30天循环）
    val today = remember { mutableStateOf(java.time.LocalDate.now()) }
    LaunchedEffect(Unit) {
        // 每分钟检查一次日期是否变化
        while (true) {
            kotlinx.coroutines.delay(60000) // 60秒检查一次
            val currentDate = java.time.LocalDate.now()
            if (currentDate != today.value) {
                today.value = currentDate
                android.util.Log.d("StoryScreen", "检测到日期变化: ${today.value}")
            }
        }
    }
    
    // 监听today变化，重新加载故事
    LaunchedEffect(today.value) {
        try {
            val todayStory = storyRepository.getTodayStory()
            if (todayStory != null) {
                // 只有当故事ID不同时才更新，避免不必要的刷新
                if (currentStory?.id != todayStory.id) {
                    currentStory = todayStory
                    android.util.Log.d("StoryScreen", "日期变化，已更新故事: ${currentStory?.title} (新ID: ${currentStory?.id})")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StoryScreen", "加载故事失败: ${e.message}")
        }
    }
    
    // 监听故事变化
    LaunchedEffect(currentStory?.id) {
        currentStory?.let { story ->
            isStoryCompleted = readingProgressManager.isStoryCompleted(story.id)
            // 重置最大进度
            maxProgress = 0f
            // 重置计时相关状态
            hasStartedTextReading = false
            readingStartTime = 0L
            pausedTime = 0L
            totalPausedDuration = 0L
            currentDisplaySeconds = 0
            // 重置音频播放状态
            isAudioCompleted = false
            // 重置完成按钮状态
            canShowCompleteButton = false
        }
    }
    
    // 监听阅读进度变化
    LaunchedEffect(Unit) {
        readingProgressManager.readingProgress.collect { progressList ->
            currentStory?.let { story ->
                progressList.find { it.storyId == story.id }?.let { progress ->
                    uiUpdateTrigger++
                }
            }
        }
    }
    
    // 实时更新计时和进度
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (hasStartedTextReading && !isStoryCompleted && !showAudioPlayer) {
                val currentTime = System.currentTimeMillis()
                val readingDuration = if (readingStartTime > 0) {
                    currentTime - readingStartTime - totalPausedDuration
                } else 0L
                val minimumReadingTime = 30000L // 硬编码30秒最小阅读时间
                
                val remainingSeconds = when {
                    readingDuration < minimumReadingTime -> {
                        ((minimumReadingTime - readingDuration) / 1000).toInt()
                    }
                    else -> 0
                }
                
                currentDisplaySeconds = remainingSeconds
                
                // 检查是否可以显示完成按钮
                val scrollProgress = if (scrollState.maxValue > 0) {
                    (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
                } else 0f
                
                if (scrollProgress >= 0.95f && remainingSeconds <= 0) {
                    canShowCompleteButton = true
                }
                
                uiUpdateTrigger++
            }
        }
    }
    
    // 监听音频界面进入/退出，处理计时暂停/恢复
    LaunchedEffect(showAudioPlayer) {
        if (showAudioPlayer) {
            // 进入音频界面，记录暂停时间
            pausedTime = System.currentTimeMillis()
            android.util.Log.d("StoryScreen", "进入音频界面，暂停文字阅读计时")
        } else {
            // 退出音频界面，累计暂停时长
            if (pausedTime > 0) {
                val pauseDuration = System.currentTimeMillis() - pausedTime
                totalPausedDuration += pauseDuration
                pausedTime = 0L
                android.util.Log.d("StoryScreen", "退出音频界面，恢复文字阅读计时，暂停时长: ${pauseDuration}ms")
            }
        }
    }
    
    // 音频播放进度更新
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // 更频繁的更新，每100ms更新一次
            if (isPlaying && showAudioPlayer) {
                if (duration > 0) {
                    // 真实音频文件
                    try {
                        val position = mediaPlayer?.currentPosition ?: 0
                        currentPosition = position
                    } catch (e: Exception) {
                        android.util.Log.e("StoryScreen", "获取音频位置失败: ${e.message}")
                    }
                } else {
                    // 模拟音频，手动递增
                    currentPosition += 100
                    if (currentPosition >= 30000) { // 30秒后完成
                        currentPosition = 30000
                        isPlaying = false
                        isAudioCompleted = true
                        android.util.Log.d("StoryScreen", "模拟音频播放完成")
                    }
                }
            }
        }
    }
    
    // 监听滚动事件
    LaunchedEffect(scrollState.value) {
        currentStory?.let { story ->
            if (!hasStartedTextReading && !isStoryCompleted) {
                hasStartedTextReading = true
                readingStartTime = System.currentTimeMillis()
                
                // 记录第一次滚动
                try {
                    readingProgressManager.recordStoryInteraction(
                        storyId = story.id,
                        interactionType = "first_scroll",
                        interactionData = mapOf(
                            "scroll_position" to scrollState.value,
                            "max_scroll" to scrollState.maxValue,
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("StoryScreen", "记录第一次滚动失败: ${e.message}")
                }
                
                // 开始文本阅读
                readingProgressManager.startTextReading(
                    storyId = story.id,
                    content = story.content,
                    audioDurationMs = 0L
                )
            } else if (hasStartedTextReading && !isStoryCompleted) {
                // 更新阅读进度
                readingProgressManager.updateTextReadingProgress(
                    storyId = story.id, 
                    position = scrollState.value, 
                    totalLength = scrollState.maxValue,
                    storyTitle = story.title,
                    isUserScroll = true
                )
            }
        }
    }
    
    // 完成阅读回调
    val onCompleteReading = {
        currentStory?.let { story ->
            // 立即更新UI状态
            isStoryCompleted = true
            hasStartedTextReading = false
            readingStartTime = 0L
            pausedTime = 0L
            totalPausedDuration = 0L
            uiUpdateTrigger++
            isAudioCompleted = false
            canShowCompleteButton = false
            
            // 异步保存到数据库
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    readingProgressManager.completeReading(story.id, story.title)
                    
                    // 记录文字模式完成阅读的交互
                    readingProgressManager.recordStoryInteraction(
                        storyId = story.id,
                        interactionType = "text_complete_button_click",
                        interactionData = mapOf(
                            "completion_time" to System.currentTimeMillis(),
                            "scroll_position" to scrollState.value,
                            "max_scroll" to scrollState.maxValue,
                            "reading_duration" to (System.currentTimeMillis() - readingStartTime),
                            "completion_method" to "text"
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("StoryScreen", "完成阅读失败: ${e.message}")
                }
            }
        }
    }
    
    // 返回回调
    val onBack = {
        // 返回逻辑
    }
    
    // 页面路由
    if (showAboutSettings) {
        AboutSettingsScreen(
            themeColors = themeColors,
            fontStyle = fontStyle,
            onBack = { 
                showAboutSettings = false
                showSettings = true
            }
        )
    } else if (showAccountSettings) {
        AccountSettingsPage(
            onBackClick = { 
                showAccountSettings = false
                showSettings = true
            },
            onShowLoginDialog = {
                showLoginDialog = true
            }
        )
        
        // 显示登录对话框
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { showLoginDialog = false },
                onLoginSuccess = {
                    showLoginDialog = false
                    // 登录成功后刷新账号设置页面
            }
        )
        }
    } else if (showSystemSettings) {
        SystemSettingsScreen(
            themeColors = themeColors,
            fontStyle = fontStyle,
            onBack = { 
                showSystemSettings = false
                showSettings = true
            }
        )
    } else if (showSettings) {
        SettingsScreen(
            themeColors = themeColors,
            fontStyle = fontStyle,
            onBack = { 
                showSettings = false
                showSystemSettings = false
                showAccountSettings = false
                showAboutSettings = false
            },
            onSystemSettings = { 
                showSettings = false
                showSystemSettings = true 
            },
            onAccountSettings = { 
                showSettings = false
                showAccountSettings = true 
            },
            onAboutSettings = { 
                showSettings = false
                showAboutSettings = true 
            }
        )
    } else if (showAudioPlayer && currentStory != null) {
        AudioPlayerScreen(
            story = currentStory!!,
            themeColors = themeColors,
            fontStyle = fontStyle,
            mediaPlayer = mediaPlayer,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            isAudioCompleted = isAudioCompleted,
            onBack = { 
                // 停止音频播放
                if (isPlaying) {
                    try {
                        if (duration > 0) {
                            // 真实音频文件
                            mediaPlayer?.pause()
                            android.util.Log.d("StoryScreen", "退出音频页面，暂停真实音频")
                        } else {
                            // 模拟音频，手动停止
                            android.util.Log.d("StoryScreen", "退出音频页面，停止模拟音频")
                        }
                        isPlaying = false
                        currentPosition = 0
                        isAudioCompleted = false
                    } catch (e: Exception) {
                        android.util.Log.e("StoryScreen", "停止音频播放失败: ${e.message}")
                        isPlaying = false
                    }
                }
                showAudioPlayer = false 
            },
            onPlayPause = { 
                android.util.Log.d("StoryScreen", "点击播放按钮，当前状态: isPlaying=$isPlaying, mediaPlayer=$mediaPlayer")
                
                if (isPlaying) {
                    // 暂停播放
                    try {
                        if (duration > 0) {
                            // 真实音频文件
                            mediaPlayer?.pause()
                            android.util.Log.d("StoryScreen", "真实音频已暂停")
                        } else {
                            // 模拟音频，手动管理状态
                            android.util.Log.d("StoryScreen", "模拟音频已暂停")
                        }
                        isPlaying = false
                    } catch (e: Exception) {
                        android.util.Log.e("StoryScreen", "暂停失败: ${e.message}")
                        isPlaying = false
                    }
                } else {
                    // 开始播放
                    if (mediaPlayer == null) {
                        // 初始化MediaPlayer
                        android.util.Log.d("StoryScreen", "开始初始化MediaPlayer")
                        try {
                            val mp = MediaPlayer()

                            // 尝试使用assets中的音频文件（使用改进的匹配策略）
                            try {
                                val storyId = currentStory?.id ?: "2024-01-01"
                                val storyTitle = currentStory?.title
                                
                                // 获取所有可用的音频文件列表
                                val availableFiles = try {
                                    context.assets.list("story_audio")?.toList() ?: emptyList()
                                } catch (e: Exception) {
                                    android.util.Log.e("StoryScreen", "获取音频文件列表失败", e)
                                    emptyList()
                                }
                                
                                // 查找匹配的音频文件
                                val matchedFile = findAudioFile(availableFiles, storyId, storyTitle)
                                
                                if (matchedFile != null) {
                                    val audioFilePath = "story_audio/$matchedFile"
                                    android.util.Log.d("StoryScreen", "找到匹配的音频文件: $audioFilePath (原始ID: $storyId, 标题: $storyTitle)")
                                    val assetFileDescriptor = context.assets.openFd(audioFilePath)
                                    mp.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                                    assetFileDescriptor.close()
                                    mp.prepare()
                                    mediaPlayer = mp
                                    duration = mp.duration
                                    android.util.Log.d("StoryScreen", "Assets音频初始化成功，文件: $audioFilePath，时长: ${duration}ms")
                                } else {
                                    throw Exception("未找到匹配的音频文件")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("StoryScreen", "Assets音频初始化失败: ${e.message}")
                                // 如果assets失败，尝试使用系统内置音频
                                try {
                                    // 尝试使用系统默认铃声
                                    val uri = android.net.Uri.parse("android.resource://${context.packageName}/android.R.raw.ringtone")
                                    mp.setDataSource(context, uri)
                                    mp.prepare()
                                    mediaPlayer = mp
                                    duration = mp.duration
                                    android.util.Log.d("StoryScreen", "系统音频初始化成功，时长: ${duration}ms")
                                } catch (e2: Exception) {
                                    android.util.Log.e("StoryScreen", "系统音频初始化失败: ${e2.message}")
                                    // 尝试使用通知音
                                    try {
                                        val uri = android.net.Uri.parse("android.resource://${context.packageName}/android.R.raw.notification")
                                        mp.setDataSource(context, uri)
                                        mp.prepare()
                                        mediaPlayer = mp
                                        duration = mp.duration
                                        android.util.Log.d("StoryScreen", "通知音初始化成功，时长: ${duration}ms")
                                    } catch (e3: Exception) {
                                        android.util.Log.e("StoryScreen", "通知音初始化失败: ${e3.message}")
                                        // 最后使用模拟音频
                                        mediaPlayer = mp
                                        duration = 0 // 标记为模拟音频
                                        android.util.Log.d("StoryScreen", "使用模拟音频，时长: 30000ms")
                                    }
                                }
                            }
                            
                            // 设置监听器（仅对真实音频）
                            if (duration > 0) {
                                mp.setOnCompletionListener {
                                    android.util.Log.d("StoryScreen", "真实音频播放完成")
                                    isPlaying = false
                                    currentPosition = 0
                                    isAudioCompleted = true
                                }
                                
                                mp.setOnErrorListener { _, what, extra ->
                                    android.util.Log.e("StoryScreen", "真实音频播放错误: what=$what, extra=$extra")
                                    isPlaying = false
                                    true
                                }
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("StoryScreen", "MediaPlayer创建失败: ${e.message}")
                            // 创建失败，无法播放
                        }
                    }

                    // 开始播放
                    if (mediaPlayer != null) {
                        try {
                            android.util.Log.d("StoryScreen", "准备开始播放，MediaPlayer状态: ${mediaPlayer?.isPlaying}")
                            // 检查MediaPlayer是否已准备好
                            if (duration > 0) {
                                // 真实音频文件
                                mediaPlayer?.start()
                                isPlaying = true
                                android.util.Log.d("StoryScreen", "真实音频播放已开始，isPlaying=$isPlaying")
                        } else {
                                // 模拟音频，手动管理状态
                                isPlaying = true
                                currentPosition = 0
                                android.util.Log.d("StoryScreen", "模拟音频播放已开始，isPlaying=$isPlaying")
                            }
                            
                            // 记录进入音频页面并点击播放的动作
                            currentStory?.let { story ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        readingProgressManager.recordStoryInteraction(
                                            storyId = story.id,
                                            interactionType = "audio_play_click",
                                            interactionData = mapOf(
                                                "play_time" to System.currentTimeMillis(),
                                                "audio_duration" to duration,
                                                "is_real_audio" to (duration > 0),
                                                "audio_type" to if (duration > 0) "real" else "simulated"
                                            )
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("StoryScreen", "记录音频播放失败: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("StoryScreen", "播放失败: ${e.message}")
                            isPlaying = false
                        }
                    } else {
                        android.util.Log.e("StoryScreen", "MediaPlayer为null，无法播放")
                        isPlaying = false
                    }
                }
            },
            onCompleteReading = {
                // 音频模式完成阅读
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        readingProgressManager.completeReading(currentStory!!.id, currentStory!!.title)
                        
                        withContext(Dispatchers.Main) {
                            isStoryCompleted = true
                            hasStartedTextReading = false
                            readingStartTime = 0L
                            pausedTime = 0L
                            totalPausedDuration = 0L
                            currentDisplaySeconds = 0
                            maxProgress = 0f
                            isAudioCompleted = false
                            showAudioPlayer = false
                            
                            // 记录音频模式完成阅读的交互
                            readingProgressManager.recordStoryInteraction(
                                storyId = currentStory!!.id,
                                interactionType = "audio_complete_button_click",
                                interactionData = mapOf(
                                    "completion_time" to System.currentTimeMillis(),
                                    "audio_duration" to duration,
                                    "is_real_audio" to (duration > 0),
                                    "completion_method" to "audio"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("StoryScreen", "音频模式完成阅读失败: ${e.message}")
                    }
                }
            }
        )
    } else {
        // 主界面
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
        Text(
                            text = "故事阅读",
                            color = themeColors.onPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColors.primary
                    ),
                    navigationIcon = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
            Text(
                            text = currentDate,
                            color = themeColors.onPrimary,
                            style = fontStyle.bodySmall,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(themeColors.background)
            ) {
                if (currentStory != null) {
                    // 主内容区域 - 使用Row布局
                    Row(
                        modifier = Modifier.weight(1f)
                    ) {
                        // 故事内容区域
                    Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp)
                                .verticalScroll(scrollState)
                    ) {
                            // 故事标题
                        Text(
                                text = currentStory!!.title,
                                style = fontStyle.headlineSmall,
                                color = themeColors.onSurface,
                            fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
            
                            // 故事内容
                                Text(
                                text = currentStory!!.content,
                                    style = fontStyle.bodyMedium,
                                color = themeColors.onSurface,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // 底部间距
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                        
                        // 右侧进度条
                        if (!isStoryCompleted) {
                            Column(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 16.dp)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 进度条背景
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(
                                            color = themeColors.textSecondary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                ) {
                                    // 进度条填充
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(maxProgress)
                                            .background(
                                                color = themeColors.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .align(Alignment.BottomCenter)
                                    )
                                }
                                
                                // 进度百分比
                                Text(
                                    text = "${(maxProgress * 100).toInt()}%",
                                    style = fontStyle.bodySmall,
                                    color = themeColors.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                    
                    // 底部操作区域
                        Card(
                            modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                            colors = CardDefaults.cardColors(
                            containerColor = themeColors.cardBackground
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (isStoryCompleted) {
                                // 已完成状态
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                        ) {
                                Text(
                                            text = "已完成阅读",
                                            style = fontStyle.bodyLarge,
                                            color = Color(0xFF4CAF50),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // 音频播放按钮
                                    OutlinedButton(
                                        onClick = { showAudioPlayer = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = themeColors.primary
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = "音频播放",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                                text = "音频播放",
                                                style = fontStyle.bodyMedium
                                            )
                                        }
                                    }
                                }
                            } else {
                                // 阅读进度显示
                                val scrollProgress = if (scrollState.maxValue > 0) {
                                    (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
                                } else 0f
                                
                                // 计算时间进度（30秒最小阅读时间）
                                val minimumReadingTime = 30000L
                                val currentTime = System.currentTimeMillis()
                                val readingDuration = if (readingStartTime > 0) {
                                    currentTime - readingStartTime - totalPausedDuration
                                } else 0L
                                val timeProgress = (readingDuration.toFloat() / minimumReadingTime).coerceIn(0f, 1f)
                                
                                // 综合进度：滚动进度和时间进度的最小值，确保在时间未达到时不能完成
                                val combinedProgress = minOf(scrollProgress, timeProgress)
                                
                                // 更新最大进度，防止回退
                                if (combinedProgress > maxProgress) {
                                    maxProgress = combinedProgress
                                }
                                
                                val progressPercent = (maxProgress * 100).toInt()
                                
                                // 显示阅读进度和剩余时间
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                        text = "阅读进度: $progressPercent%",
                                        style = fontStyle.bodyMedium,
                                        color = themeColors.textPrimary
                                    )
                                    
                                    if (currentDisplaySeconds > 0) {
                                        Text(
                                            text = "还需 $currentDisplaySeconds 秒",
                                            style = fontStyle.bodyMedium,
                                            color = themeColors.textSecondary
                                        )
                                    }
                                }
                                
                                // 底部进度条
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { maxProgress },
                            modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = themeColors.primary,
                                    trackColor = themeColors.textSecondary.copy(alpha = 0.2f)
                                )
                                
                                // 完成阅读按钮：使用稳定的状态变量
                                if (canShowCompleteButton) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onCompleteReading() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                        ) {
                            Text(
                                            text = "完成阅读",
                                style = fontStyle.bodyMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // 音频播放按钮
                                OutlinedButton(
                                    onClick = { showAudioPlayer = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = themeColors.primary
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = "音频播放",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                            text = "音频播放",
                                            style = fontStyle.bodyMedium
                            )
                        }
                    }
                }
            }
                    }
                } else {
                    // 空状态
                    EmptyState(
                        message = "暂无故事内容",
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
            }
        }
    }
    
    // 全局登录对话框（在应用启动时或需要登录时显示）
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = {
                showLoginDialog = false
                // 登录成功后刷新数据
                CoroutineScope(Dispatchers.IO).launch {
                    readingProgressManager.loadReadingProgressFromDatabase()
                }
            }
        )
    }
}

/**
 * 查找匹配的音频文件（使用多种匹配策略）
 */
private fun findAudioFile(availableFiles: List<String>, storyId: String, storyTitle: String?): String? {
    // 策略1: 使用原始标题精确匹配
    if (!storyTitle.isNullOrBlank()) {
        val exactMatch = availableFiles.find { it.equals("$storyTitle.mp3", ignoreCase = true) }
        if (exactMatch != null) {
            android.util.Log.d("StoryScreen", "精确匹配音频文件: $exactMatch")
            return exactMatch
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
            android.util.Log.d("StoryScreen", "清理后匹配音频文件: $cleanedMatch (清理后标题: $cleanedTitle)")
            return cleanedMatch
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
            android.util.Log.d("StoryScreen", "模糊匹配音频文件: $fuzzyMatch (原始标题: $storyTitle)")
            return fuzzyMatch
        }
    }
    
    // 策略4: 使用storyId匹配（日期格式）
    if (storyId.startsWith("2025-01-")) {
        val dateId = storyId.replace("2025-01-", "2024-01-")
        val dateMatch = availableFiles.find { it.startsWith(dateId, ignoreCase = true) }
        if (dateMatch != null) {
            android.util.Log.d("StoryScreen", "日期格式匹配音频文件: $dateMatch")
            return dateMatch
        }
    }
    
    // 策略5: 直接使用storyId
    val storyIdMatch = availableFiles.find { it.startsWith(storyId, ignoreCase = true) }
    if (storyIdMatch != null) {
        android.util.Log.d("StoryScreen", "StoryID匹配音频文件: $storyIdMatch")
        return storyIdMatch
    }
    
    android.util.Log.e("StoryScreen", "无法找到匹配的音频文件: storyId=$storyId, title=$storyTitle")
    android.util.Log.d("StoryScreen", "可用音频文件列表: ${availableFiles.take(10).joinToString(", ")}")
    return null
}
