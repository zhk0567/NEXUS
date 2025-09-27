package com.llasm.nexusunified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.llasm.nexusunified.ui.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBackClick: () -> Unit
) {
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors()
    val fontStyle = SettingsManager.getFontStyle()
    
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
                        text = "关于NEXUS",
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
                .padding(top = 8.dp), // 添加顶部间距
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 应用信息卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, themeColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 应用图标
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    themeColors.primary,
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "NEXUS",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 应用名称
                        Text(
                            text = "NEXUS",
                            style = fontStyle.headlineSmall.copy(fontSize = 32.sp),
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 版本信息
                        Text(
                            text = "版本 1.0.0",
                            style = fontStyle.titleMedium,
                            color = themeColors.textSecondary
                        )
                    }
                }
            }
            
            // 应用介绍
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "应用介绍",
                            style = fontStyle.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = themeColors.textPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "NEXUS是一款智能AI对话应用，集成了先进的语音识别、文本转语音和自然语言处理技术。",
                            style = fontStyle.bodyMedium,
                            color = themeColors.textPrimary,
                            lineHeight = fontStyle.bodyMedium.lineHeight * 1.5
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "主要功能：",
                            style = fontStyle.titleMedium.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Medium,
                            color = themeColors.textPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val features = listOf(
                            "• 智能AI对话，支持多轮对话",
                            "• 语音输入输出，解放双手",
                            "• 多种音色选择，个性化体验",
                            "• 深色/浅色主题，护眼舒适",
                            "• 历史记录管理，随时回顾",
                            "• 云端同步，多设备共享"
                        )
                        
                        features.forEach { feature ->
                            Text(
                                text = feature,
                                style = fontStyle.bodyMedium,
                                color = themeColors.textPrimary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
        }
    }
}

@Composable
fun InfoItem(
    title: String,
    value: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = fontStyle.bodyMedium,
            color = themeColors.textSecondary
        )
        
        Text(
            text = value,
            style = fontStyle.bodyMedium,
            color = themeColors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
