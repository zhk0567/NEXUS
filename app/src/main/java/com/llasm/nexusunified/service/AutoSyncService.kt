package com.llasm.nexusunified.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.llasm.nexusunified.MainActivity
import com.llasm.nexusunified.R
import com.llasm.nexusunified.manager.ResourceManager
import kotlinx.coroutines.*

/**
 * 自动同步服务 - 在后台自动上传和下载资源
 */
class AutoSyncService : Service() {
    
    companion object {
        private const val TAG = "AutoSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "auto_sync_channel"
        private const val CHANNEL_NAME = "自动同步服务"
        
        fun startService(context: Context) {
            val intent = Intent(context, AutoSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AutoSyncService::class.java)
            context.stopService(intent)
        }
    }
    
    private val binder = AutoSyncBinder()
    private lateinit var resourceManager: ResourceManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isRunning = false
    private var lastSyncTime = 0L
    private var syncCount = 0
    
    inner class AutoSyncBinder : Binder() {
        fun getService(): AutoSyncService = this@AutoSyncService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutoSyncService 创建")
        
        try {
            resourceManager = ResourceManager(this)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "AutoSyncService 初始化失败", e)
            // 如果初始化失败，停止服务
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AutoSyncService 启动")
        
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            startAutoSync()
            isRunning = true
        }
        
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutoSyncService 销毁")
        
        isRunning = false
        serviceScope.cancel()
        resourceManager.cleanup()
    }
    
    /**
     * 开始自动同步
     */
    private fun startAutoSync() {
        serviceScope.launch {
            try {
                // 立即执行一次同步
                performSync()
                
                // 然后定期同步
                while (isActive && isRunning) {
                    delay(30 * 60 * 1000) // 30分钟同步一次
                    performSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "自动同步异常", e)
            }
        }
    }
    
    /**
     * 执行同步操作
     */
    private suspend fun performSync() {
        try {
            Log.d(TAG, "开始执行同步...")
            updateNotification("正在同步资源...")
            
            val result = resourceManager.autoSyncResources()
            
            if (result.isSuccess) {
                syncCount++
                lastSyncTime = System.currentTimeMillis()
                Log.d(TAG, "同步成功 (第${syncCount}次)")
                updateNotification("同步完成 (第${syncCount}次)")
            } else {
                Log.e(TAG, "同步失败: ${result.exceptionOrNull()?.message}")
                updateNotification("同步失败: ${result.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "同步异常", e)
            updateNotification("同步异常: ${e.message}")
        }
    }
    
    /**
     * 手动触发同步
     */
    fun triggerSync() {
        serviceScope.launch {
            performSync()
        }
    }
    
    /**
     * 获取同步状态
     */
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isRunning = isRunning,
            lastSyncTime = lastSyncTime,
            syncCount = syncCount
        )
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自动同步聊天记录和资源"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("每日对话 自动同步")
            .setContentText("正在后台同步资源...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val notification = createNotification().apply {
            // 更新通知内容
            val builder = NotificationCompat.Builder(this@AutoSyncService, CHANNEL_ID)
                .setContentTitle("每日对话 自动同步")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }
}

/**
 * 同步状态数据类
 */
data class SyncStatus(
    val isRunning: Boolean,
    val lastSyncTime: Long,
    val syncCount: Int
)
