package com.llasm.nexusunified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import android.util.Log
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.ui.SettingsManager
import com.llasm.nexusunified.viewmodel.VoiceCallViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(
    onBackClick: () -> Unit,
    viewModel: VoiceCallViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 设置状态监听
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors()
    val fontStyle = SettingsManager.getFontStyle()
    
    // ViewModel状态
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isWaitingForResponse by viewModel.isWaitingForResponse.collectAsStateWithLifecycle()
    val isVoiceActive by viewModel.isVoiceActive.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isSubtitlesEnabled by viewModel.isSubtitlesEnabled.collectAsStateWithLifecycle()
    val currentUserQuestion by viewModel.currentUserQuestion.collectAsStateWithLifecycle()
    val currentAIAnswer by viewModel.currentAIAnswer.collectAsStateWithLifecycle()
    val subtitleHistory by viewModel.subtitleHistory.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val hintText by viewModel.hintText.collectAsStateWithLifecycle()
    
    // 初始化ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // 组件销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            viewModel.hangup()
        }
    }
    
    // 处理返回键
    BackHandler {
        // 挂断电话并清理所有资源
        viewModel.hangup()
        onBackClick()
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
                    Text(
                        text = "语音通话",
                        style = fontStyle.headlineSmall,
                        color = themeColors.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.hangup()
                            onBackClick()
                        },
                        modifier = Modifier.size(fontStyle.iconSize.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 顶部：状态指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        color = themeColors.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                 // 连接状态指示器
                 Box(
                     modifier = Modifier
                         .size(12.dp)
                         .clip(CircleShape)
                         .background(
                             if (isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                         )
                 )
                
                Text(
                    text = statusText,
                    style = fontStyle.bodyMedium,
                    color = themeColors.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 字幕显示区域
            if (isSubtitlesEnabled) {
                SubtitleDisplay(
                    currentUserQuestion = currentUserQuestion,
                    currentAIAnswer = currentAIAnswer,
                    subtitleHistory = subtitleHistory,
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            }
            
            // 中间：主要内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 语音活动指示器
                if (isRecording) {
                    VoiceActivityIndicator(
                        isActive = isVoiceActive,
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                // 提示文字
                Text(
                    text = hintText,
                    style = fontStyle.bodyLarge,
                    color = themeColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            
            // 控制按钮区域 - 固定在中间偏下位置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 三个控制按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     // 挂断按钮
                     VoiceCallButton(
                         icon = Icons.Default.CallEnd,
                         onClick = { 
                             viewModel.hangup()
                             onBackClick()
                         },
                         backgroundColor = Color(0xFF424242),
                         enabled = true,
                         themeColors = themeColors,
                         fontStyle = fontStyle
                     )
                     
                     // 暂停/继续按钮
                     VoiceCallButton(
                         icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                         onClick = { 
                             if (isPaused) {
                                 viewModel.resumeCall()
                             } else {
                                 viewModel.pauseCall()
                             }
                         },
                         backgroundColor = if (isPaused) Color(0xFF4CAF50) else Color(0xFF757575),
                         enabled = isConnected,
                         size = 80.dp,
                         themeColors = themeColors,
                         fontStyle = fontStyle
                     )
                     
                     // 字幕按钮
                     VoiceCallButton(
                         icon = Icons.Default.ClosedCaption,
                         onClick = { viewModel.toggleSubtitles() },
                         backgroundColor = if (isSubtitlesEnabled) Color(0xFF4CAF50) else Color(0xFF616161),
                         enabled = true,
                         themeColors = themeColors,
                         fontStyle = fontStyle
                     )
                }
            }
            
            // 底部：调试窗口
            DebugWindow(
                messages = messages,
                themeColors = themeColors,
                fontStyle = fontStyle,
                modifier = Modifier.height(120.dp)
            )
        }
    }
}

@Composable
fun VoiceCallButton(
    icon: ImageVector,
    onClick: () -> Unit,
    backgroundColor: Color,
    enabled: Boolean,
    size: Dp = 60.dp,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Card(
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(fontStyle.iconSize.dp * 1.2f)
                )
            }
        }
    }
}

