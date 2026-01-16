package com.llasm.nexusunified.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.llasm.nexusunified.config.ServerConfig

/**
 * AI服务 - 通过后端代理调用，不包含任何API密钥
 */
class AIService(private val context: Context) {
    
    companion object {
        private const val TAG = "AIService"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 文字对话 - 通过后端API调用，不直接调用DeepSeek
     */
    suspend fun chatWithText(message: String, conversationHistory: List<ChatMessage> = emptyList()): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始文字对话: $message")
            
            // 构建对话历史（用于后端上下文）
            val historyList = mutableListOf<Map<String, String>>()
            conversationHistory.takeLast(10).forEach { chatMessage ->
                historyList.add(mapOf(
                    "role" to if (chatMessage.isFromUser) "user" else "assistant",
                    "content" to chatMessage.content
                ))
            }
            
            // 检查用户是否已登录
            val userId = com.llasm.nexusunified.data.UserManager.getUserId()
            if (userId == null) {
                Log.e(TAG, "用户未登录，无法发送消息")
                return@withContext Result.failure(Exception("请先登录后再使用聊天功能"))
            }
            
            // 构建请求体（发送给后端，后端会处理DeepSeek调用）
            val requestBody = JSONObject().apply {
                put("message", message)
                put("user_id", userId)
                put("session_id", com.llasm.nexusunified.data.UserManager.getSessionId() ?: "")
                if (historyList.isNotEmpty()) {
                    put("conversation_history", JSONArray(historyList))
                }
            }.toString()
            
            Log.d(TAG, "通过后端API进行文字对话")
            
            // 通过后端API调用，不直接调用DeepSeek
            val apiUrl = ServerConfig.getApiUrl(ServerConfig.Endpoints.CHAT)
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()
            
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val responseTime = System.currentTimeMillis() - startTime
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无错误详情"
                Log.e(TAG, "后端API调用失败: ${response.code} - ${response.message}")
                Log.e(TAG, "错误详情: $errorBody")
                throw IOException("后端API调用失败: ${response.code} - ${response.message}\n详情: $errorBody")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw IOException("后端API返回空响应")
            }
            
            Log.d(TAG, "后端API响应: $responseBody")
            
            val jsonResponse = JSONObject(responseBody)
            if (!jsonResponse.optBoolean("success", false)) {
                throw IOException("后端API返回错误: ${jsonResponse.optString("error", "未知错误")}")
            }
            
            val content = jsonResponse.getString("message")
            
            Log.d(TAG, "文字对话完成")
            
