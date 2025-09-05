package com.llasm.voiceassistant.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    private const val TIMEOUT_SECONDS = 15L  // 减少超时时间
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 15L
    private const val WRITE_TIMEOUT_SECONDS = 15L
    
    // 动态获取可用的基础URL
    private suspend fun getBaseUrl(): String {
        return NetworkTester.findWorkingUrl() ?: "http://10.0.2.2:5000/"
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)  // 启用连接重试
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))  // 连接池
        .build()
    
    private fun createRetrofit(baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    // 缓存Retrofit实例以提高性能
    private var cachedRetrofit: Retrofit? = null
    private var cachedBaseUrl: String? = null
    
    suspend fun getApiService(): ApiService {
        val baseUrl = getBaseUrl()
        
        // 使用缓存的Retrofit实例
        if (cachedRetrofit == null || cachedBaseUrl != baseUrl) {
            cachedRetrofit = createRetrofit(baseUrl)
            cachedBaseUrl = baseUrl
        }
        
        return cachedRetrofit!!.create(ApiService::class.java)
    }
}
