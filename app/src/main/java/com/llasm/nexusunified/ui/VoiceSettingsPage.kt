package com.llasm.nexusunified.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.SharedPreferences
import com.llasm.nexusunified.ui.SettingsManager
import com.llasm.nexusunified.service.TTSService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsPage(
    onBackClick: () -> Unit
) {
    // 获取主题和字体样式
    val context = LocalContext.current
    val isDarkMode by SettingsManager.isDarkMode.collectAsState()
    val fontSize by SettingsManager.fontSize.collectAsState()
    val themeColors = SettingsManager.getThemeColors(context)
    val fontStyle = SettingsManager.getFontStyle()
    
    // 初始化TTS服务
    val ttsService = remember { TTSService(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 从SharedPreferences加载选中的音色
    val prefs = remember { context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE) }
    
    // 音色列表
    val voiceOptions = listOf(
        VoiceOption("zh-CN-XiaoxiaoNeural", "晓晓", "温柔女声", "温柔甜美的女声，声音清澈动听，适合日常对话和轻松交流"),
        VoiceOption("zh-CN-YunxiNeural", "云希", "知性男声", "知性优雅的男声，声音成熟稳重，适合商务场景和专业对话"),
        VoiceOption("zh-CN-YunyangNeural", "云扬", "成熟男声", "成熟稳重的男声，声音浑厚有力，适合正式场合和重要播报"),
        VoiceOption("zh-CN-XiaoyiNeural", "小艺", "活泼女声", "活泼可爱的女声，声音清脆悦耳，适合轻松对话和娱乐内容"),
        VoiceOption("zh-CN-YunjianNeural", "云健", "磁性男声", "磁性低沉的男声，声音富有魅力，适合朗读和播报")
    )
    
    var selectedVoice by remember { mutableStateOf(prefs.getString("selected_voice", "zh-CN-XiaoxiaoNeural") ?: "zh-CN-XiaoxiaoNeural") }
    var isPlaying by remember { mutableStateOf(false) }
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    
    // 处理手机返回键
    BackHandler {
        // 停止播放
        if (isPlaying) {
            ttsService.stopPlayback()
        }
        onBackClick()
    }
    
    // 页面销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            if (isPlaying) {
                ttsService.stopPlayback()
            }
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
                        text = "音调选择",
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
            items(voiceOptions) { voice ->
                VoiceOptionItem(
                    voice = voice,
                    isSelected = voice.id == selectedVoice,
                    isPlaying = isPlaying && voice.id == selectedVoice,
                    themeColors = themeColors,
                    fontStyle = fontStyle,
                    onSelect = { 
                        selectedVoice = voice.id
                        prefs.edit().putString("selected_voice", voice.id).apply()
                        isPlaying = false
                    },
                    onPlay = { 
                        if (isPlaying && playingVoiceId == voice.id) {
                            // 停止播放
                            ttsService.stopPlayback()
                            isPlaying = false
                            playingVoiceId = null
                        } else {
                            // 开始播放
                            isPlaying = true
                            playingVoiceId = voice.id
                            
                            // 使用TTS服务播放测试音色
                            coroutineScope.launch {
                                ttsService.testVoice(
                                    voiceId = voice.id,
                                    onPlayStart = {
                                        // 播放开始回调
                                    },
                                    onPlayComplete = {
                                        // 播放完成回调
                                        isPlaying = false
                                        playingVoiceId = null
                                    },
                                    onError = { errorMessage ->
                                        // 播放错误回调
                                        isPlaying = false
                                        playingVoiceId = null
                                        // 这里可以显示错误提示
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VoiceOptionItem(
    voice: VoiceOption,
    isSelected: Boolean,
    isPlaying: Boolean,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) themeColors.primary.copy(alpha = 0.1f) else themeColors.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 音色图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isSelected) themeColors.primary else themeColors.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else themeColors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 音色信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = voice.name,
                    style = fontStyle.titleMedium,
                    color = if (isSelected) themeColors.primary else themeColors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = voice.description,
                    style = fontStyle.bodyMedium,
                    color = themeColors.textSecondary
                )
                Text(
                    text = voice.detail,
                    style = fontStyle.bodySmall,
                    color = themeColors.textSecondary
                )
            }
            
            // 播放按钮
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止播放" else "播放试听",
                    tint = if (isSelected) themeColors.primary else themeColors.textSecondary,
                    modifier = Modifier.size(fontStyle.iconSize.dp)
                )
            }
            
            // 选中状态
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = themeColors.primary,
                    modifier = Modifier.size(fontStyle.iconSize.dp)
                )
            }
        }
    }
}

data class VoiceOption(
    val id: String,
    val name: String,
    val description: String,
    val detail: String
)
