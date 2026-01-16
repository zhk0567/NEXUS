package com.llasm.nexusunified.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import com.llasm.nexusunified.data.Conversation
import java.text.SimpleDateFormat
import java.util.Locale

// 对话项组件
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onSelected: () -> Unit,
    onDeleted: () -> Unit
) {
    // 获取第一个用户消息作为问题预览
    val firstUserMessage = conversation.messages.firstOrNull { it.isUser }
    val questionPreview = firstUserMessage?.content?.let { content ->
        if (content.length > 15) {
            content.take(15) + "..."
        } else {
            content
        }
    } ?: "新对话"
    
    // 智能格式化日期
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val conversationYear = java.util.Calendar.getInstance().apply {
        time = conversation.updatedAt
    }.get(java.util.Calendar.YEAR)
    
    val formattedDate = if (conversationYear == currentYear) {
        // 当前年份，只显示月日
        val monthDayFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
        monthDayFormat.format(conversation.updatedAt)
    } else {
        // 其他年份，显示年月日
        val fullDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        fullDateFormat.format(conversation.updatedAt)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColors.primary.copy(alpha = 0.1f) else themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) themeColors.primary else themeColors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 对话预览内容
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = questionPreview,
                    style = fontStyle.titleMedium,
                    color = themeColors.textPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = fontStyle.bodySmall,
                    color = themeColors.textSecondary
                )
            }
            
            // 删除按钮
            IconButton(
                onClick = onDeleted,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除对话",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 历史对话抽屉组件
@Composable
fun HistoryDrawer(
    isVisible: Boolean,
    conversations: List<Conversation>,
    currentConversationId: String?,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onConversationSelected: (String) -> Unit,
    onConversationDeleted: (String) -> Unit,
    onDismiss: () -> Unit,
    onSettingsClick: () -> Unit = {} // 设置按钮点击回调
) {
    val configuration = LocalConfiguration.current
    val drawerWidth = remember(configuration.screenWidthDp) { 
        configuration.screenWidthDp.dp * 0.75f 
    } // 屏幕宽度的75%
    val screenWidth = configuration.screenWidthDp.dp
    
    // 抽屉滑入滑出动画 - 使用更快的关闭动画
    val offsetX by animateFloatAsState(
        targetValue = if (isVisible) 0f else -drawerWidth.value,
        animationSpec = tween(
            durationMillis = 200, // 更快的动画，减少卡顿
            easing = LinearOutSlowInEasing // 关闭时使用线性缓动，更流畅
        ),
        label = "drawerOffset",
        finishedListener = { 
            // 动画完成后的回调，但不在这里做任何操作，避免卡顿
        }
    )
    
    // 只在可见或正在动画时渲染（避免完全隐藏时的不必要渲染）
    if (!isVisible && offsetX <= -drawerWidth.value + 0.5f) {
        return
    }
    
    // 抽屉容器
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 抽屉内容 - 使用 graphicsLayer 启用硬件加速
        Card(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = offsetX
                },
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "历史对话",
                        style = fontStyle.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeColors.textPrimary
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = themeColors.textSecondary,
                            modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 对话列表
                if (conversations.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无历史对话",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                } else {
                    // 过滤掉空对话（没有消息的对话）- 使用remember缓存结果
                    val nonEmptyConversations = remember(conversations) {
                        conversations.filter { it.messages.isNotEmpty() }
                    }
                    
                    if (nonEmptyConversations.isEmpty()) {
                        // 如果没有非空对话，显示空状态
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "历史记录",
                                tint = themeColors.textSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "暂无历史对话",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 0.dp) // 移除顶部和底部内边距
                        ) {
                            items(
                                count = nonEmptyConversations.size,
                                key = { index -> nonEmptyConversations[index].id }
                            ) { index ->
                                val conversation = nonEmptyConversations[index]
                                val itemThemeColors = themeColors
                                val itemFontStyle = fontStyle
                                
                                ConversationItem(
                                    conversation = conversation,
                                    isSelected = conversation.id == currentConversationId,
                                    themeColors = itemThemeColors,
                                    fontStyle = itemFontStyle,
                                    onSelected = { onConversationSelected(conversation.id) },
                                    onDeleted = { onConversationDeleted(conversation.id) }
                                )
                            }
                        }
                    }
                }
                
                // 设置按钮 - 放在左下角（历史对话下方），使用固定间距而不是weight
                Spacer(modifier = Modifier.height(16.dp))
                
                // 设置按钮淡入动画 - 移除延迟，与抽屉同步
                val settingsAlpha by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    ),
                    label = "settingsAlpha"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .alpha(settingsAlpha)
                        .clickable {
                            onDismiss() // 先关闭历史对话抽屉
                            onSettingsClick() // 然后打开设置页面
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp), // 增加点击区域
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 设置图标 - 类似Deepseek侧边栏风格
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = themeColors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "设置",
                        style = fontStyle.bodyMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // 右侧点击区域 - 使用pointerInput避免显示条条
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width((screenWidth - drawerWidth))
                .offset(x = drawerWidth)
                .pointerInput(Unit) {
                    detectTapGestures {
                        onDismiss()
                    }
                }
        )
    }
}

