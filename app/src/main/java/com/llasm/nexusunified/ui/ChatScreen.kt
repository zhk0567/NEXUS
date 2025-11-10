package com.llasm.nexusunified.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.Job
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.data.Conversation
import com.llasm.nexusunified.viewmodel.ChatViewModel
import com.llasm.nexusunified.R
import com.llasm.nexusunified.ui.components.AnimatedPhoneButton
import com.llasm.nexusunified.ui.components.SyncStatusIndicator
import com.llasm.nexusunified.ui.components.DraggableFloatingActionButton
import com.llasm.nexusunified.service.ASRService
import com.llasm.nexusunified.service.ASRStatus
import com.llasm.nexusunified.service.TTSService
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.data.UserManager
import com.llasm.nexusunified.data.UserData
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onVoiceCallClick: () -> Unit = {},
    onShowLoginDialog: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingTTS by remember { mutableStateOf(false) }
    var isVoiceMode by remember { mutableStateOf(false) } // 语音模式状态
    var isTranscribing by remember { mutableStateOf(false) } // 语音识别中状态
    var asrStatus by remember { mutableStateOf(ASRStatus(false, 0, null, null, "unknown")) } // ASR状态
    var showHistoryDrawer by remember { mutableStateOf(false) } // 历史对话抽屉状态
    var showLoginDialog by remember { mutableStateOf(false) } // 登录对话框状态
    var showSettingsPage by remember { mutableStateOf(false) } // 设置页面状态
    var showAccountSettings by remember { mutableStateOf(false) } // 账号设置页面状态
    var showVoiceSettings by remember { mutableStateOf(false) } // 音调选择页面状态
    var showThemeSettings by remember { mutableStateOf(false) } // 主题设置页面状态
    var showAboutPage by remember { mutableStateOf(false) } // 关于页面状态
    var showDeleteConfirmDialog by remember { mutableStateOf(false) } // 删除确认对话框状态
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) } // 待删除的消息
    var showConversationDeleteDialog by remember { mutableStateOf(false) } // 对话删除确认对话框状态
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) } // 待删除的对话
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // 设置状态监听
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    
    // 确保主题状态正确初始化
    LaunchedEffect(Unit) {
        SettingsManager.initializeTheme(context)
    }
    
    // 检查登录状态
    val isLoggedIn = UserManager.isLoggedIn()
    
    // 如果未登录，显示登录对话框
    if (!isLoggedIn && onShowLoginDialog != null) {
        LaunchedEffect(Unit) {
            onShowLoginDialog()
        }
    }
    
    
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 初始化ASR和TTS服务
    val asrService = remember { ASRService(context) }
    val ttsService = remember { TTSService.getInstance(context) }
    
    // 确保TTS服务正确初始化
    LaunchedEffect(Unit) {
        // 给TTS一些时间来初始化
        delay(1000)
    }
    
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val isTTSLoading by viewModel.isTTSLoading.collectAsStateWithLifecycle()
    val loadingTTSMessageId by viewModel.loadingTTSMessageId.collectAsStateWithLifecycle()
    val isASRRecognizing by viewModel.isASRRecognizing.collectAsStateWithLifecycle()
    val asrRecognizingText by viewModel.asrRecognizingText.collectAsStateWithLifecycle()
    
    // 流式对话状态
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val currentStreamingMessage by viewModel.currentStreamingMessage.collectAsStateWithLifecycle()
    val isStreamingRequestStarted by viewModel.isStreamingRequestStarted.collectAsStateWithLifecycle()
    
    // 历史对话状态
    val conversations by viewModel.getAllConversations().collectAsStateWithLifecycle()
    val currentConversationId by viewModel.getCurrentConversationId().collectAsStateWithLifecycle()

    // 初始化AI服务
    LaunchedEffect(Unit) {
        viewModel.initializeAIService(context)
    }
    
    // 处理录音状态变化 - 使用rememberCoroutineScope避免作用域取消
    val scope = rememberCoroutineScope()
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    
    // 移除了冲突的LaunchedEffect，录音逻辑现在完全由onHoldToSpeak控制
    
    // 移除了第二个冲突的LaunchedEffect
    
    // 处理TTS播放
    LaunchedEffect(Unit) {
        // 这里可以添加TTS播放逻辑
    }
    
    Scaffold(
        modifier = Modifier.background(themeColors.background),
        containerColor = themeColors.background, // 设置容器背景色
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface,
                    titleContentColor = themeColors.onSurface
                ),
                title = {
                    // 移除NEXUS logo，留空或显示其他内容
                    Spacer(modifier = Modifier.width(0.dp))
                },
                navigationIcon = {
                    // 左上角：设置按钮和历史记录按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                        // 设置按钮
                     IconButton(
                         onClick = { 
                                showSettingsPage = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = themeColors.onSurface,
                                modifier = Modifier.size(fontStyle.iconSize.dp)
                            )
                        }
                        
                        // 历史记录按钮
                        IconButton(
                            onClick = { 
                                showHistoryDrawer = true
                         }
                     ) {
                         Icon(
                             imageVector = Icons.Default.History,
                             contentDescription = "历史对话",
                                tint = themeColors.onSurface,
                                modifier = Modifier.size(fontStyle.iconSize.dp)
                            )
                        }
                    }
                },
                actions = {
                    // 右上角按钮组
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                     // 新话题按钮 - 深色主题样式
                     Button(
                         onClick = { 
                             // 开启新话题 - 开始新对话
                             viewModel.startNewConversation()
                             focusManager.clearFocus()
                         },
                         colors = ButtonDefaults.buttonColors(
                             containerColor = themeColors.surface,
                             contentColor = themeColors.textPrimary
                         ),
                         shape = RoundedCornerShape(8.dp),
                         modifier = Modifier.height(36.dp),
                         border = BorderStroke(1.dp, themeColors.cardBorder)
                     ) {
                         Text(
                             text = "新话题",
                             style = fontStyle.bodyMedium,
                             fontWeight = FontWeight.Medium
                         )
                     }
                 }
             }
            )
         },
         bottomBar = {
            // 状态指示器
            if (isRecording || isLoading || isStreaming || isTranscribing) {
                StatusIndicator(
                    isRecording = isRecording,
                    isLoading = isLoading,
                    isStreaming = isStreaming,
                    isTranscribing = isTranscribing,
                    streamingText = streamingText,
                    asrStatus = asrStatus
                )
            }
            
            // 微信样式的底部输入栏
            WeChatStyleInputBar(
                inputText = inputText,
                onInputTextChange = { newText -> inputText = newText },
                isRecording = isRecording,
                onRecordingChange = { newRecording -> isRecording = newRecording },
                isLoading = isLoading,
                isStreaming = isStreaming,
                isVoiceMode = isVoiceMode,
                onVoiceModeChange = { newVoiceMode -> isVoiceMode = newVoiceMode },
                themeColors = themeColors,
                fontStyle = fontStyle,
                onSendClick = {
                             if (!isLoggedIn) {
                                 onShowLoginDialog?.invoke()
                             } else {
                             if (isLoading || isStreaming) {
                                 if (isStreaming) {
                                     viewModel.stopStreaming()
                                 } else {
                                     viewModel.cancelCurrentRequest()
                                 }
                             } else if (inputText.isNotBlank()) {
                                 viewModel.sendStreamingMessageWithHistory(inputText)
                                 inputText = ""
                                 }
                             }
                         },
                onVoiceClick = {
                    if (!isLoggedIn) {
                        onShowLoginDialog?.invoke()
                    } else {
                    // 语音输入逻辑 - 切换到语音模式
                    android.util.Log.d("ChatScreen", "录音按钮被点击，切换到语音模式")
                    isVoiceMode = true
                    }
                },
                onHoldToSpeak = { isHolding ->
                    if (isHolding) {
                        android.util.Log.d("ChatScreen", "开始录音...")
                        val success = asrService.startRecording()
                        android.util.Log.d("ChatScreen", "录音启动结果: $success")
                        if (success) {
                            isRecording = true
                            android.util.Log.d("ChatScreen", "录音状态设置为true")
                        } else {
                            android.util.Log.e("ChatScreen", "录音启动失败")
                        }
                    } else {
                        isRecording = false
                        val audioData = asrService.stopRecording()
                        
                        if (audioData != null && audioData.isNotEmpty()) {
                            viewModel.startASRRecognition()
                            viewModel.updateASRRecognizingText("正在识别中...")
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val transcription = asrService.transcribeAudio(audioData)
                                    if (transcription != null && transcription.isNotEmpty()) {
                                        // 直接发送识别结果，使用流式输出
                                        viewModel.sendStreamingMessage(transcription)
                                    }
                                } catch (e: Exception) {
                                    // 忽略异常
                                } finally {
                                    isVoiceMode = false
                                    viewModel.completeASRRecognition()
                                }
                            }
                        } else {
                            isVoiceMode = false
                        }
                    }
                },
                isASRRecognizing = isASRRecognizing,
                asrRecognizingText = asrRecognizingText
            )
         }
    ) { paddingValues ->
        // 聊天消息列表 - 添加可点击背景来取消焦点
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
        ) {
            // 如果消息列表为空，显示中央标题
            if (messages.isEmpty() && !isLoading && error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(themeColors.background)
                        .padding(16.dp)
                        .offset(y = (-40).dp), // 上移40dp，调整视觉中心
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NEXUS",
                        style = fontStyle.headlineSmall.copy(
                            fontSize = 48.sp, // 放大到48sp
                            fontWeight = FontWeight.Bold,
                            color = themeColors.primary // 使用主题颜色
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "智能AI对话体验",
                        style = fontStyle.titleMedium.copy(
                            fontSize = 20.sp // 放大到20sp
                        ),
                        color = themeColors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(themeColors.background)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(
                            message = message,
                            viewModel = viewModel,
                            isLoading = isLoading,
                            isStreaming = isStreaming,
                            currentStreamingMessage = currentStreamingMessage,
                            playingMessageId = playingMessageId,
                            ttsService = ttsService,
                            isPlayingTTS = isPlayingTTS,
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onPlayingStateChange = { isPlaying -> isPlayingTTS = isPlaying },
                            onLongPress = { msg ->
                                messageToDelete = msg
                                showDeleteConfirmDialog = true
                            },
                            isLoggedIn = isLoggedIn,
                            onShowLoginDialog = onShowLoginDialog,
                            isTTSLoading = isTTSLoading,
                            loadingTTSMessageId = loadingTTSMessageId
                        )
                    }
                    
                    // 显示流式消息
                    if (isStreaming && currentStreamingMessage != null) {
                        item {
                            StreamingMessageItem(
                                message = currentStreamingMessage!!,
                                streamingText = streamingText,
                                isStreamingRequestStarted = isStreamingRequestStarted,
                                viewModel = viewModel,
                                playingMessageId = playingMessageId,
                                themeColors = themeColors,
                                fontStyle = fontStyle,
                                onLongPress = { msg ->
                                    messageToDelete = msg
                                    showDeleteConfirmDialog = true
                                },
                                isLoggedIn = isLoggedIn,
                                onShowLoginDialog = onShowLoginDialog
                            )
                        }
                    }
                
                if (isLoading) {
                    item {
                        LoadingMessage(themeColors = themeColors, fontStyle = fontStyle)
                    }
                }
                
                if (error != null) {
                    item {
                        ErrorMessage(error = error!!, themeColors = themeColors, fontStyle = fontStyle)
                    }
                }
                }
            }
            
            // 可拖动的悬浮球 - 电话模式入口
            DraggableFloatingActionButton(
                onClick = {
                    if (!isLoggedIn) {
                        onShowLoginDialog?.invoke()
                    } else {
                    // 添加日志确认点击
                    android.util.Log.d("ChatScreen", "电话模式按钮被点击")
                    onVoiceCallClick()
                    }
                },
                containerColor = Color(0xFF424242), // 黑灰色
                contentColor = Color.White
            )
        }
    }
    
    // 历史对话抽屉 - 始终渲染，通过动画控制可见性
    HistoryDrawer(
        isVisible = showHistoryDrawer,
            conversations = conversations,
            currentConversationId = currentConversationId,
        themeColors = themeColors,
        fontStyle = fontStyle,
            onConversationSelected = { conversationId ->
                viewModel.selectConversation(conversationId)
            showHistoryDrawer = false
            },
            onConversationDeleted = { conversationId ->
            val conversation = conversations.find { it.id == conversationId }
            if (conversation != null) {
                conversationToDelete = conversation
                showConversationDeleteDialog = true
            }
        },
        onDismiss = { showHistoryDrawer = false }
    )
    
    // 登录对话框
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { 
                showLoginDialog = false
                // 可以在这里添加登录成功后的逻辑
            }
        )
    }
    
    // 设置页面
    if (showSettingsPage) {
        SettingsPage(
            onBackClick = { showSettingsPage = false },
            onNavigateToAccount = { 
                showSettingsPage = false
                showAccountSettings = true
            },
            onNavigateToVoice = { 
                showSettingsPage = false
                showVoiceSettings = true
            },
            onNavigateToTheme = { 
                showSettingsPage = false
                showThemeSettings = true
            },
            onNavigateToAbout = { 
                showSettingsPage = false
                showAboutPage = true
            },
            onLogoutClick = { 
                showSettingsPage = false
                // 登出后显示登录对话框
                onShowLoginDialog?.invoke()
            }
        )
    }
    
    // 账号设置页面
    if (showAccountSettings) {
        AccountSettingsPage(
            onBackClick = { 
                showAccountSettings = false
                showSettingsPage = true
            },
            onShowLoginDialog = {
                showAccountSettings = false
                showSettingsPage = false
                onShowLoginDialog?.invoke()
            }
        )
    }
    
    // 音调选择页面
    if (showVoiceSettings) {
        VoiceSettingsPage(
            onBackClick = { 
                showVoiceSettings = false
                showSettingsPage = true
            }
        )
    }
    
    // 主题设置页面
    if (showThemeSettings) {
        ThemeSettingsPage(
            onBackClick = { 
                showThemeSettings = false
                showSettingsPage = true
            }
        )
    }
    
    
    // 关于页面
    if (showAboutPage) {
        AboutPage(
            onBackClick = { 
                showAboutPage = false
                showSettingsPage = true
            }
        )
    }
    
    // 删除确认对话框
    if (showDeleteConfirmDialog && messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                messageToDelete = null
            },
            title = {
                Text(
                    text = "删除消息",
                    style = fontStyle.headlineSmall,
                    color = themeColors.textPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除这条消息吗？",
                    style = fontStyle.bodyMedium,
                    color = themeColors.textPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        messageToDelete?.let { message ->
                            viewModel.deleteMessage(message.id)
                        }
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text(
                        text = "删除",
                        style = fontStyle.bodyMedium,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                    }
                ) {
                    Text(
                        text = "取消",
                        style = fontStyle.bodyMedium,
                        color = themeColors.textSecondary
                    )
                }
            }
        )
    }
    
    // 对话删除确认对话框
    if (showConversationDeleteDialog && conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showConversationDeleteDialog = false
                conversationToDelete = null
            },
            title = {
                Text(
                    text = "删除对话",
                    style = fontStyle.headlineSmall,
                    color = themeColors.textPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "确定要删除这个对话吗？",
                        style = fontStyle.bodyMedium,
                        color = themeColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "对话标题：${conversationToDelete?.title ?: "未知"}",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                    Text(
                        text = "消息数量：${conversationToDelete?.messages?.size ?: 0} 条",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        conversationToDelete?.let { conversation ->
                            viewModel.deleteConversation(conversation.id)
                        }
                        showConversationDeleteDialog = false
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text(
                        text = "删除",
                        style = fontStyle.bodyMedium,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConversationDeleteDialog = false
                        conversationToDelete = null
                    }
                ) {
                    Text(
                        text = "取消",
                        style = fontStyle.bodyMedium,
                        color = themeColors.textSecondary
                    )
                }
            }
        )
    }
}

