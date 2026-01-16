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
    private const val DEFAULT_BASE_URL = "http://94.74.95.80:5000"
    private const val DEFAULT_WEBSOCKET_URL = "ws://94.74.95.80:5000"
    private const val DEFAULT_API_BASE = "http://94.74.95.80:5000/api"
    
    // 私网IP（作为后备，用于内网环境）
    private const val FALLBACK_BASE_URL = "http://192.168.0.239:5000"
    
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
     * 清除缓存配置
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        cachedBaseUrl = null
        cachedWebSocketUrl = null
        cachedApiBase = null
        Log.d(TAG, "已清除服务器配置缓存")
    }
    
    /**
     * 验证缓存的IP地址是否有效
     * 如果缓存的IP不是默认IP或备用IP，则认为是无效的旧缓存
     */
    private fun isValidCachedUrl(url: String?): Boolean {
        if (url == null) return false
        // 如果缓存的URL是默认URL或备用URL，认为是有效的
        if (url == DEFAULT_BASE_URL || url == FALLBACK_BASE_URL) {
            return true
        }
        // 如果包含已知的有效IP，认为是有效的
        if (url.contains("94.74.95.80") || url.contains("192.168.0.239")) {
            return true
        }
        // 其他情况认为是无效的旧缓存
        return false
    }
    
    /**
     * 初始化配置（应用启动时调用）
     * 1. 先尝试从缓存加载
     * 2. 验证缓存是否有效，如果无效则清除
     * 3. 如果有有效缓存，尝试验证并更新配置
     * 4. 如果没有缓存，尝试从后端获取（使用默认地址）
     */
    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. 先加载缓存配置
            loadConfigFromPrefs(context)
            
            // 2. 验证缓存是否有效
            if (cachedBaseUrl != null && !isValidCachedUrl(cachedBaseUrl)) {
                Log.w(TAG, "检测到无效的缓存配置: $cachedBaseUrl，清除缓存")
                clearCache(context)
            }
            
            // 3. 如果有有效缓存配置，先尝试验证并更新
            if (cachedBaseUrl != null && isValidCachedUrl(cachedBaseUrl)) {
                Log.d(TAG, "使用缓存配置: $cachedBaseUrl")
                // 尝试从缓存的地址获取最新配置（静默更新）
                val success = fetchConfigFromServer(cachedBaseUrl)
                if (!success) {
                    // 如果缓存地址连接失败，清除缓存并尝试默认地址
                    Log.w(TAG, "缓存地址连接失败，清除缓存并尝试默认地址")
                    clearCache(context)
                } else {
                    // 保存更新后的配置
                    if (cachedBaseUrl != null) {
                        saveConfigToPrefs(context)
                    }
                    return@withContext true
                }
            }
            
            // 4. 如果没有缓存或缓存无效，尝试从默认地址（公网IP）获取配置
            Log.d(TAG, "无有效缓存配置，尝试从默认地址获取: $DEFAULT_BASE_URL")
            var success = fetchConfigFromServer(DEFAULT_BASE_URL)
            if (success && cachedBaseUrl != null) {
                saveConfigToPrefs(context)
                return@withContext true
            }
            
            // 5. 如果公网IP失败，尝试私网IP（内网环境）
            Log.d(TAG, "公网IP连接失败，尝试私网IP: $FALLBACK_BASE_URL")
            success = fetchConfigFromServer(FALLBACK_BASE_URL)
            if (success && cachedBaseUrl != null) {
                saveConfigToPrefs(context)
                return@withContext true
            }
            
            // 6. 如果都失败，使用默认配置（公网IP）
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
