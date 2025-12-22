package com.llasm.storycontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.llasm.storycontrol.data.ReadingProgressManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsPage(
    readingProgressManager: ReadingProgressManager,
    onBackClick: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 阅读统计信息
    val readingProgress by readingProgressManager.readingProgress.collectAsState()
    val completedCount = readingProgress.count { progress -> progress.isCompleted }
    
    // 处理手机返回键
    BackHandler {
        onBackClick()
    }
    
    Scaffold(
        modifier = Modifier.background(themeColors.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface,
                    titleContentColor = themeColors.onSurface
                ),
                title = {
                    Text(
                        text = "设置",
                        style = fontStyle.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 阅读统计卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.primary.copy(alpha = 0.1f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "阅读统计",
                            style = fontStyle.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "已完成故事",
                                    style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary
                                )
                                Text(
                                    text = "$completedCount 个",
                                    style = fontStyle.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColors.primary
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "阅读方式",
                                    style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary
                                )
                                Text(
                                    text = "文字+音频",
                                    style = fontStyle.bodyMedium,
                                    color = themeColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
            
            // 账号设置
            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "账号设置",
                    subtitle = "登录、用户信息、退出登录",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToAccount
                )
            }
            
            // 主题设置
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "主题设置",
                    subtitle = "跟随系统、深色模式、亮色模式",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToTheme
                )
            }
            
            // 关于应用
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于应用",
                    subtitle = "版本信息、使用说明",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToAbout
                )
            }
        }
    }
}
