package com.llasm.storycontrol.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

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
)

/**
 * 字体样式配置
 */
data class FontStyle(
    val bodySmall: androidx.compose.ui.text.TextStyle,
    val bodyMedium: androidx.compose.ui.text.TextStyle,
    val bodyLarge: androidx.compose.ui.text.TextStyle,
    val titleMedium: androidx.compose.ui.text.TextStyle,
    val headlineSmall: androidx.compose.ui.text.TextStyle,
    val iconSize: Float
)

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    LIGHT,      // 浅色模式
    DARK,       // 深色模式
    SYSTEM      // 跟随系统
}

/**
 * 字体大小枚举
 */
enum class FontSize {
    SMALL,      // 小
    MEDIUM,     // 中
    LARGE       // 大
}

/**
 * 主题管理器
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_FONT_SIZE = "font_size"
    
    private var _themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var _fontSize by mutableStateOf(FontSize.MEDIUM)
    
    val themeMode: ThemeMode get() = _themeMode
    val fontSize: FontSize get() = _fontSize
    
    /**
     * 初始化主题管理器
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _themeMode = ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        _fontSize = FontSize.valueOf(prefs.getString(KEY_FONT_SIZE, FontSize.MEDIUM.name) ?: FontSize.MEDIUM.name)
    }
    
    /**
     * 设置主题模式
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
    
    /**
     * 设置字体大小
     */
    fun setFontSize(context: Context, size: FontSize) {
        _fontSize = size
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT_SIZE, size.name).apply()
    }
    
    /**
     * 获取当前主题颜色
     */
    fun getThemeColors(isDarkMode: Boolean): ThemeColors {
        return if (isDarkMode) {
            // 深色主题
            ThemeColors(
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                primary = Color(0xFF4CAF50), // 保持绿色
                onBackground = Color.White,
                onSurface = Color.White,
                onPrimary = Color.White,
                cardBackground = Color(0xFF2D2D2D),
                inputBackground = Color(0xFF3A3A3A),
                textPrimary = Color.White,
                textSecondary = Color(0xFFB0B0B0),
                cardBorder = Color(0xFF404040)
            )
        } else {
            // 浅色主题
            ThemeColors(
                background = Color(0xFFF5F5F5),
                surface = Color.White,
                primary = Color(0xFF4CAF50), // 保持绿色
                onBackground = Color.Black,
                onSurface = Color.Black,
                onPrimary = Color.White,
                cardBackground = Color.White,
                inputBackground = Color(0xFFF0F0F0),
                textPrimary = Color.Black,
                textSecondary = Color.Gray,
                cardBorder = Color(0xFFE0E0E0)
            )
        }
    }
    
    /**
     * 获取当前字体样式
     */
    fun getFontStyle(): FontStyle {
        val (baseSize, lineHeightMultiplier) = when (_fontSize) {
            FontSize.SMALL -> 16f to 1.4f  // 小字体，行间距1.4倍
            FontSize.MEDIUM -> 18f to 1.5f  // 中字体，行间距1.5倍
            FontSize.LARGE -> 20f to 1.6f   // 大字体，行间距1.6倍
        }
        
        return FontStyle(
            bodySmall = androidx.compose.ui.text.TextStyle(
                fontSize = baseSize.sp,
                lineHeight = (baseSize * lineHeightMultiplier).sp
            ),
            bodyMedium = androidx.compose.ui.text.TextStyle(
                fontSize = (baseSize + 2).sp,
                lineHeight = ((baseSize + 2) * lineHeightMultiplier).sp
            ),
            bodyLarge = androidx.compose.ui.text.TextStyle(
                fontSize = (baseSize + 4).sp,
                lineHeight = ((baseSize + 4) * lineHeightMultiplier).sp
            ),
            titleMedium = androidx.compose.ui.text.TextStyle(
                fontSize = (baseSize + 6).sp,
                lineHeight = ((baseSize + 6) * lineHeightMultiplier).sp
            ),
            headlineSmall = androidx.compose.ui.text.TextStyle(
                fontSize = (baseSize + 8).sp,
                lineHeight = ((baseSize + 8) * lineHeightMultiplier).sp
            ),
            iconSize = baseSize + 10f
        )
    }
}
