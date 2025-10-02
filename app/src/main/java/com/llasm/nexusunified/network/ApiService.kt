package com.llasm.nexusunified.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import okhttp3.RequestBody
import retrofit2.Call

interface ApiService {
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, String>>
    
    @POST("api/chat")
    suspend fun chatWithAI(
        @Body request: Map<String, String>,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Response<ChatResponse>
    
    @Multipart
    @POST("api/transcribe")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Response<TranscriptionResponse>
    
    @Multipart
    @POST("api/voice_chat")
    suspend fun voiceChat(
        @Part audio: MultipartBody.Part,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Response<VoiceChatResponse>
    
    @Multipart
    @POST("api/voice_chat_streaming")
    fun voiceChatStreaming(
        @Part audio: MultipartBody.Part,
        @HeaderMap headers: Map<String, String> = emptyMap()
    ): Call<ResponseBody>
    
    @POST("api/tts")
    suspend fun textToSpeech(@Body request: Map<String, String>): Response<ResponseBody>
    
    @POST("api/interactions/log")
    suspend fun logInteraction(@Body request: LogInteractionRequest): Response<Map<String, String>>
}

data class ChatResponse(
    val success: Boolean,
    val response: String,
    val api_time_ms: Double,
    val audio: String? = null,  // 添加音频数据字段
    val first_two_sentences: String? = null,  // 添加前两句话字段
    val sentence_count: Int? = null  // 添加句子数字段
)

data class TranscriptionResponse(
    val success: Boolean,
    val transcription: String,
    val asr_time_ms: Double
)

data class VoiceChatResponse(
    val success: Boolean,
    val transcription: String,
    val response: String,
    val asr_time_ms: Double,
    val api_time_ms: Double
)

data class LogInteractionRequest(
    val user_id: String,
    val interaction_type: String,
    val content: String,
    val response: String,
    val session_id: String,
    val duration_seconds: Int,
    val success: Boolean,
    val error_message: String
)

