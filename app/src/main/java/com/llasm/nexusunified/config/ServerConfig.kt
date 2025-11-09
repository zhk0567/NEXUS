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
    
    // 默认配置（仅在无法连接后端时使用）
    private const val DEFAULT_BASE_URL = "http://172.31.0.2:5000"
    private const val DEFAULT_WEBSOCKET_URL = "ws://172.31.0.2:5000"
    private const val DEFAULT_API_BASE = "http://172.31.0.2:5000/api"
    
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
    suspend fun fetchConfigFromServer(baseUrl: String = DEFAULT_BASE_URL): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val url = if (baseUrl.endsWith("/")) {
                    "${baseUrl}api/config"
                } else {
                    "$baseUrl/api/config"
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
                            
                            // 保存到SharedPreferences
                            saveConfigToPrefs()
                            
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
