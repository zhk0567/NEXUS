package com.llasm.voiceassistant.identity

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import java.security.MessageDigest
import java.util.*

/**
 * 设备指纹生成器
 * 用于生成唯一且稳定的设备标识符
 */
class DeviceFingerprint(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceFingerprint"
    }
    
    /**
     * 生成设备指纹ID
     * 基于设备硬件信息和系统信息生成唯一标识
     */
    fun generateDeviceId(): String {
        try {
            val deviceInfo = StringBuilder()
            
            // 1. Android ID (最稳定的标识符)
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            deviceInfo.append("android_id:$androidId;")
            
            // 2. 设备硬件信息
            deviceInfo.append("model:${Build.MODEL};")
            deviceInfo.append("manufacturer:${Build.MANUFACTURER};")
            deviceInfo.append("brand:${Build.BRAND};")
            deviceInfo.append("device:${Build.DEVICE};")
            deviceInfo.append("product:${Build.PRODUCT};")
            
            // 3. 系统版本信息
            deviceInfo.append("sdk:${Build.VERSION.SDK_INT};")
            deviceInfo.append("release:${Build.VERSION.RELEASE};")
            deviceInfo.append("codename:${Build.VERSION.CODENAME};")
            
            // 4. 硬件特征
            deviceInfo.append("board:${Build.BOARD};")
            deviceInfo.append("bootloader:${Build.BOOTLOADER};")
            deviceInfo.append("cpu_abi:${Build.CPU_ABI};")
            deviceInfo.append("cpu_abi2:${Build.CPU_ABI2};")
            deviceInfo.append("hardware:${Build.HARDWARE};")
            deviceInfo.append("host:${Build.HOST};")
            deviceInfo.append("id:${Build.ID};")
            deviceInfo.append("tags:${Build.TAGS};")
            deviceInfo.append("type:${Build.TYPE};")
            deviceInfo.append("user:${Build.USER};")
            
            // 5. 屏幕信息
            val displayMetrics = context.resources.displayMetrics
            deviceInfo.append("screen:${displayMetrics.widthPixels}x${displayMetrics.heightPixels};")
            deviceInfo.append("density:${displayMetrics.density};")
            
            // 6. 应用包名和版本
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            deviceInfo.append("package:${context.packageName};")
            deviceInfo.append("version:${packageInfo.versionName};")
            deviceInfo.append("version_code:${packageInfo.versionCode};")
            
            // 7. 生成MD5哈希值作为设备ID
            val deviceId = generateMD5Hash(deviceInfo.toString())
            
            Log.d(TAG, "Generated device ID: $deviceId")
            Log.d(TAG, "Device info: ${deviceInfo.toString()}")
            
            return deviceId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device ID", e)
            // 如果生成失败，使用时间戳作为备用ID
            return "fallback_${System.currentTimeMillis()}"
        }
    }
    
    /**
     * 生成设备基本信息摘要
     * 用于调试和日志记录
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "android_id" to (Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"),
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "sdk_version" to Build.VERSION.SDK_INT.toString(),
            "android_version" to Build.VERSION.RELEASE,
            "device" to Build.DEVICE,
            "hardware" to Build.HARDWARE
        )
    }
    
    /**
     * 生成MD5哈希值
     */
    private fun generateMD5Hash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating MD5 hash", e)
            // 备用方案：使用简单的哈希码
            input.hashCode().toString()
        }
    }
    
    /**
     * 验证设备ID格式
     */
    fun isValidDeviceId(deviceId: String): Boolean {
        return deviceId.isNotEmpty() && 
               deviceId.length >= 16 && 
               deviceId.matches(Regex("[a-f0-9]+"))
    }
    
    /**
     * 获取设备类型
     */
    fun getDeviceType(): String {
        return when {
            Build.MODEL.contains("Tablet", ignoreCase = true) -> "tablet"
            Build.MODEL.contains("Phone", ignoreCase = true) -> "phone"
            else -> "unknown"
        }
    }
    
    /**
     * 获取设备制造商
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }
}
