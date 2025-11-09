package com.llasm.nexusunified.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import com.llasm.nexusunified.ui.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsPage(
    onBackClick: () -> Unit
) {
    // 获取主题和字体样式
    val context = LocalContext.current
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 从SharedPreferences加载设置
    val prefs = remember { context.getSharedPreferences("general_settings", Context.MODE_PRIVATE) }
    
    // 设置状态
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }
    var notificationEnabled by remember { mutableStateOf(prefs.getBoolean("notification_enabled", true)) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    
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
                        text = "通用设置",
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
            // 消息设置
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
                        text = "消息设置",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = themeColors.textPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 振动反馈
                    SettingSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = "消息振动反馈",
                        subtitle = "收到消息时振动提醒",
                        isEnabled = vibrationEnabled,
                        onToggle = { vibrationEnabled = !vibrationEnabled },
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 声音提醒
                    SettingSwitchItem(
                        icon = Icons.Default.VolumeUp,
                        title = "声音提醒",
                        subtitle = "收到消息时播放提示音",
                        isEnabled = soundEnabled,
                        onToggle = { soundEnabled = !soundEnabled },
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
            }
            
            // 通知设置
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
                        text = "通知设置",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = themeColors.textPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 推送通知
                    SettingSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "推送通知",
                        subtitle = "允许应用发送推送通知",
                        isEnabled = notificationEnabled,
                        onToggle = { notificationEnabled = !notificationEnabled },
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
            }
            
            
            // 保存按钮
            Button(
                onClick = {
                    // 保存设置到SharedPreferences
                    prefs.edit().apply {
                        putBoolean("vibration_enabled", vibrationEnabled)
                        putBoolean("sound_enabled", soundEnabled)
                        putBoolean("notification_enabled", notificationEnabled)
                        apply()
                    }
                    showSaveSuccess = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.primary
                )
            ) {
                Text(
                    text = "保存设置",
                    style = fontStyle.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // 保存成功提示
    if (showSaveSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000) // 2秒后自动隐藏
            showSaveSuccess = false
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "成功",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "设置已保存",
                        style = fontStyle.titleMedium,
                        color = themeColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = themeColors.primary,
            modifier = Modifier.size(fontStyle.iconSize.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 文字内容
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = fontStyle.titleMedium,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = fontStyle.bodySmall,
                color = themeColors.textSecondary
            )
        }
        
        // 开关
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = themeColors.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = themeColors.textSecondary.copy(alpha = 0.3f)
            )
        )
    }
}