// 微信样式的底部输入栏
@Composable
fun WeChatStyleInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isRecording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    isLoading: Boolean,
    isStreaming: Boolean,
    isVoiceMode: Boolean,
    onVoiceModeChange: (Boolean) -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onSendClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onHoldToSpeak: (Boolean) -> Unit,
    isASRRecognizing: Boolean = false,
    asrRecognizingText: String = ""
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp), // 移除padding
        colors = CardDefaults.cardColors(
            containerColor = themeColors.inputBackground
        ),
        shape = RoundedCornerShape(0.dp), // 移除圆角
        border = null, // 明确设置无边框
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp // 无阴影
        )
    ) {
        if (isVoiceMode) {
            // 语音模式：显示按住说话按钮
            VoiceModeInputBar(
                isRecording = isRecording,
                isLoading = isLoading,
                isStreaming = isStreaming,
                themeColors = themeColors,
                fontStyle = fontStyle,
                onHoldToSpeak = onHoldToSpeak,
                onVoiceModeChange = onVoiceModeChange,
                isASRRecognizing = isASRRecognizing,
                asrRecognizingText = asrRecognizingText
            )
        } else {
            // 文字模式：显示正常的输入框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 左侧语音按钮 - 添加禁用状态和更好的视觉反馈
                IconButton(
                    onClick = onVoiceClick,
                    enabled = !isLoading && !isStreaming, // 在AI处理期间禁用
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            when {
                                isRecording -> Color(0xFFFF5722) // 录音中 - 橙色
                                isLoading || isStreaming -> Color(0xFFE0E0E0) // 禁用状态 - 灰色
                                else -> Color.Transparent // 正常状态 - 透明
                            },
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = when {
                            isRecording -> "停止录音"
                            isLoading || isStreaming -> "AI处理中，请稍候"
                            else -> "开始录音"
                        },
                        tint = when {
                            isRecording -> Color.White // 录音中 - 白色
                            isLoading || isStreaming -> themeColors.textSecondary // 禁用状态 - 主题次要颜色
                            else -> themeColors.textSecondary // 正常状态 - 主题次要颜色
                        },
                        modifier = Modifier.size(fontStyle.iconSize.dp * 1.2f)
                    )
                }
            
            // 中间输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { newText ->
                    // 限制输入字符数量，最多1000个字符
                    if (newText.length <= 1000 && !isLoading && !isStreaming) {
                        onInputTextChange(newText)
                    }
                },
                enabled = !isLoading && !isStreaming,
                placeholder = {
                    Text(
                        text = "输入消息...",
                        color = themeColors.textSecondary,
                        style = fontStyle.bodyMedium
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp), // 支持多行，最小40dp，最大120dp
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeColors.primary,
                    unfocusedBorderColor = themeColors.textSecondary.copy(alpha = 0.3f),
                    focusedTextColor = themeColors.textPrimary,
                    unfocusedTextColor = themeColors.textPrimary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                textStyle = fontStyle.bodyMedium,
                maxLines = 5, // 支持最多5行
                singleLine = false // 多行模式
            )
            
            // 右侧发送按钮
            IconButton(
                onClick = onSendClick,
                enabled = inputText.isNotBlank() && !isLoading && !isStreaming,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (inputText.isNotBlank() && !isLoading && !isStreaming) 
                            themeColors.primary 
                        else 
                            themeColors.textSecondary.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = if (isLoading || isStreaming) "AI处理中" else "发送",
                    tint = if (inputText.isNotBlank() && !isLoading && !isStreaming) 
                        themeColors.onPrimary 
                    else 
                        themeColors.textSecondary,
                    modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                )
            }
            }
        }
    }
}

