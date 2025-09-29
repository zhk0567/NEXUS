package com.llasm.nexusunified.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import com.llasm.nexusunified.config.ServerConfig

/**
 * 监控客户端 - 向前端监控后端发送状态信息
 */
class MonitorClient(private val context: Context) {
    
    companion object {
        private const val TAG = "MonitorClient"
        // 支持多种网络环境的URL配置
        private val MONITOR_URLS = listOf(
            ServerConfig.CURRENT_SERVER
        )
        private var currentUrlIndex = 0
        private var MONITOR_BASE_URL = MONITOR_URLS[0]
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 尝试下一个URL
     */
    private fun tryNextUrl() {
        if (currentUrlIndex < MONITOR_URLS.size - 1) {
            currentUrlIndex++
            MONITOR_BASE_URL = MONITOR_URLS[currentUrlIndex]
            Log.d(TAG, "切换到下一个URL: $MONITOR_BASE_URL")
        } else {
            Log.e(TAG, "所有URL都尝试失败，停止重试")
        }
    }
    
    /**
     * 发送应用状态
     */
    fun sendAppStatus(
        appVersion: String,
        isActive: Boolean,
        currentScreen: String,
        lastActivity: String,
        memoryUsage: Double,
        cpuUsage: Double,
        networkStatus: String,
        apiCallsCount: Int,
        errorCount: Int
    ) {
        Log.d(TAG, "准备发送应用状态: $lastActivity")
        scope.launch {
            try {
                val statusData = JSONObject().apply {
                    put("app_version", appVersion)
                    put("is_active", isActive)
                    put("current_screen", currentScreen)
                    put("last_activity", lastActivity)
                    put("memory_usage", memoryUsage)
                    put("cpu_usage", cpuUsage)
                    put("network_status", networkStatus)
                    put("api_calls_count", apiCallsCount)
                    put("error_count", errorCount)
                    put("timestamp", System.currentTimeMillis())
                }
                
                val requestBody = statusData.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val url = "$MONITOR_BASE_URL/log/app_status"
                Log.d(TAG, "发送请求到: $url")
                Log.d(TAG, "请求数据: $statusData")
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "发送应用状态失败: ${e.message}")
                        Log.e(TAG, "请求URL: $url")
                        
                        // 尝试下一个URL
                        tryNextUrl()
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "应用状态发送成功: $lastActivity")
                        } else {
                            Log.w(TAG, "应用状态发送失败: ${response.code}")
                            Log.w(TAG, "响应内容: ${response.body?.string()}")
                            
                            // 尝试下一个URL
                            tryNextUrl()
                        }
                        response.close()
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "发送应用状态异常", e)
            }
        }
    }
    
    /**
     * 发送用户行为
     */
    fun sendUserBehavior(
        actionType: String,
        actionData: Map<String, Any>,
        success: Boolean
    ) {
        scope.launch {
            try {
                val behaviorData = JSONObject().apply {
                    put("action_type", actionType)
                    put("action_data", JSONObject(actionData))
                    put("success", success)
                    put("timestamp", System.currentTimeMillis())
                }
                
                val requestBody = behaviorData.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$MONITOR_BASE_URL/log/user_behavior")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.w(TAG, "发送用户行为失败: ${e.message}")
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "用户行为发送成功: $actionType")
                        } else {
                            Log.w(TAG, "用户行为发送失败: ${response.code}")
                        }
                        response.close()
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "发送用户行为异常", e)
            }
        }
    }
    
    /**
     * 发送API指标
     */
    fun sendApiMetrics(
        apiName: String,
        responseTime: Long,
        success: Boolean,
        errorMessage: String? = null
    ) {
        scope.launch {
            try {
                val metricsData = JSONObject().apply {
                    put("api_name", apiName)
                    put("response_time", responseTime)
                    put("success", success)
                    put("error_message", errorMessage ?: "")
                    put("timestamp", System.currentTimeMillis())
                }
                
                val requestBody = metricsData.toString()
                    .toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$MONITOR_BASE_URL/log/api_metrics")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.w(TAG, "发送API指标失败: ${e.message}")
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            Log.d(TAG, "API指标发送成功: $apiName")
                        } else {
                            Log.w(TAG, "API指标发送失败: ${response.code}")
                        }
                        response.close()
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "发送API指标异常", e)
            }
        }
    }
}
