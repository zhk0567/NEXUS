package com.llasm.storycontrol

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.llasm.storycontrol.config.ServerConfig
import com.llasm.storycontrol.data.UserManager
import com.llasm.storycontrol.ui.StoryScreen
import com.llasm.storycontrol.ui.theme.StoryControlAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化用户管理器
        UserManager.init(this)
        
        // 初始化服务器配置（从后端动态获取，无需修改代码）
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServerConfig.initialize(this@MainActivity)
                Log.d("MainActivity", "服务器配置初始化完成: ${ServerConfig.CURRENT_SERVER}")
            } catch (e: Exception) {
                Log.e("MainActivity", "服务器配置初始化失败: ${e.message}")
                // 即使初始化失败，也会使用默认配置，应用仍可运行
            }
        }
        
        setContent {
            StoryControlAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StoryScreen()
                }
            }
        }
    }
}
