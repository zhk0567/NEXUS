package com.llasm.storycontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle

/**
 * 空状态组件
 */
@Composable
fun EmptyState(
    message: String,
    themeColors: ThemeColors,
    fontStyle: FontStyle
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "空状态",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = fontStyle.bodyLarge,
                color = themeColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
