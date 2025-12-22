package com.llasm.storycontrol.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.llasm.storycontrol.data.ThemeColors
import com.llasm.storycontrol.data.FontStyle
import com.llasm.storycontrol.data.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    
    // 获取当前主题颜色 - 现在使用ThemeManager
    fun getThemeColors(context: Context? = null): ThemeColors {
        return ThemeManager.getThemeColors(_isDarkMode.value)
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
    
    // 获取当前字体样式 - 现在使用ThemeManager
    fun getFontStyle(): FontStyle {
        return ThemeManager.getFontStyle()
    }
}