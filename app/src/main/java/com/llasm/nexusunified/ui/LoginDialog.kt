package com.llasm.nexusunified.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.data.UserData
import com.llasm.nexusunified.data.UserManager
import kotlinx.coroutines.*
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// 登录用户函数
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
            jsonObject.put("device_info", "Android-AIChat")
            
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
            
            val responseText = response.toString()
            
            // 检查响应是否为HTML（错误页面）
            if (responseText.trim().startsWith("<!") || responseText.trim().startsWith("<html")) {
                callback(false, "服务器返回错误页面，请检查服务器地址是否正确")
                return@Thread
            }
            
            // 尝试解析JSON
            val jsonResponse = try {
                JSONObject(responseText)
            } catch (e: Exception) {
                callback(false, "服务器响应格式错误: ${e.message}")
                return@Thread
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK && jsonResponse.optBoolean("success", false)) {
                // 登录成功，检查账号是否在白名单中
                val user = jsonResponse.getJSONObject("user")
                val username = user.getString("username")
                
                // 白名单：只允许这10个账号
                val ALLOWED_USERS = setOf("user01", "user02", "user03", "user04", "user05",
                                          "user06", "user07", "user08", "user09", "user10")
                
                if (username !in ALLOWED_USERS) {
                    android.util.Log.w("LoginDialog", "拒绝登录：用户名不在允许列表中")
                    callback(false, "该账号不允许登录，请联系管理员")
                    return@Thread
                }
                
                val sessionId = jsonResponse.getString("session_id")
                
                val userData = UserData(
                    userId = user.getString("user_id"),
                    username = username,
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

// 登录对话框组件
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
    
    // 获取协程作用域
    val scope = rememberCoroutineScope()
    
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
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "账号"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "密码"
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible }
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                        // 切换到主线程更新UI
                        Handler(Looper.getMainLooper()).post {
                            isLoading = false
                            if (success) {
                                showSuccessMessage = true
                                // 延迟关闭对话框，让用户看到成功提示
                                scope.launch {
                                    delay(1500L)
                                    onLoginSuccess()
                                }
                            } else {
                                errorMessage = message ?: "登录失败"
                            }
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
            TextButton(onClick = onDismiss) {
                Text("取消", style = fontStyle.bodyMedium)
            }
        }
    )
}

