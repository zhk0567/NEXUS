package com.llasm.nexusunified.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.content.res.Configuration

/**
 * 设置管理器 - 管理主题和字体大小设置
 */
object SettingsManager {
    // 主题模式类型 - 默认跟随系统
    private val _themeMode = MutableStateFlow("跟随系统")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()
    
    // 主题模式 - 初始值设为false，表示未初始化
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()
    
    // 标记是否已初始化
    private var isInitialized = false
    
    // 字体大小
    private val _fontSize = MutableStateFlow("中")
    val fontSize: StateFlow<String> = _fontSize.asStateFlow()
    
    // 设置主题模式
    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
    }
    
    // 设置字体大小
    fun setFontSize(size: String) {
        _fontSize.value = size
    }
    
    // 设置主题模式类型
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        // 立即应用主题模式
        when (mode) {
            "跟随系统" -> {
                // 跟随系统模式，需要context来检测系统主题
                // 这里不直接设置，等待调用者传入context
            }
            "深色模式" -> {
                _isDarkMode.value = true
            }
            "亮色模式" -> {
                _isDarkMode.value = false
            }
        }
    }
    
    // 获取当前主题颜色
    fun getThemeColors(context: Context? = null): ThemeColors {
        // 如果未初始化，使用系统主题
        if (!isInitialized && context != null) {
            val systemIsDark = isSystemDarkMode(context)
            return if (systemIsDark) ThemeColors.Dark else ThemeColors.Light
        }
        
        return if (_isDarkMode.value) {
            ThemeColors.Dark
        } else {
            ThemeColors.Light
        }
    }
    
    // 获取系统主题状态
    fun isSystemDarkMode(context: Context): Boolean {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }
    
    // 更新主题状态（根据系统设置）
    fun updateThemeFromSystem(context: Context) {
        if (_themeMode.value == "跟随系统") {
            _isDarkMode.value = isSystemDarkMode(context)
        }
    }
    
    // 初始化主题状态
    fun initializeTheme(context: Context) {
        if (!isInitialized) {
            val systemIsDark = isSystemDarkMode(context)
            
            when (_themeMode.value) {
                "跟随系统" -> {
                    _isDarkMode.value = systemIsDark
                }
                "深色模式" -> {
                    _isDarkMode.value = true
                }
                "亮色模式" -> {
                    _isDarkMode.value = false
                }
            }
            
            isInitialized = true
        }
    }
    
    // 获取当前字体样式
    fun getFontStyle(): FontStyle {
        return when (_fontSize.value) {
            "小" -> FontStyle.Small
            "大" -> FontStyle.Large
            else -> FontStyle.Medium
        }
    }
}

/**
 * 主题颜色配置
 */
data class ThemeColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val cardBackground: Color,
    val inputBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBorder: Color
) {
    companion object {
        val Light = ThemeColors(
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            primary = Color(0xFF07C160),
            onBackground = Color(0xFF333333),
            onSurface = Color(0xFF333333),
            onPrimary = Color.White,
            cardBackground = Color.White,
            inputBackground = Color(0xFFF7F7F7),
            textPrimary = Color(0xFF333333),
            textSecondary = Color(0xFF666666),
            cardBorder = Color(0xFFE0E0E0)
        )
        
        val Dark = ThemeColors(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            primary = Color(0xFF07C160),
            onBackground = Color.White,
            onSurface = Color.White,
            onPrimary = Color.White,
            cardBackground = Color(0xFF2D2D2D),
            inputBackground = Color(0xFF3A3A3A),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB0B0B0),
            cardBorder = Color(0xFF404040)
        )
    }
}

/**
 * 字体样式配置
 */
data class FontStyle(
    val bodySmall: TextStyle,
    val bodyMedium: TextStyle,
    val bodyLarge: TextStyle,
    val titleMedium: TextStyle,
    val headlineSmall: TextStyle,
    val iconSize: Float
) {
    companion object {
        val Small = FontStyle(
            bodySmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp),
            bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
            bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            iconSize = 20f
        )
        
        val Medium = FontStyle(
            bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
            bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 22.sp),
            titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            iconSize = 28f
        )
        
        val Large = FontStyle(
            bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 26.sp),
            titleMedium = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
            iconSize = 36f
        )
    }
}