@Composable
fun VoiceActivityIndicator(
    isActive: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
         // 动态的语音活动指示器
         Box(
             modifier = Modifier
                 .size(120.dp)
                 .clip(CircleShape)
                 .background(
                     if (isActive) Color(0xFF424242).copy(alpha = 0.3f) 
                     else Color(0xFF9E9E9E).copy(alpha = 0.2f)
                 ),
             contentAlignment = Alignment.Center
         ) {
             Icon(
                 imageVector = Icons.Default.Mic,
                 contentDescription = "麦克风",
                 tint = if (isActive) Color(0xFF212121) else Color(0xFF9E9E9E),
                 modifier = Modifier.size(48.dp)
             )
         }
         
         Text(
             text = if (isActive) "检测到语音" else "静音中",
             style = fontStyle.bodyMedium,
             color = if (isActive) Color(0xFF424242) else Color(0xFF9E9E9E)
         )
    }
}

@Composable
fun DebugWindow(
    messages: List<ChatMessage>,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 调试窗口标题
            Text(
                text = "调试信息",
                style = fontStyle.titleMedium,
                color = themeColors.onSurface,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Medium
            )
            
            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { message ->
                    DebugMessageItem(
                        message = message,
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
                
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "应用已启动，等待您开始对话...",
                            style = fontStyle.bodyMedium,
                            color = themeColors.textSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DebugMessageItem(
    message: ChatMessage,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    val rolePrefix = when {
        message.isUser -> "[用户]:"
        else -> "[AI]:"
    }
    
    Text(
        text = "$rolePrefix ${message.content}",
        style = fontStyle.bodySmall,
        color = if (message.isUser) themeColors.primary else themeColors.textSecondary,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun SubtitleDisplay(
    currentUserQuestion: String,
    currentAIAnswer: String,
    subtitleHistory: List<VoiceCallViewModel.SubtitleMessage>,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    // 详细调试信息
    Log.d("SubtitleDisplay", "=== 渲染字幕组件 ===")
    Log.d("SubtitleDisplay", "currentUserQuestion: '$currentUserQuestion'")
    Log.d("SubtitleDisplay", "currentAIAnswer: '$currentAIAnswer'")
    Log.d("SubtitleDisplay", "subtitleHistory数量: ${subtitleHistory.size}")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = themeColors.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        // 当前对话字幕
        if (currentUserQuestion.isNotEmpty() || currentAIAnswer.isNotEmpty()) {
            Log.d("SubtitleDisplay", "显示当前对话字幕")
            
            // 用户问题（右侧）
            if (currentUserQuestion.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 280.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColors.primary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = currentUserQuestion,
                            style = fontStyle.bodyMedium,
                            color = themeColors.onPrimary,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = fontStyle.bodyMedium.lineHeight * 1.2f
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // AI回答（左侧）
            if (currentAIAnswer.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 280.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = themeColors.surface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = currentAIAnswer,
                            style = fontStyle.bodyMedium,
                            color = themeColors.onSurface,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = fontStyle.bodyMedium.lineHeight * 1.2f,
                            maxLines = 10,  // 增加最大行数
                            overflow = TextOverflow.Visible  // 确保文本不被截断
                        )
                    }
                }
            }
        } else {
            Log.d("SubtitleDisplay", "当前对话字幕为空，不显示内容")
        }
        
        // 字幕历史记录
        if (subtitleHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "对话历史:",
                style = fontStyle.bodySmall,
                color = themeColors.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(subtitleHistory.takeLast(10).reversed()) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 200.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (message.isUser) themeColors.primary else themeColors.surface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = message.content,
                                style = fontStyle.bodySmall,
                                color = if (message.isUser) themeColors.onPrimary else themeColors.onSurface,
                                modifier = Modifier.padding(8.dp),
                                lineHeight = fontStyle.bodySmall.lineHeight * 1.1f
                            )
                        }
                    }
                }
            }
        }
    }
}