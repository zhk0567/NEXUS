package com.llasm.nexusunified.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import com.llasm.nexusunified.ui.VoiceOption


/**
 * 现代化语音通话界面 - 参考设计重制版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(
    isConnected: Boolean,
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    conversationHistory: List<ConversationItem>,
    onHangup: () -> Unit,
    onStartCall: () -> Unit,
    onEndCall: () -> Unit,
    onSettings: () -> Unit = {},
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    val isDarkMode = themeColors.background == Color(0xFF121212)
    
    
    // 动画状态
    val callingScale by animateFloatAsState(
        targetValue = if (isCalling) 1.05f else 1f,
        animationSpec = tween(300),
        label = "calling_scale"
    )
    
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkMode) Color(0xFF000000) else Color(0xFFF5F5F5)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部状态栏 - 左上角挂断按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                ControlButton(
                    icon = Icons.Default.Close,
                    label = "",
                    onClick = onHangup,
                    backgroundColor = Color(0xFFF44336),
                    iconColor = Color.White,
                    isDarkMode = isDarkMode,
                    fontStyle = fontStyle
                )
            }
            
            Spacer(modifier = Modifier.height(44.dp))
            
            // 中心通话状态区域
            CallStatusArea(
                isCalling = isCalling,
                isWaitingForResponse = isWaitingForResponse,
                isConnected = isConnected,
                scale = callingScale,
                pulseScale = pulseAnimation,
                isDarkMode = isDarkMode,
                onStartCall = onStartCall,
                onEndCall = onEndCall
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 状态提示文字
            StatusText(
                isCalling = isCalling,
                isWaitingForResponse = isWaitingForResponse,
                isConnected = isConnected,
                isDarkMode = isDarkMode,
                fontStyle = fontStyle
            )
            
            Spacer(modifier = Modifier.height(60.dp))
            
            // 底部控制按钮
            BottomControlButtons(
                onStartCall = onStartCall,
                onEndCall = onEndCall,
                isCalling = isCalling,
                isWaitingForResponse = isWaitingForResponse,
                isDarkMode = isDarkMode,
                fontStyle = fontStyle
            )
            
            Spacer(modifier = Modifier.height(40.dp))
        }
        
    }
}


@Composable
fun CallStatusArea(
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    isConnected: Boolean,
    scale: Float,
    pulseScale: Float,
    isDarkMode: Boolean,
    onStartCall: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(
                modifier = Modifier
            .size(300.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // 外层通话光晕效果
        if (isCalling || isWaitingForResponse) {
            android.util.Log.d("VoiceCallScreen", "显示CallBubble动画 - isCalling: $isCalling, isWaitingForResponse: $isWaitingForResponse")
            CallBubble(
                    modifier = Modifier
                    .size(300.dp)
                    .scale(pulseScale),
                isDarkMode = isDarkMode,
                isActive = isCalling || isWaitingForResponse  // 在录音或等待响应时都显示动画
            )
        }
        
        // 内层圆形背景
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(
                    if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFF2C2C2C),
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isDarkMode) Color(0xFF404040) else Color(0xFFE0E0E0),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 发光圆环视觉化器
            GlowingRingVisualizer(
                isCalling = isCalling,
                isWaitingForResponse = isWaitingForResponse,
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun CallBubble(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    Box(
        modifier = modifier
            .background(
                if (isActive) {
                    if (isDarkMode) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
                } else {
                    if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                },
                CircleShape
            )
            .scale(if (isActive) scale else 1f)
    )
}

@Composable
private fun GlowingRingVisualizer(
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    isDarkMode: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    
    // 呼吸动画
    val breathingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_progress"
    )
    
    Canvas(
        modifier = Modifier.size(200.dp)
    ) {
        drawGlowingRing(
            breathingProgress = if (isCalling || isWaitingForResponse) breathingProgress else 0f,
            isActive = isCalling || isWaitingForResponse,
            isDarkMode = isDarkMode
        )
    }
}

private fun DrawScope.drawGlowingRing(
    breathingProgress: Float,
    isActive: Boolean,
    isDarkMode: Boolean
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val baseRadius = minOf(size.width, size.height) / 2 - 20
    
    if (isActive) {
        // 呼吸动画的发光圆环
        drawBreathingRing(centerX, centerY, baseRadius, breathingProgress, isDarkMode)
    } else {
        // 静态的发光圆环
        drawStaticRing(centerX, centerY, baseRadius, isDarkMode)
    }
}

private fun DrawScope.drawBreathingRing(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    breathingProgress: Float,
    isDarkMode: Boolean
) {
    try {
        // 呼吸动画：圆环大小在0.8-1.2倍之间变化
        val ringScale = 0.8f + breathingProgress * 0.4f
        val ringRadius = baseRadius * ringScale
        
        // 呼吸动画：透明度在0.6-1.0之间变化
        val ringAlpha = 0.6f + breathingProgress * 0.4f
        
        // 发光圆环
        val ringColor = Color(0xFF4CAF50).copy(alpha = ringAlpha)
        
        // 绿色环
        drawCircle(
            color = ringColor,
            radius = ringRadius,
            center = Offset(centerX, centerY)
        )
        
        // 荧光发光亮边
        val glowColor = Color(0xFF00FF88).copy(alpha = 0.8f + breathingProgress * 0.2f)
        drawCircle(
            color = glowColor,
            radius = ringRadius + 2.dp.toPx(),
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        
        // 中心黑色圆
        val innerRadius = ringRadius * 0.6f
        drawCircle(
            color = Color(0xFF000000),
            radius = innerRadius,
            center = Offset(centerX, centerY)
        )
        
    } catch (e: Exception) {
        // 如果计算出错，绘制简单的静态效果
        drawCircle(
            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
            radius = baseRadius,
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )
    }
}

private fun DrawScope.drawStaticRing(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    isDarkMode: Boolean
) {
    // 静态绿色环
    val ringColor = Color(0xFF4CAF50).copy(alpha = 0.8f)
    
    // 绿色环
    drawCircle(
        color = ringColor,
        radius = baseRadius,
        center = Offset(centerX, centerY)
    )
    
    // 荧光发光亮边
    val glowColor = Color(0xFF00FF88).copy(alpha = 0.8f)
    drawCircle(
        color = glowColor,
        radius = baseRadius + 2.dp.toPx(),
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
    
    // 中心黑色圆
    val innerRadius = baseRadius * 0.6f
    drawCircle(
        color = Color(0xFF000000),
        radius = innerRadius,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawFlowingEnergy(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    flowProgress: Float,
    pulseProgress: Float
) {
    // 流动的能量路径
    val path = Path()
    val energyRadius = baseRadius * 0.8f
    
    // 创建流动的S形路径
    val t = flowProgress * 2 * PI.toFloat()
    val points = mutableListOf<Offset>()
    
    // 生成流动路径点
    for (i in 0..50) {
        val progress = i / 50f
        val angle = progress * 4 * PI.toFloat() + t
        val radius = energyRadius * (0.3f + 0.7f * sin(progress * PI.toFloat()))
        
        val x = centerX + cos(angle) * radius
        val y = centerY + sin(angle) * radius
        points.add(Offset(x, y))
    }
    
    // 绘制流动能量
    for (i in 0 until points.size - 1) {
        val point1 = points[i]
        val point2 = points[i + 1]
        val progress = i / 50f
        
        // 能量颜色渐变
        val hue = (i * 7f + flowProgress * 360f) % 360f
        val alpha = (0.3f + 0.7f * sin(progress * PI.toFloat())) * (0.5f + 0.5f * pulseProgress)
        val energyColor = Color.hsv(hue, 0.8f, 1f).copy(alpha = alpha)
        
        // 绘制能量段
        val strokeWidth = 8.dp.toPx() * (0.5f + 0.5f * sin(progress * 3 * PI.toFloat()))
        
        drawLine(
            color = energyColor,
            start = point1,
            end = point2,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
    
    // 3. 能量核心
    val coreRadius = baseRadius * 0.15f + sin(pulseProgress * 4 * PI.toFloat()) * baseRadius * 0.05f
    val coreAlpha = 0.8f + sin(pulseProgress * 6 * PI.toFloat()) * 0.2f
    
    // 核心渐变
    val coreGradient = Brush.radialGradient(
        colors = listOf(
            Color(0xFF4CAF50).copy(alpha = coreAlpha),
            Color(0xFF2E7D32).copy(alpha = coreAlpha * 0.7f),
            Color.Transparent
        ),
        radius = coreRadius
    )
    
    drawCircle(
        brush = coreGradient,
        radius = coreRadius,
        center = Offset(centerX, centerY)
    )
    
    // 4. 能量粒子
    for (i in 0 until 12) {
        val particleProgress = (flowProgress + i * 0.083f) % 1f
        val particleAngle = particleProgress * 4 * PI.toFloat() + i * 0.5f
        val particleRadius = energyRadius * (0.2f + particleProgress * 0.8f)
        
        val particleX = centerX + cos(particleAngle) * particleRadius
        val particleY = centerY + sin(particleAngle) * particleRadius
        val particleSize = 3f + sin(particleProgress * 2 * PI.toFloat()) * 2f
        
        val particleHue = (i * 30f + flowProgress * 360f) % 360f
        val particleAlpha = 0.6f - particleProgress * 0.4f
        
        drawCircle(
            color = Color.hsv(particleHue, 0.9f, 1f).copy(alpha = particleAlpha),
            radius = particleSize,
            center = Offset(particleX, particleY)
        )
    }
}

private fun DrawScope.drawStaticSphere(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    isDarkMode: Boolean
) {
    // 1. 透明球体外壳
    val sphereColor = Color(0xFF4CAF50).copy(alpha = 0.4f)
    val borderColor = Color(0xFF4CAF50).copy(alpha = 0.8f)
    
    // 球体背景（半透明）
    drawCircle(
        color = sphereColor,
        radius = baseRadius,
        center = Offset(centerX, centerY)
    )
    
    // 球体边框
    drawCircle(
        color = borderColor,
        radius = baseRadius,
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
    
    // 2. 中心简洁设计
    val centerSize = baseRadius * 0.3f
    
    // 内层圆环
    val innerRingRadius = centerSize * 0.8f
    val innerRingColor = Color(0xFF4CAF50).copy(alpha = 0.6f)
    
    drawCircle(
        color = innerRingColor,
        radius = innerRingRadius,
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
    
    // 中心点
    val centerDotRadius = centerSize * 0.2f
    val centerDotColor = Color(0xFF4CAF50).copy(alpha = 0.8f)
    
    drawCircle(
        color = centerDotColor,
        radius = centerDotRadius,
        center = Offset(centerX, centerY)
    )
    
    // 3. 装饰性光晕
    val glowRadius = baseRadius * 0.7f
    val glowColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
    
    drawCircle(
        color = glowColor,
        radius = glowRadius,
        center = Offset(centerX, centerY),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
    )
    
    // 4. 中心能量点
    val coreRadius = baseRadius * 0.08f
    val coreColor = Color(0xFF4CAF50).copy(alpha = 0.6f)
    
    drawCircle(
        color = coreColor,
        radius = coreRadius,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawMinimalistIcon(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    isDarkMode: Boolean
) {
    val color = if (isDarkMode) Color.White else Color.Black
    
    // 极简的圆形背景
    drawCircle(
        color = color.copy(alpha = 0.1f),
        radius = baseRadius * 0.8f,
        center = Offset(centerX, centerY)
    )
    
    // 中心矩形
    val rectWidth = baseRadius * 0.2f
    val rectHeight = baseRadius * 0.6f
    val rectX = centerX - rectWidth / 2
    val rectY = centerY - rectHeight / 2
    
    drawRect(
        color = color,
        topLeft = Offset(rectX, rectY),
        size = androidx.compose.ui.geometry.Size(rectWidth, rectHeight)
    )
}


@Composable
fun StatusText(
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    isConnected: Boolean,
    isDarkMode: Boolean,
    fontStyle: FontStyle
    ) {
        Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态指示点
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    when {
                        isCalling -> Color(0xFFFF5722)
                    isWaitingForResponse -> Color(0xFFFF9800)
                        isConnected -> Color(0xFF4CAF50)
                        else -> Color(0xFF9E9E9E)
                    },
                    CircleShape
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
                                Text(
            text = when {
                isCalling -> "松开发送"
                isWaitingForResponse -> "AI正在思考..."
                isConnected -> "长按开始通话"
                else -> "等待连接..."
            },
            style = fontStyle.bodyMedium.copy(
                color = if (isDarkMode) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )
        )
    }
}

@Composable
fun BottomControlButtons(
    onStartCall: () -> Unit,
    onEndCall: () -> Unit,
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    isDarkMode: Boolean,
    fontStyle: FontStyle
) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 通话按钮 - 长按样式
        HoldToCallButton(
            icon = when {
                isWaitingForResponse -> Icons.Default.Pause // AI回答时显示暂停图标
                !isCalling -> Icons.Default.Call // 正常状态显示通话图标
                else -> null // 录音时不显示图标，使用自定义内容
            },
            customContent = if (isCalling) {
                {
                    AnimatedDots(
                        color = Color.White,
                        fontSize = 20.sp
                    )
                }
            } else null,
            label = "",
            onHoldToCall = { isHolding ->
                if (isWaitingForResponse) {
                    // AI回答时，点击暂停直接结束对话
                    onEndCall()
                } else if (isHolding) {
                    onStartCall()
                } else {
                    onEndCall()
                }
            },
            isCalling = isCalling,
            isWaitingForResponse = isWaitingForResponse,
            isDarkMode = isDarkMode,
            fontStyle = fontStyle
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    isDarkMode: Boolean,
    fontStyle: FontStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(backgroundColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.ifEmpty { "按钮" },
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 只有当label不为空时才显示文字
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            
                            Text(
                text = label,
                style = fontStyle.bodySmall.copy(
                    color = if (isDarkMode) Color.White else Color.Black,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun HoldToSpeakButton(
    icon: ImageVector,
    label: String,
    onHoldToSpeak: (Boolean) -> Unit,
    backgroundColor: Color,
    iconColor: Color,
    isDarkMode: Boolean,
    fontStyle: FontStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(backgroundColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            android.util.Log.d("VoiceCallScreen", "=== 按下开始录音 ===")
                            onHoldToSpeak(true)
                            try {
                                // 等待释放
                                tryAwaitRelease()
                                android.util.Log.d("VoiceCallScreen", "=== 松开停止录音 ===")
                                onHoldToSpeak(false)
                            } catch (e: Exception) {
                                android.util.Log.d("VoiceCallScreen", "=== 异常停止录音 ===")
                                onHoldToSpeak(false)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label.ifEmpty { "麦克风按钮" },
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 只有当label不为空时才显示文字
        if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                style = fontStyle.bodySmall.copy(
                    color = if (isDarkMode) Color.White else Color.Black,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun SubtitleOverlay(
    onClose: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
                    ) {
                        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                    text = "实时字幕",
                    style = fontStyle.titleMedium.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
                    text = "字幕功能开发中...",
                    style = fontStyle.bodyMedium.copy(
                        color = themeColors.textSecondary,
            textAlign = TextAlign.Center
                    )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "关闭",
                        color = Color.White
                    )
                }
            }
        }
    }
}

// 数据类
data class ConversationItem(
    val role: String, // "user", "assistant", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
private fun VoiceSelectionDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onVoiceSelected: (VoiceOption) -> Unit,
    onVoicePreview: (VoiceOption) -> Unit,
    currentVoice: VoiceOption,
    isDarkMode: Boolean,
    fontStyle: FontStyle
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "选择音色",
                    style = fontStyle.headlineSmall.copy(
                        color = if (isDarkMode) Color.White else Color.Black
                    )
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.height(300.dp)
                ) {
                    items(getVoiceOptions()) { voice ->
                        VoiceOptionItem(
                            voice = voice,
                            isSelected = voice.id == currentVoice.id,
                            onSelect = { onVoiceSelected(voice) },
                            onPreview = { onVoicePreview(voice) },
                            isDarkMode = isDarkMode,
                            fontStyle = fontStyle
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "确定",
                        style = fontStyle.bodyMedium.copy(
                            color = if (isDarkMode) Color.White else Color.Black
                        )
                    )
                }
            },
            containerColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
        )
    }
}

@Composable
private fun VoiceOptionItem(
    voice: VoiceOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    isDarkMode: Boolean,
    fontStyle: FontStyle
                        ) {
                            Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
                                colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (isDarkMode) Color(0xFF2E7D32) else Color(0xFF4CAF50)
            } else {
                if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF5F5F5)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                    text = voice.name,
                    style = fontStyle.bodyLarge.copy(
                        color = if (isDarkMode) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = voice.description,
                    style = fontStyle.bodyMedium.copy(
                        color = if (isDarkMode) Color.Gray else Color.Gray
                    )
                )
                Text(
                    text = voice.detail,
                    style = fontStyle.bodySmall.copy(
                        color = if (isDarkMode) Color.LightGray else Color.DarkGray
                    )
                )
            }
            
            // 预览按钮
            IconButton(
                onClick = onPreview,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "预览音色",
                    tint = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// 获取可用的音色选项
private fun getVoiceOptions(): List<VoiceOption> {
    return listOf(
        VoiceOption("zh_female_qingxin", "清新女声", "温柔清新的女性声音", "女声"),
        VoiceOption("zh_female_ruyi", "如意女声", "优雅知性的女性声音", "女声"),
        VoiceOption("zh_female_aiqi", "爱奇女声", "活泼可爱的女性声音", "女声"),
        VoiceOption("zh_male_ruyi", "如意男声", "沉稳大气的男性声音", "男声"),
        VoiceOption("zh_male_qingxin", "清新男声", "温和清新的男性声音", "男声"),
        VoiceOption("zh_male_aiqi", "爱奇男声", "年轻活力的男性声音", "男声"),
        VoiceOption("zh_female_zhichang", "职场女声", "专业干练的女性声音", "女声"),
        VoiceOption("zh_male_zhichang", "职场男声", "专业稳重的男性声音", "男声")
    )
}

@Composable
private fun HoldToCallButton(
    icon: ImageVector? = null,
    customContent: @Composable (() -> Unit)? = null,
    label: String,
    onHoldToCall: (Boolean) -> Unit,
    isCalling: Boolean,
    isWaitingForResponse: Boolean,
    isDarkMode: Boolean,
    fontStyle: FontStyle
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp) // 更大的按钮
                .background(
                    when {
                        isWaitingForResponse -> Color(0xFF9C27B0) // 等待响应 - 紫色
                        isCalling -> Color(0xFFFF5722) // 通话中 - 橙色
                        else -> Color(0xFF4CAF50) // 正常状态 - 绿色
                    },
                    CircleShape
                )
                .border(
                    width = 3.dp,
                    color = when {
                        isWaitingForResponse -> Color(0xFFBA68C8) // 等待响应 - 浅紫色边框
                        isCalling -> Color(0xFFFF9800) // 通话中 - 橙色边框
                        else -> Color(0xFF66BB6A) // 正常状态 - 浅绿色边框
                    },
                    CircleShape
                )
                .pointerInput(isWaitingForResponse) {
                    if (isWaitingForResponse) {
                        // AI回答时，使用点击逻辑
                        detectTapGestures(
                            onTap = { offset ->
                                android.util.Log.d("VoiceCallScreen", "=== 点击暂停对话 ===")
                                onHoldToCall(false) // 直接结束对话
                            }
                        )
                    } else {
                        // 正常录音时，使用长按逻辑
                        detectTapGestures(
                            onPress = { offset ->
                                android.util.Log.d("VoiceCallScreen", "=== 按下开始通话 ===")
                                onHoldToCall(true)
                                try {
                                    // 等待释放
                                    tryAwaitRelease()
                                    android.util.Log.d("VoiceCallScreen", "=== 松开停止通话 ===")
                                    onHoldToCall(false)
                                } catch (e: Exception) {
                                    android.util.Log.d("VoiceCallScreen", "=== 异常停止通话 ===")
                                    onHoldToCall(false)
                                }
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // 发光效果
            if (isCalling) {
                GlowEffect(
                    modifier = Modifier.size(80.dp),
                    color = Color(0xFFFF9800),
                    intensity = 0.8f
                )
            }
            
            // 显示自定义内容或图标
            if (customContent != null) {
                customContent()
            } else if (icon != null) {
            Icon(
                    imageVector = icon,
                    contentDescription = label.ifEmpty { "通话按钮" },
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // 只有当label不为空时才显示文字
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            
        Text(
                text = label,
                style = fontStyle.bodySmall.copy(
                    color = if (isDarkMode) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )
            )
        }
    }
}

@Composable
private fun GlowEffect(
    modifier: Modifier = Modifier,
    color: Color,
    intensity: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .background(
                color.copy(alpha = alpha * intensity),
                CircleShape
            )
    )
}

// 音色预览功能 - 使用豆包端到端模型
private fun previewVoice(voice: VoiceOption) {
    // 预览文本
    val previewText = when {
        voice.detail.contains("女声") -> "您好，我是${voice.name}，很高兴为您服务。"
        voice.detail.contains("男声") -> "您好，我是${voice.name}，很高兴为您服务。"
        else -> "您好，我是${voice.name}，很高兴为您服务。"
    }
    
    android.util.Log.d("VoiceCallScreen", "播放音色预览: ${voice.name} (${voice.id}) - $previewText")
    
    // 调用豆包端到端模型进行音色预览
    // 这里需要调用豆包端到端的音色转换接口
    // 根据火山引擎文档，应该使用音色转换API
    callDoubaoVoicePreview(voice.id, previewText)
}

// 调用豆包端到端音色预览
private fun callDoubaoVoicePreview(voiceId: String, text: String) {
    android.util.Log.d("VoiceCallScreen", "调用豆包端到端音色转换API")
    android.util.Log.d("VoiceCallScreen", "音色ID: $voiceId")
    android.util.Log.d("VoiceCallScreen", "预览文本: $text")
    
    // 使用协程异步调用豆包端到端API
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 调用豆包端到端音色转换接口
            val audioData = callDoubaoVoiceConversionAPI(voiceId, text)
            
            if (audioData != null) {
                // 播放预览音频
                playPreviewAudio(audioData)
                android.util.Log.d("VoiceCallScreen", "音色预览播放成功")
            } else {
                android.util.Log.e("VoiceCallScreen", "音色预览失败：无法获取音频数据")
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceCallScreen", "音色预览异常", e)
        }
    }
}

// 调用豆包端到端音色转换API
private suspend fun callDoubaoVoiceConversionAPI(voiceId: String, text: String): ByteArray? {
    return try {
        // 根据火山引擎文档 https://www.volcengine.com/docs/6561/1594356
        // 调用豆包端到端音色转换接口
        
        val url = "http://192.168.50.205:5000/api/doubao/voice_conversion"
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        
        // 构建请求参数
        val requestBody = org.json.JSONObject().apply {
            put("voice_id", voiceId)
            put("text", text)
            put("format", "wav")
            put("sample_rate", 16000)
        }
        
        // 发送请求
        val outputStream = connection.outputStream
        outputStream.write(requestBody.toString().toByteArray())
        outputStream.close()
        
        // 读取响应
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            val inputStream = connection.inputStream
            val audioData = inputStream.readBytes()
            inputStream.close()
            audioData
        } else {
            android.util.Log.e("VoiceCallScreen", "豆包API调用失败: $responseCode")
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("VoiceCallScreen", "豆包API调用异常", e)
        null
    }
}

// 播放预览音频
private fun playPreviewAudio(audioData: ByteArray) {
    try {
        // 创建临时文件
        val tempFile = java.io.File.createTempFile("voice_preview", ".wav")
        tempFile.writeBytes(audioData)
        
        // 使用MediaPlayer播放
        val mediaPlayer = android.media.MediaPlayer()
        mediaPlayer.setDataSource(tempFile.absolutePath)
        mediaPlayer.prepare()
        mediaPlayer.start()
        
        // 播放完成后清理
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
            tempFile.delete()
        }
        
        android.util.Log.d("VoiceCallScreen", "开始播放音色预览")
    } catch (e: Exception) {
        android.util.Log.e("VoiceCallScreen", "播放预览音频失败", e)
    }
}

@Composable
private fun AnimatedDots(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 16.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            color = color.copy(alpha = dot1Alpha),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "•",
            color = color.copy(alpha = dot2Alpha),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "•",
            color = color.copy(alpha = dot3Alpha),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

