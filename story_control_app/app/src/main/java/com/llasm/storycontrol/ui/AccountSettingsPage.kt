package com.llasm.storycontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.UserData
import com.llasm.storycontrol.data.UserManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsPage(
    onBackClick: () -> Unit,
    onShowLoginDialog: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 初始化UserManager
    LaunchedEffect(Unit) {
        UserManager.init(context)
    }
    
    // 获取用户登录状态和信息（使用状态管理，确保UI能响应变化）
    var isLoggedIn by remember { mutableStateOf(UserManager.isLoggedIn()) }
    var userData by remember { mutableStateOf(UserManager.getUserData()) }
    
    // 监听登录状态变化
    LaunchedEffect(Unit) {
        // 定期检查登录状态（当从其他页面返回时）
        kotlinx.coroutines.delay(100)
        isLoggedIn = UserManager.isLoggedIn()
        userData = UserManager.getUserData()
    }
    
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
                        text = "账号设置",
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
        if (isLoggedIn && userData != null) {
            // 已登录状态 - 显示用户信息
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColors.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 用户头像和基本信息卡片
                item {
                    UserProfileCard(
                        userData = userData,
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
                
                // 详细信息卡片
                item {
                    UserDetailsCard(
                        userData = userData,
                        themeColors = themeColors,
                        fontStyle = fontStyle
                    )
                }
                
                // 退出登录按钮
                item {
                    var isLoggingOut by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = {
                            if (isLoggingOut) return@Button
                            isLoggingOut = true
                            
                            // 在后台线程调用登出API
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    // 调用后端登出API
                                    val sessionId = UserManager.getSessionId()
                                    if (sessionId != null) {
                                        try {
                                            val url = java.net.URL(com.llasm.storycontrol.config.ServerConfig.getApiUrl(com.llasm.storycontrol.config.ServerConfig.Endpoints.AUTH_LOGOUT))
                                            val connection = url.openConnection() as java.net.HttpURLConnection
                                            
                                            connection.requestMethod = "POST"
                                            connection.setRequestProperty("Content-Type", "application/json")
                                            connection.doOutput = true
                                            
                                            val jsonObject = org.json.JSONObject()
                                            jsonObject.put("session_id", sessionId)
                                            
                                            val outputStream = connection.outputStream
                                            val writer = java.io.OutputStreamWriter(outputStream)
                                            writer.write(jsonObject.toString())
                                            writer.flush()
                                            writer.close()
                                            
                                            val responseCode = connection.responseCode
                                            android.util.Log.d("AccountSettingsPage", "登出API响应码: $responseCode")
                                        } catch (e: Exception) {
                                            android.util.Log.e("AccountSettingsPage", "调用登出API失败: ${e.message}")
                                            // 即使API调用失败，也清除本地数据
                                        }
                                    }
                                    
                                    // 清除本地用户数据
                                    UserManager.logout()
                                    
                                    // 更新UI状态
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        isLoggedIn = false
                                        userData = null
                                        isLoggingOut = false
                                        
                                        // 返回上一页
                                        onBackClick()
                                        
                                        // 如果提供了登录对话框回调，显示登录对话框
                                        onShowLoginDialog?.invoke()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("AccountSettingsPage", "登出过程出错: ${e.message}")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        isLoggingOut = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoggingOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isLoggingOut) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isLoggingOut) "退出中..." else "退出登录",
                            style = fontStyle.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // 未登录状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColors.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = themeColors.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "未登录",
                            modifier = Modifier.size(64.dp),
                            tint = themeColors.textSecondary.copy(alpha = 0.6f)
                        )
                        
                        Text(
                            text = "未登录",
                            style = fontStyle.headlineSmall,
                            color = themeColors.textSecondary.copy(alpha = 0.6f)
                        )
                        
                        Text(
                            text = "请先登录以查看账号信息",
                            style = fontStyle.bodyMedium,
                            color = themeColors.textSecondary.copy(alpha = 0.6f)
                        )
                        
                        // 登录按钮
                        if (onShowLoginDialog != null) {
                            Button(
                                onClick = onShowLoginDialog,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = themeColors.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "立即登录",
                                    color = Color.White,
                                    style = fontStyle.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 用户头像和基本信息卡片
@Composable
fun UserProfileCard(
    userData: UserData,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 用户头像
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(themeColors.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "用户头像",
                        modifier = Modifier.size(40.dp),
                        tint = themeColors.primary
                    )
                }
                
                // 用户名
                Text(
                    text = userData.username,
                    style = fontStyle.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = themeColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                
                // 用户ID
                Text(
                    text = "ID: ${userData.userId}",
                    style = fontStyle.bodyMedium,
                    color = themeColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// 详细信息卡片
@Composable
fun UserDetailsCard(
    userData: UserData,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "账户详情",
                style = fontStyle.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = themeColors.textPrimary
            )
            
            InfoRowWithCopy(
                label = "会话ID",
                value = userData.sessionId,
                themeColors = themeColors,
                fontStyle = fontStyle
            )
            
            if (userData.createdAt != null) {
                InfoRow(
                    label = "注册时间",
                    value = formatDateTime(userData.createdAt),
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            }
            
            if (userData.lastLoginAt != null) {
                InfoRow(
                    label = "最后登录",
                    value = formatDateTime(userData.lastLoginAt),
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            }
        }
    }
}

// 可复制信息行组件
@Composable
fun InfoRowWithCopy(
    label: String,
    value: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    val clipboardManager = LocalClipboardManager.current
    var showCopied by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(2000)
            showCopied = false
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                clipboardManager.setText(AnnotatedString(value))
                showCopied = true
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
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
        
        if (showCopied) {
            Text(
                text = "已复制",
                style = fontStyle.bodySmall,
                color = themeColors.primary
            )
        } else {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(20.dp),
                tint = themeColors.textSecondary
            )
        }
    }
}

// 信息行组件
@Composable
fun InfoRow(
    label: String,
    value: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
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

// 格式化日期时间
private fun formatDateTime(dateTimeString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateTimeString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateTimeString
    }
}
