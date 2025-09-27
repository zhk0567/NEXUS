package com.llasm.nexusunified.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences
import com.llasm.nexusunified.ui.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsPage(
    onBackClick: () -> Unit
) {
    // 获取主题和字体样式
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors()
    val fontStyle = SettingsManager.getFontStyle()
    val context = LocalContext.current
    
    // 从SharedPreferences加载用户信息
    val prefs = remember { context.getSharedPreferences("user_settings", Context.MODE_PRIVATE) }
    
    // 用户信息状态
    var nickname by remember { mutableStateOf(prefs.getString("nickname", "用户昵称") ?: "用户昵称") }
    var phoneNumber by remember { mutableStateOf(prefs.getString("phone_number", "13812345678") ?: "13812345678") }
    var selectedAvatar by remember { mutableStateOf(prefs.getInt("avatar_index", 0)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editField by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("") }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    
    // 默认头像列表
    val defaultAvatars = listOf(
        "👤", "👨", "👩", "🧑", "👨‍💼", "👩‍💼", "👨‍🎓", "👩‍🎓", "👨‍🎨", "👩‍🎨"
    )
    
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp), // 添加顶部间距
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 头像和基本信息
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 头像
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(themeColors.primary)
                            .clickable { 
                                showAvatarDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = defaultAvatars[selectedAvatar],
                            style = fontStyle.headlineSmall.copy(fontSize = 32.sp),
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "点击更换头像",
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                }
            }
            
            // 昵称设置
            AccountSettingItem(
                icon = Icons.Default.Person,
                title = "昵称",
                value = nickname,
                themeColors = themeColors,
                fontStyle = fontStyle,
                onClick = {
                    editType = "nickname"
                    editField = nickname
                    showEditDialog = true
                }
            )
            
            // 手机号设置
            AccountSettingItem(
                icon = Icons.Default.Phone,
                title = "手机号",
                value = phoneNumber,
                themeColors = themeColors,
                fontStyle = fontStyle,
                onClick = {
                    editType = "phone"
                    editField = phoneNumber
                    showEditDialog = true
                }
            )
            
            // 注销账号
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteAccountDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFFFCDD2))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(fontStyle.iconSize.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = "注销账号",
                        style = fontStyle.titleMedium,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    // 头像选择对话框
    if (showAvatarDialog) {
        AvatarSelectionDialog(
            avatars = defaultAvatars,
            selectedIndex = selectedAvatar,
            onAvatarSelected = { index ->
                selectedAvatar = index
                prefs.edit().putInt("avatar_index", index).apply()
                showAvatarDialog = false
            },
            onDismiss = { showAvatarDialog = false },
            themeColors = themeColors,
            fontStyle = fontStyle
        )
    }
    
    // 编辑对话框
    if (showEditDialog) {
        EditFieldDialog(
            title = when (editType) {
                "nickname" -> "编辑昵称"
                "phone" -> "编辑手机号"
                else -> "编辑"
            },
            currentValue = editField,
            onConfirm = { newValue ->
                when (editType) {
                    "nickname" -> {
                        nickname = newValue
                        prefs.edit().putString("nickname", newValue).apply()
                    }
                    "phone" -> {
                        phoneNumber = newValue
                        prefs.edit().putString("phone_number", newValue).apply()
                    }
                }
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
            themeColors = themeColors,
            fontStyle = fontStyle
        )
    }
    
    // 注销账号确认对话框
    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                showDeleteAccountDialog = false
                // 这里可以添加注销账号的逻辑
            },
            onDismiss = { showDeleteAccountDialog = false },
            themeColors = themeColors,
            fontStyle = fontStyle
        )
    }
}

@Composable
fun AccountSettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.primary,
                modifier = Modifier.size(fontStyle.iconSize.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = fontStyle.bodyMedium,
                    color = themeColors.textSecondary
                )
                Text(
                    text = value,
                    style = fontStyle.titleMedium,
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "编辑",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(fontStyle.iconSize.dp * 0.8f)
            )
        }
    }
}

@Composable
fun AvatarSelectionDialog(
    avatars: List<String>,
    selectedIndex: Int,
    onAvatarSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择头像",
                style = fontStyle.headlineSmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 每行显示4个头像
                items(avatars.chunked(4)) { rowAvatars ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowAvatars.forEachIndexed { rowIndex, avatar ->
                            val globalIndex = avatars.indexOf(avatar)
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (globalIndex == selectedIndex) themeColors.primary else themeColors.primary.copy(alpha = 0.3f)
                                    )
                                    .clickable { onAvatarSelected(globalIndex) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = avatar,
                                    style = fontStyle.titleMedium.copy(fontSize = 32.sp),
                                    color = Color.White
                                )
                            }
                        }
                        // 如果这一行不足4个，用空白填充
                        repeat(4 - rowAvatars.size) {
                            Spacer(modifier = Modifier.size(70.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("确定", style = fontStyle.bodyMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = fontStyle.bodyMedium, color = themeColors.textSecondary)
            }
        }
    )
}

@Composable
fun EditFieldDialog(
    title: String,
    currentValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    var textValue by remember { mutableStateOf(TextFieldValue(currentValue)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = fontStyle.headlineSmall,
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("输入新值") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(textValue.text) }
            ) {
                Text(
                    text = "确定",
                    style = fontStyle.bodyMedium
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

@Composable
fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "注销账号",
                style = fontStyle.headlineSmall,
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "注销账号后将无法恢复，确定要继续吗？",
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
                    text = "确定注销",
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
