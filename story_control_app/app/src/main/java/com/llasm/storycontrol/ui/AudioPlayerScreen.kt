package com.llasm.storycontrol.ui

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.Story
import com.llasm.storycontrol.data.ThemeColors

/**
 * 音频播放页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    story: Story,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    isAudioCompleted: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onCompleteReading: () -> Unit
) {
    // 处理系统返回键
    BackHandler {
        onBack()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "音频播放",
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
                .background(themeColors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 故事封面区域
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = themeColors.cardBackground
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "音频",
                        tint = themeColors.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 故事标题
            Text(
                text = story.title,
                style = fontStyle.headlineSmall,
                color = themeColors.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 播放控制按钮
            Button(
                onClick = onPlayPause,
                modifier = Modifier.size(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.primary
                ),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = themeColors.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 进度条
            Column(
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = themeColors.primary,
                    trackColor = themeColors.cardBorder
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                    Text(
                        text = formatTime(duration),
                        style = fontStyle.bodySmall,
                        color = themeColors.textSecondary
                    )
                }
            }
            
            // 音频播放完成后的完成阅读按钮
            if (isAudioCompleted) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = onCompleteReading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.primary
                    )
                ) {
                    Text(
                        text = "完成阅读",
                        style = fontStyle.bodyMedium,
                        color = themeColors.onPrimary
                    )
                }
            }
        }
    }
}

