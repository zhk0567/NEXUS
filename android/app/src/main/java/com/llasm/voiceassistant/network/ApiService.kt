package com.llasm.voiceassistant.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, Any>>
    
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
    
    @POST("api/tts")
    suspend fun textToSpeech(@Body request: Map<String, String>): Response<ResponseBody>
}

data class ChatResponse(
    val success: Boolean,
    val response: String,
    val api_time_ms: Double
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
