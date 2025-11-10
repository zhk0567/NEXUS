package com.llasm.storycontrol.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import com.llasm.storycontrol.config.ServerConfig

/**
 * 故事控制API服务
 * 负责与后端服务器通信，处理阅读进度、会话管理等
 */
object StoryApiService {
    private const val TAG = "StoryApiService"
    
    // 服务器配置
    private const val TIMEOUT = 30000 // 30秒超时
    
    /**
     * 开始阅读会话
     */
    suspend fun startReadingSession(
        userId: String,
        storyId: String,
        storyTitle: String,
        sessionId: String? = null,
        deviceInfo: String = ""
    ): ApiResult<ReadingSessionResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/reading/session/start"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
            }
            
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("story_id", storyId)
                put("story_title", storyTitle)
                sessionId?.let { put("session_id", it) }
                if (deviceInfo.isNotEmpty()) put("device_info", deviceInfo)
            }
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "开始阅读会话响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val sessionResponse = ReadingSessionResponse(
                    success = jsonResponse.getBoolean("success"),
                    sessionId = jsonResponse.getString("session_id"),
                    message = jsonResponse.getString("message")
                )
                ApiResult.Success(sessionResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "开始阅读会话失败", e)
            ApiResult.Error(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 结束阅读会话
     */
    suspend fun endReadingSession(
        sessionId: String,
        charactersRead: Int = 0
    ): ApiResult<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/reading/session/end"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
            }
            
            val requestBody = JSONObject().apply {
                put("session_id", sessionId)
                put("characters_read", charactersRead)
            }
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "结束阅读会话响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val apiResponse = ApiResponse(
                    success = jsonResponse.getBoolean("success"),
                    message = jsonResponse.getString("message")
                )
                ApiResult.Success(apiResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "结束阅读会话失败", e)
            ApiResult.Error(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 更新阅读进度
     */
    suspend fun updateReadingProgress(
        userId: String,
        storyId: String,
        storyTitle: String,
        currentPosition: Int,
        totalLength: Int,
        sessionId: String? = null,
        deviceInfo: String = ""
    ): ApiResult<ReadingProgressResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/reading/progress"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
            }
            
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("story_id", storyId)
                put("story_title", storyTitle)
                put("current_position", currentPosition)
                put("total_length", totalLength)
                sessionId?.let { put("session_id", it) }
                if (deviceInfo.isNotEmpty()) put("device_info", deviceInfo)
            }
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "更新阅读进度响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val progressResponse = ReadingProgressResponse(
                    success = jsonResponse.getBoolean("success"),
                    progressPercentage = jsonResponse.getDouble("progress_percentage"),
                    isCompleted = jsonResponse.getBoolean("is_completed"),
                    message = jsonResponse.getString("message")
                )
                ApiResult.Success(progressResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "更新阅读进度失败", e)
            ApiResult.Error(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 获取阅读进度
     */
    suspend fun getReadingProgress(
        userId: String,
        storyId: String? = null
    ): ApiResult<ReadingProgressListResponse> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder(ServerConfig.getApiUrl("api/story/reading/progress") + "?user_id=$userId")
            storyId?.let { urlBuilder.append("&story_id=$it") }
            
            val url = URL(urlBuilder.toString())
            Log.d(TAG, "获取阅读进度请求URL: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Android App")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            Log.d(TAG, "开始连接服务器...")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "服务器响应码: $responseCode")
            
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "获取阅读进度响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val progressList = mutableListOf<NetworkReadingProgress>()
                
                val progressArray = jsonResponse.getJSONArray("progress")
                for (i in 0 until progressArray.length()) {
                    val progressJson = progressArray.getJSONObject(i)
                    val progress = NetworkReadingProgress(
                        storyId = progressJson.getString("story_id"),
                        storyTitle = progressJson.getString("story_title"),
                        currentPosition = progressJson.getInt("current_position"),
                        totalLength = progressJson.getInt("total_length"),
                        readingProgress = progressJson.getDouble("reading_progress"),
                        isCompleted = progressJson.getBoolean("is_completed"),
                        startTime = progressJson.optString("start_time", null),
                        lastReadTime = progressJson.optString("last_read_time", null),
                        completionTime = progressJson.optString("completion_time", null),
                        readingDurationSeconds = progressJson.getInt("reading_duration_seconds")
                    )
                    progressList.add(progress)
                }
                
                val progressListResponse = ReadingProgressListResponse(
                    success = jsonResponse.getBoolean("success"),
                    progress = progressList,
                    count = jsonResponse.getInt("count")
                )
                ApiResult.Success(progressListResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取阅读进度失败: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "错误消息: ${e.message}")
            Log.e(TAG, "错误堆栈: ${e.stackTrace.joinToString("\n")}")
            ApiResult.Error(e.message ?: "网络请求失败: ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * 记录故事交互
     */
    suspend fun logStoryInteraction(
        userId: String,
        storyId: String,
        interactionType: String,
        interactionData: Map<String, Any>? = null,
        sessionId: String? = null,
        deviceInfo: String = ""
    ): ApiResult<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/interaction"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                doOutput = true
            }
            
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("story_id", storyId)
                put("interaction_type", interactionType)
                sessionId?.let { put("session_id", it) }
                if (deviceInfo.isNotEmpty()) put("device_info", deviceInfo)
                interactionData?.let { data ->
                    val dataJson = JSONObject()
                    data.forEach { (key, value) ->
                        dataJson.put(key, value)
                    }
                    put("interaction_data", dataJson)
                }
            }
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "记录故事交互响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val apiResponse = ApiResponse(
                    success = jsonResponse.getBoolean("success"),
                    message = jsonResponse.getString("message")
                )
                ApiResult.Success(apiResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "记录故事交互失败", e)
            ApiResult.Error(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 获取阅读统计
     */
    suspend fun getReadingStatistics(
        userId: String,
        days: Int = 30
    ): ApiResult<ReadingStatisticsResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/statistics") + "?user_id=$userId&days=$days")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            val responseCode = connection.responseCode
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "获取阅读统计响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val statisticsJson = jsonResponse.getJSONObject("statistics")
                
                val recentStories = mutableListOf<RecentStory>()
                val recentStoriesArray = statisticsJson.getJSONArray("recent_stories")
                for (i in 0 until recentStoriesArray.length()) {
                    val storyJson = recentStoriesArray.getJSONObject(i)
                    val story = RecentStory(
                        storyId = storyJson.getString("story_id"),
                        storyTitle = storyJson.getString("story_title"),
                        readingProgress = storyJson.getDouble("reading_progress"),
                        isCompleted = storyJson.getBoolean("is_completed"),
                        lastReadTime = storyJson.optString("last_read_time", null)
                    )
                    recentStories.add(story)
                }
                
                val dailyReading = mutableListOf<DailyReading>()
                val dailyReadingArray = statisticsJson.getJSONArray("daily_reading")
                for (i in 0 until dailyReadingArray.length()) {
                    val dailyJson = dailyReadingArray.getJSONObject(i)
                    val daily = DailyReading(
                        date = dailyJson.optString("date", null),
                        durationSeconds = dailyJson.getInt("duration_seconds")
                    )
                    dailyReading.add(daily)
                }
                
                val statistics = ReadingStatistics(
                    totalStories = statisticsJson.getInt("total_stories"),
                    completedStories = statisticsJson.getInt("completed_stories"),
                    totalReadingTimeSeconds = statisticsJson.getInt("total_reading_time_seconds"),
                    averageProgress = statisticsJson.getDouble("average_progress"),
                    lastReadingTime = statisticsJson.optString("last_reading_time", null),
                    recentStories = recentStories,
                    dailyReading = dailyReading
                )
                
                val statisticsResponse = ReadingStatisticsResponse(
                    success = jsonResponse.getBoolean("success"),
                    statistics = statistics,
                    periodDays = jsonResponse.getInt("period_days")
                )
                ApiResult.Success(statisticsResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取阅读统计失败", e)
            ApiResult.Error(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 完成故事阅读
     */
    suspend fun completeStoryReading(
        userId: String,
        storyId: String,
        storyTitle: String,
        completionMode: String,
        deviceInfo: String = ""
    ): ApiResult<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/story/complete"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("story_id", storyId)
                put("story_title", storyTitle)
                put("completion_mode", completionMode)
                put("device_info", deviceInfo)
            }
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream, "UTF-8")
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            outputStream.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "完成阅读API响应码: $responseCode")
            
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val responseBody = reader.readText()
                reader.close()
                inputStream.close()
                
                // 检查响应是否为HTML（错误页面）
                if (responseBody.trim().startsWith("<!") || responseBody.trim().startsWith("<html")) {
                    return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
                }
                
                val jsonResponse = try {
                    JSONObject(responseBody)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                Log.d(TAG, "完成阅读API响应: $responseBody")
                
                if (jsonResponse.optBoolean("success", false)) {
                    ApiResult.Success(ApiResponse(
                        success = true,
                        message = jsonResponse.optString("message", "完成阅读成功")
                    ))
                } else {
                    val errorMessage = jsonResponse.optString("error", "完成阅读失败")
                    Log.e(TAG, "完成阅读API返回错误: $errorMessage")
                    ApiResult.Error(errorMessage)
                }
            } else {
                val errorStream = connection.errorStream
                val errorBody = if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream, "UTF-8"))
                    val error = reader.readText()
                    reader.close()
                    errorStream.close()
                    error
                } else {
                    "HTTP错误: $responseCode"
                }
                Log.e(TAG, "完成阅读API调用失败: $responseCode, $errorBody")
                ApiResult.Error("完成阅读失败: $errorBody")
            }
            
            connection.disconnect()
            response
            
        } catch (e: Exception) {
            Log.e(TAG, "完成阅读API调用异常", e)
            ApiResult.Error("完成阅读失败: ${e.message}")
        }
    }
    
    /**
     * 获取活跃故事列表
     */
    suspend fun getActiveStories(): ApiResult<StoriesListResponse> = withContext(Dispatchers.IO) {
        try {
            val url = URL(ServerConfig.getApiUrl("api/stories/active"))
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Android App")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
            }
            
            Log.d(TAG, "获取活跃故事列表请求URL: $url")
            
            val responseCode = connection.responseCode
            Log.d(TAG, "获取活跃故事列表响应码: $responseCode")
            
            val inputStream = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            
            val response = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            Log.d(TAG, "获取活跃故事列表响应: $response")
            
            // 检查响应是否为HTML（错误页面）
            if (response.trim().startsWith("<!") || response.trim().startsWith("<html")) {
                return@withContext ApiResult.Error("服务器返回错误页面，请检查服务器地址是否正确")
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonResponse = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                val storiesList = mutableListOf<Story>()
                
                val storiesArray = jsonResponse.getJSONArray("stories")
                for (i in 0 until storiesArray.length()) {
                    val storyJson = storiesArray.getJSONObject(i)
                    val story = Story(
                        id = storyJson.getString("id"),
                        title = storyJson.getString("title"),
                        content = storyJson.getString("content"),
                        audioFilePath = storyJson.optString("audio_file_path", null),
                        audioDurationSeconds = storyJson.optInt("audio_duration_seconds", 0)
                    )
                    storiesList.add(story)
                }
                
                val storiesListResponse = StoriesListResponse(
                    success = jsonResponse.getBoolean("success"),
                    stories = storiesList,
                    total = jsonResponse.getInt("total")
                )
                ApiResult.Success(storiesListResponse)
            } else {
                val errorJson = try {
                    JSONObject(response)
                } catch (e: org.json.JSONException) {
                    return@withContext ApiResult.Error("服务器响应格式错误: ${e.message}")
                }
                ApiResult.Error(errorJson.getString("error"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取活跃故事列表失败: ${e.javaClass.simpleName}", e)
            Log.e(TAG, "错误消息: ${e.message}")
            ApiResult.Error(e.message ?: "网络请求失败: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * API结果封装
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

/**
 * 基础API响应
 */
data class ApiResponse(
    val success: Boolean,
    val message: String
)

/**
 * 阅读会话响应
 */
data class ReadingSessionResponse(
    val success: Boolean,
    val sessionId: String,
    val message: String
)

/**
 * 阅读进度响应
 */
data class ReadingProgressResponse(
    val success: Boolean,
    val progressPercentage: Double,
    val isCompleted: Boolean,
    val message: String
)

/**
 * 阅读进度（网络响应）
 */
data class NetworkReadingProgress(
    val storyId: String,
    val storyTitle: String,
    val currentPosition: Int,
    val totalLength: Int,
    val readingProgress: Double,
    val isCompleted: Boolean,
    val startTime: String?,
    val lastReadTime: String?,
    val completionTime: String?,
    val readingDurationSeconds: Int
)

/**
 * 阅读进度列表响应
 */
data class ReadingProgressListResponse(
    val success: Boolean,
    val progress: List<NetworkReadingProgress>,
    val count: Int
)

/**
 * 阅读统计
 */
data class ReadingStatistics(
    val totalStories: Int,
    val completedStories: Int,
    val totalReadingTimeSeconds: Int,
    val averageProgress: Double,
    val lastReadingTime: String?,
    val recentStories: List<RecentStory>,
    val dailyReading: List<DailyReading>
)

/**
 * 最近阅读的故事
 */
data class RecentStory(
    val storyId: String,
    val storyTitle: String,
    val readingProgress: Double,
    val isCompleted: Boolean,
    val lastReadTime: String?
)

/**
 * 每日阅读统计
 */
data class DailyReading(
    val date: String?,
    val durationSeconds: Int
)

/**
 * 阅读统计响应
 */
data class ReadingStatisticsResponse(
    val success: Boolean,
    val statistics: ReadingStatistics,
    val periodDays: Int
)

/**
 * 故事数据类
 */
data class Story(
    val id: String,
    val title: String,
    val content: String,
    val audioFilePath: String?,
    val audioDurationSeconds: Int
)

/**
 * 故事列表响应
 */
data class StoriesListResponse(
    val success: Boolean,
    val stories: List<Story>,
    val total: Int
)
