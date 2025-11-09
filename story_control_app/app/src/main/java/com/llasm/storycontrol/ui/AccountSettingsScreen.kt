package com.llasm.storycontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
 * 账号设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onBack: () -> Unit
) {
    val isLoggedIn = remember { mutableStateOf(true) }
    val currentUser = remember { mutableStateOf("testuser1") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "账号设置",
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
            // 当前账号信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "当前账号",
                        style = fontStyle.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "用户名",
                            style = fontStyle.bodyLarge,
                            color = themeColors.textPrimary
                        )
                        Text(
                            text = currentUser.value,
                            style = fontStyle.bodyLarge,
                            color = themeColors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "登录状态",
                            style = fontStyle.bodyLarge,
                            color = themeColors.textPrimary
                        )
                        Text(
                            text = if (isLoggedIn.value) "已登录" else "未登录",
                            style = fontStyle.bodyLarge,
                            color = if (isLoggedIn.value) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // 账号操作
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "账号操作",
                        style = fontStyle.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (isLoggedIn.value) {
                        Button(
                            onClick = { 
                                isLoggedIn.value = false
                                currentUser.value = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336)
                            )
                        ) {
                            Text(
                                text = "退出登录",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Button(
                            onClick = { 
                                isLoggedIn.value = true
                                currentUser.value = "testuser1"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = themeColors.primary
                            )
                        ) {
                            Text(
                                text = "登录",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
