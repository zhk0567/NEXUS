package com.llasm.nexusunified.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.llasm.nexusunified.data.ChatMessage
import com.llasm.nexusunified.service.TTSService
import com.llasm.nexusunified.viewmodel.ChatViewModel

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
                        // 播放按钮
                        IconButton(
                            onClick = { 
                                if (isPlaying) {
                                    viewModel.stopAudio()
                                } else {
                                    viewModel.playAudioForMessage(message.id, message.content)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isLoadingTTS) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = themeColors.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = if (isPlaying) "停止播放" else "播放语音",
                                    tint = if (isPlaying) themeColors.primary else themeColors.textSecondary,
                                    modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
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
    onShowLoginDialog: (() -> Unit)?,
    isTTSLoading: Boolean,
    loadingTTSMessageId: String?
) {
    val isPlaying = playingMessageId == message.id
    val isLoadingTTS = loadingTTSMessageId == message.id
    
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
                    // 播放按钮
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                viewModel.stopAudio()
                            } else {
                                viewModel.playAudioForMessage(message.id, streamingText)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isLoadingTTS) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = themeColors.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = if (isPlaying) "停止播放" else "播放语音",
                                tint = if (isPlaying) Color(0xFF07C160) else themeColors.textSecondary,
                                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
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
                    text = "小美正在思考中...",
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

