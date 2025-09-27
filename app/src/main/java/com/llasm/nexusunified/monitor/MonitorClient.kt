package com.llasm.nexusunified.monitor

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 监控客户端 - 向前端监控后端发送数据
 */
class MonitorClient(private val context: Context) {
    
    companion object {
        private const val TAG = "MonitorClient"
        private const val MONITOR_BASE_URL = "http://10.0.2.2:5001" // Android模拟器访问本地服务器
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 发送应用状态
     */
    fun sendAppStatus(
        appVersion: String = "1.0.0",
        userId: String = "user_${System.currentTimeMillis()}",
        sessionId: String = "session_${System.currentTimeMillis()}",
        isActive: Boolean = true,
        currentScreen: String = "MainActivity",
        lastActivity: String = "app_start",
        memoryUsage: Float = 0f,
        cpuUsage: Float = 0f,
        networkStatus: String = "connected",
        apiCallsCount: Int = 0,
        errorCount: Int = 0,
        batteryLevel: Int? = null,
        deviceInfo: Map<String, Any>? = null
    ) {
        scope.launch {
            try {
                val statusData = mapOf(
                    "app_version" to appVersion,
                    "user_id" to userId,
                    "session_id" to sessionId,
                    "is_active" to isActive,
                    "current_screen" to currentScreen,
                    "last_activity" to lastActivity,
                    "memory_usage" to memoryUsage,
                    "cpu_usage" to cpuUsage,
                    "network_status" to networkStatus,
                    "api_calls_count" to apiCallsCount,
                    "error_count" to errorCount,
                    "battery_level" to batteryLevel,
                    "device_info" to deviceInfo
                )
                
                val requestBody = JSONObject(statusData).toString()
                val request = Request.Builder()
                    .url("$MONITOR_BASE_URL/log/app_status")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "应用状态发送成功")
                } else {
                    Log.w(TAG, "应用状态发送失败: ${response.code}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "发送应用状态失败", e)
            }
        }
    }
    
    /**
     * 发送API指标
     */
    fun sendApiMetrics(
        apiName: String,
        endpoint: String,
        responseTime: Double,
        statusCode: Int,
        success: Boolean,
        errorMessage: String? = null,
        requestSize: Int? = null,
        responseSize: Int? = null
    ) {
        scope.launch {
            try {
                val metricsData = mapOf(
                    "api_name" to apiName,
                    "endpoint" to endpoint,
                    "response_time" to responseTime,
                    "status_code" to statusCode,
                    "success" to success,
                    "error_message" to errorMessage,
                    "request_size" to requestSize,
                    "response_size" to responseSize
                )
                
                val requestBody = JSONObject(metricsData).toString()
                val request = Request.Builder()
                    .url("$MONITOR_BASE_URL/log/api_metrics")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "API指标发送成功: $apiName")
                } else {
                    Log.w(TAG, "API指标发送失败: ${response.code}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "发送API指标失败", e)
            }
        }
    }
    
    /**
     * 发送用户行为
     */
    fun sendUserBehavior(
        userId: String = "user_${System.currentTimeMillis()}",
        actionType: String,
        actionData: Map<String, Any> = emptyMap(),
        duration: Double? = null,
        success: Boolean = true
    ) {
        scope.launch {
            try {
                val behaviorData = mapOf(
                    "user_id" to userId,
                    "action_type" to actionType,
                    "action_data" to actionData,
                    "duration" to duration,
                    "success" to success
                )
                
                val requestBody = JSONObject(behaviorData).toString()
                val request = Request.Builder()
                    .url("$MONITOR_BASE_URL/log/user_behavior")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "用户行为发送成功: $actionType")
                } else {
                    Log.w(TAG, "用户行为发送失败: ${response.code}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "发送用户行为失败", e)
            }
        }
    }
    
    /**
     * 发送聊天行为
     */
    fun sendChatBehavior(
        userId: String,
        message: String,
        response: String? = null,
        duration: Double? = null,
        success: Boolean = true
    ) {
        val actionData = mapOf(
            "message_length" to message.length,
            "has_response" to (response != null),
            "response_length" to (response?.length ?: 0)
        )
        
        sendUserBehavior(
            userId = userId,
            actionType = "chat",
            actionData = actionData,
            duration = duration,
            success = success
        )
    }
    
    /**
     * 发送语音行为
     */
    fun sendVoiceBehavior(
        userId: String,
        duration: Double,
        success: Boolean = true
    ) {
        val actionData = mapOf(
            "voice_duration" to duration,
            "voice_type" to "recording"
        )
        
        sendUserBehavior(
            userId = userId,
            actionType = "voice",
            actionData = actionData,
            duration = duration,
            success = success
        )
    }
    
    /**
     * 发送错误行为
     */
    fun sendErrorBehavior(
        userId: String,
        errorType: String,
        errorMessage: String,
        screen: String
    ) {
        val actionData = mapOf(
            "error_type" to errorType,
            "error_message" to errorMessage,
            "screen" to screen
        )
        
        sendUserBehavior(
            userId = userId,
            actionType = "error",
            actionData = actionData,
            success = false
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}
