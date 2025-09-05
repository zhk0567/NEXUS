package com.llasm.voiceassistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object NetworkTester {
    
    private val TEST_URLS = listOf(
        "http://10.0.2.2:5000/health",      // Android模拟器
        "http://192.168.215.85:5000/health", // 您的电脑IP地址
        "http://localhost:5000/health",      // 本地地址
        "http://127.0.0.1:5000/health"      // 回环地址
    )
    
    suspend fun findWorkingUrl(): String? = withContext(Dispatchers.IO) {
        for (url in TEST_URLS) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 3000  // 减少连接超时
                connection.readTimeout = 3000     // 减少读取超时
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    connection.disconnect()
                    return@withContext url.replace("/health", "/")
                }
                connection.disconnect()
            } catch (e: Exception) {
                // 继续尝试下一个URL
            }
        }
        null
    }
    
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        findWorkingUrl() != null
    }
}
