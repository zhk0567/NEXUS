package com.llasm.voiceassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llasm.voiceassistant.identity.UserManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRegistrationScreen(
    userManager: UserManager,
    onRegistrationComplete: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var registrationMessage by remember { mutableStateOf("") }
    
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "用户注册",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF424242)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 显示当前用户状态
            currentUser?.let { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (user.isRegistered) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (user.isRegistered) "已注册用户" else "设备用户",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (user.isRegistered) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Text(
                            text = "统计ID: ${userManager.getStatisticsUserId() ?: "未知"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "设备ID: ${user.deviceId.take(8)}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 注册表单
            if (currentUser?.isRegistered != true) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF212121),
                        unfocusedTextColor = Color(0xFF212121),
                        cursorColor = Color(0xFF424242),
                        focusedPlaceholderColor = Color(0xFF757575),
                        unfocusedPlaceholderColor = Color(0xFF757575),
                        focusedBorderColor = Color(0xFF424242),
                        unfocusedBorderColor = Color(0xFFBDBDBD)
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱 (可选)") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        if (nickname.isNotBlank() && phone.isNotBlank()) {
                            isRegistering = true
                            val success = userManager.registerUser(nickname, phone, email.takeIf { it.isNotBlank() })
                            isRegistering = false
                            
                            if (success) {
                                registrationMessage = "注册成功！现在使用用户ID进行统计。"
                                onRegistrationComplete()
                            } else {
                                registrationMessage = "注册失败，请重试。"
                            }
                        } else if (nickname.isBlank()) {
                            registrationMessage = "请输入昵称"
                        } else if (phone.isBlank()) {
                            registrationMessage = "请输入手机号"
                        }
                    },
                    enabled = !isRegistering && nickname.isNotBlank() && phone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFBDBDBD),
                        disabledContentColor = Color(0xFF757575)
                    )
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("注册用户")
                }
                
                if (registrationMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = registrationMessage,
                        color = if (registrationMessage.contains("成功")) 
                            Color(0xFF424242)
                        else 
                            Color(0xFF424242),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // 已注册用户显示信息
                Text(
                    text = "您已经是注册用户",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF424242)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "昵称: ${currentUser?.nickname ?: "未设置"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                currentUser?.phone?.let { phone ->
                    Text(
                        text = "手机: $phone",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                currentUser?.email?.let { email ->
                    Text(
                        text = "邮箱: $email",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onRegistrationComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242),
                        contentColor = Color.White
                    )
                ) {
                    Text("继续使用")
                }
            }
        }
    }
}
