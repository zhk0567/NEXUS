package com.llasm.nexusunified.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.llasm.nexusunified.config.ServerConfig
import com.llasm.nexusunified.data.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 错误报告工具类 - 统一收集和上报所有错误
 */
object ErrorReporter {
    private const val TAG = "ErrorReporter"
    private const val ERROR_REPORT_ENDPOINT = "/api/error/report"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * 报告错误
     */
    fun reportError(
        context: Context?,
        errorType: String,
        errorLevel: String = "ERROR",
        errorMessage: String,
        throwable: Throwable? = null,
        errorContext: Map<String, Any>? = null,
        apiEndpoint: String? = null,
        requestData: String? = null,
        responseData: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取错误堆栈
                val errorStack = throwable?.let {
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    it.printStackTrace(pw)
                    sw.toString()
                }
                
                // 获取设备信息
                val deviceInfo = getDeviceInfo(context)
                val screenInfo = getScreenInfo(context)
                
                // 获取用户信息
                val userId = UserManager.getUserId()
                val username = UserManager.getUsername()
                val sessionId = UserManager.getSessionId()
                
                // 构建错误上下文
                val contextMap = mutableMapOf<String, Any>()
                errorContext?.let { contextMap.putAll(it) }
                contextMap["app_state"] = getAppState()
                contextMap["memory_info"] = getMemoryInfo()
                
                // 构建请求体
                val requestBody = JSONObject().apply {
                    put("error_type", errorType)
                    put("error_level", errorLevel)
                    put("error_message", errorMessage)
                    errorStack?.let { put("error_stack", it) }
                    put("error_context", JSONObject(contextMap as Map<*, *>))
                    put("app_version", getAppVersion(context))
                    put("device_info", deviceInfo)
                    put("os_version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    put("screen_info", screenInfo)
                    put("network_type", getNetworkType(context))
                    userId?.let { put("user_id", it) }
                    username?.let { put("username", it) }
                    sessionId?.let { put("session_id", it) }
                    apiEndpoint?.let { put("api_endpoint", it) }
                    requestData?.let { put("request_data", it) }
                    responseData?.let { put("response_data", it) }
                }.toString().toRequestBody("application/json".toMediaType())
                
                // 发送错误报告
                val request = Request.Builder()
                    .url("${ServerConfig.CURRENT_SERVER}$ERROR_REPORT_ENDPOINT")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                } else {
                    Log.w(TAG, "错误报告发送失败: ${response.code}")
                }
                
                response.close()
                
            } catch (e: Exception) {
                // 静默失败，避免错误报告本身导致错误
                Log.e(TAG, "发送错误报告失败", e)
            }
        }
    }
    
    /**
     * 报告API错误
     */
    fun reportApiError(
        context: Context?,
        apiEndpoint: String,
        errorMessage: String,
        requestData: String? = null,
        responseData: String? = null,
        throwable: Throwable? = null
    ) {
        reportError(
            context = context,
            errorType = "api_error",
            errorLevel = "ERROR",
            errorMessage = errorMessage,
            throwable = throwable,
            errorContext = mapOf(
                "api_endpoint" to apiEndpoint,
                "request_method" to "POST"
            ),
            apiEndpoint = apiEndpoint,
            requestData = requestData,
            responseData = responseData
        )
    }
    
    /**
     * 报告网络错误
     */
    fun reportNetworkError(
        context: Context?,
        errorMessage: String,
        throwable: Throwable? = null
    ) {
        reportError(
            context = context,
            errorType = "network_error",
            errorLevel = "ERROR",
            errorMessage = errorMessage,
            throwable = throwable,
            errorContext = mapOf(
                "network_available" to isNetworkAvailable(context)
            )
        )
    }
    
    /**
     * 报告应用崩溃
     */
    fun reportCrash(
        context: Context?,
        throwable: Throwable,
        errorMessage: String = throwable.message ?: "Application crash"
    ) {
        reportError(
            context = context,
            errorType = "app_crash",
            errorLevel = "CRITICAL",
            errorMessage = errorMessage,
            throwable = throwable,
            errorContext = mapOf(
                "crash_time" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * 报告UI错误
     */
    fun reportUIError(
        context: Context?,
        errorMessage: String,
        screenName: String? = null,
        userAction: String? = null
    ) {
        val contextMap = mutableMapOf<String, Any>()
        screenName?.let { contextMap["screen_name"] = it }
        userAction?.let { contextMap["user_action"] = it }
        
        reportError(
            context = context,
            errorType = "ui_error",
            errorLevel = "WARNING",
            errorMessage = errorMessage,
            errorContext = contextMap
        )
    }
    
    // 辅助方法
    private fun getDeviceInfo(context: Context?): String {
        return try {
            "${Build.MANUFACTURER} ${Build.MODEL}"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getScreenInfo(context: Context?): String {
        return try {
            context?.let { ctx ->
                val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.defaultDisplay?.let { display ->
                    val metrics = android.util.DisplayMetrics()
                    display.getMetrics(metrics)
                    "${metrics.widthPixels}x${metrics.heightPixels} (${metrics.densityDpi}dpi)"
                } ?: "Unknown"
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getAppVersion(context: Context?): String {
        return try {
            context?.packageManager?.getPackageInfo(context.packageName, 0)?.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getNetworkType(context: Context?): String {
        return try {
            context?.let { ctx ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.activeNetworkInfo?.typeName ?: "Unknown"
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun isNetworkAvailable(context: Context?): Boolean {
        return try {
            context?.let { ctx ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.activeNetworkInfo?.isConnected == true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getAppState(): Map<String, Any> {
        return mapOf(
            "thread_count" to Thread.activeCount(),
            "uptime" to android.os.SystemClock.uptimeMillis()
        )
    }
    
    private fun getMemoryInfo(): Map<String, Any> {
        return try {
            val runtime = Runtime.getRuntime()
            mapOf(
                "total_memory" to runtime.totalMemory(),
                "free_memory" to runtime.freeMemory(),
                "max_memory" to runtime.maxMemory(),
                "used_memory" to (runtime.totalMemory() - runtime.freeMemory())
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

