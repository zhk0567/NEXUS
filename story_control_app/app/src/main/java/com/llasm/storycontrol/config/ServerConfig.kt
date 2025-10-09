package com.llasm.storycontrol.config

/**
 * 服务器配置
 */
object ServerConfig {
    
    // 本地服务器配置
    const val LOCAL_SERVER = "http://192.168.50.205:5000/"
    const val LOCAL_WEBSOCKET = "ws://192.168.50.205:5000"
    
    // fgnwct 公网隧道配置
    const val FGNWCT_SERVER = "http://nexus.free.svipss.top/"
    const val FGNWCT_WEBSOCKET = "ws://nexus.free.svipss.top"
    
    // 当前使用的配置 - 使用本地地址
    const val CURRENT_SERVER = LOCAL_SERVER
    const val CURRENT_WEBSOCKET = LOCAL_WEBSOCKET
    
    // Android用户配置
    const val ANDROID_USER_ID = "android_user_default"
    const val ANDROID_SESSION_ID = "android_session_default"
    
    // API端点
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
    }
    
    // 获取完整的API URL
    fun getApiUrl(endpoint: String): String {
        return CURRENT_SERVER + endpoint.removePrefix("/")
    }
    
    // 获取WebSocket URL
    fun getWebSocketUrl(endpoint: String): String {
        return CURRENT_WEBSOCKET + "/" + endpoint.removePrefix("/")
    }
}
