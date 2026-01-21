package com.llasm.storycontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.ThemeColors

/**
 * 软件介绍页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onBack: () -> Unit
) {
    // 处理手机返回键
    BackHandler {
        onBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "软件介绍",
                        color = themeColors.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // 故事来源说明（放在最上面）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "故事来源于杂志老年博览2025年1月至10月期刊。",
                        style = fontStyle.bodyMedium,
                        color = themeColors.textSecondary,
                        lineHeight = 24.sp
                    )
                }
            }
            
            // 应用信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 应用图标 - 使用更合适的图标
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = themeColors.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "应用图标",
                            tint = themeColors.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text(
                        text = "每日故事",
                        style = fontStyle.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "版本 1.0.0",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Text(
                        text = "智能阅读，轻松管理",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                }
            }
            
            // 功能介绍
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "功能介绍",
                        style = fontStyle.bodyLarge,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    
                    val features = listOf(
                        Triple("📖", "文字阅读模式", "支持滚动阅读和进度跟踪"),
                        Triple("🎵", "音频播放模式", "支持音频播放和进度控制"),
                        Triple("📊", "阅读进度管理", "自动保存阅读进度"),
                        Triple("🎨", "个性化设置", "主题、字体大小等自定义选项"),
                        Triple("👤", "账号管理", "登录、退出、数据同步"),
                        Triple("📱", "响应式设计", "适配不同屏幕尺寸")
                    )
                    
                    features.forEach { (icon, title, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = icon,
                                style = fontStyle.bodyLarge,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .padding(top = 2.dp)
                            )
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = title,
                                    style = fontStyle.bodyLarge,
                                    color = themeColors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                
                                Text(
                                    text = description,
                                    style = fontStyle.bodySmall,
                                    color = themeColors.textSecondary
                                )
                            }
                        }
                        
                        if (features.indexOf(Triple(icon, title, description)) < features.size - 1) {
                            HorizontalDivider(
                                color = themeColors.cardBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            // 底部间距，确保可以滚动到底部
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
