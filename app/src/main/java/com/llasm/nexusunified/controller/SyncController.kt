package com.llasm.nexusunified.controller

import android.content.Context
import android.content.Intent
import android.util.Log
import com.llasm.nexusunified.service.AutoSyncService
import kotlinx.coroutines.*

/**
 * 同步控制器 - 管理自动同步的启动、停止和状态
 */
class SyncController(private val context: Context) {
    
    companion object {
        private const val TAG = "SyncController"
    }
    
    private var isServiceRunning = false
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 启动自动同步
     */
    fun startAutoSync() {
        if (isServiceRunning) {
            Log.d(TAG, "自动同步已在运行")
            return
        }
        
        try {
            // 检查网络连接
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val networkInfo = connectivityManager?.activeNetworkInfo
            if (networkInfo?.isConnected != true) {
                Log.w(TAG, "网络未连接，跳过启动同步服务")
                return
            }
            
            AutoSyncService.startService(context)
            isServiceRunning = true
            Log.d(TAG, "自动同步已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动自动同步失败", e)
            // 不抛出异常，避免应用崩溃
        }
    }
    
    /**
     * 停止自动同步
     */
    fun stopAutoSync() {
        if (!isServiceRunning) {
            Log.d(TAG, "自动同步未运行")
            return
        }
        
        try {
            AutoSyncService.stopService(context)
            isServiceRunning = false
            Log.d(TAG, "自动同步已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止自动同步失败", e)
        }
    }
    
    /**
     * 手动触发同步
     */
    fun triggerSync() {
        controllerScope.launch {
            try {
                Log.d(TAG, "手动触发同步")
                // 这里可以添加手动同步的逻辑
                // 或者通过广播通知服务执行同步
                val intent = Intent("com.llasm.nexusunified.TRIGGER_SYNC")
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "手动同步失败", e)
            }
        }
    }
    
    /**
     * 检查同步状态
     */
    fun isSyncRunning(): Boolean = isServiceRunning
    
    /**
     * 设置同步间隔
     */
    fun setSyncInterval(intervalMinutes: Int) {
        // 这里可以添加设置同步间隔的逻辑
        Log.d(TAG, "设置同步间隔: ${intervalMinutes}分钟")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        controllerScope.cancel()
    }
}
