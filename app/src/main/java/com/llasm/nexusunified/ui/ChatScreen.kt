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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import com.llasm.nexusunified.ui.SettingsManager

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
    var recordingTime by remember { mutableStateOf(0) } // 录音时长（秒）- 参照CSDN文章
    var countdown by remember { mutableStateOf(0) } // 倒计时（剩余秒数，0表示不显示）- 参照CSDN文章
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
    
    // 处理录音状态变化 - 使用rememberCoroutineScope避免作用域问题
    val scope = rememberCoroutineScope()
    
    // 设置ASR录音回调 - 参照WXSoundRecord和CSDN文章实现
    LaunchedEffect(asrService) {
        asrService.setRecordingCallback(object : com.llasm.nexusunified.service.RecordingCallback {
            override fun onRecordingStart() {
                android.util.Log.d("ChatScreen", "录音开始回调")
                isRecording = true
                recordingTime = 0
                countdown = 0
            }
            
            override fun onRecordingStop(audioData: ByteArray?) {
                android.util.Log.d("ChatScreen", "录音停止回调，音频数据大小: ${audioData?.size ?: 0}")
                isRecording = false
                recordingTime = 0
                countdown = 0
                // 录音停止时保持在语音模式，不自动切换回文字模式
                android.util.Log.d("ChatScreen", "录音停止，当前isVoiceMode=${isVoiceMode}，保持语音模式")
            }
            
            override fun onRecordingCancel() {
                android.util.Log.d("ChatScreen", "录音取消回调，保持在语音模式")
                isRecording = false
                recordingTime = 0
                countdown = 0
                // 取消录音后也保持在语音模式，不切换回文字模式
            }
            
            override fun onRecordingTimeUpdate(seconds: Int) {
                recordingTime = seconds
            }
            
            override fun onRecordingCountdown(remainingSeconds: Int) {
                countdown = remainingSeconds
            }
            
            override fun onRecordingTimeout() {
                android.util.Log.d("ChatScreen", "录音超时回调")
                isRecording = false
                recordingTime = 0
                countdown = 0
                // 超时自动停止录音并发送（参照CSDN文章）
                val audioData = asrService.stopRecording()
                if (audioData != null && audioData.isNotEmpty()) {
                    viewModel.startASRRecognition()
                    viewModel.updateASRRecognizingText("正在识别中..")
                    scope.launch {
                        try {
                            val transcription = asrService.transcribeAudio(audioData)
                            if (transcription != null && transcription.isNotEmpty()) {
                                viewModel.sendStreamingMessage(transcription)
                            } else {
                                android.util.Log.w("ChatScreen", "转录结果为空")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatScreen", "转录失败: ${e.message}", e)
                        } finally {
                            // 超时发送完成后也保持在语音模式，不自动切换回文字模式
                            viewModel.completeASRRecognition()
                        }
                    }
                } else {
                    // 音频数据为空时也保持在语音模式，用户可以继续录音
                }
            }
        })
    }
    
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
    
    // 移除了冲突的LaunchedEffect，录音逻辑现在完全由onHoldToSpeak控制
    
    // 移除了第二个冲突的LaunchedEffect
    
    // 处理TTS播放
    LaunchedEffect(Unit) {
        // 这里可以添加TTS播放逻辑
    }
    
    // 保护isVoiceMode状态：确保在发送完成后不会被意外重置
    // 使用一个标志来跟踪是否应该保持在语音模式
    var shouldStayInVoiceMode by remember { mutableStateOf(false) }
    
    // 监听状态变化，确保发送完成后保持在语音模式
    // 注意：只有在用户没有手动切换到文字模式时才保持语音模式
    LaunchedEffect(isASRRecognizing, isLoading, isStreaming, isVoiceMode) {
        // 当开始ASR识别时，标记应该保持在语音模式
        if (isASRRecognizing) {
            shouldStayInVoiceMode = true
            android.util.Log.d("ChatScreen", "ASR识别开始，标记shouldStayInVoiceMode=true")
        }
        
        // 当ASR识别完成、AI处理完成时，确保保持在语音模式
        // 但只有在当前已经是语音模式时才强制保持，避免覆盖用户的手动切换
        if (!isASRRecognizing && !isLoading && !isStreaming && shouldStayInVoiceMode && isVoiceMode) {
            android.util.Log.d("ChatScreen", "状态检查：ASR识别完成，AI处理完成，保持语音模式，当前isVoiceMode=${isVoiceMode}")
            // 如果用户已经切换到文字模式，不要强制切换回语音模式
            // 这里不再强制设置isVoiceMode，让用户的选择优先
        } else if (!isASRRecognizing && !isLoading && !isStreaming && !isVoiceMode) {
            // 如果用户已经切换到文字模式，重置标志
            if (shouldStayInVoiceMode) {
                android.util.Log.d("ChatScreen", "用户已切换到文字模式，重置shouldStayInVoiceMode")
                shouldStayInVoiceMode = false
            }
        }
    }
    
    // 当用户手动切换到语音模式时，也标记应该保持
    LaunchedEffect(isVoiceMode) {
        if (isVoiceMode) {
            android.util.Log.d("ChatScreen", "用户切换到语音模式，isVoiceMode=${isVoiceMode}")
            // 如果用户手动切换到语音模式，也标记应该保持
            shouldStayInVoiceMode = true
        } else {
            // 只有在用户手动切换回文字模式时，才重置标志
            if (shouldStayInVoiceMode) {
                android.util.Log.d("ChatScreen", "用户手动切换回文字模式，重置shouldStayInVoiceMode")
                shouldStayInVoiceMode = false
            }
        }
    }
    
    // 页面退出时停止音频播放
    DisposableEffect(Unit) {
        onDispose {
            // 无论播放状态如何，都尝试停止播放，确保资源清理
            viewModel.stopAudio()
        }
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
                    // 左上角：历史记录按钮（使用菜单图标）
                    IconButton(
                        onClick = { 
                            showHistoryDrawer = true
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu),
                            contentDescription = "历史对话",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                },
                actions = {
                    // 右上角按钮组
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 开始新对话按钮 - 深色主题样式
                        // 根据字体大小动态调整按钮高度和内边距，确保文字不被遮挡
                        val fontSize = fontStyle.bodyMedium.fontSize.value
                        val lineHeight = fontStyle.bodyMedium.lineHeight.value
                        // 按钮高度和内边距根据字体大小设置
                        val (buttonHeight, buttonVerticalPadding) = when {
                            fontSize <= 14f -> 36.dp to 6.dp  // 小字体
                            fontSize <= 20f -> 44.dp to 8.dp  // 中字体
                            else -> 56.dp to 10.dp  // 大字体：高度56dp，内边距10dp，确保48sp行高有足够空间
                        }
                        Button(
                            onClick = { 
                                // 开始新对话
                                viewModel.startNewConversation()
                                focusManager.clearFocus()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColors.surface,
                                contentColor = themeColors.textPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(buttonHeight),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = buttonVerticalPadding),
                            border = BorderStroke(1.dp, themeColors.cardBorder)
                        ) {
                            Text(
                                text = "新对话",
                                style = fontStyle.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 状态指示器已移除 - 不再显示AI处理时的绿色背景提示
            
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
                        // 语音输入逻辑 - 立即切换到语音模式（确保UI第一时间响应）
                        isVoiceMode = true
                        // 清除焦点，确保UI立即更新
                        focusManager.clearFocus()
                    }
                },
                onHoldToSpeak = { isHolding ->
                    if (isHolding) {
                        // 开始录音 - 参照WXSoundRecord和CSDN文章实现
                        android.util.Log.d("ChatScreen", "onHoldToSpeak: 开始录音")
                        val success = asrService.startRecording()
                        if (!success) {
                            android.util.Log.e("ChatScreen", "录音启动失败")
                            isVoiceMode = false
                            isRecording = false
                        }
                    } else {
                        // 停止录音并发送 - 参照WXSoundRecord和CSDN文章实现
                        android.util.Log.d("ChatScreen", "onHoldToSpeak: 停止录音")
                        val audioData = asrService.stopRecording()
                        
                        if (audioData != null && audioData.isNotEmpty()) {
                            viewModel.startASRRecognition()
                            viewModel.updateASRRecognizingText("正在识别中..")
                            
                            // 在开始识别前，确保isVoiceMode为true，并标记应该保持在语音模式
                            shouldStayInVoiceMode = true
                            isVoiceMode = true // 确保在语音模式
                            android.util.Log.d("ChatScreen", "开始ASR识别，设置shouldStayInVoiceMode=true，确保isVoiceMode=true")
                            
                            scope.launch {
                                try {
                                    val transcription = asrService.transcribeAudio(audioData)
                                    if (transcription != null && transcription.isNotEmpty()) {
                                        // 直接发送识别结果，使用流式输出
                                        viewModel.sendStreamingMessage(transcription)
                                    } else {
                                        android.util.Log.w("ChatScreen", "转录结果为空")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "转录失败: ${e.message}", e)
                                } finally {
                                    // 发送完成后保持在语音模式，不自动切换回文字模式
                                    android.util.Log.d("ChatScreen", "完成ASR识别，当前isVoiceMode=${isVoiceMode}，保持语音模式")
                                    viewModel.completeASRRecognition()
                                    // 确保保持在语音模式
                                    if (!isVoiceMode) {
                                        android.util.Log.w("ChatScreen", "检测到isVoiceMode被意外设置为false，重新设置为true")
                                        isVoiceMode = true
                                    }
                                    // 确保标志保持，直到LaunchedEffect处理
                                    shouldStayInVoiceMode = true
                                }
                            }
                        } else {
                            android.util.Log.w("ChatScreen", "音频数据为空，不发送")
                            // 音频数据为空时也保持在语音模式，用户可以继续录音
                        }
                    }
                },
                onCancelRecording = {
                    // 取消录音：停止录音但不发送 - 参照WXSoundRecord实现
                    // 取消录音后也保持在语音模式，不切换回文字模式
                    android.util.Log.d("ChatScreen", "onCancelRecording: 取消录音，保持在语音模式")
                    asrService.cancelRecording()
                    // 保持在语音模式，不设置isVoiceMode = false
                },
                isASRRecognizing = isASRRecognizing,
                asrRecognizingText = asrRecognizingText,
                recordingTime = recordingTime,
                countdown = countdown
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
                        text = "AI对话",
                        style = fontStyle.headlineSmall.copy(
                            fontSize = 48.sp, // 放大到48sp
                            fontWeight = FontWeight.Bold,
                            color = themeColors.primary // 使用主题颜色
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "人工智能对话体验",
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
                                onShowLoginDialog = onShowLoginDialog,
                                isTTSLoading = isTTSLoading,
                                loadingTTSMessageId = loadingTTSMessageId
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
            
            // 可拖动的悬浮按钮 - 电话模式入口
            DraggableFloatingActionButton(
                onClick = {
                    if (!isLoggedIn) {
                        onShowLoginDialog?.invoke()
                    } else {
                        // 添加日志确认点击
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
        onConversationSelected = { conversationId: String ->
            viewModel.selectConversation(conversationId)
            showHistoryDrawer = false
        },
        onConversationDeleted = { conversationId: String ->
            val conversation = conversations.find { it.id == conversationId }
            if (conversation != null) {
                conversationToDelete = conversation
                showConversationDeleteDialog = true
            }
        },
        onDismiss = { showHistoryDrawer = false },
        onSettingsClick = { showSettingsPage = true } // 点击设置按钮时打开设置页面
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
                TextButton(
                    onClick = {
                        messageToDelete?.let { msg ->
                            viewModel.deleteMessage(msg.id)
                        }
                        showDeleteConfirmDialog = false
                        messageToDelete = null
                    }
                ) {
                    Text(
                        text = "删除",
                        style = fontStyle.bodyMedium,
                        color = Color.Red
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
                TextButton(
                    onClick = {
                        conversationToDelete?.let { conv ->
                            viewModel.deleteConversation(conv.id)
                        }
                        showConversationDeleteDialog = false
                        conversationToDelete = null
                    }
                ) {
                    Text(
                        text = "删除",
                        style = fontStyle.bodyMedium,
                        color = Color.Red
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
