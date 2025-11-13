package com.llasm.nexusunified.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 可拖动的悬浮球组件
 * 
 * 功能特性：
 * - 支持手动拖动到屏幕任意位置
 * - 拖动结束后自动贴住最近的屏幕边缘
 * - 拖动时提供视觉反馈（缩放效果）
 * - 防止拖动时误触点击事件
 * - 自动限制在屏幕范围内
 */
@Composable
fun DraggableFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF07C160),
    contentColor: Color = Color.White,
    icon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "语音通话",
            modifier = Modifier.size(40.dp) // 从32dp放大到40dp
        )
    }
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // 屏幕尺寸
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // 悬浮球尺寸
    val fabSize = with(density) { 64.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    
    // 计算安全区域边界 - 动态适应屏幕尺寸
    val topBarHeight = with(density) { 56.dp.toPx() } // TopAppBar高度
    val statusBarHeight = with(density) { 24.dp.toPx() } // 状态栏高度
    val navigationBarHeight = with(density) { 48.dp.toPx() } // 导航栏高度
    
    // 动态计算底部输入区域高度，根据屏幕高度调整
    val dynamicBottomHeight = with(density) { 
        if (screenHeight > 2000) 140.dp.toPx() // 大屏幕需要更多空间
        else 100.dp.toPx() // 普通屏幕
    }
    
    // 计算可移动区域 - 优化边界计算
    val minY = statusBarHeight + margin // 顶部边界：只考虑状态栏，减少不必要的限制
    val maxY = screenHeight - dynamicBottomHeight - navigationBarHeight - fabSize - margin // 底部边界：动态加强限制
    
    // 调试信息 - 在开发时输出边界信息
    LaunchedEffect(minY, maxY) {
        android.util.Log.d("DraggableFAB", "边界计算: minY=$minY, maxY=$maxY, 屏幕高度=$screenHeight")
    }
    
    // 位置状态 - 默认在右下角，贴着边缘，确保在安全区域内
    var offsetX by remember { mutableStateOf(screenWidth - fabSize - margin) } // 贴着右边缘
    var offsetY by remember { mutableStateOf(minY + (maxY - minY) * 0.7f) } // 安全区域的下方位置（70%处）
    
    // 动画偏移量
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(300),
        label = "offsetX"
    )
    
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = tween(300),
        label = "offsetY"
    )
    
    // 拖动处理
    var isDragging by remember { mutableStateOf(false) }
    
    // 拖动时的缩放效果
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1.0f,
        animationSpec = tween(200),
        label = "scale"
    )
    
    FloatingActionButton(
        onClick = {
            if (!isDragging) {
                onClick()
            }
        },
        modifier = modifier
            .offset {
                IntOffset(
                    animatedOffsetX.roundToInt(),
                    animatedOffsetY.roundToInt()
                )
            }
            .size(64.dp)
            .scale(scale)
            .clip(CircleShape) // 设置为圆形
            .pointerInput(Unit) {
                // 使用更灵敏的拖动检测，减少延迟
                detectDragGestures(
                    onDragStart = { 
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        // 拖动结束，智能贴边
                        val centerX = offsetX + fabSize / 2
                        
                        // 水平贴边：根据中心点位置决定贴左边还是右边
                        val targetX = if (centerX < screenWidth / 2) {
                            margin // 贴左边
                        } else {
                            screenWidth - fabSize - margin // 贴右边
                        }
                        
                        // 垂直贴边：保持用户拖动的位置，但确保在安全区域内
                        val targetY = offsetY.coerceIn(
                            minY,
                            maxY
                        )
                        
                        offsetX = targetX
                        offsetY = targetY
                    }
                ) { _, dragAmount ->
                    // 更新位置 - 立即响应拖动
                    val newX = (offsetX + dragAmount.x).coerceIn(
                        margin,
                        screenWidth - fabSize - margin
                    )
                    val newY = (offsetY + dragAmount.y).coerceIn(
                        minY,
                        maxY
                    )
                    
                    offsetX = newX
                    offsetY = newY
                }
            },
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        icon()
    }
}
