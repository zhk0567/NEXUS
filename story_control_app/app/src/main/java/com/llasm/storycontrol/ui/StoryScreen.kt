package com.llasm.storycontrol.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llasm.storycontrol.data.Story
import com.llasm.storycontrol.data.StoryRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 故事阅读界面
 * 保持与原应用相同的UI风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen() {
    val storyRepository = remember { StoryRepository() }
    var selectedDate by remember { mutableStateOf(LocalDate.of(2024, 1, 1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // 设置状态监听
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors()
    val fontStyle = SettingsManager.getFontStyle()
    
    // 获取当前日期的故事
    val currentStory by remember(selectedDate) {
        derivedStateOf { storyRepository.getStoryByDate(selectedDate) }
    }
    
    Scaffold(
        modifier = Modifier.background(themeColors.background),
        containerColor = themeColors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.surface,
                    titleContentColor = themeColors.onSurface
                ),
                title = {
                    Text(
                        text = "每日故事",
                        style = fontStyle.titleMedium,
                        color = themeColors.onSurface
                    )
                },
                navigationIcon = {
                    // 设置按钮
                    IconButton(
                        onClick = { showSettingsDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                },
                actions = {
                    // 日期选择按钮
                    IconButton(
                        onClick = { showDatePicker = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "选择日期",
                            tint = themeColors.onSurface,
                            modifier = Modifier.size(fontStyle.iconSize.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(themeColors.background)
        ) {
            if (currentStory != null) {
                StoryContent(
                    story = currentStory!!,
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            } else {
                // 没有故事时的空状态
                EmptyState(
                    selectedDate = selectedDate,
                    themeColors = themeColors,
                    fontStyle = fontStyle
                )
            }
        }
    }
    
    // 日历形式的日期选择对话框
    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = {
                Text(
                    text = "选择日期 - 2024年1月",
                    style = fontStyle.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = "当前选择: ${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                        style = fontStyle.bodyMedium,
                        color = themeColors.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 日历网格
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.height(300.dp)
                    ) {
                        // 星期标题
                        items(listOf("日", "一", "二", "三", "四", "五", "六")) { day ->
                            Text(
                                text = day,
                                style = fontStyle.bodySmall,
                                color = themeColors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // 日期按钮
                        items(31) { day ->
                            val date = LocalDate.of(2024, 1, day + 1)
                            val isSelected = date == selectedDate
                            
                            Button(
                                onClick = { 
                                    selectedDate = date
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) themeColors.primary else themeColors.surface
                                ),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = "${day + 1}",
                                    style = fontStyle.bodySmall,
                                    color = if (isSelected) themeColors.onPrimary else themeColors.textPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDatePicker = false }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 设置对话框
    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * 故事内容组件
 */
@Composable
fun StoryContent(
    story: Story,
    themeColors: SettingsManager.ThemeColors,
    fontStyle: SettingsManager.FontStyle
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 故事标题
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = story.title,
                    style = fontStyle.headlineSmall.copy(
                        color = themeColors.onPrimary
                    ),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = story.date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                    style = fontStyle.bodySmall.copy(
                        color = themeColors.onPrimary.copy(alpha = 0.8f)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 故事内容
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = themeColors.cardBackground
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = story.content,
                style = fontStyle.bodyLarge.copy(
                    color = themeColors.textPrimary,
                    lineHeight = 28.sp
                ),
                modifier = Modifier.padding(20.dp),
                textAlign = TextAlign.Justify
            )
        }
        
    }
}

/**
 * 空状态组件
 */
@Composable
fun EmptyState(
    selectedDate: LocalDate,
    themeColors: SettingsManager.ThemeColors,
    fontStyle: SettingsManager.FontStyle
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            tint = themeColors.textSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无故事",
            style = fontStyle.headlineSmall.copy(
                color = themeColors.textPrimary
            ),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${selectedDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))} 还没有故事内容",
            style = fontStyle.bodyMedium.copy(
                color = themeColors.textSecondary
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "请选择其他日期查看故事",
            style = fontStyle.bodySmall.copy(
                color = themeColors.textSecondary
            ),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 设置对话框
 */
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    // 从设置管理器获取当前状态
    val currentIsDarkMode by SettingsManager.isDarkMode.collectAsState()
    val currentFontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors()
    val fontStyle = SettingsManager.getFontStyle()
    
    var isDarkMode by remember { mutableStateOf(currentIsDarkMode) }
    var fontSize by remember { mutableStateOf(currentFontSize) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "设置",
                style = fontStyle.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 主题模式设置
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "主题模式",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 白天模式按钮
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isDarkMode = false },
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isDarkMode) Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "白天模式",
                                    tint = if (!isDarkMode) Color(0xFF1976D2) else Color(0xFF666666),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "白天",
                                    style = fontStyle.bodyMedium,
                                    color = if (!isDarkMode) Color(0xFF1976D2) else Color(0xFF666666)
                                )
                            }
                        }
                        
                        // 夜间模式按钮
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isDarkMode = true },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkMode) Color(0xFF424242) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = "夜间模式",
                                    tint = if (isDarkMode) Color.White else Color(0xFF666666),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "夜间",
                                    style = fontStyle.bodyMedium,
                                    color = if (isDarkMode) Color.White else Color(0xFF666666)
                                )
                            }
                        }
                    }
                }
                
                // 字体大小设置
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "字体大小",
                        style = fontStyle.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 小字体按钮
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { fontSize = "小" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (fontSize == "小") Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "小",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                style = fontStyle.bodySmall,
                                color = if (fontSize == "小") Color(0xFF1976D2) else Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // 中字体按钮
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { fontSize = "中" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (fontSize == "中") Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "中",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                style = fontStyle.bodyMedium,
                                color = if (fontSize == "中") Color(0xFF1976D2) else Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        // 大字体按钮
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { fontSize = "大" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (fontSize == "大") Color(0xFFE3F2FD) else Color(0xFFF5F5F5)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "大",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                style = fontStyle.bodyLarge,
                                color = if (fontSize == "大") Color(0xFF1976D2) else Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 保存设置到设置管理器
                    SettingsManager.setDarkMode(isDarkMode)
                    SettingsManager.setFontSize(fontSize)
                    onDismiss()
                }
            ) {
                Text("保存", style = fontStyle.bodyMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = fontStyle.bodyMedium)
            }
        }
    )
}
