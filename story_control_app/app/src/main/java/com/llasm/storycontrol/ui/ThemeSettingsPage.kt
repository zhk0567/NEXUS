package com.llasm.storycontrol.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsPage(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val currentThemeMode by SettingsManager.themeMode.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    var themeMode by remember { mutableStateOf(currentThemeMode) }
    var isDarkModeLocal by remember { mutableStateOf(isDarkMode) }
    
    // 处理手机返回键
    BackHandler {
        onBackClick()
    }
    
    // 监听系统主题变化
    LaunchedEffect(themeMode) {
        if (themeMode == "跟随系统") {
            SettingsManager.updateThemeFromSystem(context)
            isDarkModeLocal = SettingsManager.isDarkMode.value
        }
    }
    
    // 当取消系统跟随时，默认选择当前状态
    LaunchedEffect(themeMode) {
        if (themeMode != "跟随系统") {
            // 取消系统跟随时，保持当前的主题状态
            isDarkModeLocal = isDarkMode
        }
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
                        text = "主题设置",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp), // 添加顶部间距
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 跟随系统开关
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, themeColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "跟随系统",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = themeColors.textPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 使用稳定的Switch组件
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "跟随系统主题",
                            style = fontStyle.titleMedium,
                            color = themeColors.textPrimary
                        )
                        
                        Switch(
                            checked = themeMode == "跟随系统",
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    themeMode = "跟随系统"
                                    SettingsManager.setThemeMode("跟随系统")
                                    SettingsManager.updateThemeFromSystem(context)
                                    isDarkModeLocal = SettingsManager.isDarkMode.value
                                } else {
                                    // 取消系统跟随时，默认选择当前状态
                                    if (isDarkModeLocal) {
                                        themeMode = "深色模式"
                                        SettingsManager.setThemeMode("深色模式")
                                    } else {
                                        themeMode = "亮色模式"
                                        SettingsManager.setThemeMode("亮色模式")
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = themeColors.primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = themeColors.textSecondary.copy(alpha = 0.3f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (themeMode == "跟随系统") "当前跟随系统主题设置" else "当前使用手动主题设置",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                }
            }
            
            // 字体大小设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, themeColors.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "字体大小",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = themeColors.textPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 字体大小选择
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 小字体
                        FontSizeCard(
                            size = "小",
                            isSelected = fontSize == "小",
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = {
                                SettingsManager.setFontSize("小")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 中字体
                        FontSizeCard(
                            size = "中",
                            isSelected = fontSize == "中",
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = {
                                SettingsManager.setFontSize("中")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 大字体
                        FontSizeCard(
                            size = "大",
                            isSelected = fontSize == "大",
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = {
                                SettingsManager.setFontSize("大")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // 手动主题选择（当不跟随系统时显示）
            if (themeMode != "跟随系统") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, themeColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "主题模式",
                            style = fontStyle.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = themeColors.textPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 白天模式
                            ThemeModeCard(
                                icon = Icons.Default.LightMode,
                                title = "白天",
                                isSelected = !isDarkModeLocal,
                                themeColors = themeColors,
                                fontStyle = fontStyle,
                                onClick = {
                                    isDarkModeLocal = false
                                    themeMode = "亮色模式"
                                    // 立即应用主题
                                    SettingsManager.setThemeMode("亮色模式")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            // 夜间模式
                            ThemeModeCard(
                                icon = Icons.Default.DarkMode,
                                title = "夜间",
                                isSelected = isDarkModeLocal,
                                themeColors = themeColors,
                                fontStyle = fontStyle,
                                onClick = {
                                    isDarkModeLocal = true
                                    themeMode = "深色模式"
                                    // 立即应用主题
                                    SettingsManager.setThemeMode("深色模式")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 保存按钮 - 主题已经立即应用，这里只是确认保存
            Button(
                onClick = {
                    // 主题已经立即应用，这里可以添加保存成功的提示
                    // 或者直接返回上一页
                    onBackClick()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.primary
                )
            ) {
                Text(
                    text = "完成",
                    style = fontStyle.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ThemeModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColors.primary.copy(alpha = 0.1f) else themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) themeColors.primary else themeColors.textSecondary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                style = fontStyle.bodyMedium,
                color = if (isSelected) themeColors.primary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = themeColors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FontSizeCard(
    size: String,
    isSelected: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColors.primary.copy(alpha = 0.1f) else themeColors.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        ),
        border = if (isSelected) BorderStroke(2.dp, themeColors.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 字体大小预览
            Text(
                text = "Aa",
                style = when (size) {
                    "小" -> fontStyle.bodySmall.copy(fontSize = 14.sp)
                    "中" -> fontStyle.bodyMedium.copy(fontSize = 18.sp)
                    "大" -> fontStyle.bodyLarge.copy(fontSize = 24.sp)
                    else -> fontStyle.bodyMedium
                },
                color = if (isSelected) themeColors.primary else themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = size,
                style = fontStyle.bodyMedium,
                color = if (isSelected) themeColors.primary else themeColors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = themeColors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
