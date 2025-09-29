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
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val isSubtitlesEnabled by viewModel.isSubtitlesEnabled.collectAsStateWithLifecycle()
    val currentMessage by viewModel.currentMessage.collectAsStateWithLifecycle()
    val currentUserQuestion by viewModel.currentUserQuestion.collectAsStateWithLifecycle()
    val currentAIAnswer by viewModel.currentAIAnswer.collectAsStateWithLifecycle()
    val subtitleHistory by viewModel.subtitleHistory.collectAsStateWithLifecycle()
    
    // 初始化服务
    LaunchedEffect(Unit) {
        viewModel.initializeServices(context)
        viewModel.connectWebSocket()
    }
    
    // 处理返回键
    BackHandler {
        viewModel.hangupCall()
        onBackClick()
    }
    
    // 主题颜色
    val backgroundColor = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val surfaceColor = if (isDarkMode) Color(0xFF2D2D2D) else Color.White
    val primaryColor = themeColors.primary
    val onSurfaceColor = if (isDarkMode) Color.White else Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 顶部标题栏
            TopAppBar(
                title = {
                    Text(
                        text = "语音对话",
                        style = MaterialTheme.typography.headlineSmall,
                        color = onSurfaceColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.hangupCall()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = onSurfaceColor
                        )
                    }
                },
                actions = {
                    // 字幕切换按钮
                    IconButton(onClick = { viewModel.toggleSubtitles() }) {
                        Icon(
                            imageVector = if (isSubtitlesEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff,
                            contentDescription = if (isSubtitlesEnabled) "关闭字幕" else "开启字幕",
                            tint = if (isSubtitlesEnabled) primaryColor else onSurfaceColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 连接状态指示器
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "已连接" else "未连接",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 状态消息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor)
            ) {
                Text(
                    text = currentMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceColor
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 字幕显示区域
            if (isSubtitlesEnabled) {
                SubtitleDisplay(
                    currentUserQuestion = currentUserQuestion,
                    currentAIAnswer = currentAIAnswer,
                    subtitleHistory = subtitleHistory,
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // 控制按钮区域
            ControlButtons(
                isRecording = isRecording,
                isWaitingForResponse = isWaitingForResponse,
                isPlaying = isPlaying,
                isPaused = isPaused,
                isConnected = isConnected,
                onToggleRecording = { viewModel.toggleRecording() },
                onPauseCall = { viewModel.pauseCall() },
                onResumeCall = { viewModel.resumeCall() },
                onHangupCall = { 
                    viewModel.hangupCall()
                    onBackClick()
                },
                primaryColor = primaryColor,
                onSurfaceColor = onSurfaceColor
            )
        }
    }
}

@Composable
fun SubtitleDisplay(
    currentUserQuestion: String,
    currentAIAnswer: String,
    subtitleHistory: List<VoiceCallViewModel.SubtitleMessage>,
    surfaceColor: Color,
    onSurfaceColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "对话字幕",
                style = MaterialTheme.typography.titleMedium,
                color = onSurfaceColor,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 当前对话
            if (currentUserQuestion.isNotEmpty() || currentAIAnswer.isNotEmpty()) {
                // 用户问题
                if (currentUserQuestion.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 280.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = currentUserQuestion,
                                modifier = Modifier.padding(12.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // AI回答
                if (currentAIAnswer.isNotEmpty() && currentAIAnswer != "对话字幕已开启，等待对话...") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 280.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = currentAIAnswer,
                                modifier = Modifier.padding(12.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // 历史记录
            if (subtitleHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "历史记录",
                    style = MaterialTheme.typography.titleSmall,
                    color = onSurfaceColor,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subtitleHistory.reversed()) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.widthIn(max = 250.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (message.isUser) Color(0xFF2196F3) else Color(0xFF4CAF50)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = message.content,
                                    modifier = Modifier.padding(8.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButtons(
    isRecording: Boolean,
    isWaitingForResponse: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    isConnected: Boolean,
    onToggleRecording: () -> Unit,
    onPauseCall: () -> Unit,
    onResumeCall: () -> Unit,
    onHangupCall: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主要录音按钮
        Button(
            onClick = onToggleRecording,
            enabled = isConnected && !isPaused,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    isRecording -> Color(0xFFF44336)
                    isWaitingForResponse -> Color(0xFFFF9800)
                    isPlaying -> Color(0xFF4CAF50)
                    else -> primaryColor
                }
            )
        ) {
            Icon(
                imageVector = when {
                    isRecording -> Icons.Default.Stop
                    isWaitingForResponse -> Icons.Default.HourglassEmpty
                    isPlaying -> Icons.Default.VolumeUp
                    else -> Icons.Default.Mic
                },
                contentDescription = when {
                    isRecording -> "停止录音"
                    isWaitingForResponse -> "等待回复"
                    isPlaying -> "正在播放"
                    else -> "开始录音"
                },
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 按钮说明文字
        Text(
            text = when {
                isRecording -> "点击停止录音"
                isWaitingForResponse -> "等待AI回复..."
                isPlaying -> "正在播放AI回复"
                isPaused -> "通话已暂停"
                else -> "点击开始录音"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceColor,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 控制按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 暂停/恢复按钮
            if (isPaused) {
                Button(
                    onClick = onResumeCall,
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "恢复通话",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复")
                }
            } else {
                Button(
                    onClick = onPauseCall,
                    enabled = isConnected && !isRecording && !isWaitingForResponse,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "暂停通话",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("暂停")
                }
            }
            
            // 挂断按钮
            Button(
                onClick = onHangupCall,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "挂断通话",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("挂断")
            }
        }
    }
}