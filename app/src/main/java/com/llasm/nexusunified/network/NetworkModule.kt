package com.llasm.nexusunified.network

import com.llasm.nexusunified.config.ServerConfig
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val BASE_URL = ServerConfig.CURRENT_SERVER
    
    // 性能优化：添加HTTP缓存
    private val cacheDirectory = File(System.getProperty("java.io.tmpdir"), "nexus_cache")
    private val cache = Cache(cacheDirectory, 10 * 1024 * 1024) // 10MB缓存
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC // 减少日志输出
    }
    
    // 性能优化：连接池和超时优化
    private val okHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS) // 减少连接超时
        .readTimeout(20, TimeUnit.SECONDS)    // 减少读取超时
        .writeTimeout(20, TimeUnit.SECONDS)   // 减少写入超时
        .retryOnConnectionFailure(true)       // 启用连接失败重试
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    fun getApiService(): ApiService = retrofit.create(ApiService::class.java)
}
