package com.llasm.nexusunified.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

/**
 * 服务器配置 - 从后端动态获取，不包含任何硬编码的敏感信息
 */
object ServerConfig {
    private const val TAG = "ServerConfig"
    private const val PREFS_NAME = "server_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_WEBSOCKET_URL = "websocket_url"
    private const val KEY_API_BASE = "api_base"
    
    // 默认配置（首次连接时使用，公网IP确保外网用户可访问）
    // 注意：这个地址用于首次获取配置，获取成功后会被后端返回的实际地址替换
    private const val DEFAULT_BASE_URL = "http://115.191.10.104:5000"
    private const val DEFAULT_WEBSOCKET_URL = "ws://115.191.10.104:5000"
    private const val DEFAULT_API_BASE = "http://115.191.10.104:5000/api"
    
    // 私网IP（作为后备，用于内网环境）
    private const val FALLBACK_BASE_URL = "http://172.31.0.3:5000"
    
    // 缓存的配置
    private var cachedBaseUrl: String? = null
    private var cachedWebSocketUrl: String? = null
    private var cachedApiBase: String? = null
    
    // Android用户配置
    const val ANDROID_USER_ID = "android_user_default"
    const val ANDROID_SESSION_ID = "android_session_default"
    
    // API端点（相对路径）
    object Endpoints {
        const val HEALTH = "api/health"
        const val CHAT = "api/chat"
        const val CHAT_STREAMING = "api/chat_streaming"
        const val TRANSCRIBE = "api/transcribe"
        const val TTS = "api/tts"
        const val VOICE_CHAT = "api/voice_chat"
        const val VOICE_CHAT_STREAMING = "api/voice_chat_streaming"
        const val AUTH_LOGIN = "api/auth/login"
        const val AUTH_LOGOUT = "api/auth/logout"
        const val AUTH_REGISTER = "api/auth/register"
        const val INTERACTIONS_LOG = "api/interactions/log"
        const val INTERACTIONS_HISTORY = "api/interactions/history"
        const val STATS_INTERACTIONS = "api/stats/interactions"
        const val STATS_ACTIVE_USERS = "api/stats/active_users"
        const val ADMIN_CLEANUP = "api/admin/cleanup"
        const val CONFIG = "api/config"
    }
    
    /**
     * 从后端获取配置
     */
    suspend fun fetchConfigFromServer(baseUrl: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            // 确定要使用的baseUrl：优先使用传入的，其次使用缓存的，最后使用默认的
            val targetUrl = baseUrl ?: cachedBaseUrl ?: DEFAULT_BASE_URL
            
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val url = if (targetUrl.endsWith("/")) {
                    "${targetUrl}api/config"
                } else {
                    "$targetUrl/api/config"
                }
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        if (json.optBoolean("success", false)) {
                            val server = json.getJSONObject("server")
                            cachedBaseUrl = server.getString("base_url")
                            cachedWebSocketUrl = server.getString("websocket_url")
                            cachedApiBase = server.getString("api_base")
                            
                            Log.d(TAG, "配置获取成功: $cachedBaseUrl")
                            return@withContext true
                        }
                    }
                }
                Log.w(TAG, "配置获取失败: ${response.code}")
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "获取配置异常: ${e.message}")
                return@withContext false
            }
        }
    }
    
    /**
     * 初始化配置（应用启动时调用）
     * 1. 先尝试从缓存加载
     * 2. 如果有缓存，直接使用，延迟验证更新（避免启动时网络连接）
     * 3. 如果没有缓存，使用默认配置，延迟获取（避免启动时网络连接）
     */
    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. 先加载缓存配置
            loadConfigFromPrefs(context)
            
            // 2. 如果有缓存配置，直接使用，延迟验证更新
            if (cachedBaseUrl != null && cachedBaseUrl != DEFAULT_BASE_URL) {
                // 延迟验证更新配置（避免启动时立即连接网络）
                CoroutineScope(Dispatchers.IO).launch {
                    delay(3000) // 延迟3秒后验证更新
                    try {
                fetchConfigFromServer(cachedBaseUrl)
                if (cachedBaseUrl != null) {
                    saveConfigToPrefs(context)
                        }
                    } catch (e: Exception) {
                        // 静默失败，不影响应用启动
                    }
                }
                return@withContext true
            }
            
            // 3. 如果没有缓存，使用默认配置，延迟获取
            cachedBaseUrl = DEFAULT_BASE_URL
            cachedWebSocketUrl = DEFAULT_WEBSOCKET_URL
            cachedApiBase = DEFAULT_API_BASE
            
            // 延迟获取配置（避免启动时立即连接网络）
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000) // 延迟5秒后获取配置
                try {
                    var success = fetchConfigFromServer(DEFAULT_BASE_URL)
                    if (!success) {
                        success = fetchConfigFromServer(FALLBACK_BASE_URL)
                    }
                    if (success && cachedBaseUrl != null) {
                        saveConfigToPrefs(context)
                    }
                } catch (e: Exception) {
                    // 静默失败，不影响应用启动
                }
            }
            
            return@withContext true
        }
    }
    
    /**
     * 从SharedPreferences加载配置
     */
    fun loadConfigFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedBaseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL)
        cachedWebSocketUrl = prefs.getString(KEY_WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL)
        cachedApiBase = prefs.getString(KEY_API_BASE, DEFAULT_API_BASE)
    }
    
    /**
     * 保存配置到SharedPreferences
     */
    private fun saveConfigToPrefs() {
        // 需要在有Context的情况下调用，这里先缓存，由调用者传入Context保存
    }
    
    /**
     * 保存配置到SharedPreferences（需要Context）
     */
    fun saveConfigToPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            cachedBaseUrl?.let { putString(KEY_BASE_URL, it) }
            cachedWebSocketUrl?.let { putString(KEY_WEBSOCKET_URL, it) }
            cachedApiBase?.let { putString(KEY_API_BASE, it) }
            apply()
        }
    }
    
    /**
     * 获取当前服务器地址
     */
    val CURRENT_SERVER: String
        get() = cachedBaseUrl ?: DEFAULT_BASE_URL
    
    /**
     * 获取当前WebSocket地址
     */
    val CURRENT_WEBSOCKET: String
        get() = cachedWebSocketUrl ?: DEFAULT_WEBSOCKET_URL
    
    /**
     * 获取API基础地址
     */
    val CURRENT_API_BASE: String
        get() = cachedApiBase ?: DEFAULT_API_BASE
    
    /**
     * 获取完整的API URL
     */
    fun getApiUrl(endpoint: String): String {
        val base = CURRENT_API_BASE
        var path = endpoint.removePrefix("/")
        
        // 如果base已经包含/api，且endpoint也包含api/，则移除endpoint中的api/前缀
        if (base.contains("/api") && path.startsWith("api/")) {
            path = path.removePrefix("api/")
        }
        
        return if (base.endsWith("/")) {
            "$base$path"
        } else {
            "$base/$path"
        }
    }
    
    /**
     * 获取WebSocket URL
     */
    fun getWebSocketUrl(endpoint: String): String {
        val base = CURRENT_WEBSOCKET
        val path = endpoint.removePrefix("/")
        return if (base.endsWith("/")) {
            "$base$path"
        } else {
            "$base/$path"
        }
    }
}
