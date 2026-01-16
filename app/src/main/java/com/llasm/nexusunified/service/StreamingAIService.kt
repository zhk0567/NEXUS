package com.llasm.nexusunified.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.llasm.nexusunified.network.StreamingResponse
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.data.UserManager
import com.llasm.nexusunified.util.ErrorReporter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 流式AI服务 - 支持流式文字对话
 */
class StreamingAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "StreamingAIService"
        private val AI_BACKEND_URL = ServerConfig.CURRENT_SERVER
        private const val STREAMING_CHAT_ENDPOINT = "/api/chat_streaming"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    interface StreamingCallback {
        fun onTextUpdate(content: String, fullText: String, sentenceCount: Int)
        fun onComplete(text: String, sentenceCount: Int, sessionId: String? = null)
        fun onError(message: String)
        fun onSearchStatus(message: String) // 联网搜索状态回调
    }
    
    /**
     * 开始流式文字对话
     * @param isRefresh 是否为刷新请求，刷新时使用更高的temperature以增加变化
     */
    fun startStreamingChat(
        message: String,
        conversationHistory: List<com.llasm.nexusunified.data.ChatMessage> = emptyList(),
        callback: StreamingCallback,
        isRefresh: Boolean = false
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                
                // 构建对话历史JSON数组（限制长度，避免请求过大）
                val historyArray = org.json.JSONArray()
                // 只保留最近20条对话历史，避免请求过大
                val recentHistory = conversationHistory.takeLast(20)
                for (historyMessage in recentHistory) {
                    // 验证内容不为空
                    val content = historyMessage.content?.trim() ?: ""
                    if (content.isEmpty()) {
                        continue
                    }
                    
                    // 限制单条消息长度（避免过长）
                    val maxContentLength = 2000
                    val safeContent = if (content.length > maxContentLength) {
                        content.substring(0, maxContentLength) + "..."
                    } else {
                        content
                    }
                    
                    try {
                    val historyItem = org.json.JSONObject().apply {
                            put("content", safeContent)
                        put("isUser", historyMessage.isUser)
                    }
                    historyArray.put(historyItem)
                    } catch (e: Exception) {
                        Log.e(TAG, "构建对话历史项失败", e)
                        // 跳过有问题的消息，继续处理其他消息
                    }
                }
                
                
                // 检查用户是否已登录
                val userId = UserManager.getUserId()
                if (userId == null) {
                    Log.e(TAG, "用户未登录，无法发送消息")
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError("请先登录后再使用聊天功能")
                    }
                    return@launch
                }
                
                // 如果对话历史为空，表示是新话题，不发送session_id（让后端自动创建新的）
                // 如果对话历史不为空，使用当前对话的session_id（从UserManager获取，应该是对应历史对话的session_id）
                val sessionId = if (conversationHistory.isEmpty()) {
                    null  // 新话题，不发送session_id，让后端自动创建
                } else {
                    // 继续历史对话，使用当前对话的session_id（已在selectConversation时设置到UserManager）
                    UserManager.getSessionId()
                }
                
                // 验证和清理消息内容
                val safeMessage = message.trim().take(1000) // 限制消息长度
                if (safeMessage.isEmpty()) {
                    Log.e(TAG, "消息内容为空")
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError("消息内容不能为空")
                    }
                    return@launch
                }
                
                val requestBody = try {
                    JSONObject().apply {
                        put("message", safeMessage)
                    put("conversation_history", historyArray)
                        put("user_id", userId)  // userId已经验证不为null
                    // 如果sessionId为null，不添加到请求中（或发送空字符串）
                        if (sessionId != null && sessionId.isNotBlank()) {
                        put("session_id", sessionId)
                    } else {
                        put("session_id", "")  // 发送空字符串，让后端创建新session
                    }
                    // 添加刷新标识，用于后端调整temperature
                    put("is_refresh", isRefresh)
                    }.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "构建请求体失败", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        callback.onError("构建请求失败: ${e.message}")
                    }
                    return@launch
                }
                
                val requestBodySize = requestBody.length
                if (requestBodySize > 50000) {
                    Log.w(TAG, "请求体较大: $requestBodySize 字符")
                }
                
                val requestBodyObj = requestBody.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$AI_BACKEND_URL$STREAMING_CHAT_ENDPOINT")
                    .post(requestBodyObj)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "流式请求失败", e)
                        
                        // 上报网络错误
                        ErrorReporter.reportNetworkError(
                            context = context,
                            errorMessage = "流式请求网络失败: ${e.message}",
                            throwable = e
                        )
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onError("网络请求失败: ${e.message}")
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            processStreamingResponse(response.body!!, callback)
                        } else {
                            Log.e(TAG, "流式请求失败: ${response.code}")
                            
                            // 读取响应内容用于错误报告
                            val responseBody = response.body?.string() ?: ""
                            
                            // 上报API错误
                            ErrorReporter.reportApiError(
                                context = context,
                                apiEndpoint = "$AI_BACKEND_URL$STREAMING_CHAT_ENDPOINT",
                                errorMessage = "流式请求失败: HTTP ${response.code}",
                                requestData = requestBody,
                                responseData = responseBody,
                                throwable = null
                            )
                            
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onError("服务器错误: ${response.code}")
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "启动流式文字对话失败", e)
                        
                        // 上报启动错误
                        ErrorReporter.reportError(
                            context = context,
                            errorType = "streaming_start_error",
                            errorLevel = "ERROR",
                            errorMessage = "启动流式文字对话失败: ${e.message}",
                            throwable = e
                        )
                        
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError("启动失败: ${e.message}")
                }
            }
        }
    }
    
    private fun processStreamingResponse(
        responseBody: ResponseBody,
        callback: StreamingCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line
                    if (currentLine?.startsWith("data: ") == true) {
                        val jsonData = currentLine.substring(6) // 移除 "data: " 前缀
                        
                        try {
                            val streamingResponse = JSONObject(jsonData)
                            val response = StreamingResponse(
                                type = streamingResponse.getString("type"),
                                content = streamingResponse.optString("content", null),
                                full_text = streamingResponse.optString("full_text", null),
                                text = streamingResponse.optString("text", null),
                                sentence_count = streamingResponse.optInt("sentence_count", 0),
                                message = streamingResponse.optString("message", null),
                                session_id = streamingResponse.optString("session_id", null)
                            )
                            
                            // session_id会在complete消息中通过callback传递，这里不需要处理
                            handleStreamingResponse(response, callback)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "解析流式响应失败", e)
                        }
                    }
                }
                
                
            } catch (e: Exception) {
                Log.e(TAG, "处理流式响应失败", e)
                            
                            // 上报处理错误
                            ErrorReporter.reportError(
                                context = context,
                                errorType = "streaming_response_error",
                                errorLevel = "ERROR",
                                errorMessage = "处理流式响应失败: ${e.message}",
                                throwable = e,
                                errorContext = mapOf(
                                    "endpoint" to "$AI_BACKEND_URL$STREAMING_CHAT_ENDPOINT"
                                )
                            )
                            
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError("处理响应失败: ${e.message}")
                }
            } finally {
                responseBody.close()
            }
        }
    }
    
    private suspend fun handleStreamingResponse(
        response: StreamingResponse,
        callback: StreamingCallback
    ) {
        when (response.type) {
            "text_update" -> {
                withContext(Dispatchers.Main) {
                    callback.onTextUpdate(
                        response.content ?: "",
                        response.full_text ?: "",
                        response.sentence_count ?: 0
                    )
                }
            }
            
            "complete" -> {
                withContext(Dispatchers.Main) {
                    callback.onComplete(
                        response.text ?: "",
                        response.sentence_count ?: 0,
                        response.session_id
                    )
                }
            }
            
            "error" -> {
                val errorMessage = response.message ?: "未知错误"
                Log.e(TAG, "流式响应错误: $errorMessage")
                
                // 上报流式响应错误
                ErrorReporter.reportApiError(
                    context = context,
                    apiEndpoint = "$AI_BACKEND_URL$STREAMING_CHAT_ENDPOINT",
                    errorMessage = "流式响应错误: $errorMessage",
                    responseData = errorMessage
                )
                
                withContext(Dispatchers.Main) {
                    callback.onError(errorMessage)
                }
            }
            
            "search_status" -> {
                withContext(Dispatchers.Main) {
                    callback.onSearchStatus(response.message ?: "正在搜索...")
                }
            }
            
            "end" -> {
            }
        }
    }
}
