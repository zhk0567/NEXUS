package com.llasm.storycontrol.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
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
                        text = "每日故事",
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
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "每日故事",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 应用名称
                        Text(
                            text = "每日故事",
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
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, themeColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "应用介绍",
                            style = fontStyle.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = themeColors.textPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "每日故事是一款专为儿童设计的智能故事阅读应用，提供丰富的故事内容和个性化的阅读体验。",
                            style = fontStyle.bodyMedium,
                            color = themeColors.textPrimary,
                            lineHeight = 24.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "主要功能：",
                            style = fontStyle.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = themeColors.textPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val features = listOf(
                            "• 精选儿童故事，内容丰富多样",
                            "• 文字+音频双重阅读模式",
                            "• 智能阅读进度管理",
                            "• 个性化主题设置",
                            "• 用户账号系统",
                            "• 阅读统计和记录"
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
