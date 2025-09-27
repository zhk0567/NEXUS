package com.llasm.nexusunified.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 同步状态指示器
 */
@Composable
fun SyncStatusIndicator(
    isSyncing: Boolean,
    lastSyncTime: Long,
    syncCount: Int,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    
    // 动画效果
    val rotation by animateFloatAsState(
        targetValue = if (isSyncing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSyncing) 1.1f else 1f,
        animationSpec = tween(300),
        label = "sync_scale"
    )
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { showDetails = !showDetails }
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 同步图标
            Icon(
                imageVector = when {
                    isSyncing -> Icons.Default.CloudSync
                    lastSyncTime > 0 -> Icons.Default.CloudDone
                    else -> Icons.Default.CloudOff
                },
                contentDescription = "同步状态",
                tint = when {
                    isSyncing -> MaterialTheme.colorScheme.primary
                    lastSyncTime > 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
                    .graphicsLayer { rotationZ = rotation }
            )
            
            // 详细信息（可选显示）
            AnimatedVisibility(
                visible = showDetails,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (isSyncing) "同步中..." else "已同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp
                    )
                    
                    if (syncCount > 0) {
                        Text(
                            text = "第${syncCount}次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 同步状态卡片
 */
@Composable
fun SyncStatusCard(
    isSyncing: Boolean,
    lastSyncTime: Long,
    syncCount: Int,
    onTriggerSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "自动同步",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                SyncStatusIndicator(
                    isSyncing = isSyncing,
                    lastSyncTime = lastSyncTime,
                    syncCount = syncCount
                )
            }
            
            // 同步状态文本
            Text(
                text = when {
                    isSyncing -> "正在同步资源到云端..."
                    lastSyncTime > 0 -> "上次同步: ${formatTime(lastSyncTime)}"
                    else -> "尚未同步"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 手动同步按钮
            Button(
                onClick = onTriggerSync,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("立即同步")
            }
        }
    }
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        else -> "${diff / (24 * 60 * 60 * 1000)}天前"
    }
}
