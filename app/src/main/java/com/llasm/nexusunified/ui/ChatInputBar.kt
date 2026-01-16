package com.llasm.nexusunified.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.llasm.nexusunified.R

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
    onCancelRecording: (() -> Unit)? = null,
    isASRRecognizing: Boolean = false,
    asrRecognizingText: String = "",
    recordingTime: Int = 0,
    countdown: Int = 0
) {
    // 录音取消状态（用于控制取消按钮颜色）
    var isCancelling by remember { mutableStateOf(false) }
    
    // 使用更大的容器来容纳向上偏移的录音提示和取消区域
    // 使用Box布局，确保向上偏移的元素不被裁剪
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 录音提示和取消区域 - 放在最外层，避免被Card裁剪
        if (isVoiceMode && (isRecording || recordingTime > 0)) {
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            
            // 录音时的半透明黑色遮罩 - 覆盖全屏
            Box(
                modifier = Modifier
                    .fillMaxSize() // 覆盖全屏
                    .zIndex(50f) // 在底层，但高于普通内容
                    .background(
                        Color(0x66000000) // 40%透明度的黑色遮罩
                    )
            )
            
            // 声波动画 - 放在屏幕正中央
            val soundWaveAnimation by rememberInfiniteTransition(label = "sound_wave").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sound_wave_animation"
            )
            
            // 声波动画显示在屏幕正中央
            Box(
                modifier = Modifier
                    .align(Alignment.Center) // 屏幕正中央
                    .width(280.dp)
                    .height(120.dp)
                    .zIndex(100f)
                    .background(
                        Color(0xE6000000), // 更不透明的黑色背景
                        RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 声波动画
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 5条声波线
                        repeat(5) { index ->
                            val delay = index * 0.2f
                            val phase = (soundWaveAnimation + delay) % 1f
                            val height = (20 + phase * 30).dp // 高度在20-50dp之间变化
                            
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(height)
                                    .background(
                                        Color(0xFF07C160),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    
                    // 录音时长和倒计时
                    Text(
                        text = if (countdown > 0) {
                            "剩余 ${countdown} 秒"
                        } else {
                            "${recordingTime}''"
                        },
                        color = Color.White,
                        style = fontStyle.bodyLarge,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 取消区域背景（参照WXSoundRecord和CSDN文章）
            // 取消区域在按钮上方，避免与声波动画重叠
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-120).dp) // 在按钮上方120dp处（取消区域中心位置）
                    .width(280.dp) // 增加宽度，确保文字不被裁剪
                    .height(120.dp) // 增加高度，确保有足够空间显示完整内容
                    .zIndex(101f) // 确保在最上层
                    .background(
                        if (isCancelling) Color(0xFFD32F2F) else Color(0xFF000000), // 进入取消区域时变为深红色
                        RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = if (isCancelling) Color(0xFFFF5722) else Color.White, // 进入取消区域时边框变为橙色
                        shape = RoundedCornerShape(20.dp)
                    )
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp), // 增加垂直内边距，确保文字完整显示
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, // 使用Center对齐，确保内容居中
                    modifier = Modifier.fillMaxHeight() // 填充整个高度
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp) // 稍微减小图标，为文字留出空间
                    )
                    Spacer(modifier = Modifier.height(8.dp)) // 使用固定间距
                    Text(
                        text = "上滑取消发送",
                        color = Color.White,
                        style = fontStyle.bodyLarge, // 使用bodyLarge而不是bodyMedium
                        fontSize = 16.sp, // 稍微减小字体，确保完整显示
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp, // 减少字间距
                        maxLines = 1, // 确保单行显示
                        overflow = TextOverflow.Visible // 确保文字可见
                    )
                }
            }
        }
        
        // 原有的Card内容 - 对齐到底部，语音模式时完全透明
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
            if (isVoiceMode) {
                // 语音模式：显示按住说话按钮，不使用Card避免白条
                VoiceModeInputBar(
                    isRecording = isRecording,
                    isLoading = isLoading,
                    isStreaming = isStreaming,
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onHoldToSpeak = onHoldToSpeak,
                    onCancelRecording = onCancelRecording,
                    onVoiceModeChange = onVoiceModeChange,
                    isASRRecognizing = isASRRecognizing,
                    asrRecognizingText = asrRecognizingText,
                    recordingTime = recordingTime,
                    countdown = countdown,
                    onCancellingChange = { isCancelling = it } // 传递取消状态变化
                )
            } else {
                // 文字模式：使用Card显示正常的输入框
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.inputBackground
                    ),
                    shape = RoundedCornerShape(0.dp),
                    border = null,
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
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
    }
}