// 聊天消息项
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    viewModel: ChatViewModel,
    isLoading: Boolean,
    isStreaming: Boolean,
    currentStreamingMessage: ChatMessage?,
    playingMessageId: String?,
    ttsService: TTSService,
    isPlayingTTS: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onPlayingStateChange: (Boolean) -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    isLoggedIn: Boolean,
    onShowLoginDialog: (() -> Unit)?,
    isTTSLoading: Boolean,
    loadingTTSMessageId: String?
) {
    val isUser = message.isUser
    val isPlaying = playingMessageId == message.id
    val isLoadingTTS = loadingTTSMessageId == message.id
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // AI头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Color(0xFF07C160),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // 消息气泡
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .pointerInput(message.id) {
                    detectTapGestures(
                        onLongPress = {
                            // 长按显示删除确认对话框
                            onLongPress(message)
                        }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) themeColors.primary else themeColors.cardBackground
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) themeColors.onPrimary else themeColors.textPrimary,
                    style = fontStyle.bodyMedium
                )
                
                if (!isUser && !isStreaming && message.id != currentStreamingMessage?.id) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 播放按钮 - 加载状态和圆形收缩动画
                        var showPulse by remember { mutableStateOf(false) }
                        
                        // 省略号加载动画 - 波浪效果
                        val infiniteTransition = rememberInfiniteTransition(label = "loading")
                        val animationProgress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "progress"
                        )
                        
                        // 计算每个点的透明度，实现波浪效果
                        val dot1Alpha = when {
                            animationProgress < 0.33f -> 0.3f + (animationProgress / 0.33f) * 0.7f
                            animationProgress < 0.66f -> 1f - ((animationProgress - 0.33f) / 0.33f) * 0.7f
                            else -> 0.3f
                        }
                        
                        val dot2Alpha = when {
                            animationProgress < 0.33f -> 0.3f
                            animationProgress < 0.66f -> 0.3f + ((animationProgress - 0.33f) / 0.33f) * 0.7f
                            animationProgress < 1f -> 1f - ((animationProgress - 0.66f) / 0.34f) * 0.7f
                            else -> 0.3f
                        }
                        
                        val dot3Alpha = when {
                            animationProgress < 0.66f -> 0.3f
                            animationProgress < 1f -> 0.3f + ((animationProgress - 0.66f) / 0.34f) * 0.7f
                            else -> 0.3f
                        }
                        
                        // 播放时的圆形收缩动画
                        val breathingTransition = rememberInfiniteTransition(label = "breathing")
                        val breathingScale by breathingTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "breathing"
                        )
                        
                        // 脉冲效果
                        val pulseTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )
                        
                        // 处理准备播放效果
                        LaunchedEffect(isLoadingTTS) {
                            if (isLoadingTTS) {
                                showPulse = true
                                kotlinx.coroutines.delay(2000)
                                showPulse = false
                            }
                        }
                        
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 脉冲背景效果（加载时）
                            if (showPulse && isLoadingTTS) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .scale(pulseScale)
                                        .background(
                                            Color(0xFF4CAF50).copy(alpha = 0.3f),
                                            CircleShape
                                        )
                                )
                            }
                            
                            // 播放时的圆形收缩背景
                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .scale(breathingScale)
                                        .background(
                                            Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                )
                            }
                            
                            // 主按钮
                        IconButton(
                            onClick = { 
                                    if (!isLoggedIn) {
                                        onShowLoginDialog?.invoke()
                                    } else {
                                if (isPlaying) {
                                    viewModel.stopAudio()
                                } else {
                                viewModel.playAudioForMessage(message.id, message.content)
                                        }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                            ) {
                                if (isLoadingTTS) {
                                    // 加载状态 - 省略号动画
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 第一个点
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    Color(0xFF4CAF50).copy(alpha = dot1Alpha),
                                                    CircleShape
                                                )
                                        )
                                        // 第二个点
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    Color(0xFF4CAF50).copy(alpha = dot2Alpha),
                                                    CircleShape
                                                )
                                        )
                                        // 第三个点
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    Color(0xFF4CAF50).copy(alpha = dot3Alpha),
                                                    CircleShape
                                                )
                                        )
                                    }
                                } else {
                                    // 正常状态
                                Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                contentDescription = if (isPlaying) "停止播放" else "播放语音",
                                        tint = if (isPlaying) Color(0xFF4CAF50) else themeColors.primary,
                                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                            )
                                }
                            }
                        }
                        
                        // 刷新按钮
                        IconButton(
                            onClick = { 
                                viewModel.refreshLastAIResponse()
                            },
                            modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新回答",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                            )
                        }
                    }
                }
            }
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // 用户头像
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        Color(0xFF2196F3),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "用户",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 流式消息项
@Composable
fun StreamingMessageItem(
    message: ChatMessage,
    streamingText: String,
    isStreamingRequestStarted: Boolean,
    viewModel: ChatViewModel,
    playingMessageId: String?,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onLongPress: (ChatMessage) -> Unit,
    isLoggedIn: Boolean,
    onShowLoginDialog: (() -> Unit)?
) {
    val isPlaying = playingMessageId == message.id
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI头像
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Color(0xFF07C160),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "AI",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        // 消息气泡
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .pointerInput(message.id) {
                    detectTapGestures(
                        onLongPress = {
                            // 长按显示删除确认对话框
                            onLongPress(message)
                        }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = themeColors.cardBackground
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 显示内容：流式请求开始时显示"请稍候"，否则显示流式文本
                if (isStreamingRequestStarted && streamingText.isEmpty()) {
                    Text(
                        text = "请稍候，数据请求中......",
                        color = themeColors.textSecondary,
                        style = fontStyle.bodyMedium
                    )
                } else {
                Text(
                    text = streamingText,
                        color = themeColors.textPrimary,
                        style = fontStyle.bodyMedium
                )
                }
                
                // 只在流式完成后显示按钮
                if (streamingText.isNotEmpty() && !viewModel.isStreaming.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // 播放按钮 - 改进的点击效果
                    var isPreparingTTS by remember { mutableStateOf(false) }
                    var showPulse by remember { mutableStateOf(false) }
                    
                    // 动画效果
                    val scale by animateFloatAsState(
                        targetValue = if (isPreparingTTS) 1.3f else 1f,
                        animationSpec = tween(200),
                        label = "scale"
                    )
                    
                    val alpha by animateFloatAsState(
                        targetValue = if (isPreparingTTS) 0.8f else 1f,
                        animationSpec = tween(200),
                        label = "alpha"
                    )
                    
                    // 脉冲效果
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    
                    // 处理准备播放效果
                    LaunchedEffect(isPreparingTTS) {
                        if (isPreparingTTS) {
                            showPulse = true
                            kotlinx.coroutines.delay(1500)
                            isPreparingTTS = false
                            showPulse = false
                        }
                    }
                    
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 脉冲背景效果
                        if (showPulse) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .scale(pulseScale)
                                    .background(
                                        Color(0xFF4CAF50).copy(alpha = 0.3f),
                                        CircleShape
                                    )
                            )
                        }
                        
                        // 主按钮
                    IconButton(
                        onClick = {
                                if (!isLoggedIn) {
                                    onShowLoginDialog?.invoke()
                                } else {
                            if (isPlaying) {
                                viewModel.stopAudio()
                            } else {
                                        // 显示准备播放效果
                                        isPreparingTTS = true
                                viewModel.playAudioForMessage(message.id, message.content)
                                    }
                            }
                        },
                            modifier = Modifier
                                .size(32.dp)
                                .scale(scale)
                                .alpha(alpha)
                    ) {
                        Icon(
                                imageVector = Icons.Default.VolumeUp,
                            contentDescription = if (isPlaying) "停止播放" else "播放语音",
                                tint = if (isPreparingTTS) Color(0xFF4CAF50) else themeColors.primary,
                                modifier = Modifier
                                    .size(fontStyle.iconSize.dp * 0.8f)
                                    .background(
                                        if (isPreparingTTS) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                            )
                        }
                    }
                    
                    // 刷新按钮
                    IconButton(
                        onClick = {
                                viewModel.refreshLastAIResponse()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新回答",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                        )
                        }
                    }
                }
            }
        }
    }
}

