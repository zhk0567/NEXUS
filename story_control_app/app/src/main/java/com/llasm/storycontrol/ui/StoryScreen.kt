package com.llasm.storycontrol.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import com.llasm.storycontrol.data.Story
import com.llasm.storycontrol.data.StoryRepository
import com.llasm.storycontrol.ui.ThemeColors
import com.llasm.storycontrol.ui.FontStyle
import com.llasm.storycontrol.data.ReadingProgressManager
import com.llasm.storycontrol.data.ReadingMode
import com.llasm.storycontrol.data.UserManager
import com.llasm.storycontrol.service.TTSService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 故事阅读界面
 * 保持与原应用相同的UI风格
 */
/**
 * 登录要求界面
 */
@Composable
fun LoginRequiredScreen(
    onLoginClick: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.surface
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 应用图标
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            themeColors.primary,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "故事",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // 标题
                Text(
                    text = "欢迎使用故事",
                    style = fontStyle.headlineSmall.copy(fontSize = 28.sp),
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // 描述
                Text(
                    text = "请先登录以开始您的故事阅读之旅",
                    style = fontStyle.bodyLarge,
                    color = themeColors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                // 登录按钮
                Button(
                    onClick = {
                        android.util.Log.d("LoginRequiredScreen", "登录按钮被点击")
                        onLoginClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "立即登录",
                        style = fontStyle.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 提示信息
                Text(
                    text = "登录后可享受完整的阅读体验",
                    style = fontStyle.bodySmall,
                    color = themeColors.textSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 设置对话框
 */
@Composable
fun SettingsDialog(
    readingProgressManager: ReadingProgressManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = { Text("设置功能") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen() {
    val context = LocalContext.current
    val storyRepository = remember { StoryRepository() }
    val ttsService = remember { TTSService(context) }
    val readingProgressManager = remember { 
        val manager = ReadingProgressManager.getInstance(context)
        manager.initialize() // 初始化时加载数据库数据
        manager
    }
    val scope = rememberCoroutineScope()
    
    var selectedDate by remember { mutableStateOf(LocalDate.of(2025, 1, 1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMainSettings by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    
    // 初始化主题
    LaunchedEffect(Unit) {
        SettingsManager.initializeTheme(context)
    }
    
    // 检查登录状态
    val isLoggedIn = UserManager.isLoggedIn()
    android.util.Log.d("StoryScreen", "当前登录状态: $isLoggedIn")
    
    // 阅读进度状态
    val readingProgress by readingProgressManager.readingProgress.collectAsState()
    val currentSession by readingProgressManager.currentSession.collectAsState()
    val readingStatistics by readingProgressManager.readingStatistics.collectAsState()
    val isSyncing by readingProgressManager.isSyncing.collectAsState()
    
    // 阅读状态
    val textReadingState by readingProgressManager.textReadingState.collectAsState()
    val audioReadingState by readingProgressManager.audioReadingState.collectAsState()
    
    // 设置状态监听
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 如果未登录，显示登录界面
    if (!isLoggedIn) {
        LoginRequiredScreen(
            onLoginClick = { 
                android.util.Log.d("StoryScreen", "LoginRequiredScreen onLoginClick 被调用")
                showLoginDialog = true 
            },
            themeColors = themeColors,
            fontStyle = fontStyle
        )
        
        // 登录对话框
        if (showLoginDialog) {
            LoginDialog(
                onDismiss = { 
                    showLoginDialog = false
                },
                onLoginSuccess = {
                    showLoginDialog = false
                    // 登录成功后，界面会自动刷新显示主内容
                }
            )
        }
        return
    }
    
    // 获取当前日期的故事
    val currentStory by remember(selectedDate) {
        derivedStateOf { storyRepository.getStoryByDate(selectedDate) }
    }
    
    Scaffold(
        modifier = Modifier.background(themeColors.background),
        containerColor = themeColors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface,
                    titleContentColor = themeColors.onSurface
                ),
                title = {
                    Column {
                        Text(
                            text = "每日故事",
                            style = fontStyle.titleMedium,
                            color = themeColors.onSurface
                        )
                        Text(
                            text = "已完成: ${readingProgress.count { it.isCompleted }} 个故事",
                            style = fontStyle.bodySmall,
                            color = themeColors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    // 设置按钮
                    IconButton(
                        onClick = { showMainSettings = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                },
                actions = {
                    // 语音播放按钮
                    IconButton(
                        onClick = { 
                            if (isPlaying) {
                                if (isPaused) {
                                    // 恢复播放
                                    ttsService.resumePlayback()
                                    isPaused = false
                                } else {
                                    // 暂停播放
                                    ttsService.pausePlayback()
                                    isPaused = true
                                }
                            } else {
                                // 开始播放
                                currentStory?.let { story ->
                                    // 在协程中调用suspend函数
                                    scope.launch {
                                        readingProgressManager.startAudioReading(story.id, story.content)
                                    }
                                    ttsService.playStoryAudioWithProgress(
                                        storyId = story.id,
                                        storyText = story.content,
                                        onPlayStart = { 
                                            isPlaying = true
                                            isPaused = false
                                        },
                                        onPlayComplete = { 
                                            isPlaying = false
                                            isPaused = false
                                            scope.launch {
                                                // 确保音频播放完成时进度为100%
                                                readingProgressManager.updateAudioProgress(story.id, Int.MAX_VALUE, 1)
                                                readingProgressManager.updateAudioReadingState(story.id, false, 0)
                                            }
                                        },
                                        onError = { error ->
                                            isPlaying = false
                                            isPaused = false
                                        },
                                        onProgressUpdate = { currentPosition, duration ->
                                            scope.launch {
                                                readingProgressManager.updateAudioProgress(story.id, currentPosition, duration)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when {
                                isPlaying && isPaused -> Icons.Default.PlayArrow
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.VolumeUp
                            },
                            contentDescription = when {
                                isPlaying && isPaused -> "恢复播放"
                                isPlaying -> "暂停播放"
                                else -> "播放语音"
                            },
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                    
                    // 日期选择按钮
                    IconButton(
                        onClick = { showDatePicker = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "选择日期",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(themeColors.background)
        ) {
            if (currentStory != null) {
                StoryContent(
                    story = currentStory!!,
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    readingProgressManager = readingProgressManager,
                    textReadingState = textReadingState,
                    audioReadingState = audioReadingState,
                    isPlaying = isPlaying,
                    ttsService = ttsService
                )
            } else {
                // 没有故事时的空状态
                EmptyState(
                    selectedDate = selectedDate,
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            }
        }
    }
    
    // 日历形式的日期选择对话框
    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = {
                Text(
                    text = "选择日期 - 2025年1月",
                    style = fontStyle.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "当前选择: ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                        style = fontStyle.bodyMedium,
                        color = themeColors.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 日历网格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.height(300.dp)
                    ) {
                        // 星期标题
                        items(listOf("日", "一", "二", "三", "四", "五", "六")) { day ->
                            Text(
                                text = day,
                                style = fontStyle.bodySmall,
                                color = themeColors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // 日期按钮
                        items(31) { day ->
                            val date = LocalDate.of(2025, 1, day + 1)
                            val isSelected = date == selectedDate
                            
                            Button(
                                onClick = { 
                                    selectedDate = date
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) themeColors.primary else themeColors.surface
                                ),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = "${day + 1}",
                                    style = fontStyle.bodySmall,
                                    color = if (isSelected) themeColors.onPrimary else themeColors.textPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDatePicker = false }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 主设置页面
    if (showMainSettings) {
        MainSettingsPage(
            readingProgressManager = readingProgressManager,
            onBackClick = { showMainSettings = false },
            onNavigateToTheme = { 
                showMainSettings = false
                showThemeSettings = true
            },
            onNavigateToAccount = {
                showMainSettings = false
                showAccountSettings = true
            },
            onNavigateToAbout = {
                showMainSettings = false
                showAboutPage = true
            }
        )
    }
    
    // 主题设置页面
    if (showThemeSettings) {
        ThemeSettingsPage(
            onBackClick = { 
                showThemeSettings = false
                showMainSettings = true
            }
        )
    }
    
    // 账号设置页面
    if (showAccountSettings) {
        AccountSettingsPage(
            onBackClick = { 
                showAccountSettings = false
                showMainSettings = true
            },
            onShowLoginDialog = {
                showAccountSettings = false
                showLoginDialog = true
            }
        )
    }
    
    // 关于页面
    if (showAboutPage) {
        AboutPage(
            onBackClick = { 
                showAboutPage = false
                showMainSettings = true
            }
        )
    }
    
    // 设置对话框
    if (showSettingsDialog) {
        SettingsDialog(
            readingProgressManager = readingProgressManager,
            onDismiss = { showSettingsDialog = false }
        )
    }
    
    
}

/**
 * 故事内容组件
 */
@Composable
fun StoryContent(
    story: Story,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    readingProgressManager: ReadingProgressManager,
    textReadingState: com.llasm.storycontrol.data.TextReadingState?,
    audioReadingState: com.llasm.storycontrol.data.AudioReadingState?,
    isPlaying: Boolean,
    ttsService: com.llasm.storycontrol.service.TTSService
) {
    val scrollState = rememberScrollState()
    // 基于实时阅读条件判断是否完成，而不是缓存状态
    val isStoryCompleted = if (textReadingState?.isReading == true) {
        // 文字阅读状态：只有在用户主动点击完成阅读后才显示完成状态
        val hasReached100Percent = textReadingState.progress >= 1.0f
        val hasMinimumTime = textReadingState.readingDuration >= textReadingState.minimumReadingTime
        // 只有当用户点击完成阅读按钮后，isReading变为false时才显示完成状态
        val result = hasReached100Percent && hasMinimumTime && !textReadingState.isReading
        android.util.Log.d("StoryScreen", "正在文字阅读状态判断: 进度=${(textReadingState.progress * 100).toInt()}%, 时长=${textReadingState.readingDuration}ms, 最少时长=${textReadingState.minimumReadingTime}ms, 达到100%=$hasReached100Percent, 达到最少时长=$hasMinimumTime, 仍在阅读=${textReadingState.isReading}, 结果=$result")
        result
    } else if (textReadingState?.progress == 1.0f && 
               textReadingState?.readingDuration != null && 
               textReadingState?.minimumReadingTime != null &&
               textReadingState.readingDuration >= textReadingState.minimumReadingTime &&
               !textReadingState.isReading) {
        // 已完成状态：文字阅读进度100%且时长满足，但不在阅读中
        android.util.Log.d("StoryScreen", "已完成文字阅读状态判断: 进度=${(textReadingState.progress * 100).toInt()}%, 时长=${textReadingState.readingDuration}ms, 最少时长=${textReadingState.minimumReadingTime}ms, 不在阅读=${!textReadingState.isReading}, 结果=true")
        true
    } else if (audioReadingState?.progress == 1.0f && !audioReadingState.isPlaying) {
        // 已完成状态：音频播放进度100%且不在播放中
        android.util.Log.d("StoryScreen", "已完成音频播放状态判断: 进度=${(audioReadingState.progress * 100).toInt()}%, 不在播放=${!audioReadingState.isPlaying}, 结果=true")
        true
    } else {
        val cacheResult = readingProgressManager.isStoryCompleted(story.id)
        android.util.Log.d("StoryScreen", "缓存状态判断: 文字进度=${textReadingState?.progress}, 文字时长=${textReadingState?.readingDuration}, 音频进度=${audioReadingState?.progress}, 结果=$cacheResult")
        cacheResult
    }
    
    // 开始阅读时初始化进度（只在故事ID变化时初始化一次）
    LaunchedEffect(story.id) {
        // 检查故事是否已经完成，如果已完成则不重新开始阅读
        val isAlreadyCompleted = readingProgressManager.isStoryCompleted(story.id)
        if (!isAlreadyCompleted) {
            readingProgressManager.startTextReading(story.id, story.content)
        } else {
            android.util.Log.d("StoryScreen", "故事已完成，不重新开始阅读: ${story.id}")
        }
    }
    
    // 定时更新UI显示（每500ms更新一次）
    var uiUpdateTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            uiUpdateTrigger++ // 触发UI更新
        }
    }
    
    // 文字阅读进度跟踪
    var readingStartTime by remember { mutableStateOf(0L) }
    var lastScrollPosition by remember { mutableStateOf(0) }
    var lastProgress by remember { mutableStateOf(0f) }
    
    // 统一的滚动监听（处理音频播放时的文字阅读启动和进度更新）
    LaunchedEffect(scrollState.value) {
        // 如果在音频播放时滚动，同时开始文字阅读模式（不暂停音频）
        if (isPlaying && scrollState.value > 0) {
            // 检查故事是否已经完成，如果已完成则不重新开始阅读
            val isAlreadyCompleted = readingProgressManager.isStoryCompleted(story.id)
            if (!isAlreadyCompleted) {
                // 检查是否已经在文字阅读模式，如果是则不重新开始
                val currentState = readingProgressManager.textReadingState.value
                if (currentState == null || !currentState.isReading) {
                    // 只有在未开始文字阅读时才开始
                    readingProgressManager.startTextReading(story.id, story.content)
                }
            } else {
                android.util.Log.d("StoryScreen", "故事已完成，音频播放时滚动不重新开始文字阅读: ${story.id}")
            }
        }
        
        // 处理文字阅读进度更新
        if (textReadingState?.isReading == true) {
            val currentTime = System.currentTimeMillis()
            
            // 基于滚动位置计算当前阅读位置
            val scrollProgress = if (scrollState.maxValue > 0) {
                (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
            } else 0f
            val estimatedPosition = (story.content.length * scrollProgress).toInt().coerceAtMost(story.content.length)
            
            // 确保位置不会后退，但也不能超过总长度
            val currentPosition = maxOf(estimatedPosition, lastScrollPosition).coerceAtMost(story.content.length)
            val currentProgress = if (story.content.length > 0) {
                (currentPosition.toFloat() / story.content.length.toFloat()).coerceIn(0f, 1f)
            } else 0f
            
            // 确保进度不会后退
            val finalProgress = maxOf(currentProgress, lastProgress)
            
            // 只有当进度有实际增长时才更新
            if (currentPosition > lastScrollPosition || finalProgress > lastProgress) {
                // 第一次滚动时开始计时
                if (readingStartTime == 0L) {
                    readingStartTime = currentTime
                    android.util.Log.d("StoryContent", "第一次滚动，开始计时: $readingStartTime")
                }
                
                lastScrollPosition = currentPosition
                lastProgress = finalProgress
                
                android.util.Log.d("StoryContent", "实时滚动更新: 位置=$currentPosition, 总长度=${story.content.length}, 进度=${(finalProgress * 100).toInt()}%")
                
                // 直接更新ReadingProgressManager的状态，确保时长计算一致
                CoroutineScope(Dispatchers.IO).launch {
                readingProgressManager.updateTextReadingProgress(
                        storyId = story.id,
                        position = currentPosition,
                        totalLength = story.content.length,
                        storyTitle = story.title
                    )
                }
            }
        }
    }
    
    // 开始文字阅读时记录开始时间（但不立即开始计时）
    LaunchedEffect(textReadingState?.isReading) {
        if (textReadingState?.isReading == true) {
            lastScrollPosition = 0
            lastProgress = 0f
            android.util.Log.d("StoryContent", "开始文字阅读，等待第一次滚动")
        } else {
            readingStartTime = 0L
            lastScrollPosition = 0
            lastProgress = 0f
        }
    }
    
    // 实时时长更新（每1秒更新一次，确保时间显示实时）
    LaunchedEffect(textReadingState?.isReading, readingStartTime) {
        while (textReadingState?.isReading == true && readingStartTime > 0) {
            delay(1000) // 每1秒更新一次时长显示
            if (textReadingState?.isReading == true && readingStartTime > 0) {
                android.util.Log.d("StoryContent", "实时时长更新: 通过ReadingProgressManager更新")
                
                // 更新ReadingProgressManager中的时长，它会处理空闲检测
                CoroutineScope(Dispatchers.IO).launch {
                readingProgressManager.updateTextReadingProgress(
                        storyId = story.id,
                        position = lastScrollPosition, // 使用上次的位置，不更新位置
                        totalLength = story.content.length,
                        storyTitle = story.title
                    )
                }
            }
        }
    }
    
    // 基于时间的进度更新（每3秒更新一次，减少更新频率）
    LaunchedEffect(textReadingState?.isReading) {
        while (textReadingState?.isReading == true) {
            delay(3000) // 每3秒更新一次
            if (textReadingState?.isReading == true) {
                val currentTime = System.currentTimeMillis()
                
                // 基于滚动位置计算当前阅读位置
                val scrollProgress = if (scrollState.maxValue > 0) {
                    (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val estimatedPosition = (story.content.length * scrollProgress).toInt().coerceAtMost(story.content.length)
                
                // 确保位置不会后退，但也不能超过总长度
                val currentPosition = maxOf(estimatedPosition, lastScrollPosition).coerceAtMost(story.content.length)
                val currentProgress = if (story.content.length > 0) {
                    (currentPosition.toFloat() / story.content.length.toFloat()).coerceIn(0f, 1f)
                } else 0f
                
                // 确保进度不会后退
                val finalProgress = maxOf(currentProgress, lastProgress)
                
                // 只有当进度有实际增长时才更新
                if (currentPosition > lastScrollPosition || finalProgress > lastProgress) {
                    // 第一次滚动时开始计时
                    if (readingStartTime == 0L) {
                        readingStartTime = currentTime
                        android.util.Log.d("StoryContent", "第一次滚动，开始计时: $readingStartTime")
                    }
                    
                    lastScrollPosition = currentPosition
                    lastProgress = finalProgress
                    
                    android.util.Log.d("StoryContent", "定时更新文字阅读进度: 位置=$currentPosition, 总长度=${story.content.length}, 进度=${(finalProgress * 100).toInt()}%")
                    
                    readingProgressManager.updateTextReadingProgress(
                        storyId = story.id,
                        position = currentPosition,
                        totalLength = story.content.length,
                        storyTitle = story.title
                    )
                }
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 主要内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // 故事标题
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = story.title,
                        style = fontStyle.headlineSmall.copy(
                            color = themeColors.onPrimary
                        ),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = story.date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                        style = fontStyle.bodySmall.copy(
                            color = themeColors.onPrimary.copy(alpha = 0.8f)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 故事内容
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = story.content,
                    style = fontStyle.bodyLarge.copy(
                        color = themeColors.textPrimary,
                        lineHeight = 28.sp
                    ),
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Justify
                )
            }
            
            // 底部间距，为固定进度卡片留出空间
            Spacer(modifier = Modifier.height(120.dp))
        }
        
        // 固定在底部的进度卡片
        ReadingProgressCard(
            story = story,
            textReadingState = textReadingState,
            audioReadingState = audioReadingState,
            isStoryCompleted = isStoryCompleted,
            themeColors = themeColors,
            fontStyle = fontStyle,
            onCompleteReading = { 
                // 在协程中调用suspend函数并等待完成
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        android.util.Log.d("StoryScreen", "开始完成阅读: ${story.id}")
                        
                        // 停止音频播放
                        if (isPlaying) {
                            ttsService.pausePlayback()
                            android.util.Log.d("StoryScreen", "已停止音频播放")
                        }
                        
                        // 完成阅读处理
                        readingProgressManager.completeReading(story.id, story.title)
                        android.util.Log.d("StoryScreen", "完成阅读成功: ${story.id}")
                        
                        // 强制触发UI重新计算
                        android.util.Log.d("StoryScreen", "强制触发UI更新")
                    } catch (e: Exception) {
                        android.util.Log.e("StoryScreen", "完成阅读失败: ${e.message}")
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

/**
 * 阅读进度卡片组件
 */
@Composable
fun ReadingProgressCard(
    story: Story,
    textReadingState: com.llasm.storycontrol.data.TextReadingState?,
    audioReadingState: com.llasm.storycontrol.data.AudioReadingState?,
    isStoryCompleted: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onCompleteReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isStoryCompleted) themeColors.primary else themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 综合阅读进度显示
            val hasTextReading = textReadingState?.isReading == true
            val hasAudioReading = audioReadingState?.isPlaying == true
            val hasAudioCompleted = audioReadingState?.progress == 1.0f && !audioReadingState.isPlaying
            
            if (hasTextReading || hasAudioReading || hasAudioCompleted || isStoryCompleted) {
                // 完成状态显示 - 只显示"已完成阅读"
                if (isStoryCompleted && !hasAudioReading) {
                    Text(
                        text = "已完成阅读",
                        style = fontStyle.bodyMedium,
                        color = themeColors.onPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 只在非完成状态显示进度信息
                    if (!isStoryCompleted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    }
                    
                    // 只在非完成状态显示进度条
                    if (!isStoryCompleted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 判断是否同时进行两种模式
                    val isBothModesActive = hasTextReading && hasAudioReading
                    
                    if (isBothModesActive) {
                        // 同时进行两种模式时，显示两个进度条
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 文字阅读进度条
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                    text = "文字阅读",
                                    style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                                Text(
                                    text = "${(textReadingState!!.progress * 100).toInt()}%",
                                    style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            LinearProgressIndicator(
                                progress = { textReadingState!!.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = themeColors.primary,
                                trackColor = themeColors.primary.copy(alpha = 0.3f)
                            )
                            
                            // 音频播放进度条
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            Text(
                                    text = "音频播放",
                                style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                    text = "${(audioReadingState!!.progress * 100).toInt()}%",
                                style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                            // 可拖动的音频进度条（完成阅读后可拖动）
                            if (isStoryCompleted) {
                                var sliderPosition by remember { mutableStateOf(audioReadingState!!.progress) }
                                
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { newValue ->
                                        sliderPosition = newValue
                                        // 这里可以添加跳转到指定位置的逻辑
                                        android.util.Log.d("AudioProgress", "用户拖动到: ${(newValue * 100).toInt()}%")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = themeColors.primary,
                                        activeTrackColor = themeColors.primary.copy(alpha = 0.7f),
                                        inactiveTrackColor = themeColors.primary.copy(alpha = 0.2f)
                                    )
                                )
                    } else {
                                // 完成阅读前显示不可拖动的进度条
                    LinearProgressIndicator(
                                    progress = { audioReadingState!!.progress },
                        modifier = Modifier.fillMaxWidth(),
                                    color = themeColors.primary.copy(alpha = 0.7f),
                                    trackColor = themeColors.primary.copy(alpha = 0.2f)
                                )
                            }
                        }
                        } else {
                        // 单一模式时，显示综合进度条
                        val maxProgress = if (hasTextReading) {
                            textReadingState!!.progress
                        } else {
                            audioReadingState!!.progress
                    }
                    
                    LinearProgressIndicator(
                            progress = { maxProgress },
                        modifier = Modifier.fillMaxWidth(),
                            color = themeColors.primary,
                            trackColor = themeColors.primary.copy(alpha = 0.3f)
                        )
                    }
                        
                        // 完成阅读按钮 - 检查任意一种模式是否完成
                        val canComplete = run {
                            // 检查文字阅读是否完成
                            val textCompleted = if (hasTextReading) {
                                val textState = textReadingState!!
                                val hasReached100Percent = textState.progress >= 1.0f
                                val hasMinimumTime = textState.readingDuration >= textState.minimumReadingTime
                                val result = hasReached100Percent && hasMinimumTime
                                android.util.Log.d("ReadingProgressCard", "文字阅读完成条件检查: 进度=${(textState.progress * 100).toInt()}%, 时长=${textState.readingDuration}ms, 最少时长=${textState.minimumReadingTime}ms, 达到100%=$hasReached100Percent, 达到最少时长=$hasMinimumTime, 结果=$result")
                                result
                        } else {
                                false
                            }
                            
                            // 检查音频播放是否完成
                            val audioCompleted = if (hasAudioReading) {
                                val audioState = audioReadingState!!
                                val hasReached100Percent = audioState.progress >= 1.0f
                                val result = hasReached100Percent
                                android.util.Log.d("ReadingProgressCard", "音频播放完成条件检查: 进度=${(audioState.progress * 100).toInt()}%, 达到100%=$hasReached100Percent, 结果=$result")
                                result
                            } else if (hasAudioCompleted) {
                                android.util.Log.d("ReadingProgressCard", "音频播放已完成，显示完成阅读按钮")
                                true
                        } else {
                                false
                            }
                            
                            // 任意一种模式完成即可显示按钮
                            val result = textCompleted || audioCompleted
                            android.util.Log.d("ReadingProgressCard", "完成条件综合判断: 文字完成=$textCompleted, 音频完成=$audioCompleted, 最终结果=$result")
                            result
                        }
                        
                        if (canComplete) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                                onClick = {
                                    android.util.Log.d("ReadingProgressCard", "完成阅读按钮被点击")
                                    onCompleteReading()
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColors.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "完成阅读",
                                style = fontStyle.bodyMedium,
                                color = themeColors.onPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                    
                    // 显示完成条件提示
                    if (hasTextReading) {
                        val textState = textReadingState!!
                        val progressPercent = (textState.progress * 100).toInt()
                        val timeRemaining = if (textState.readingDuration < textState.minimumReadingTime) {
                            val remainingMs = textState.minimumReadingTime - textState.readingDuration
                            val remainingSeconds = remainingMs / 1000
                            "还需 ${remainingSeconds}秒"
                        } else {
                            "时长已满足"
                        }
                        
                        val progressStatus = if (textState.progress < 1.0f) {
                            "继续阅读到100%"
                        } else {
                            "进度已满足"
                        }
                        
                        val activityStatus = if (textState.isIdle) {
                            "保持活跃"
                        } else {
                            "状态正常"
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$progressStatus • $timeRemaining • $activityStatus",
                            style = fontStyle.bodySmall,
                            color = themeColors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空状态组件
 */
@Composable
fun EmptyState(
    selectedDate: LocalDate,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            tint = themeColors.textSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无故事",
            style = fontStyle.headlineSmall.copy(
                color = themeColors.textPrimary
            ),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))} 还没有故事内容",
            style = fontStyle.bodyMedium.copy(
                color = themeColors.textSecondary
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请选择其他日期查看故事",
            style = fontStyle.bodySmall.copy(
                color = themeColors.textSecondary
            ),
            textAlign = TextAlign.Center
        )
    }
}




