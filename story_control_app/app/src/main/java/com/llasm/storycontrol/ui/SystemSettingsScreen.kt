package com.llasm.storycontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llasm.storycontrol.data.FontSize
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.ThemeManager
import com.llasm.storycontrol.data.ThemeMode

/**
 * 系统设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // 主题状态管理 - 直接使用ThemeManager的状态
    val themeMode = ThemeManager.themeMode
    val isDarkModeLocal = themeMode == ThemeMode.DARK
    val fontSize = ThemeManager.fontSize
    
    // 处理手机返回键
    BackHandler {
        onBack()
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
                    IconButton(onClick = onBack) {
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
                .padding(top = 8.dp),
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
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
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
                            checked = themeMode == ThemeMode.SYSTEM,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    ThemeManager.setThemeMode(context, ThemeMode.SYSTEM)
                                } else {
                                    // 取消系统跟随时，默认选择当前状态
                                    val newMode = if (isDarkModeLocal) ThemeMode.DARK else ThemeMode.LIGHT
                                    ThemeManager.setThemeMode(context, newMode)
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
                        text = if (themeMode == ThemeMode.SYSTEM) "当前跟随系统主题设置" else "当前使用手动主题设置",
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
                border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
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
                            isSelected = fontSize == FontSize.SMALL,
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = { 
                                ThemeManager.setFontSize(context, FontSize.SMALL)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 中字体
                        FontSizeCard(
                            size = "中",
                            isSelected = fontSize == FontSize.MEDIUM,
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = { 
                                ThemeManager.setFontSize(context, FontSize.MEDIUM)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 大字体
                        FontSizeCard(
                            size = "大",
                            isSelected = fontSize == FontSize.LARGE,
                            themeColors = themeColors,
                            fontStyle = fontStyle,
                            onClick = { 
                                ThemeManager.setFontSize(context, FontSize.LARGE)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // 手动主题选择（当不跟随系统时显示）
            if (themeMode != ThemeMode.SYSTEM) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColors.cardBorder)
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
                                    ThemeManager.setThemeMode(context, ThemeMode.LIGHT)
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
                                    ThemeManager.setThemeMode(context, ThemeMode.DARK)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 完成按钮
            Button(
                onClick = {
                    onBack()
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