// 加载消息
@Composable
fun LoadingMessage(themeColors: ThemeColors, fontStyle: FontStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI头像
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Color(0xFF07C160),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "AI",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        // 加载气泡
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.cardBackground
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI正在思考中...",
                    color = themeColors.textSecondary,
                    style = fontStyle.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = themeColors.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

// 错误消息
@Composable
fun ErrorMessage(error: String, themeColors: ThemeColors, fontStyle: FontStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI头像
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Color(0xFFFF5722),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "错误",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        // 错误气泡
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (themeColors.background == Color(0xFFF5F5F5)) 
                    Color(0xFFFFEBEE) else Color(0xFF4A2C2C)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "错误: $error",
                color = if (themeColors.background == Color(0xFFF5F5F5)) 
                    Color(0xFFD32F2F) else Color(0xFFFF6B6B),
                style = fontStyle.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun StatusIndicator(
    isRecording: Boolean,
    isLoading: Boolean,
    isStreaming: Boolean,
    isTranscribing: Boolean,
    streamingText: String,
    asrStatus: ASRStatus = ASRStatus(false, 0, null, null, "unknown")
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecording -> Color(0xFFFF5722).copy(alpha = 0.1f)
                isLoading -> Color(0xFF2196F3).copy(alpha = 0.1f)
                isStreaming -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                else -> Color(0xFFF5F5F5)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        when {
                            isRecording -> Color(0xFFFF5722)
                            isTranscribing -> Color(0xFFFF9800)
                            isLoading -> Color(0xFF2196F3)
                            isStreaming -> Color(0xFF4CAF50)
                            else -> Color(0xFF9E9E9E)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isRecording -> Icons.Default.Mic
                        isTranscribing -> Icons.Default.Info
                        isLoading -> Icons.Default.Refresh
                        isStreaming -> Icons.Default.PlayArrow
                        else -> Icons.Default.Info
                    },
                    contentDescription = "状态",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // 状态文本
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when {
                        isRecording -> "🎤 正在录音..."
                        isTranscribing -> "🎯 语音识别中... ${asrStatus.progress}%"
                        isLoading -> "🤖 AI正在思考..."
                        isStreaming -> "💬 AI正在回复..."
                        else -> "就绪"
                    },
                    color = when {
                        isRecording -> Color(0xFFFF5722)
                        isTranscribing -> Color(0xFFFF9800)
                        isLoading -> Color(0xFF2196F3)
                        isStreaming -> Color(0xFF4CAF50)
                        else -> Color(0xFF666666)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                // ASR进度条
                if (isTranscribing && asrStatus.progress > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = asrStatus.progress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFFFF9800),
                        trackColor = Color(0xFFFF9800).copy(alpha = 0.3f)
                    )
                    
                    // 处理时间显示
                    asrStatus.processingTime?.let { time ->
                        Text(
                            text = "处理时间: ${String.format("%.1f", time)}s",
                            color = Color(0xFF666666),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // 流式文本预览
                if (isStreaming && streamingText.isNotBlank()) {
                    Text(
                        text = streamingText.take(50) + if (streamingText.length > 50) "..." else "",
                        color = Color(0xFF666666),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 动画指示器
            if (isRecording || isLoading || isStreaming) {
                val infiniteTransition = rememberInfiniteTransition(label = "status")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when {
                                isRecording -> Color(0xFFFF5722)
                                isLoading -> Color(0xFF2196F3)
                                isStreaming -> Color(0xFF4CAF50)
                                else -> Color(0xFF9E9E9E)
                            }.copy(alpha = alpha),
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
fun VoiceModeInputBar(
    isRecording: Boolean,
    isLoading: Boolean,
    isStreaming: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onHoldToSpeak: (Boolean) -> Unit,
    onVoiceModeChange: (Boolean) -> Unit,
    isASRRecognizing: Boolean = false,
    asrRecognizingText: String = ""
) {
    // 简化的录音状态管理
    var isHolding by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var startY by remember { mutableStateOf(0f) }
    var currentY by remember { mutableStateOf(0f) }
    
    // 计算是否应该取消录音（上滑超过阈值）
    val shouldCancel = isHolding && (currentY - startY) < -60f
    
    // 监听取消状态变化
    LaunchedEffect(isCancelling) {
        if (isCancelling && isRecording) {
            android.util.Log.d("ChatScreen", "=== 检测到取消状态，停止录音 ===")
            onHoldToSpeak(false)
        }
    }
    
    // 简化的录音界面
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 返回文字模式按钮
        IconButton(
            onClick = { onVoiceModeChange(false) },
            enabled = !isLoading && !isStreaming && !isASRRecognizing,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "返回文字模式",
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 按住说话按钮 - 简化版本
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(
                    when {
                        isCancelling -> Color(0xFFFF5722) // 取消状态 - 红色
                        isRecording -> Color(0xFFFF5722) // 录音中 - 橙色
                        isLoading || isStreaming || isASRRecognizing -> Color(0xFFE0E0E0) // 禁用状态 - 灰色
                        else -> Color(0xFF07C160) // 正常状态 - 微信绿
                    },
                    RoundedCornerShape(20.dp)
                )
                .pointerInput(isLoading, isStreaming, isASRRecognizing) {
                    if (!isLoading && !isStreaming && !isASRRecognizing) {
                        detectTapGestures(
                            onPress = { offset ->
                                android.util.Log.d("ChatScreen", "=== 按下开始录音 ===")
                                onHoldToSpeak(true)
                                try {
                                    // 等待释放
                                    tryAwaitRelease()
                                    android.util.Log.d("ChatScreen", "=== 松开停止录音 ===")
                                    onHoldToSpeak(false)
                                } catch (e: Exception) {
                                    android.util.Log.d("ChatScreen", "=== 异常停止录音 ===")
                                    onHoldToSpeak(false)
                                }
                            }
                        )
                    } else {
                        android.util.Log.w("ChatScreen", "=== 按钮被禁用 ===")
                    }
                },
                // 移除了重复的detectDragGestures，按压和拖拽逻辑已合并
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 录音图标
                if (isRecording || isCancelling) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "录音中",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
            Text(
                text = when {
                        isCancelling -> "松开取消"
                        isRecording -> "松开结束"
                    isLoading || isStreaming -> "AI处理中..."
                        isASRRecognizing -> asrRecognizingText.ifEmpty { "正在识别中..." }
                        else -> "按住说话"
                },
                color = when {
                        isCancelling -> Color.White
                    isRecording -> Color.White
                        isLoading || isStreaming || isASRRecognizing -> themeColors.textSecondary
                    else -> Color.White
                },
                    style = fontStyle.bodyMedium,
                fontWeight = FontWeight.Medium
                )
                
                // 上滑取消提示
                if (isCancelling) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "↑ 上滑取消",
                        color = Color.White.copy(alpha = 0.8f),
                        style = fontStyle.bodySmall,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// 登录对话框组件
// 登录API调用函数
fun loginUser(username: String, password: String, callback: (Boolean, String?) -> Unit) {
    Thread {
        try {
            val url = URL(ServerConfig.getApiUrl(ServerConfig.Endpoints.AUTH_LOGIN))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonObject = JSONObject()
            jsonObject.put("username", username)
            jsonObject.put("password", password)
            jsonObject.put("device_info", "Android")
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            
            val responseText = response.toString()
            
            // 检查响应是否为HTML（错误页面）
            if (responseText.trim().startsWith("<!") || responseText.trim().startsWith("<html")) {
                callback(false, "服务器返回错误页面，请检查服务器地址是否正确")
                return@Thread
            }
            
            // 尝试解析JSON
            val jsonResponse = try {
                JSONObject(responseText)
            } catch (e: Exception) {
                callback(false, "服务器响应格式错误: ${e.message}")
                return@Thread
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK && jsonResponse.optBoolean("success", false)) {
                // 登录成功，保存用户信息
                val user = jsonResponse.getJSONObject("user")
                val sessionId = jsonResponse.getString("session_id")
                
                val userData = UserData(
                    userId = user.getString("user_id"),
                    username = user.getString("username"),
                    sessionId = sessionId,
                    createdAt = user.optString("created_at", null),
                    lastLoginAt = user.optString("last_login_at", null)
                )
                
                // 保存用户信息到SharedPreferences
                UserManager.saveLoginData(userData)
                
                callback(true, null)
            } else {
                val errorMessage = jsonResponse.optString("error", "登录失败")
                callback(false, errorMessage)
            }
            
        } catch (e: Exception) {
            callback(false, "网络连接失败: ${e.message}")
        }
    }.start()
}

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessMessage by remember { mutableStateOf(false) }
    
    // 获取当前字体样式
    val fontStyle = SettingsManager.getFontStyle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "用户登录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 用户名输入框
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "账号"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "密码"
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible }
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // 成功信息显示
                if (showSuccessMessage) {
                    Text(
                        text = "登录成功！",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 错误信息显示
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "请输入账号和密码"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = ""
                    
                    // 调用后端登录API
                    loginUser(username, password) { success, message ->
                        isLoading = false
                        if (success) {
                            showSuccessMessage = true
                            // 延迟关闭对话框，让用户看到成功提示
                            kotlinx.coroutines.GlobalScope.launch {
                                kotlinx.coroutines.delay(1500)
                                onLoginSuccess()
                            }
                        } else {
                            errorMessage = message ?: "登录失败"
                        }
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = fontStyle.bodyMedium)
            }
        }
    )
}


// 历史对话抽屉组件
@Composable
fun HistoryDrawer(
    isVisible: Boolean,
    conversations: List<Conversation>,
    currentConversationId: String?,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onConversationSelected: (String) -> Unit,
    onConversationDeleted: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val drawerWidth = screenWidth * 0.75f // 屏幕宽度的75%
    
    // 控制组件是否应该显示（用于动画完成后隐藏）
    var shouldShow by remember { mutableStateOf(false) }
    
    // 当isVisible变为true时，立即显示组件
    LaunchedEffect(isVisible) {
        if (isVisible) {
            shouldShow = true
        } else {
            // 延迟隐藏，等待动画完成
            kotlinx.coroutines.delay(500)
            shouldShow = false
        }
    }
    
    // 如果组件不应该显示，则不渲染
    if (!shouldShow) {
        return
    }
    
    // 添加一个延迟，确保组件完全渲染后再开始动画
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50) // 50ms延迟
        animationStarted = true
    }
    
    // 抽屉滑入滑出动画 - 使用tween动画确保稳定性
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible && animationStarted) 0f else -drawerWidth.value,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "drawerOffset"
    )
    
    // 调试信息
    LaunchedEffect(offsetX, isVisible, animationStarted) {
        android.util.Log.d("HistoryDrawer", "isVisible: $isVisible, animationStarted: $animationStarted, offsetX: $offsetX, drawerWidth: ${drawerWidth.value}")
    }
    
    // 抽屉容器
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 抽屉内容
        Card(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .offset(x = offsetX.dp),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "历史对话",
                        style = fontStyle.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeColors.textPrimary
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 对话列表
                if (conversations.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无历史对话",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                } else {
                    // 过滤掉空对话（没有消息的对话）
                    val nonEmptyConversations = conversations.filter { it.messages.isNotEmpty() }
                    
                    if (nonEmptyConversations.isEmpty()) {
                        // 如果没有非空对话，显示空状态
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "历史记录",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无历史对话",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(nonEmptyConversations.size) { index ->
                                val conversation = nonEmptyConversations[index]
                                
                                ConversationItem(
                                    conversation = conversation,
                                    isSelected = conversation.id == currentConversationId,
                                    themeColors = themeColors,
                                    fontStyle = fontStyle,
                                    onSelected = { onConversationSelected(conversation.id) },
                                    onDeleted = { onConversationDeleted(conversation.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // 右侧点击区域 - 使用pointerInput避免显示条条
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((screenWidth - drawerWidth))
                .offset(x = drawerWidth)
                .pointerInput(Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                }
        )
    }
}

// 对话项组件
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onSelected: () -> Unit,
    onDeleted: () -> Unit
) {
    // 获取第一个用户消息作为问题预览
    val firstUserMessage = conversation.messages.firstOrNull { it.isUser }
    val questionPreview = firstUserMessage?.content?.let { content ->
        if (content.length > 15) {
            content.take(15) + "..."
        } else {
            content
        }
    } ?: "新对话"
    
    // 智能格式化日期
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val conversationYear = java.util.Calendar.getInstance().apply {
        time = conversation.updatedAt
    }.get(java.util.Calendar.YEAR)
    
    val formattedDate = if (conversationYear == currentYear) {
        // 当前年份，只显示月日
        val monthDayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        monthDayFormat.format(conversation.updatedAt)
    } else {
        // 其他年份，显示年月日
        val fullDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        fullDateFormat.format(conversation.updatedAt)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColors.primary else themeColors.cardBackground
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：问题预览
            Text(
                text = questionPreview,
                style = fontStyle.bodyMedium,
                color = if (isSelected) themeColors.onPrimary else themeColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // 右侧：日期和删除按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日期显示
                Text(
                    text = formattedDate,
                    style = fontStyle.bodySmall,
                    color = if (isSelected) themeColors.onPrimary.copy(alpha = 0.7f) else themeColors.textSecondary
                )
                
                // 删除按钮
                IconButton(
                    onClick = onDeleted,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = if (isSelected) themeColors.onPrimary else themeColors.textSecondary,
                        modifier = Modifier.size(fontStyle.iconSize.dp * 0.6f)
                    )
                }
            }
        }
    }
}

// 稳定的系统跟随开关组件
@Composable
fun StableSystemToggleSwitch(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    fontStyle: FontStyle
) {
    // 使用稳定的动画，避免抖动
    val sliderOffset by animateDpAsState(
        targetValue = if (isEnabled) 4.dp else (-36.dp),
        animationSpec = tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "stable_slider_offset"
    )
    
    // 背景颜色动画
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
        animationSpec = tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "background_color"
    )
    
    // 文字颜色动画
    val textColor by animateColorAsState(
        targetValue = if (isEnabled) Color.White else Color(0xFF666666),
        animationSpec = tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "text_color"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                backgroundColor,
                RoundedCornerShape(24.dp)
            )
            .clickable { onToggle() }
    ) {
        // 背景文字 - 使用稳定的布局
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "关闭",
                modifier = Modifier.padding(start = 16.dp),
                style = fontStyle.bodyMedium,
                color = textColor
            )
            
            Text(
                text = "开启",
                modifier = Modifier.padding(end = 16.dp),
                style = fontStyle.bodyMedium,
                color = textColor
            )
        }
        
        // 滑动滑块 - 使用稳定的尺寸和位置
        Box(
            modifier = Modifier
                .size(40.dp)
                .offset(
                    x = sliderOffset,
                    y = 4.dp
                )
                .background(
                    Color.White,
                    RoundedCornerShape(20.dp)
                )
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(20.dp)
                )
        )
    }
}