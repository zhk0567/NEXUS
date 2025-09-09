package com.llasm.voiceassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llasm.voiceassistant.data.ChatMessage
import com.llasm.voiceassistant.data.MessageType
import com.llasm.voiceassistant.ui.components.VoiceInputButton
import com.llasm.voiceassistant.viewmodel.ChatViewModel
import com.llasm.voiceassistant.identity.UserManager
import com.llasm.voiceassistant.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var inputText by remember { mutableStateOf("") }
    var showUserRegistration by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()

    // 初始化语音服务和用户管理
    LaunchedEffect(Unit) {
        viewModel.initializeVoiceService(context)
        viewModel.initializeUserManager(context)
        viewModel.initializeHistoryManager(context)
    }
    
    // 获取用户管理器
    val userManager = remember { UserManager(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            ),
         topBar = {
             // 标题栏 - 透明背景
             Row(
                 modifier = Modifier
                     .fillMaxWidth()
                     .padding(horizontal = 16.dp, vertical = 12.dp),
                 horizontalArrangement = Arrangement.SpaceBetween,
                 verticalAlignment = Alignment.CenterVertically
             ) {
                 // 左上角图标组
                 Row(
                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     // 历史记录按钮
                     IconButton(
                         onClick = { 
                             showHistory = true
                         }
                     ) {
                         Image(
                             painter = painterResource(id = R.drawable.ic_history),
                             contentDescription = "历史记录",
                             modifier = Modifier.size(24.dp)
                         )
                     }
                     
                     // 新话题按钮
                     IconButton(
                         onClick = { 
                             // 保存当前对话到历史记录
                             viewModel.saveCurrentConversation()
                             // 开启新话题 - 清空消息列表
                             viewModel.clearMessages()
                             focusManager.clearFocus()
                         }
                     ) {
                         Icon(
                             imageVector = Icons.Default.Add,
                             contentDescription = "新话题",
                             tint = Color(0xFF424242),
                             modifier = Modifier.size(24.dp)
                         )
                     }
                 }
                 
                 // 右上角设置按钮
                 IconButton(
                     onClick = { 
                         showUserRegistration = true
                         println("Settings button clicked! showUserRegistration = $showUserRegistration")
                     }
                 ) {
                     Icon(
                         imageVector = Icons.Default.Settings,
                         contentDescription = "用户设置",
                         tint = Color(0xFF424242)
                     )
                 }
             }
         },
         bottomBar = {
             // 输入框和语音按钮 - 降低高度
             Card(
                 modifier = Modifier
                     .fillMaxWidth()
                     .shadow(2.dp, RoundedCornerShape(8.dp)),
                 colors = CardDefaults.cardColors(
                     containerColor = MaterialTheme.colorScheme.surface
                 ),
                 shape = RoundedCornerShape(8.dp)
             ) {
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(12.dp),
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     // 文本输入框 - 半圆角，增大尺寸
                     OutlinedTextField(
                         value = inputText,
                         onValueChange = { inputText = it },
                         placeholder = { 
                             Text(
                                 text = "输入消息...",
                                 style = MaterialTheme.typography.bodyMedium.copy(
                                     fontSize = 18.sp,
                                     lineHeight = 28.sp
                                 )
                             ) 
                         },
                         modifier = Modifier
                             .weight(1f)
                             .height(56.dp),
                         singleLine = true,
                         textStyle = MaterialTheme.typography.bodyMedium.copy(
                             lineHeight = 28.sp,
                             fontSize = 18.sp
                         ),
                         shape = RoundedCornerShape(28.dp), // 半圆角
                         colors = OutlinedTextFieldDefaults.colors(
                             focusedTextColor = Color(0xFF212121),
                             unfocusedTextColor = Color(0xFF212121),
                             cursorColor = Color(0xFF424242),
                             focusedPlaceholderColor = Color(0xFF9E9E9E),
                             unfocusedPlaceholderColor = Color(0xFF9E9E9E),
                             focusedBorderColor = Color(0xFF424242),
                             unfocusedBorderColor = Color(0xFFBDBDBD)
                         )
                     )
                     
                     Spacer(modifier = Modifier.width(8.dp))
                     
                     // 语音输入按钮 - 增大尺寸
                     VoiceInputButton(
                         isRecording = isRecording,
                         onStartRecording = { viewModel.startVoiceRecording() },
                         onStopRecording = { viewModel.stopVoiceRecording() },
                         modifier = Modifier.size(56.dp)
                     )
                     
                     Spacer(modifier = Modifier.width(8.dp))
                     
                     // 发送/停止按钮 - 增大尺寸
                     FloatingActionButton(
                         onClick = {
                             if (isLoading) {
                                 // 如果正在加载，点击停止请求
                                 viewModel.cancelCurrentRequest()
                             } else if (inputText.isNotBlank()) {
                                 // 如果不在加载且有输入内容，发送消息
                                 viewModel.sendMessage(inputText)
                                 inputText = ""
                             }
                         },
                         modifier = Modifier.size(56.dp),
                         containerColor = when {
                             isLoading -> Color(0xFF424242) // 深灰色
                             inputText.isNotBlank() -> Color(0xFF424242) // 深灰色
                             else -> Color(0xFFBDBDBD) // 浅灰色（禁用状态）
                         },
                     ) {
                         Icon(
                             imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Send,
                             contentDescription = if (isLoading) "停止" else "发送",
                             tint = if (isLoading || inputText.isNotBlank()) Color.White else Color(0xFF757575),
                             modifier = Modifier.size(28.dp)
                         )
                     }
                 }
             }
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
                        .padding(16.dp)
                        .offset(y = (-40).dp), // 上移40dp，调整视觉中心
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NEXUS",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF667eea), // 优雅蓝紫色
                                    Color(0xFF764ba2), // 深紫色
                                    Color(0xFFf093fb), // 粉紫色
                                    Color(0xFFf5576c)  // 珊瑚红
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(1000f, 1000f)
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "智能AI对话体验",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF757575)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { message ->
                        ChatMessageItem(
                            message = message,
                            viewModel = viewModel,
                            isLoading = isLoading,
                            playingMessageId = playingMessageId
                        )
                    }
                
                if (isLoading) {
                    item {
                        LoadingMessage()
                    }
                }
                
                if (error != null) {
                    item {
                        ErrorMessage(error = error!!)
                    }
                }
                }
            }
        }
    }
    
    // 用户注册对话框
    if (showUserRegistration) {
        AlertDialog(
            onDismissRequest = { showUserRegistration = false },
            title = { Text("用户管理") },
            text = {
                UserRegistrationScreen(
                    userManager = userManager,
                    onRegistrationComplete = { showUserRegistration = false }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showUserRegistration = false }
                ) {
                    Text("关闭")
                }
            }
        )
    }
    
    // 历史记录对话框
    if (showHistory) {
        HistoryScreen(
            viewModel = viewModel,
            onBackClick = { showHistory = false }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    viewModel: ChatViewModel,
    isLoading: Boolean,
    playingMessageId: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            // AI头像 - 使用Canvas绘制完美圆形
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp, top = 8.dp)
                    .drawWithContent {
                        // 绘制圆形背景
                        drawCircle(
                            color = primaryColor,
                            radius = size.minDimension / 2f
                        )
                        // 绘制内容
                        drawContent()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = onPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // 消息内容
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 语音播放和刷新按钮（仅AI消息）
                if (!message.isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 播放按钮
                        val isThisMessagePlaying = playingMessageId == message.id
                        Button(
                            onClick = { 
                                viewModel.playAudioForMessage(message.id, message.content)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isThisMessagePlaying) 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            enabled = !isLoading && !isThisMessagePlaying
                        ) {
                            val textColor = MaterialTheme.colorScheme.primary
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = if (isThisMessagePlaying) "播放中" else "播放语音",
                                    tint = textColor.copy(alpha = if (isThisMessagePlaying) 1.0f else 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isThisMessagePlaying) "播放中..." else "播放语音",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                            }
                        }
                        
                        // 刷新按钮
                        Button(
                            onClick = { 
                                viewModel.refreshLastAIResponse()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLoading) 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                                else 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            enabled = !isLoading
                        ) {
                            val textColor = MaterialTheme.colorScheme.secondary
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = if (isLoading) "刷新中" else "刷新回答",
                                    tint = textColor.copy(alpha = if (isLoading) 1.0f else 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLoading) "刷新中..." else "刷新回答",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (message.isUser) {
            // 用户头像 - 使用Canvas绘制完美圆形
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val onSecondaryColor = MaterialTheme.colorScheme.onSecondary
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(start = 8.dp, top = 8.dp)
                    .drawWithContent {
                        // 绘制圆形背景
                        drawCircle(
                            color = secondaryColor,
                            radius = size.minDimension / 2f
                        )
                        // 绘制内容
                        drawContent()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "用户",
                    tint = onSecondaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LoadingMessage() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 8.dp, top = 8.dp)
                .drawWithContent {
                    // 绘制圆形背景
                    drawCircle(
                        color = primaryColor,
                        radius = size.minDimension / 2f
                    )
                    // 绘制内容
                    drawContent()
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = onPrimaryColor
            )
        }
        
        Card(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "AI正在思考中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ErrorMessage(error: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        val errorColor = MaterialTheme.colorScheme.error
        val onErrorColor = MaterialTheme.colorScheme.onError
        
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(end = 8.dp, top = 8.dp)
                .drawWithContent {
                    // 绘制圆形背景
                    drawCircle(
                        color = errorColor,
                        radius = size.minDimension / 2f
                    )
                    // 绘制内容
                    drawContent()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelSmall,
                color = onErrorColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = "错误: $error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
