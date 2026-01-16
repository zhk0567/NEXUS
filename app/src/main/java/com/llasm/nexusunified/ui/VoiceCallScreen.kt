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
import androidx.compose.ui.geometry.Size
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
import com.llasm.nexusunified.config.ServerConfig


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
    fontStyle: FontStyle,
    audioSpectrumData: List<Float> = emptyList(), // 真实音频频谱数据
    isAudioPlaying: Boolean = false // AI音频播放状态
) {
    val isDarkMode = themeColors.background == Color(0xFF121212)
    
    
    // 动画状态
    val callingScale by animateFloatAsState(
        targetValue = if (isCalling) 1.05f else 1f,
        animationSpec = tween(1200),  // 从600ms增加到1200ms
        label = "calling_scale"
    )
    
    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = EaseInOut),  // 从2400ms增加到4800ms
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
                    backgroundColor = Color.Transparent,
                    iconColor = if (isDarkMode) Color.White else Color.Black,
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
                onEndCall = onEndCall,
                audioSpectrumData = audioSpectrumData,
                isAudioPlaying = isAudioPlaying
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
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 底部控制按钮
            BottomControlButtons(
                onStartCall = onStartCall,
                onEndCall = onEndCall,
                isCalling = isCalling,
                isWaitingForResponse = isWaitingForResponse,
                isDarkMode = isDarkMode,
                fontStyle = fontStyle
            )
            
            Spacer(modifier = Modifier.height(60.dp))
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
    onEndCall: () -> Unit,
    audioSpectrumData: List<Float> = emptyList(),
    isAudioPlaying: Boolean = false
) {
    Box(
                modifier = Modifier
            .size(300.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // 双镜像频谱分析器 - 完全展现
        DualMirrorSpectrumVisualizer(
            isActive = isCalling || isWaitingForResponse || isAudioPlaying,
                isDarkMode = isDarkMode,
            audioSpectrumData = audioSpectrumData,
            isRecording = isCalling,  // 用户录音时
            isAIResponding = isAudioPlaying  // AI回答时
        )
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
            animation = tween(8000, easing = EaseInOut),  // 从4000ms增加到8000ms
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = EaseInOut),  // 从6000ms增加到12000ms
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
private fun DualMirrorSpectrumVisualizer(
    isActive: Boolean,
    isDarkMode: Boolean,
    audioSpectrumData: List<Float> = emptyList(),
    isRecording: Boolean = false,
    isAIResponding: Boolean = false
) {
    
    // 频谱状态管理（与Python脚本完全一致）
    var smoothedSpectrum by remember { mutableStateOf(List(24) { 0f }) }
    var lastAmplitude by remember { mutableStateOf(0f) }
    var frameCount by remember { mutableStateOf(0) }
    var hasAudioEverDetected by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 频谱参数（减慢跳跃速度，保持随机度）
    val audioThreshold = 500f
    val smoothingFactor = 0.7f  // 降低平滑因子，让时间倍数效果明显
    val bounceFactor = 1.005f  // 保持弹跳因子，不调整随机度
    val randomFactor = 0.15f  // 保持随机因子，不调整随机度
    val energyDecay = 0.99f  // 大幅减少能量衰减，减慢跳跃速度
    val spectrumBins = 12  // 进一步减少频谱数量，降低密度
    
    // 延迟因子（减少变化范围，降低跳跃频率）
    val delayFactors = remember {
        (0 until spectrumBins).map { 
            0.8f + kotlin.random.Random.nextFloat() * 0.4f // 0.8-1.2，减少变化范围
        }
    }
    
    // 动画循环（固定播放动画，不依赖真实音频数据）
    LaunchedEffect(isRecording, isAIResponding) {
        while (true) {
            frameCount++
            
            // 定义动画强度：录音和AI回答时强，AI思考时弱
            val isStrongAnimation = isRecording || isAIResponding
            val isWeakAnimation = !isRecording && !isAIResponding && frameCount % 3 == 0 // AI思考时每3帧更新一次
            val shouldAnimate = isStrongAnimation || isWeakAnimation
            
            if (shouldAnimate) {
                hasAudioEverDetected = true
                
                // 正态分布频谱生成（增加随机性）
                val time = System.currentTimeMillis() * 0.000001f  // 进一步降低时间倍数，大幅减慢动画速度
                
                // 每帧生成不同的随机参数，增加变化性
                val randomPhase1 = kotlin.random.Random.nextFloat() * kotlin.math.PI.toFloat() * 2f
                val randomPhase2 = kotlin.random.Random.nextFloat() * kotlin.math.PI.toFloat() * 2f
                val randomAmplitude = 0.5f + kotlin.random.Random.nextFloat() * 0.5f  // 0.5-1.0的随机幅度
                val randomFrequency = 0.8f + kotlin.random.Random.nextFloat() * 0.4f  // 0.8-1.2的随机频率
                
                val spectrumData = (0 until spectrumBins).map { i ->
                    val normalizedX = i.toFloat() / (spectrumBins - 1)
                    
                    // 正态分布参数（添加随机变化）
                    val mean = 0.4f + kotlin.random.Random.nextFloat() * 0.2f  // 中心位置随机偏移
                    val stdDev = 0.12f + kotlin.random.Random.nextFloat() * 0.08f  // 标准差随机变化
                    
                    // 计算正态分布值
                    val x = normalizedX - mean
                    val normalDistribution = kotlin.math.exp(-(x * x) / (2 * stdDev * stdDev))
                    
                    // 添加多种时间动画效果（大幅减慢速度）
                    val timeOffset1 = kotlin.math.sin(time * randomFrequency * 0.1f + normalizedX * kotlin.math.PI.toFloat() + randomPhase1) * 0.3f
                    val timeOffset2 = kotlin.math.sin(time * (randomFrequency * 0.15f) + normalizedX * kotlin.math.PI.toFloat() * 0.7f + randomPhase2) * 0.2f
                    val timeVariation = kotlin.math.cos(time * (randomFrequency * 0.08f) + normalizedX * kotlin.math.PI.toFloat() * 1.5f) * 0.15f
                    
                    // 添加随机噪声（增加强度）
                    val randomNoise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.2f
                    
                    // 组合所有效果
                    var finalAmplitude = (normalDistribution * randomAmplitude + timeOffset1 + timeOffset2 + timeVariation + randomNoise).coerceIn(0f, 1f)
                    
                    // AI思考时大幅降低强度
                    if (isWeakAnimation) {
                        finalAmplitude *= 0.15f // 降低到15%强度
                    }
                    
                    finalAmplitude
                }
                
                // 增强随机变化效果
                val enhancedSpectrum = spectrumData.mapIndexed { index, spectrum ->
                    // 为每个频谱柱添加不同的随机变化
                    val positionVariation = kotlin.math.sin(index * kotlin.math.PI.toFloat() / spectrumBins) * 0.1f
                    val randomVariation = (kotlin.random.Random.nextFloat() - 0.5f) * 0.15f
                    val timeBasedVariation = kotlin.math.sin(time * 0.2f + index * 0.5f) * 0.08f
                    
                    (spectrum + positionVariation + randomVariation + timeBasedVariation).coerceIn(0f, 1f)
                }
                
                // 应用随机延迟因子变化
                val delayedSpectrum = enhancedSpectrum.zip(delayFactors).mapIndexed { index, (spectrum, delay) ->
                    val randomMultiplier = 0.9f + kotlin.random.Random.nextFloat() * 0.2f
                    val positionMultiplier = 0.95f + kotlin.math.sin(index * kotlin.math.PI.toFloat() / spectrumBins) * 0.1f
                    spectrum * delay * randomMultiplier * positionMultiplier
                }
                
                // 更新最后更新时间
                lastUpdateTime = System.currentTimeMillis()
                
                // 平滑频谱数据（与Python脚本一致）
                smoothedSpectrum = smoothedSpectrum.zip(delayedSpectrum).map { (old, new) ->
                    val smoothed = smoothingFactor * new + (1 - smoothingFactor) * old
                    // 添加衰退机制，让频谱能够衰退
                    smoothed * energyDecay
                }
                
                // 增强的正态分布随机效果
                val normalEnhancement = (0 until spectrumBins).mapIndexed { index, _ ->
                    val baseEnhancement = 0.9f + kotlin.random.Random.nextFloat() * 0.2f  // 0.9-1.1的变化范围
                    val positionEnhancement = 0.95f + kotlin.math.sin(index * kotlin.math.PI.toFloat() / spectrumBins) * 0.1f
                    val timeEnhancement = 0.98f + kotlin.math.sin(time * 0.1f + index * 0.3f) * 0.04f
                    baseEnhancement * positionEnhancement * timeEnhancement
                }
                
                smoothedSpectrum = smoothedSpectrum.zip(normalEnhancement).map { (spectrum, enhancement) ->
                    (spectrum * enhancement).coerceIn(0f, 1f)
                }
            } else {
                // 不在录音且AI不在回答时，强制重置频谱
                if (hasAudioEverDetected) {
                    smoothedSpectrum = smoothedSpectrum.map { it * energyDecay }
                    if (smoothedSpectrum.all { it < 0.01f }) {
                        hasAudioEverDetected = false
                        smoothedSpectrum = List(spectrumBins) { 0f }
                    }
                }
            }
            
            // 关键调试信息（每100帧输出一次）
            if (frameCount % 100 == 0) {
                val maxHeight = smoothedSpectrum.maxOrNull() ?: 0f
                val nonZeroCount = smoothedSpectrum.count { it > 0.01f }
            }
            
            // 每10帧输出一次动画状态
            if (frameCount % 10 == 0 && shouldAnimate) {
            }
            
            delay(50) // 约20FPS，进一步减慢动画更新频率
        }
    }
    
    // 使用 key 强制重新创建 Canvas
    key(smoothedSpectrum, isRecording, isAIResponding) {
    Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            // 不裁剪内容，允许频谱柱超出边界
            // 透明背景，不绘制任何背景
            
            // 强制重新绘制 - 使用 key 来触发重组
            drawSpectrumBars(
                isActive = isRecording || isAIResponding,
                spectrumData = smoothedSpectrum,
                centerX = size.width / 2,
                centerY = size.height / 2,
                spectrumWidth = size.width * 0.9f,  // 增加宽度，减少边距
                spectrumHeight = size.height * 0.8f  // 增加高度，减少边距
            )
        }
    }
}

// 绘制频谱柱（与Python脚本完全一致）
private fun DrawScope.drawSpectrumBars(
    isActive: Boolean,
    spectrumData: List<Float>,
    centerX: Float,
    centerY: Float,
    spectrumWidth: Float,
    spectrumHeight: Float
) {
    val startX = centerX - spectrumWidth / 2
    val endX = centerX + spectrumWidth / 2
    val spectrumBins = 12  // 进一步减少频谱数量，降低密度
    val barWidth = spectrumWidth / spectrumBins
    val maxBarHeight = spectrumHeight * 0.5f  // 进一步增加频谱柱的最大高度，让上下极限更长
    
    // 添加半透明背景模糊效果，适应更长的频谱
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.1f),  // 半透明黑色背景
        topLeft = Offset(startX - spectrumWidth * 0.1f, centerY - spectrumHeight * 0.7f),
        size = Size(spectrumWidth * 1.2f, spectrumHeight * 1.4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx())
    )
    
    // 先绘制中心线（底层）
    val extendedStartX = startX - spectrumWidth * 0.02f  // 向左稍微延伸2%
    val extendedEndX = endX + spectrumWidth * 0.02f      // 向右稍微延伸2%
        drawLine(
            color = Color(0xFF2E7D32).copy(alpha = 0.8f),  // 更深的绿色，增加可见度
            start = Offset(extendedStartX, centerY),
            end = Offset(extendedEndX, centerY),
            strokeWidth = 2.dp.toPx()  // 变细中心线
        )
    
    if (isActive) {
        // 绘制活跃状态的频谱柱（基于真实音频数据，在中心线上层）
        val maxHeight = spectrumData.maxOrNull() ?: 0f
        val nonZeroCount = spectrumData.count { it > 0.01f }
        // 减少调试日志，只在关键状态变化时输出
        
        // 绘制完整的频谱柱效果
        for (i in 0 until spectrumBins) {
            val barX = startX + (i * barWidth) + barWidth * 0.15f  // 进一步增加左边距
            val actualBarWidth = barWidth * 0.6f  // 进一步减少柱宽度，增加间距
            // 增加差异性：使用平方根函数让差异更明显
            val normalizedValue = spectrumData[i]
            val enhancedValue = kotlin.math.sqrt(normalizedValue) * normalizedValue
            val height = enhancedValue * maxBarHeight
            
            if (height > 0.01f) {
                // 使用渐变色彩，从绿色渐变到青色，减少晃眼效果
                val normalizedHeight = height / maxBarHeight
                val normalizedPosition = i.toFloat() / (spectrumBins - 1)
                
                // 根据高度和位置创建更深的渐变色彩
                val color = when {
                    normalizedHeight > 0.6f -> Color(0xFF2E7D32)  // 高：深绿色
                    normalizedHeight > 0.3f -> Color(0xFF00695C)  // 中：深青绿色
                    else -> Color(0xFF00838F)  // 低：深青色
                }
                
                // 添加动画缓动，让颜色变化更平滑，增加透明度让颜色更深
                val smoothAlpha = kotlin.math.sin(normalizedHeight * kotlin.math.PI.toFloat()) * 0.4f + 0.6f
                val alpha = smoothAlpha.coerceIn(0.4f, 0.9f)  // 提高透明度范围，让颜色更深更明显
                
                // 绘制上半部分（向上延伸）
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(barX, centerY - height),
                    size = Size(actualBarWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(actualBarWidth * 0.4f)  // 使用实际宽度
                )
                
                // 绘制下半部分（向下延伸，镜像）
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(barX, centerY),
                    size = Size(actualBarWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(actualBarWidth * 0.4f)  // 使用实际宽度
                )
            }
        }
    }
    // 注意：不活跃时不绘制任何频谱柱，只显示中心线
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
    // 默认状态：静止的波浪状，更大
    val waveCount = 3
    val waveHeight = baseRadius * 0.4f
    val waveSpacing = waveHeight / waveCount
    
    for (i in 0 until waveCount) {
        val waveY = centerY - (waveHeight / 2) + (i * waveSpacing)
        val waveAlpha = 0.3f - (i * 0.08f)
        
        // 创建静止的水平波浪路径
        val path = Path()
        val points = mutableListOf<Offset>()
        
        // 生成静止水平波浪点
        val waveWidth = baseRadius * 1.8f // 更大的波浪
        val startX = centerX - waveWidth / 2
        val endX = centerX + waveWidth / 2
        
        for (x in startX.toInt()..endX.toInt() step 2) {
            val normalizedX = (x - startX) / (endX - startX)
            val waveAmplitude = 20f + (i * 4f) // 更大的波浪幅度
            val waveFrequency = 1.5f + (i * 0.3f) // 波浪频率
            
            val waveYOffset = sin(normalizedX * waveFrequency * PI.toFloat()) * waveAmplitude
            val y = waveY + waveYOffset
            
            points.add(Offset(x.toFloat(), y))
        }
        
        // 绘制静止波浪路径
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            for (j in 1 until points.size) {
                path.lineTo(points[j].x, points[j].y)
            }
            
            // 渐变颜色：从蓝色到紫色
            val hue = (200f + (i * 20f)) % 360f
            val waveColor = Color.hsv(hue, 0.7f, 1f).copy(alpha = waveAlpha)
            
            drawPath(
                path = path,
                color = waveColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }
    }
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
    // 绘制静态频谱分析器效果 - 只在非播放状态显示
    val spectrumWidth = baseRadius * 2.0f
    val spectrumHeight = baseRadius * 0.8f
    val startX = centerX - spectrumWidth / 2
    val endX = centerX + spectrumWidth / 2
    
    // 绘制静态频谱条形 - 更多更细的条形
    val barCount = 40
    val barWidth = spectrumWidth / barCount
    val barSpacing = barWidth * 0.2f
    val actualBarWidth = barWidth - barSpacing
    
    for (i in 0 until barCount) {
        val barX = startX + (i * barWidth) + barSpacing / 2
        val normalizedX = i.toFloat() / (barCount - 1)
        
        // 创建静态频谱高度模式 - 左侧高，右侧低
        val baseHeight = if (normalizedX < 0.3f) {
            spectrumHeight * (0.6f + 0.2f * (1f - normalizedX / 0.3f))
        } else if (normalizedX < 0.7f) {
            spectrumHeight * (0.3f + 0.1f * sin(normalizedX * 4 * PI.toFloat()))
        } else {
            spectrumHeight * (0.1f + 0.05f * (1f - normalizedX))
        }
        
        val barHeight = baseHeight * 0.6f // 静态时降低高度
        val barY = centerY - barHeight / 2
        
        // 绿色频谱条形 - 静态时较暗
        val spectrumColor = Color(0xFF00FF00).copy(alpha = 0.4f)
    
    drawRect(
            color = spectrumColor,
            topLeft = Offset(barX, barY),
            size = androidx.compose.ui.geometry.Size(actualBarWidth, barHeight)
        )
    }
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
                        isCalling -> Color(0xFF4CAF50)
                    isWaitingForResponse -> Color(0xFF00838F)
                        isConnected -> Color(0xFF4CAF50)
                        else -> Color(0xFF9E9E9E)
                    },
                    CircleShape
                )
            )
            
            Spacer(modifier = Modifier.height(30.dp))
            
                                Text(
            text = when {
                isCalling -> "松开发送"
                isWaitingForResponse -> "小美正在思考..."
                    isConnected -> ""
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
            label = "长按开始录制",
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
                            onHoldToSpeak(true)
                            try {
                                // 等待释放
                                tryAwaitRelease()
                                onHoldToSpeak(false)
                            } catch (e: Exception) {
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
                .size(120.dp) // 更大的按钮
                .background(
                    when {
                        isWaitingForResponse -> if (isDarkMode) Color(0xFF00695C) else Color(0xFF00838F) // 等待响应 - 深色模式用深青绿色，浅色模式用青绿色
                        isCalling -> if (isDarkMode) Color(0xFF2E7D32) else Color(0xFF4CAF50) // 通话中 - 深色模式用深绿色，浅色模式用绿色
                        else -> if (isDarkMode) Color(0xFF424242) else Color(0xFF757575) // 正常状态 - 深色模式用深灰色，浅色模式用中灰色
                    },
                    CircleShape
                )
                .border(
                    width = 2.dp,
                    color = when {
                        isWaitingForResponse -> if (isDarkMode) Color(0xFF00838F) else Color(0xFF00ACC1) // 等待响应 - 深色模式用深青绿色边框，浅色模式用浅青绿色边框
                        isCalling -> if (isDarkMode) Color(0xFF4CAF50) else Color(0xFF66BB6A) // 通话中 - 深色模式用绿色边框，浅色模式用浅绿色边框
                        else -> if (isDarkMode) Color(0xFF616161) else Color(0xFF9E9E9E) // 正常状态 - 深色模式用深灰色边框，浅色模式用浅灰色边框
                    },
                    CircleShape
                )
                .pointerInput(isWaitingForResponse) {
                    if (isWaitingForResponse) {
                        // AI回答时，使用点击逻辑
                        detectTapGestures(
                            onTap = { offset ->
                                onHoldToCall(false) // 直接结束对话
                            }
                        )
                    } else {
                        // 正常录音时，使用长按逻辑
                        detectTapGestures(
                            onPress = { offset ->
                                onHoldToCall(true)
                                try {
                                    // 等待释放
                                    tryAwaitRelease()
                                    onHoldToCall(false)
                                } catch (e: Exception) {
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
                    color = if (isDarkMode) Color(0xFF4CAF50) else Color(0xFF66BB6A),
                    intensity = 0.6f
                )
            }
            
            // 内层圆圈，根据主题调整颜色
            Box(
                modifier = Modifier
                    .size(60.dp)  // 增加圆圈大小
                    .background(
                        if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFF5F5F5), 
                        CircleShape
                    )
            )
        }
        
        // 只有当label不为空时才显示文字
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            
        Text(
                text = label,
                style = fontStyle.bodyMedium.copy(
                    color = if (isDarkMode) Color.White else Color.Black,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp
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

// 音色预览功能 - 使用小美端到端模型
private fun previewVoice(voice: VoiceOption) {
    // 预览文本
    val previewText = when {
        voice.detail.contains("女声") -> "您好，我是${voice.name}，很高兴为您服务。"
        voice.detail.contains("男声") -> "您好，我是${voice.name}，很高兴为您服务。"
        else -> "您好，我是${voice.name}，很高兴为您服务。"
    }
    
    
    // 调用小美端到端模型进行音色预览
    // 这里需要调用小美端到端的音色转换接口
    // 根据火山引擎文档，应该使用音色转换API
    callDoubaoVoicePreview(voice.id, previewText)
}

// 调用小美端到端音色预览
private fun callDoubaoVoicePreview(voiceId: String, text: String) {
    
    // 使用协程异步调用小美端到端API
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 调用小美端到端音色转换接口
            val audioData = callDoubaoVoiceConversionAPI(voiceId, text)
            
            if (audioData != null) {
                // 播放预览音频
                playPreviewAudio(audioData)
            } else {
                android.util.Log.e("VoiceCallScreen", "音色预览失败：无法获取音频数据")
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceCallScreen", "音色预览异常", e)
        }
    }
}

// 调用小美端到端音色转换API
private suspend fun callDoubaoVoiceConversionAPI(voiceId: String, text: String): ByteArray? {
    return try {
        // 根据火山引擎文档 https://www.volcengine.com/docs/6561/1594356
        // 调用小美端到端音色转换接口
        
        val url = ServerConfig.getApiUrl("api/doubao/voice_conversion")
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
            android.util.Log.e("VoiceCallScreen", "小美API调用失败: $responseCode")
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("VoiceCallScreen", "小美API调用异常", e)
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
            animation = tween(2400, easing = EaseInOut),  // 从1200ms增加到2400ms
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = EaseInOut),  // 从1200ms增加到2400ms，延迟也增加
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 1600, easing = EaseInOut),  // 从1200ms增加到2400ms，延迟也增加
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

