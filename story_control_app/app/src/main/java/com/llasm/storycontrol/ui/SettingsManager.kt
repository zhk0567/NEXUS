package com.llasm.storycontrol.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置管理器
 * 管理主题模式和字体大小设置
 */
object SettingsManager {
    
    // 主题颜色配置
    data class ThemeColors(
        val background: Color,
        val surface: Color,
        val onSurface: Color,
        val primary: Color,
        val onPrimary: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val cardBackground: Color,
        val inputBackground: Color
    )
    
    // 字体样式配置
    data class FontStyle(
        val headlineSmall: TextStyle,
        val titleMedium: TextStyle,
        val bodyMedium: TextStyle,
        val bodySmall: TextStyle,
        val bodyLarge: TextStyle,
        val iconSize: Int
    )
    
    // 深色模式状态
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()
    
    // 字体大小状态
    private val _fontSize = MutableStateFlow("中")
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()
    
    /**
     * 设置深色模式
     */
    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
    }
    
    /**
     * 设置字体大小
     */
    fun setFontSize(size: String) {
        _fontSize.value = size
    }
    
    /**
     * 获取主题颜色
     */
    fun getThemeColors(): ThemeColors {
        val isDark = _isDarkMode.value
        return if (isDark) {
            ThemeColors(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE6E1E5),
                primary = Color(0xFF90CAF9),
                onPrimary = Color(0xFF0D47A1),
                textPrimary = Color(0xFFE6E1E5),
                textSecondary = Color(0xFFB0B0B0),
                cardBackground = Color(0xFF2C2C2C),
                inputBackground = Color(0xFF2C2C2C)
            )
        } else {
            ThemeColors(
                background = Color(0xFFF5F5F5),
                surface = Color.White,
                onSurface = Color(0xFF1C1B1F),
                primary = Color(0xFF2196F3),
                onPrimary = Color.White,
                textPrimary = Color(0xFF212121),
                textSecondary = Color(0xFF757575),
                cardBackground = Color.White,
                inputBackground = Color(0xFFF8F9FA)
            )
        }
    }
    
    /**
     * 获取字体样式
     */
    fun getFontStyle(): FontStyle {
        val size = _fontSize.value
        val baseSize = when (size) {
            "小" -> 1.0f
            "中" -> 1.2f
            "大" -> 1.4f
            else -> 1.2f
        }
        
        return FontStyle(
            headlineSmall = TextStyle(
                fontSize = (24 * baseSize).sp,
                fontWeight = FontWeight.Bold
            ),
            titleMedium = TextStyle(
                fontSize = (18 * baseSize).sp,
                fontWeight = FontWeight.Medium
            ),
            bodyMedium = TextStyle(
                fontSize = (16 * baseSize).sp,
                fontWeight = FontWeight.Normal
            ),
            bodySmall = TextStyle(
                fontSize = (14 * baseSize).sp,
                fontWeight = FontWeight.Normal
            ),
            bodyLarge = TextStyle(
                fontSize = (18 * baseSize).sp,
                fontWeight = FontWeight.Normal
            ),
            iconSize = (24 * baseSize).toInt()
        )
    }
}
