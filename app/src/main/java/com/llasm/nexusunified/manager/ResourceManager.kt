package com.llasm.nexusunified.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 资源管理器 - 负责自动上传和下载资源
 */
class ResourceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceManager"
        private const val BASE_URL = "http://10.0.2.2:5000/"
        private const val UPLOAD_ENDPOINT = "upload"
        private const val DOWNLOAD_ENDPOINT = "download"
        private const val SYNC_ENDPOINT = "sync"
    }
    
    private val client = try {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "创建OkHttpClient失败", e)
        OkHttpClient() // 使用默认配置
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 自动上传资源
     */
    suspend fun autoUploadResources(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始自动上传资源...")
            
            // 获取需要上传的资源列表
            val resourcesToUpload = getResourcesToUpload()
            
            if (resourcesToUpload.isEmpty()) {
                Log.d(TAG, "没有需要上传的资源")
                return@withContext Result.success(Unit)
            }
            
            // 并行上传所有资源
            val uploadJobs = resourcesToUpload.map { resource ->
                async { uploadSingleResource(resource) }
            }
            
            val results = uploadJobs.awaitAll()
            val failedUploads = results.filter { it.isFailure }
            
            if (failedUploads.isNotEmpty()) {
                Log.w(TAG, "部分资源上传失败: ${failedUploads.size}/${results.size}")
                return@withContext Result.failure(Exception("部分资源上传失败"))
            }
            
            Log.d(TAG, "所有资源上传成功")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "自动上传资源失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 自动下载资源
     */
    suspend fun autoDownloadResources(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始自动下载资源...")
            
            // 获取服务器资源列表
            val serverResources = getServerResourceList()
            
            if (serverResources.isEmpty()) {
                Log.d(TAG, "服务器没有可下载的资源")
                return@withContext Result.success(Unit)
            }
            
            // 过滤出需要下载的资源
            val resourcesToDownload = filterResourcesToDownload(serverResources)
            
            if (resourcesToDownload.isEmpty()) {
                Log.d(TAG, "没有需要下载的资源")
                return@withContext Result.success(Unit)
            }
            
            // 并行下载所有资源
            val downloadJobs = resourcesToDownload.map { resource ->
                async { downloadSingleResource(resource) }
            }
            
            val results = downloadJobs.awaitAll()
            val failedDownloads = results.filter { it.isFailure }
            
            if (failedDownloads.isNotEmpty()) {
                Log.w(TAG, "部分资源下载失败: ${failedDownloads.size}/${results.size}")
                return@withContext Result.failure(Exception("部分资源下载失败"))
            }
            
            Log.d(TAG, "所有资源下载成功")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "自动下载资源失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 自动同步资源（上传+下载）
     */
    suspend fun autoSyncResources(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始自动同步资源...")
            
            // 先上传本地资源
            val uploadResult = autoUploadResources()
            if (uploadResult.isFailure) {
                Log.w(TAG, "资源上传失败，继续下载...")
            }
            
            // 再下载服务器资源
            val downloadResult = autoDownloadResources()
            if (downloadResult.isFailure) {
                Log.w(TAG, "资源下载失败")
                return@withContext downloadResult
            }
            
            Log.d(TAG, "资源同步完成")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "自动同步资源失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取需要上传的资源列表
     */
    private fun getResourcesToUpload(): List<ResourceInfo> {
        val resources = mutableListOf<ResourceInfo>()
        
        // 检查聊天记录
        val chatDir = File(context.filesDir, "chat_history")
        if (chatDir.exists()) {
            chatDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "json") {
                    resources.add(ResourceInfo(
                        id = file.nameWithoutExtension,
                        type = ResourceType.CHAT_HISTORY,
                        localPath = file.absolutePath,
                        size = file.length(),
                        lastModified = file.lastModified()
                    ))
                }
            }
        }
        
        // 检查音频文件
        val audioDir = File(context.filesDir, "audio")
        if (audioDir.exists()) {
            audioDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension in listOf("wav", "mp3", "m4a")) {
                    resources.add(ResourceInfo(
                        id = file.nameWithoutExtension,
                        type = ResourceType.AUDIO,
                        localPath = file.absolutePath,
                        size = file.length(),
                        lastModified = file.lastModified()
                    ))
                }
            }
        }
        
        // 检查用户设置
        val settingsFile = File(context.filesDir, "user_settings.json")
        if (settingsFile.exists()) {
            resources.add(ResourceInfo(
                id = "user_settings",
                type = ResourceType.SETTINGS,
                localPath = settingsFile.absolutePath,
                size = settingsFile.length(),
                lastModified = settingsFile.lastModified()
            ))
        }
        
        return resources
    }
    
    /**
     * 获取服务器资源列表
     */
    private suspend fun getServerResourceList(): List<ResourceInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL$SYNC_ENDPOINT/list")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("获取服务器资源列表失败: ${response.code}")
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext emptyList()
            }
            
            // 解析JSON响应（这里简化处理）
            // 实际项目中应该使用Gson或其他JSON库
            parseResourceList(responseBody)
            
        } catch (e: Exception) {
            Log.e(TAG, "获取服务器资源列表失败", e)
            emptyList()
        }
    }
    
    /**
     * 过滤需要下载的资源
     */
    private fun filterResourcesToDownload(serverResources: List<ResourceInfo>): List<ResourceInfo> {
        return serverResources.filter { serverResource ->
            val localFile = File(serverResource.localPath)
            
            // 如果本地文件不存在，需要下载
            if (!localFile.exists()) {
                true
            } else {
                // 如果服务器文件更新，需要下载
                serverResource.lastModified > localFile.lastModified()
            }
        }
    }
    
    /**
     * 上传单个资源
     */
    private suspend fun uploadSingleResource(resource: ResourceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(resource.localPath)
            if (!file.exists()) {
                return@withContext Result.failure(IOException("文件不存在: ${resource.localPath}"))
            }
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.readBytes().toRequestBody("application/octet-stream".toMediaType()))
                .addFormDataPart("type", resource.type.name)
                .addFormDataPart("id", resource.id)
                .build()
            
            val request = Request.Builder()
                .url("$BASE_URL$UPLOAD_ENDPOINT")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("上传失败: ${response.code}")
            }
            
            Log.d(TAG, "资源上传成功: ${resource.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "上传资源失败: ${resource.id}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 下载单个资源
     */
    private suspend fun downloadSingleResource(resource: ResourceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL$DOWNLOAD_ENDPOINT/${resource.id}")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw IOException("下载失败: ${response.code}")
            }
            
            val responseBody = response.body ?: throw IOException("响应体为空")
            
            // 确保目录存在
            val localFile = File(resource.localPath)
            localFile.parentFile?.mkdirs()
            
            // 写入文件
            FileOutputStream(localFile).use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "资源下载成功: ${resource.id}")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "下载资源失败: ${resource.id}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析资源列表JSON（简化实现）
     */
    private fun parseResourceList(json: String): List<ResourceInfo> {
        // 这里应该使用Gson解析JSON
        // 为了简化，返回空列表
        return emptyList()
    }
    
    /**
     * 启动后台同步
     */
    fun startBackgroundSync() {
        scope.launch {
            while (isActive) {
                try {
                    autoSyncResources()
                    delay(30 * 60 * 1000) // 30分钟同步一次
                } catch (e: Exception) {
                    Log.e(TAG, "后台同步失败", e)
                    delay(5 * 60 * 1000) // 失败后5分钟重试
                }
            }
        }
    }
    
    /**
     * 停止后台同步
     */
    fun stopBackgroundSync() {
        scope.cancel()
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * 资源信息数据类
 */
data class ResourceInfo(
    val id: String,
    val type: ResourceType,
    val localPath: String,
    val size: Long,
    val lastModified: Long
)

/**
 * 资源类型枚举
 */
enum class ResourceType {
    CHAT_HISTORY,   // 聊天记录
    AUDIO,          // 音频文件
    SETTINGS,       // 用户设置
    IMAGE,          // 图片文件
    DOCUMENT        // 文档文件
}
