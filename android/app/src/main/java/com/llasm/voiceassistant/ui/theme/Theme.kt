package com.llasm.voiceassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF0F0F23),  // 深色背景
    surface = Color(0xFF1A1A2E),  // 深色表面
    surfaceVariant = Color(0xFF16213E),  // 深色表面变体
    onBackground = Color(0xFFE2E8F0),  // 深色背景上的文字
    onSurface = Color(0xFFE2E8F0),  // 深色表面上的文字
    onSurfaceVariant = Color(0xFFCBD5E1)  // 深色表面变体上的文字
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFAFAFA),  // 浅色背景
    surface = Color(0xFFFFFFFF),  // 浅色表面
    surfaceVariant = Color(0xFFF1F5F9),  // 浅色表面变体
    onBackground = Color(0xFF1E293B),  // 浅色背景上的文字
    onSurface = Color(0xFF1E293B),  // 浅色表面上的文字
    onSurfaceVariant = Color(0xFF475569)  // 浅色表面变体上的文字
)

@Composable
fun NEXUSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
