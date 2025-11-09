package com.llasm.storycontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.llasm.storycontrol.data.UserManager
import com.llasm.storycontrol.ui.StoryScreen
import com.llasm.storycontrol.ui.theme.StoryControlAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化用户管理器
        UserManager.init(this)
        
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