// 语音模式输入栏
@Composable
fun VoiceModeInputBar(
    isRecording: Boolean,
    isLoading: Boolean,
    isStreaming: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onHoldToSpeak: (Boolean) -> Unit,
    onCancelRecording: (() -> Unit)? = null,
    onVoiceModeChange: (Boolean) -> Unit,
    isASRRecognizing: Boolean = false,
    asrRecognizingText: String = "",
    recordingTime: Int = 0, // 录音时长（秒）
    countdown: Int = 0, // 倒计时（剩余秒数，0表示不显示）
    onCancellingChange: ((Boolean) -> Unit)? = null // 取消状态变化回调
) {
    // 录音状态管理（参照WXSoundRecord）
    var isHolding by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) } // 拖拽偏移量（向上为负值）
    
    // 当isCancelling状态改变时，通知外层
    LaunchedEffect(isCancelling) {
        onCancellingChange?.invoke(isCancelling)
    }
    
    val density = LocalDensity.current
    
    // 取消区域定义（参照CSDN文章和WXSoundRecord）
    // 取消区域UI在按钮上方120dp处（中心），高度100dp，所以实际范围是-70dp到-170dp
    // 向上滑动时dragOffsetY为负值，所以检测范围应该是-50dp到-180dp（稍微扩大范围，让用户更容易触发）
    val cancelAreaTopPx = with(density) { (-180).dp.toPx() } // 取消区域顶部（向上180dp，扩大范围）
    val cancelAreaBottomPx = with(density) { (-50).dp.toPx() } // 取消区域底部（向上50dp，扩大范围）
    
    // 简化的录音界面 - 只显示按钮，录音提示和取消区域已移到外层（参照WXSoundRecord）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 返回文字模式按钮
        IconButton(
            onClick = { 
                // 立即切换回文字模式
                onVoiceModeChange(false)
            },
            enabled = !isLoading && !isStreaming && !isASRRecognizing && !isRecording,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "返回文字模式",
                tint = Color(0xFF666666),
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 按住说话按钮 - 支持上滑取消（参照WXSoundRecord实现）
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .background(
                    when {
                        isCancelling -> Color(0xFFD32F2F) // 取消状态 - 深红色，更明显
                        isHolding || isRecording -> Color(0xFFFF9800) // 按住或录音中 - 橙色
                        isLoading || isStreaming || isASRRecognizing -> Color(0xFFE0E0E0) // 禁用状态 - 灰色
                        else -> Color(0xFF07C160) // 正常状态 - 微信绿
                    },
                    RoundedCornerShape(20.dp)
                )
                .pointerInput(isLoading, isStreaming, isASRRecognizing) {
                    if (!isLoading && !isStreaming && !isASRRecognizing) {
                        // 参照WXSoundRecord：使用awaitEachGesture实现全局触摸检测
                        // 这样即使手指移出按钮范围，也能继续检测
                        awaitEachGesture {
                            // 等待按下事件
                            val down = awaitFirstDown()
                            val downPosition = down.position
                            
                            // 按下时立即开始录音（参照CSDN文章和WXSoundRecord）
                            android.util.Log.d("VoiceModeInputBar", "按下: 立即开始录音")
                            isHolding = true
                            dragOffsetY = 0f
                            isCancelling = false
                            onHoldToSpeak(true)
                            
                            var lastPosition = downPosition
                            
                            // 使用drag检测拖拽，直到手指松开
                            drag(down.id) { change ->
                                // 手指移动中
                                val currentPosition = change.position
                                val dragAmount = currentPosition - lastPosition
                                
                                // 累计拖拽距离（向上滑动为负值，参照WXSoundRecord）
                                dragOffsetY += dragAmount.y
                                
                                // 实时检测是否进入取消区域（参照CSDN文章和WXSoundRecord）
                                // 向上滑动时dragOffsetY为负值，取消区域在-50px到-180px之间
                                // 注意：dragOffsetY是负值，所以 <= cancelAreaBottomPx（-50px）表示向上滑动超过50px
                                // 且 >= cancelAreaTopPx（-180px）表示向上滑动不超过180px
                                val isInCancelZone = dragOffsetY <= cancelAreaBottomPx && dragOffsetY >= cancelAreaTopPx
                                isCancelling = isInCancelZone
                                
                                android.util.Log.d("VoiceModeInputBar", "移动: dragOffsetY=$dragOffsetY, cancelArea=[$cancelAreaTopPx, $cancelAreaBottomPx], isCancelling=$isCancelling")
                                
                                lastPosition = currentPosition
                            }
                            
                            // 手指松开：根据是否在取消区域决定发送或取消（参照WXSoundRecord）
                            // 注意：dragOffsetY是负值，所以 <= cancelAreaBottomPx（-50px）表示向上滑动超过50px
                            // 且 >= cancelAreaTopPx（-180px）表示向上滑动不超过180px
                            val finalIsCancelling = dragOffsetY <= cancelAreaBottomPx && dragOffsetY >= cancelAreaTopPx
                            
                            android.util.Log.d("VoiceModeInputBar", "松开: finalIsCancelling=$finalIsCancelling, dragOffsetY=$dragOffsetY")
                            
                            if (finalIsCancelling) {
                                // 在取消区域内松开：取消录音
                                onCancelRecording?.invoke()
                            } else if (isHolding) {
                                // 不在取消区域：发送录音
                                onHoldToSpeak(false)
                            }
                            
                            // 重置状态
                            isHolding = false
                            isCancelling = false
                            dragOffsetY = 0f
                        }
                    }
                }
        ) {
            // 按钮内容 - 确保文字和图标居中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 根据状态显示不同内容
                if (isHolding || isRecording || isCancelling) {
                    // 录音中：显示图标和文字
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "录音中",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCancelling) "松开取消" else "松开 发送",
                            color = Color.White,
                            style = fontStyle.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    // 正常状态：只显示文字
                    Text(
                        text = when {
                            isLoading || isStreaming -> "AI处理中..."
                            isASRRecognizing -> asrRecognizingText.ifEmpty { "正在识别中..." }
                            else -> "按住说话"
                        },
                        color = when {
                            isLoading || isStreaming || isASRRecognizing -> themeColors.textSecondary
                            else -> Color.White
                        },
                        style = fontStyle.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}