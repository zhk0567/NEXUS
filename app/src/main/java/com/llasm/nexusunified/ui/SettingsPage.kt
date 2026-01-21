package com.llasm.nexusunified.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import com.llasm.nexusunified.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.llasm.nexusunified.ui.SettingsManager
import com.llasm.nexusunified.data.UserManager
import com.llasm.nexusunified.config.ServerConfig
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onBackClick: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogoutClick: () -> Unit
) {
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val fontStyle = SettingsManager.getFontStyle()
    
    // 确保主题状态正确初始化
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SettingsManager.initializeTheme(context)
        UserManager.init(context)
    }
    
    // 获取应用版本信息
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    val versionCode = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: Exception) {
            "1"
        }
    }
    
    val themeColors = SettingsManager.getThemeColors()
    val isLoggedIn = UserManager.isLoggedIn()
    val username = UserManager.getUsername()
    
    
    // 退出登录对话框状态
    var showLogoutDialog by remember { mutableStateOf(false) }
    
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
                .padding(top = 8.dp), // 添加顶部间距
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 账号设置
            item {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "账号设置",
                    subtitle = if (isLoggedIn) {
                        "已登录: ${username ?: "未知用户"}"
                    } else {
                        "未登录"
                    },
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToAccount
                )
            }
            
            // 音调选择
            item {
                SettingsItem(
                    icon = Icons.Default.VolumeUp,
                    title = "音调选择",
                    subtitle = "选择TTS音色",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToVoice
                )
            }
            
            // 主题设置
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "主题设置",
                    subtitle = "跟随系统、深色模式、亮色模式、字体大小",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToTheme
                )
            }
            
            
            // 关于每日对话
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于每日对话",
                    subtitle = "应用信息和介绍",
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onClick = onNavigateToAbout
                )
            }
            
            // 退出登录（仅在已登录时显示）
            if (isLoggedIn) {
                item {
                    SettingsItem(
                        icon = Icons.Default.ExitToApp,
                        title = "退出登录",
                        subtitle = "退出当前账号",
                        themeColors = themeColors,
                        fontStyle = fontStyle,
                        onClick = { showLogoutDialog = true },
                        isDestructive = true
                    )
                }
            }
            
            // 版本号显示
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "版本 $versionName ($versionCode)",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // 退出登录确认对话框
    if (showLogoutDialog) {
        LogoutDialog(
            onConfirm = {
                showLogoutDialog = false
                // 调用登出API
                logoutUser { success, message ->
                    if (success) {
                        // 登出成功
                        onLogoutClick()
                    } else {
                        // 登出失败，可以显示错误信息
                        // 这里可以添加错误提示
                    }
                }
            },
            onDismiss = { showLogoutDialog = false },
            themeColors = themeColors,
            fontStyle = fontStyle
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, themeColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFD32F2F) else Color(0xFF07C160),
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
                    color = if (isDestructive) Color(0xFFD32F2F) else themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = fontStyle.bodySmall,
                    color = themeColors.textSecondary
                )
            }
            
            // 右箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
            )
        }
    }
}

@Composable
fun SettingsItemWithPainter(
    icon: Painter,
    title: String,
    subtitle: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, themeColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Image(
                painter = icon,
                contentDescription = null,
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
                    color = if (isDestructive) Color(0xFFD32F2F) else themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = fontStyle.bodySmall,
                    color = themeColors.textSecondary
                )
            }
            
            // 右箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "进入",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
            )
        }
    }
}

@Composable
fun LogoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "退出登录",
                style = fontStyle.headlineSmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "确定要退出当前账号吗？",
                style = fontStyle.bodyMedium,
                color = themeColors.textPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                )
            ) {
                Text(
                    text = "退出登录",
                    style = fontStyle.bodyMedium,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    style = fontStyle.bodyMedium,
                    color = themeColors.textSecondary
                )
            }
        }
    )
}

// 登出API调用函数
fun logoutUser(callback: (Boolean, String?) -> Unit) {
    Thread {
        try {
            val sessionId = UserManager.getSessionId()
            if (sessionId == null) {
                callback(false, "未找到会话信息")
                return@Thread
            }
            
            val url = URL(ServerConfig.getApiUrl(ServerConfig.Endpoints.AUTH_LOGOUT))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonObject = JSONObject()
            jsonObject.put("session_id", sessionId)
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 登出成功，清除本地用户数据
                UserManager.logout()
                callback(true, null)
            } else {
                val jsonResponse = JSONObject(response.toString())
                val errorMessage = jsonResponse.optString("error", "登出失败")
                callback(false, errorMessage)
            }
            
        } catch (e: Exception) {
            // 即使网络请求失败，也清除本地数据
            UserManager.logout()
            callback(true, null)
        }
    }.start()
}
