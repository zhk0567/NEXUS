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
        fun onComplete(text: String, sentenceCount: Int)
        fun onError(message: String)
    }
    
    /**
     * 开始流式文字对话
     */
    fun startStreamingChat(
        message: String,
        conversationHistory: List<com.llasm.nexusunified.data.ChatMessage> = emptyList(),
        callback: StreamingCallback
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🚀 开始流式文字对话: $message")
                Log.d(TAG, "📚 对话历史长度: ${conversationHistory.size}")
                
                // 构建对话历史JSON数组
                val historyArray = org.json.JSONArray()
                for (historyMessage in conversationHistory) {
                    val historyItem = org.json.JSONObject().apply {
                        put("content", historyMessage.content)
                        put("isUser", historyMessage.isUser)
                    }
                    historyArray.put(historyItem)
                }
                
                val requestBody = JSONObject().apply {
                    put("message", message)
                    put("conversation_history", historyArray)
                    put("user_id", ServerConfig.ANDROID_USER_ID)
                    put("session_id", ServerConfig.ANDROID_SESSION_ID)
                }.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$AI_BACKEND_URL$STREAMING_CHAT_ENDPOINT")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "❌ 流式请求失败", e)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onError("网络请求失败: ${e.message}")
                        }
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "✅ 流式连接建立成功")
                            processStreamingResponse(response.body!!, callback)
                        } else {
                            Log.e(TAG, "❌ 流式请求失败: ${response.code}")
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onError("服务器错误: ${response.code}")
                            }
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 启动流式文字对话失败", e)
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
                                message = streamingResponse.optString("message", null)
                            )
                            
                            handleStreamingResponse(response, callback)
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 解析流式响应失败", e)
                        }
                    }
                }
                
                Log.d(TAG, "🏁 流式响应处理完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理流式响应失败", e)
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
                Log.d(TAG, "📝 文本更新: ${response.content}")
                withContext(Dispatchers.Main) {
                    callback.onTextUpdate(
                        response.content ?: "",
                        response.full_text ?: "",
                        response.sentence_count ?: 0
                    )
                }
            }
            
            "complete" -> {
                Log.d(TAG, "✅ 流式响应完成")
                withContext(Dispatchers.Main) {
                    callback.onComplete(
                        response.text ?: "",
                        response.sentence_count ?: 0
                    )
                }
            }
            
            "error" -> {
                Log.e(TAG, "❌ 流式响应错误: ${response.message}")
                withContext(Dispatchers.Main) {
                    callback.onError(response.message ?: "未知错误")
                }
            }
            
            "end" -> {
                Log.d(TAG, "🏁 流式响应结束")
            }
        }
    }
}
