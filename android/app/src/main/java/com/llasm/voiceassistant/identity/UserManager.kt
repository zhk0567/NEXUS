package com.llasm.voiceassistant.identity

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * 用户管理器
 * 负责用户身份管理、设备ID生成和用户状态维护
 */
class UserManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UserManager"
        private const val PREFS_NAME = "nexus_user_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_PHONE = "phone"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_REGISTERED = "is_registered"
        private const val KEY_LAST_SESSION_ID = "last_session_id"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceFingerprint = DeviceFingerprint(context)
    
    // 用户状态流
    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()
    
    // 当前会话ID
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    
    /**
     * 用户信息数据类
     */
    data class UserInfo(
        val userId: String,
        val deviceId: String,
        val userType: UserType,
        val nickname: String? = null,
        val phone: String? = null,
        val email: String? = null,
        val isRegistered: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 用户类型枚举
     */
    enum class UserType {
        DEVICE,     // 设备用户（未注册）
        REGISTERED  // 注册用户
    }
    
    /**
     * 初始化用户管理器 - 混合方案实现
     * 在应用启动时调用
     */
    fun initialize() {
        try {
            Log.d(TAG, "Initializing UserManager with hybrid approach...")
            
            // 1. 首次启动 → 生成设备指纹ID
            val deviceId = deviceFingerprint.generateDeviceId()
            Log.d(TAG, "Generated device ID: $deviceId")
            
            // 2. 检查是否已有用户信息
            val savedUserId = prefs.getString(KEY_USER_ID, null)
            val savedDeviceId = prefs.getString(KEY_DEVICE_ID, null)
            val isRegistered = prefs.getBoolean(KEY_IS_REGISTERED, false)
            
            if (savedUserId != null && savedDeviceId != null) {
                // 加载已保存的用户信息
                val userType = UserType.valueOf(prefs.getString(KEY_USER_TYPE, UserType.DEVICE.name) ?: UserType.DEVICE.name)
                val nickname = prefs.getString(KEY_NICKNAME, null)
                val phone = prefs.getString(KEY_PHONE, null)
                val email = prefs.getString(KEY_EMAIL, null)
                
                val userInfo = UserInfo(
                    userId = savedUserId,
                    deviceId = savedDeviceId,
                    userType = userType,
                    nickname = nickname,
                    phone = phone,
                    email = email,
                    isRegistered = isRegistered
                )
                
                _currentUser.value = userInfo
                Log.d(TAG, "Loaded existing user: $savedUserId (Type: $userType)")
                
            } else {
                // 3. 未注册用户 → 使用设备ID统计
                createNewDeviceUser(deviceId)
            }
            
            // 生成新的会话ID
            generateNewSessionId()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UserManager", e)
            // 创建备用用户
            createFallbackUser()
        }
    }
    
    /**
     * 创建新的设备用户 - 使用设备ID统计
     */
    private fun createNewDeviceUser(deviceId: String) {
        try {
            val userId = "device_$deviceId"
            
            val userInfo = UserInfo(
                userId = userId,
                deviceId = deviceId,
                userType = UserType.DEVICE,
                isRegistered = false
            )
            
            saveUserInfo(userInfo)
            _currentUser.value = userInfo
            
            Log.d(TAG, "Created new device user: $userId (using device ID for statistics)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating new device user", e)
            createFallbackUser()
        }
    }
    
    /**
     * 创建备用用户（当所有方法都失败时）
     */
    private fun createFallbackUser() {
        val fallbackId = "fallback_${System.currentTimeMillis()}"
        val userInfo = UserInfo(
            userId = fallbackId,
            deviceId = fallbackId,
            userType = UserType.DEVICE,
            isRegistered = false
        )
        
        saveUserInfo(userInfo)
        _currentUser.value = userInfo
        
        Log.w(TAG, "Created fallback user: $fallbackId")
    }
    
    /**
     * 注册用户 - 绑定设备ID到用户账号
     * 将设备用户升级为注册用户，保持设备ID关联
     */
    fun registerUser(nickname: String, phone: String? = null, email: String? = null): Boolean {
        return try {
            val currentUser = _currentUser.value ?: return false
            
            // 4. 已注册用户 → 使用用户ID统计，但保持设备ID关联
            val registeredUserId = "user_${phone?.hashCode() ?: email?.hashCode() ?: System.currentTimeMillis()}"
            
            val registeredUser = currentUser.copy(
                userId = registeredUserId,  // 使用新的用户ID进行统计
                deviceId = currentUser.deviceId,  // 保持设备ID关联
                userType = UserType.REGISTERED,
                nickname = nickname,
                phone = phone,
                email = email,
                isRegistered = true
            )
            
            saveUserInfo(registeredUser)
            _currentUser.value = registeredUser
            
            Log.d(TAG, "User registered successfully: $registeredUserId (bound to device: ${currentUser.deviceId})")
            Log.d(TAG, "Statistics will now use user ID: $registeredUserId")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user", e)
            false
        }
    }
    
    /**
     * 更新用户信息
     */
    fun updateUserInfo(nickname: String? = null, phone: String? = null, email: String? = null): Boolean {
        return try {
            val currentUser = _currentUser.value ?: return false
            
            val updatedUser = currentUser.copy(
                nickname = nickname ?: currentUser.nickname,
                phone = phone ?: currentUser.phone,
                email = email ?: currentUser.email
            )
            
            saveUserInfo(updatedUser)
            _currentUser.value = updatedUser
            
            Log.d(TAG, "User info updated successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user info", e)
            false
        }
    }
    
    /**
     * 生成新的会话ID
     */
    fun generateNewSessionId(): String {
        val sessionId = "session_${UUID.randomUUID().toString().replace("-", "")}"
        _currentSessionId.value = sessionId
        
        // 保存到本地存储
        prefs.edit()
            .putString(KEY_LAST_SESSION_ID, sessionId)
            .apply()
        
        Log.d(TAG, "Generated new session ID: $sessionId")
        return sessionId
    }
    
    /**
     * 获取当前用户ID
     */
    fun getCurrentUserId(): String? {
        return _currentUser.value?.userId
    }
    
    /**
     * 获取用于统计的用户ID
     * 混合方案：已注册用户使用用户ID，未注册用户使用设备ID
     */
    fun getStatisticsUserId(): String? {
        val currentUser = _currentUser.value ?: return null
        
        return when (currentUser.userType) {
            UserType.REGISTERED -> {
                // 已注册用户 → 使用用户ID统计
                currentUser.userId
            }
            UserType.DEVICE -> {
                // 未注册用户 → 使用设备ID统计
                currentUser.deviceId
            }
        }
    }
    
    /**
     * 获取当前设备ID
     */
    fun getCurrentDeviceId(): String? {
        return _currentUser.value?.deviceId
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return _currentSessionId.value
    }
    
    /**
     * 检查用户是否已注册
     */
    fun isUserRegistered(): Boolean {
        return _currentUser.value?.isRegistered ?: false
    }
    
    /**
     * 获取用户类型
     */
    fun getUserType(): UserType {
        return _currentUser.value?.userType ?: UserType.DEVICE
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): Map<String, String> {
        return deviceFingerprint.getDeviceInfo()
    }
    
    /**
     * 保存用户信息到本地存储
     */
    private fun saveUserInfo(userInfo: UserInfo) {
        prefs.edit()
            .putString(KEY_USER_ID, userInfo.userId)
            .putString(KEY_DEVICE_ID, userInfo.deviceId)
            .putString(KEY_USER_TYPE, userInfo.userType.name)
            .putString(KEY_NICKNAME, userInfo.nickname)
            .putString(KEY_PHONE, userInfo.phone)
            .putString(KEY_EMAIL, userInfo.email)
            .putBoolean(KEY_IS_REGISTERED, userInfo.isRegistered)
            .apply()
    }
    
    /**
     * 清除用户数据
     * 用于登出或重置
     */
    fun clearUserData() {
        prefs.edit().clear().apply()
        _currentUser.value = null
        _currentSessionId.value = null
        
        // 重新初始化
        initialize()
        
        Log.d(TAG, "User data cleared and reinitialized")
    }
    
    /**
     * 获取用户统计信息
     */
    fun getUserStats(): Map<String, Any> {
        val user = _currentUser.value ?: return emptyMap()
        val statisticsUserId = getStatisticsUserId()
        
        return mapOf(
            "user_id" to user.userId,
            "device_id" to user.deviceId,
            "statistics_user_id" to (statisticsUserId ?: "未知"),
            "user_type" to user.userType.name,
            "is_registered" to user.isRegistered,
            "nickname" to (user.nickname ?: "未设置"),
            "phone" to (user.phone ?: "未设置"),
            "email" to (user.email ?: "未设置"),
            "created_at" to user.createdAt,
            "session_id" to (_currentSessionId.value ?: "未生成"),
            "statistics_method" to if (user.isRegistered) "用户ID统计" else "设备ID统计"
        )
    }
}
