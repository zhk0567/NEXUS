package com.llasm.storycontrol.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * 用户状态管理器
 */
object UserManager {
    private const val PREFS_NAME = "story_control_user_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_USER_DATA = "user_data"
    
    private var sharedPreferences: SharedPreferences? = null
    
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isLoggedIn(): Boolean {
        val isLoggedIn = sharedPreferences?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
        android.util.Log.d("UserManager", "检查登录状态: $isLoggedIn")
        return isLoggedIn
    }
    
    fun getUserId(): String? {
        return sharedPreferences?.getString(KEY_USER_ID, null)
    }
    
    fun getUsername(): String? {
        return sharedPreferences?.getString(KEY_USERNAME, null)
    }
    
    fun getSessionId(): String? {
        return sharedPreferences?.getString(KEY_SESSION_ID, null)
    }
    
    fun getUserData(): UserData? {
        val userDataJson = sharedPreferences?.getString(KEY_USER_DATA, null) ?: return null
        return try {
            val json = JSONObject(userDataJson)
            UserData(
                userId = json.getString("user_id"),
                username = json.getString("username"),
                sessionId = json.getString("session_id"),
                createdAt = json.optString("created_at", null).takeIf { it.isNotEmpty() },
                lastLoginAt = json.optString("last_login_at", null).takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveLoginData(userData: UserData) {
        android.util.Log.d("UserManager", "保存登录数据: ${userData.username}")
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, userData.userId)
            putString(KEY_USERNAME, userData.username)
            putString(KEY_SESSION_ID, userData.sessionId)
            
            val userDataJson = JSONObject().apply {
                put("user_id", userData.userId)
                put("username", userData.username)
                put("session_id", userData.sessionId)
                put("created_at", userData.createdAt ?: "")
                put("last_login_at", userData.lastLoginAt ?: "")
            }
            putString(KEY_USER_DATA, userDataJson.toString())
            apply()
        }
        android.util.Log.d("UserManager", "登录数据保存完成")
    }
    
    fun logout() {
        sharedPreferences?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_SESSION_ID)
            remove(KEY_USER_DATA)
            apply()
        }
    }
}

/**
 * 用户数据类
 */
data class UserData(
    val userId: String,
    val username: String,
    val sessionId: String,
    val createdAt: String? = null,
    val lastLoginAt: String? = null
)