            Result.success(ChatResponse(
                success = true,
                response = content,
                api_time_ms = 0.0, // DeepSeek API不返回时间信息
                audio = null,
                first_two_sentences = content.take(100), // 取前100个字符作为预览
                sentence_count = content.split("。").size
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "文字对话失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 语音转文字 - 调用火山引擎ASR API
     */
    suspend fun transcribeAudio(audioData: ByteArray): Result<TranscriptionResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始语音转文字，音频大小: ${audioData.size} 字节")
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.wav", audioData.toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("format", "wav")
                .addFormDataPart("sample_rate", "16000")
                .addFormDataPart("language", "zh")
                .build()
            
            // 通过后端API调用，不直接调用火山引擎
            val apiUrl = ServerConfig.getApiUrl(ServerConfig.Endpoints.TRANSCRIBE)
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("后端ASR API调用失败: ${response.code} - ${response.message}")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw IOException("后端ASR API返回空响应")
            }
            
            val jsonResponse = JSONObject(responseBody)
            val success = jsonResponse.getBoolean("success")
            
            if (!success) {
                throw IOException("语音识别失败: ${jsonResponse.optString("error", "未知错误")}")
            }
            
            val transcription = jsonResponse.getString("text")
            val asrTimeMs = jsonResponse.optDouble("duration", 0.0) * 1000
            
            Log.d(TAG, "语音转文字完成: $transcription")
            
            Result.success(TranscriptionResponse(
                success = true,
                transcription = transcription,
                asr_time_ms = asrTimeMs
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "语音转文字失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 文字转语音 - 调用火山引擎TTS API
     */
    suspend fun textToSpeech(text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始文字转语音: $text")
            
            val requestBody = JSONObject().apply {
                put("text", text)
                put("voice", "zh_female_01") // 使用中文女声
                put("format", "wav")
                put("sample_rate", 16000)
                put("speed", 1.0)
                put("volume", 1.0)
            }.toString()
            
            // 通过后端API调用，不直接调用火山引擎
            val apiUrl = ServerConfig.getApiUrl(ServerConfig.Endpoints.TTS)
            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("后端TTS API调用失败: ${response.code} - ${response.message}")
            }
            
            val audioData = response.body?.bytes()
            if (audioData == null || audioData.isEmpty()) {
                throw IOException("后端TTS API返回空音频数据")
            }
            
            Log.d(TAG, "文字转语音完成，音频大小: ${audioData.size} 字节")
            
            Result.success(audioData)
            
        } catch (e: Exception) {
            Log.e(TAG, "文字转语音失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 端到端语音对话 - 语音输入，语音输出
     */
    suspend fun voiceChat(audioData: ByteArray): Result<VoiceChatResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始端到端语音对话，音频大小: ${audioData.size} 字节")
            
            // 1. 语音转文字
            val transcriptionResult = transcribeAudio(audioData)
            if (transcriptionResult.isFailure) {
                return@withContext Result.failure(transcriptionResult.exceptionOrNull() ?: Exception("语音识别失败"))
            }
            
            val transcription = transcriptionResult.getOrThrow()
            Log.d(TAG, "语音识别结果: ${transcription.transcription}")
            
            // 2. 文字对话
            val chatResult = chatWithText(transcription.transcription)
            if (chatResult.isFailure) {
                return@withContext Result.failure(chatResult.exceptionOrNull() ?: Exception("文字对话失败"))
            }
            
            val chatResponse = chatResult.getOrThrow()
            Log.d(TAG, "AI回复: ${chatResponse.response}")
            
            // 3. 文字转语音
            val ttsResult = textToSpeech(chatResponse.response)
            if (ttsResult.isFailure) {
                // 即使TTS失败，也返回文字回复
                Log.w(TAG, "文字转语音失败，返回文字回复")
                return@withContext Result.success(VoiceChatResponse(
                    success = true,
                    transcription = transcription.transcription,
                    response = chatResponse.response,
                    asr_time_ms = transcription.asr_time_ms,
                    api_time_ms = chatResponse.api_time_ms
                ))
            }
            
            Log.d(TAG, "端到端语音对话完成")
            
            Result.success(VoiceChatResponse(
                success = true,
                transcription = transcription.transcription,
                response = chatResponse.response,
                asr_time_ms = transcription.asr_time_ms,
                api_time_ms = chatResponse.api_time_ms
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "端到端语音对话失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检查API配置（通过后端检查）
     */
    suspend fun checkApiConfiguration(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = ServerConfig.getApiUrl(ServerConfig.Endpoints.HEALTH)
            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                mapOf(
                    "backend_available" to true,
                    "deepseek_configured" to true,
                    "volcano_configured" to true
                )
            } else {
                mapOf(
                    "backend_available" to false,
                    "deepseek_configured" to false,
                    "volcano_configured" to false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查API配置失败", e)
            mapOf(
                "backend_available" to false,
                "deepseek_configured" to false,
                "volcano_configured" to false
            )
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 聊天响应数据类
 */
data class ChatResponse(
    val success: Boolean,
    val response: String,
    val api_time_ms: Double,
    val audio: String? = null,
    val first_two_sentences: String? = null,
    val sentence_count: Int? = null
)

/**
 * 语音识别响应数据类
 */
data class TranscriptionResponse(
    val success: Boolean,
    val transcription: String,
    val asr_time_ms: Double
)

/**
 * 语音对话响应数据类
 */
data class VoiceChatResponse(
    val success: Boolean,
    val transcription: String,
    val response: String,
    val asr_time_ms: Double,
    val api_time_ms: Double
)
