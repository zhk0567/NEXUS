package com.llasm.storycontrol.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private const val DEFAULT_BASE_URL = "http://115.190.227.112:5000"
    private const val DEFAULT_WEBSOCKET_URL = "ws://115.190.227.112:5000"
    private const val DEFAULT_API_BASE = "http://115.190.227.112:5000/api"
    
    // 私网IP（作为后备，用于内网环境）
    private const val FALLBACK_BASE_URL = "http://172.31.0.2:5000"
    
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
                        val json = JSONObject(responseBody as String)
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
     * 2. 如果有缓存，尝试验证并更新配置
     * 3. 如果没有缓存，尝试从后端获取（使用默认地址）
     */
    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. 先加载缓存配置
            loadConfigFromPrefs(context)
            
            // 2. 如果有缓存配置，先尝试验证并更新
            if (cachedBaseUrl != null && cachedBaseUrl != DEFAULT_BASE_URL) {
                Log.d(TAG, "使用缓存配置: $cachedBaseUrl")
                // 尝试从缓存的地址获取最新配置（静默更新）
                fetchConfigFromServer(cachedBaseUrl)
                // 保存更新后的配置
                if (cachedBaseUrl != null) {
                    saveConfigToPrefs(context)
                }
                return@withContext true
            }
            
            // 3. 如果没有缓存，尝试从默认地址（公网IP）获取配置
            Log.d(TAG, "无缓存配置，尝试从默认地址获取: $DEFAULT_BASE_URL")
            var success = fetchConfigFromServer(DEFAULT_BASE_URL)
            if (success && cachedBaseUrl != null) {
                saveConfigToPrefs(context)
                return@withContext true
            }
            
            // 4. 如果公网IP失败，尝试私网IP（内网环境）
            Log.d(TAG, "公网IP连接失败，尝试私网IP: $FALLBACK_BASE_URL")
            success = fetchConfigFromServer(FALLBACK_BASE_URL)
            if (success && cachedBaseUrl != null) {
                saveConfigToPrefs(context)
                return@withContext true
            }
            
            // 5. 如果都失败，使用默认配置（公网IP）
            Log.w(TAG, "无法从后端获取配置，使用默认配置: $DEFAULT_BASE_URL")
            cachedBaseUrl = DEFAULT_BASE_URL
            cachedWebSocketUrl = DEFAULT_WEBSOCKET_URL
            cachedApiBase = DEFAULT_API_BASE
            return@withContext false
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
        val path = endpoint.removePrefix("/")
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
