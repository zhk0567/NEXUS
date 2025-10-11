package com.llasm.storycontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    themeColors: ThemeColors,
    fontStyle: FontStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图标
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = themeColors.primary,
                    modifier = Modifier.size(fontStyle.iconSize.dp)
                )
                
                // 标题和副标题
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = fontStyle.titleMedium,
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = fontStyle.bodySmall,
                            color = themeColors.textSecondary
                        )
                    }
                }
            }
            
            // 箭头图标
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = "进入",
                tint = themeColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
