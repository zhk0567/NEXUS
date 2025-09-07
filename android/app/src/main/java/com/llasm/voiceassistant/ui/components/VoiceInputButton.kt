package com.llasm.voiceassistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.llasm.voiceassistant.R

@Composable
fun VoiceInputButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // 录制时的脉冲动画 - 更快的响应
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isRecording) 0.7f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    // 颜色动画 - 黑白灰配色
    val backgroundColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFF424242) else Color(0xFF757575),
        animationSpec = tween(150),
        label = "backgroundColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isRecording) Color(0xFFFFFFFF) else Color(0xCCFFFFFF),
        animationSpec = tween(150),
        label = "borderColor"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 录制状态指示环 - 更快的响应
        if (isRecording) {
            // 外层脉冲环
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .scale(scale)
                    .background(
                        color = Color(0xFF424242).copy(alpha = alpha * 0.4f),
                        shape = CircleShape
                    )
            )
            
            // 中层脉冲环
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale * 0.9f)
                    .background(
                        color = Color(0xFF424242).copy(alpha = alpha * 0.6f),
                        shape = CircleShape
                    )
            )
        }
        
        // 主按钮
        FloatingActionButton(
            onClick = {
                try {
                    if (isRecording) {
                        onStopRecording()
                    } else {
                        onStartRecording()
                    }
                } catch (e: Exception) {
                    // 处理点击异常
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (isRecording) 8.dp else 4.dp,
                    shape = CircleShape
                ),
            containerColor = Color.Transparent,
            contentColor = Color.White
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = backgroundColor,
                        shape = CircleShape
                    )
                    .border(
                        width = if (isRecording) 3.dp else 2.dp,
                        color = borderColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 图标切换
                if (isRecording) {
                    // 录制状态：音波图标（使用简单的矩形条表示音波）
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(if (index == 1) 20.dp else 12.dp)
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                            )
                        }
                    }
                } else {
                    // 待机状态：麦克风图标
                    Icon(
                        painter = painterResource(id = R.drawable.ic_microphone),
                        contentDescription = "开始录音",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}