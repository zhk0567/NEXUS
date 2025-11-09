package com.llasm.storycontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.ThemeColors

/**
 * 设置主页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onBack: () -> Unit,
    onSystemSettings: () -> Unit,
    onAccountSettings: () -> Unit,
    onAboutSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(themeColors.background)
                .padding(16.dp)
        ) {
            // 账号设置入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onAccountSettings() },
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "账号设置",
                            tint = themeColors.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "账号设置",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "登录、退出、账号管理",
                                style = fontStyle.bodySmall,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                }
            }
            
            // 系统设置入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onSystemSettings() },
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "系统设置",
                            tint = themeColors.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "系统设置",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "深色/浅色模式、字体大小",
                                style = fontStyle.bodySmall,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                }
            }
            
            // 软件介绍入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onAboutSettings() },
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "软件介绍",
                            tint = themeColors.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "软件介绍",
                                style = fontStyle.bodyLarge,
                                color = themeColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "版本信息、功能介绍、帮助",
                                style = fontStyle.bodySmall,
                                color = themeColors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
