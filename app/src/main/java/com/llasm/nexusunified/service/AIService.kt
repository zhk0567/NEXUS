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
import com.llasm.nexusunified.network.MonitorClient

/**
 * AI服务 - 直接调用DeepSeek API和火山引擎API
 */
class AIService(private val context: Context) {
    
    companion object {
        private const val TAG = "AIService"
        
        // DeepSeek API配置
        private const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        private const val DEEPSEEK_API_KEY = "sk-66a8c43ecb14406ea020b5a9dd47090d" // 从原项目获取的真实API Key
        
        // 火山引擎API配置
        private const val VOLCANO_ASR_URL = "https://openspeech.bytedance.com/api/v1/asr"
        private const val VOLCANO_TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
        private const val VOLCANO_ACCESS_KEY = "nvbcav9Xew3Vx6Td_kmcJAZbrU1-eBif" // 从原项目获取的真实Access Key
        private const val VOLCANO_APP_ID = "2684898037" // 从原项目获取的真实App ID
        private const val VOLCANO_RESOURCE_ID = "volc.speech.dialog" // 从原项目获取的真实Resource ID
        private const val VOLCANO_APP_KEY = "PlgvMymc7f3tQnJ6" // 从原项目获取的真实App Key
        
        // 实时语音对话配置 - 豆包端到端语音对话
        private const val REALTIME_WS_URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"
        
        // 豆包语音对话配置
        private const val DOUBAO_BOT_NAME = "豆包"
        private const val DOUBAO_SYSTEM_ROLE = "你是一个智能的AI助手，名字叫豆包。你使用活泼灵动的女声，性格开朗，热爱生活。你的说话风格简洁明了，语速适中，语调自然。你可以帮助用户解答问题、聊天、提供建议等。请用友好、专业的语气与用户交流。"
        private const val DOUBAO_SPEAKING_STYLE = "你的说话风格简洁明了，语速适中，语调自然，能够进行智能对话。"
        private const val DOUBAO_TTS_SPEAKER = "zh_female_vv_jupiter_bigtts" // vv音色，活泼灵动的女声
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val monitorClient = MonitorClient(context)
    
    /**
     * 文字对话 - 调用DeepSeek API
     */
    suspend fun chatWithText(message: String, conversationHistory: List<ChatMessage> = emptyList()): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始文字对话: $message")
            
            // 构建对话历史
            val messages = mutableListOf<Map<String, String>>()
            
            // 添加系统提示
            messages.add(mapOf(
                "role" to "system",
                "content" to "你是一个智能助手，请用中文回答用户的问题。回答要简洁明了，有帮助性。"
            ))
            
            // 添加对话历史
            conversationHistory.takeLast(10).forEach { chatMessage -> // 只保留最近10条对话
                messages.add(mapOf(
                    "role" to if (chatMessage.isFromUser) "user" else "assistant",
                    "content" to chatMessage.content
                ))
            }
            
            // 添加当前消息
            messages.add(mapOf(
                "role" to "user",
                "content" to message
            ))
            
            val requestBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray(messages))
                put("max_tokens", 1000)
                put("temperature", 0.7)
                put("stream", false)
            }.toString()
            
            Log.d(TAG, "DeepSeek API请求: $requestBody")
            
            val request = Request.Builder()
                .url(DEEPSEEK_API_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $DEEPSEEK_API_KEY")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val responseTime = System.currentTimeMillis() - startTime
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无错误详情"
                Log.e(TAG, "DeepSeek API调用失败: ${response.code} - ${response.message}")
                Log.e(TAG, "错误详情: $errorBody")
                
                // 发送失败的API指标到监控后端
                monitorClient.sendApiMetrics(
                    apiName = "DeepSeek API",
                    responseTime = responseTime,
                    success = false,
                    errorMessage = errorBody
                )
                
                throw IOException("DeepSeek API调用失败: ${response.code} - ${response.message}\n详情: $errorBody")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw IOException("DeepSeek API返回空响应")
            }
            
            Log.d(TAG, "DeepSeek API响应: $responseBody")
            
            // 发送成功的API指标到监控后端
            monitorClient.sendApiMetrics(
                apiName = "DeepSeek API",
                responseTime = responseTime,
                success = true
            )
            
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.getJSONObject("message")
            val content = messageObj.getString("content")
            
            val usage = jsonResponse.optJSONObject("usage")
            val totalTokens = usage?.optInt("total_tokens") ?: 0
            
            Log.d(TAG, "文字对话完成，消耗tokens: $totalTokens")
            
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
            
            val request = Request.Builder()
                .url(VOLCANO_ASR_URL)
                .post(requestBody)
                .addHeader("X-Api-App-ID", VOLCANO_APP_ID)
                .addHeader("X-Api-Access-Key", VOLCANO_ACCESS_KEY)
                .addHeader("X-Api-Resource-Id", VOLCANO_RESOURCE_ID)
                .addHeader("X-Api-App-Key", VOLCANO_APP_KEY)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("火山引擎ASR API调用失败: ${response.code} - ${response.message}")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                throw IOException("火山引擎ASR API返回空响应")
            }
            
            val jsonResponse = JSONObject(responseBody)
            val success = jsonResponse.getBoolean("success")
            
            if (!success) {
                throw IOException("语音识别失败: ${jsonResponse.optString("message", "未知错误")}")
            }
            
            val transcription = jsonResponse.getString("transcription")
            val asrTimeMs = jsonResponse.getDouble("asr_time_ms")
            
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
            
            val request = Request.Builder()
                .url(VOLCANO_TTS_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("X-Api-App-ID", VOLCANO_APP_ID)
                .addHeader("X-Api-Access-Key", VOLCANO_ACCESS_KEY)
                .addHeader("X-Api-Resource-Id", VOLCANO_RESOURCE_ID)
                .addHeader("X-Api-App-Key", VOLCANO_APP_KEY)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("火山引擎TTS API调用失败: ${response.code} - ${response.message}")
            }
            
            val audioData = response.body?.bytes()
            if (audioData == null || audioData.isEmpty()) {
                throw IOException("火山引擎TTS API返回空音频数据")
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
     * 检查API配置
     */
    fun checkApiConfiguration(): Map<String, Boolean> {
        return mapOf(
            "deepseek_configured" to (DEEPSEEK_API_KEY != "sk-your-deepseek-api-key"),
            "volcano_configured" to (VOLCANO_ACCESS_KEY != "your-volcano-api-key")
        )
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
