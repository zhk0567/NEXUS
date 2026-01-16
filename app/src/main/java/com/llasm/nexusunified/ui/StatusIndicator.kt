package com.llasm.nexusunified.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llasm.nexusunified.service.ASRStatus

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
                        isLoading -> "🤖 小美正在思考..."
                        isStreaming -> "💬 小美正在回复..."
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

