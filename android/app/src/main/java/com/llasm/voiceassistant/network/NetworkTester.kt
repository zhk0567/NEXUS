package com.llasm.voiceassistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkTester {
    
    private val TEST_URLS = listOf(
        "http://192.168.215.85:5000/health", // 电脑在手机热点中的IP地址
        "http://192.168.1.100:5000/health",  // 常见局域网地址
        "http://192.168.0.100:5000/health",  // 另一个常见局域网地址
        "http://192.168.43.1:5000/health",   // 手机热点常见地址
        "http://192.168.137.1:5000/health",  // Windows热点常见地址
        "http://172.25.154.232:5000/health", // 之前的电脑IP地址
        "http://10.0.2.2:5000/health"        // Android模拟器（最后尝试）
    )
    
    suspend fun findWorkingUrl(): String? = withContext(Dispatchers.IO) {
        for (url in TEST_URLS) {
            try {
                android.util.Log.d("NetworkTester", "Testing URL: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000  // 增加连接超时
                connection.readTimeout = 5000     // 增加读取超时
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                android.util.Log.d("NetworkTester", "Response code for $url: $responseCode")
                
                if (responseCode == 200) {
                    val workingUrl = url.replace("/health", "/")
                    android.util.Log.d("NetworkTester", "Found working URL: $workingUrl")
                    connection.disconnect()
                    return@withContext workingUrl
                }
                connection.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("NetworkTester", "Error testing $url: ${e.message}")
                // 继续尝试下一个URL
            }
        }
        android.util.Log.w("NetworkTester", "No working URL found")
        null
    }
    
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        findWorkingUrl() != null
    }
}
