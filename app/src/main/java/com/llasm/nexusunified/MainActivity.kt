package com.llasm.nexusunified

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llasm.nexusunified.ui.ChatScreen
import com.llasm.nexusunified.ui.theme.NEXUSUnifiedTheme
import com.llasm.nexusunified.viewmodel.ChatViewModel
import com.llasm.nexusunified.data.UserManager
import com.llasm.nexusunified.data.UserData
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.ui.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject

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
        
        // 初始化UserManager
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
    
    // 检查登录状态
    val isLoggedIn = UserManager.isLoggedIn()
    var showLoginDialog by remember { mutableStateOf(!isLoggedIn) }
    
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
    
    // 如果未登录，显示登录对话框
    if (showLoginDialog) {
        LoginDialog(
            onDismiss = { 
                // 不允许关闭登录对话框，必须登录才能使用
                // 这里不执行任何操作，保持对话框显示
            },
            onLoginSuccess = {
                showLoginDialog = false
                // 登录成功后刷新对话数据
                chatViewModel.refreshConversationData()
            }
        )
    } else {
        // 已登录，显示主界面
    ChatScreen(
        onVoiceCallClick = { 
                // 启动电话模式Activity (Compose版本)
                val intent = Intent(context, VoiceCallComposeActivity::class.java)
            context.startActivity(intent)
            },
            viewModel = chatViewModel,
            onShowLoginDialog = { showLoginDialog = true }
        )
    }
}

// 登录API调用函数
fun loginUser(username: String, password: String, callback: (Boolean, String?) -> Unit) {
    Thread {
        try {
            val url = URL(ServerConfig.getApiUrl(ServerConfig.Endpoints.AUTH_LOGIN))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonObject = JSONObject()
            jsonObject.put("username", username)
            jsonObject.put("password", password)
            jsonObject.put("device_info", "Android")
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(jsonObject.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            
            val jsonResponse = JSONObject(response.toString())
            
            if (responseCode == HttpURLConnection.HTTP_OK && jsonResponse.getBoolean("success")) {
                // 登录成功，保存用户信息
                val user = jsonResponse.getJSONObject("user")
                val sessionId = jsonResponse.getString("session_id")
                
                val userData = UserData(
                    userId = user.getString("user_id"),
                    username = user.getString("username"),
                    sessionId = sessionId,
                    createdAt = user.optString("created_at", null),
                    lastLoginAt = user.optString("last_login_at", null)
                )
                
                // 保存用户信息到SharedPreferences
                UserManager.saveLoginData(userData)
                
                callback(true, null)
            } else {
                val errorMessage = jsonResponse.optString("error", "登录失败")
                callback(false, errorMessage)
            }
            
        } catch (e: Exception) {
            callback(false, "网络连接失败: ${e.message}")
        }
    }.start()
}

@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessMessage by remember { mutableStateOf(false) }
    
    // 焦点请求器
    val passwordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 获取当前字体样式
    val fontStyle = SettingsManager.getFontStyle()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "用户登录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 用户名输入框
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                
                // 密码输入框
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .clickable {
                            passwordFocusRequester.requestFocus()
                            keyboardController?.show()
                        },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )
                
                // 成功信息显示
                if (showSuccessMessage) {
                    Text(
                        text = "登录成功！",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 错误信息显示
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = "请输入账号和密码"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = ""
                    
                    // 调用后端登录API
                    loginUser(username, password) { success, message ->
                        isLoading = false
                        if (success) {
                            showSuccessMessage = true
                            // 延迟关闭对话框，让用户看到成功提示
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(1500)
                                onLoginSuccess()
                            }
                        } else {
                            errorMessage = message ?: "登录失败"
                        }
                    }
                },
                enabled = !isLoading && username.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录")
                }
            }
        },
        dismissButton = {
            // 不显示取消按钮，强制用户登录
        }
    )
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
