package com.llasm.nexusunified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llasm.nexusunified.ui.ChatScreen
import com.llasm.nexusunified.ui.VoiceCallScreen
import com.llasm.nexusunified.ui.theme.NEXUSUnifiedTheme
import com.llasm.nexusunified.viewmodel.ChatViewModel
import com.llasm.nexusunified.viewmodel.VoiceCallViewModel
// import com.llasm.nexusunified.controller.SyncController // 暂时禁用同步功能
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    
    private var hasRecordingPermission by mutableStateOf(false)
    private var chatViewModel: ChatViewModel? = null
    // private lateinit var syncController: SyncController // 暂时禁用同步功能
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasRecordingPermission = isGranted
        if (isGranted) {
            Log.d("MainActivity", "录音权限已授予")
        } else {
            Log.w("MainActivity", "录音权限被拒绝")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 暂时禁用同步功能，避免启动时崩溃
        // syncController = SyncController(this)
        
        // 检查录音权限状态
        hasRecordingPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        // 添加生命周期监听
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // 暂时禁用自动同步，避免启动时崩溃
                        Log.d("MainActivity", "应用恢复，同步功能已禁用")
                        // TODO: 后续版本中重新启用同步功能
                    }
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                        // 应用进入后台或退出时停止语音播放
                        chatViewModel?.stopAudio()
                        // 同步功能已禁用
                        // syncController.stopAutoSync()
                    }
                    else -> {}
                }
            }
        })
        
        setContent {
            NEXUSUnifiedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        hasRecordingPermission = hasRecordingPermission,
                        onRequestPermission = {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onViewModelCreated = { viewModel ->
                            chatViewModel = viewModel
                            // 初始化AI服务
                            viewModel.initializeAIService(this@MainActivity)
                        }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 同步功能已禁用
        // syncController.cleanup()
    }
}

@Composable
fun MainScreen(
    hasRecordingPermission: Boolean,
    onRequestPermission: () -> Unit,
    onViewModelCreated: (ChatViewModel) -> Unit = {}
) {
    val chatViewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    var showVoiceCall by remember { mutableStateOf(false) }
    
    // 通知MainActivity chatViewModel已创建
    LaunchedEffect(chatViewModel) {
        onViewModelCreated(chatViewModel)
    }
    
    // 应用退出时停止所有语音播放
    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.stopAudio()
        }
    }
    
    if (showVoiceCall) {
        VoiceCallScreen(
            onBackClick = { showVoiceCall = false },
            viewModel = viewModel()
        )
    } else {
        ChatScreen(
            onVoiceCallClick = { showVoiceCall = true },
            viewModel = chatViewModel
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NEXUSUnifiedTheme {
        MainScreen(
            hasRecordingPermission = true,
            onRequestPermission = {}
        )
    }
}
