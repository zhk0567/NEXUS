package com.llasm.nexusunified.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import com.llasm.nexusunified.config.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

// ASR状态数据类
data class ASRStatus(
    val isProcessing: Boolean,
    val progress: Int,
    val currentRequestId: String?,
    val processingTime: Float?,
    val health: String
)

// ASR状态回调接口
interface ASRStatusCallback {
    fun onStatusUpdate(status: ASRStatus)
    fun onError(error: String)
}

class ASRService(private val context: Context) {
    
    companion object {
        private const val TAG = "ASRService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val API_URL = ServerConfig.getApiUrl("transcribe")
        private val STATUS_API_URL = ServerConfig.getApiUrl("asr/status")
        private const val STATUS_POLL_INTERVAL = 1000L  // 状态轮询间隔（毫秒）
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var shouldStopRecording = false  // 添加停止标志
    
    // 状态监控相关
    private var statusCallback: ASRStatusCallback? = null
    private var statusMonitoringJob: Job? = null
    private var currentRequestId: String? = null
    
    /**
     * 设置状态回调
     */
    fun setStatusCallback(callback: ASRStatusCallback?) {
        this.statusCallback = callback
    }
    
    /**
     * 开始状态监控
     */
    private fun startStatusMonitoring() {
        stopStatusMonitoring() // 先停止现有的监控
        
        statusMonitoringJob = GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val status = fetchASRStatus()
                    statusCallback?.onStatusUpdate(status)
                    
                    // 如果不在处理中，停止监控
                    if (!status.isProcessing) {
                        break
                    }
                    
                    delay(STATUS_POLL_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "状态监控异常", e)
                    statusCallback?.onError("状态监控失败: ${e.message}")
                    break
                }
            }
        }
    }
    
    /**
     * 开始状态监控 - 带回调版本
     */
    private fun startStatusMonitoringWithCallback(onStatusUpdate: (ASRStatus) -> Unit) {
        stopStatusMonitoring() // 先停止现有的监控
        
        statusMonitoringJob = GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val status = fetchASRStatus()
                    onStatusUpdate(status)
                    
                    // 如果不在处理中，停止监控
                    if (!status.isProcessing) {
                        break
                    }
                    
                    delay(STATUS_POLL_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "状态监控异常", e)
                    break
                }
            }
        }
    }
    
    /**
     * 停止状态监控
     */
    private fun stopStatusMonitoring() {
        statusMonitoringJob?.cancel()
        statusMonitoringJob = null
    }
    
    /**
     * 获取ASR状态
     */
    private suspend fun fetchASRStatus(): ASRStatus {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(STATUS_API_URL)
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val jsonResponse = JSONObject(responseBody)
                        val processing = jsonResponse.getJSONObject("processing")
                        
                        return@withContext ASRStatus(
                            isProcessing = processing.getBoolean("is_processing"),
                            progress = processing.getInt("progress"),
                            currentRequestId = processing.optString("current_request_id").takeIf { it.isNotEmpty() },
                            processingTime = processing.optDouble("processing_time").toFloat().takeIf { it > 0 },
                            health = jsonResponse.getString("asr_health")
                        )
                    }
                }
                
                // 默认状态
                ASRStatus(
                    isProcessing = false,
                    progress = 0,
                    currentRequestId = null,
                    processingTime = null,
                    health = "unknown"
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取ASR状态失败", e)
                ASRStatus(
                    isProcessing = false,
                    progress = 0,
                    currentRequestId = null,
                    processingTime = null,
                    health = "error"
                )
            }
        }
    }
    
    /**
     * 开始录音
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "录音已在进行中")
            return false
        }
        
        try {
            // 检查录音权限
            val permission = android.Manifest.permission.RECORD_AUDIO
            val hasPermission = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasPermission) {
                Log.e(TAG, "没有录音权限")
                return false
            }
            
            Log.d(TAG, "录音权限检查通过")
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            Log.d(TAG, "计算缓冲区大小: $bufferSize")
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            Log.d(TAG, "AudioRecord创建完成，状态: ${audioRecord?.state}")
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败，状态: ${audioRecord?.state}")
                return false
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            Log.d(TAG, "开始录音成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录音失败", e)
            return false
        }
    }
    
    /**
     * 停止录音并返回音频数据
     */
    fun stopRecording(): ByteArray? {
        Log.d(TAG, "=== 立即停止录音 ===")
        shouldStopRecording = true
        
        if (!isRecording) {
            Log.w(TAG, "没有正在进行的录音")
            return null
        }
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            
            Log.d(TAG, "录音已立即停止")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            return null
        }
    }
    
    /**
     * 停止录音并获取音频数据
     */
    private fun stopRecordingAndGetData(): ByteArray? {
        if (!isRecording) {
            Log.w(TAG, "没有正在进行的录音")
            return null
        }
        
        try {
            // 先收集音频数据
            val audioData = collectAudioData()
            
            // 然后停止录音
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            
            Log.d(TAG, "停止录音，数据大小: ${audioData?.size ?: 0} 字节")
            return audioData
            
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
            return null
        }
    }
    
    /**
     * 录音并识别语音 - 带状态监控
     */
    suspend fun recordAndTranscribe(
        onRecordingStart: () -> Unit = {},
        onRecordingStop: () -> Unit = {},
        onTranscriptionResult: (String?) -> Unit = {},
        onError: (String) -> Unit = {},
        onStatusUpdate: (ASRStatus) -> Unit = {}
    ) {
        try {
            Log.d(TAG, "=== 开始录音识别流程 ===")
            Log.d(TAG, "当前线程: ${Thread.currentThread().name}")
            Log.d(TAG, "isRecording状态: $isRecording")
            
            // 重置停止标志
            shouldStopRecording = false
            
            withContext(Dispatchers.IO) {
                Log.d(TAG, "切换到IO线程: ${Thread.currentThread().name}")
                // 开始录音
                Log.d(TAG, "尝试启动录音")
                if (!startRecording()) {
                    Log.e(TAG, "录音启动失败")
                    onError("录音启动失败")
                    return@withContext
                }
                
                Log.d(TAG, "录音启动成功，调用开始回调")
                onRecordingStart()
                
                // 启动录音数据收集协程 - 使用GlobalScope避免被LaunchedEffect取消
                Log.d(TAG, "启动独立录音数据收集协程")
                val collectJob = GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val audioData = collectAudioData()
                        Log.d(TAG, "录音数据收集完成，大小: ${audioData?.size ?: 0} 字节")
                        
                        // 停止录音 - 只在这里停止一次
                        if (audioRecord != null) {
                            try {
                                audioRecord?.stop()
                                Log.d(TAG, "AudioRecord已停止")
                            } catch (e: Exception) {
                                Log.w(TAG, "AudioRecord停止时出现异常: ${e.message}")
                            }
                            audioRecord?.release()
                            audioRecord = null
                        }
                        isRecording = false
                        onRecordingStop()
                        
                        if (audioData != null && audioData.isNotEmpty()) {
                            Log.d(TAG, "开始发送音频数据到后端进行识别")
                            
                            // 开始状态监控
                            startStatusMonitoringWithCallback { status ->
                                onStatusUpdate(status)
                            }
                            
                            val transcription = transcribeAudio(audioData)
                            Log.d(TAG, "后端识别完成，结果: $transcription")
                            
                            // 停止状态监控
                            stopStatusMonitoring()
                            
                            onTranscriptionResult(transcription)
                        } else {
                            Log.e(TAG, "录音数据为空")
                            onError("录音数据为空")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "录音数据收集异常", e)
                        onError("录音数据收集异常: ${e.message}")
                    }
                }
                
                // 等待录音完成或超时
                try {
                    withTimeout(10000) { // 10秒超时
                        collectJob.join()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "录音超时")
                    collectJob.cancel()
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isRecording = false
                    onError("录音超时")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "录音识别失败", e)
            onError("录音识别失败: ${e.message}")
        }
    }
    
    /**
     * 收集录音数据 - 修复版本
     */
    private fun collectAudioData(): ByteArray? {
        if (audioRecord == null) {
            Log.e(TAG, "AudioRecord为null，无法收集数据")
            return null
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        Log.d(TAG, "开始收集音频数据，缓冲区大小: $bufferSize")
        
        val audioData = mutableListOf<Byte>()
        val buffer = ByteArray(bufferSize)
        
        try {
            // 动态读取音频数据，根据实际录制状态
            var totalBytesRead = 0
            val maxReadTime = 10000L // 最多读取10秒
            val minReadTime = 1000L  // 最少读取1秒
            val startTime = System.currentTimeMillis()
            var lastDataTime = startTime
            
            while (System.currentTimeMillis() - startTime < maxReadTime) {
                // 检查录音状态和停止标志，如果已停止则立即退出
                if (!isRecording || shouldStopRecording) {
                    Log.d(TAG, "检测到录音状态已停止或收到停止信号，立即结束数据收集")
                    break
                }
                
                // 使用非阻塞方式读取音频数据
                val bytesRead = try {
                    audioRecord?.read(buffer, 0, bufferSize) ?: 0
                } catch (e: Exception) {
                    Log.w(TAG, "音频读取异常: ${e.message}")
                    break
                }
                
                if (bytesRead > 0) {
                    audioData.addAll(buffer.take(bytesRead))
                    totalBytesRead += bytesRead
                    lastDataTime = System.currentTimeMillis()
                    Log.d(TAG, "读取音频数据: $bytesRead 字节，总计: $totalBytesRead 字节")
                } else if (bytesRead < 0) {
                    Log.e(TAG, "音频读取错误: $bytesRead")
                    break
                }
                
                // 再次检查录音状态，确保在读取数据后立即响应停止信号
                if (!isRecording) {
                    Log.d(TAG, "读取数据后检测到录音状态已停止，立即结束数据收集")
                    break
                }
                
                // 检查是否应该结束录制
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                val timeSinceLastData = currentTime - lastDataTime
                
                // 如果录制时间超过最小时间，且最近没有数据，则结束
                if (elapsedTime > minReadTime && timeSinceLastData > 500) {
                    Log.d(TAG, "检测到录制结束，时间: ${elapsedTime}ms，最后数据时间: ${timeSinceLastData}ms")
                    break
                }
                
                // 短暂休眠，避免CPU占用过高
                Thread.sleep(10)
            }
            
            Log.d(TAG, "音频数据收集完成，总大小: ${audioData.size} 字节，录制时长: ${System.currentTimeMillis() - startTime}ms")
            return audioData.toByteArray()
            
        } catch (e: Exception) {
            Log.e(TAG, "录音数据收集失败", e)
            return null
        }
    }
    
    /**
     * 发送音频数据到后端进行识别
     */
    private suspend fun transcribeAudio(audioData: ByteArray): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始处理音频数据，大小: ${audioData.size} 字节")
                
                // 创建临时文件
                val tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
                tempFile.deleteOnExit()
                Log.d(TAG, "创建临时文件: ${tempFile.absolutePath}")
                
                // 写入WAV文件头
                writeWavHeader(tempFile, audioData.size)
                Log.d(TAG, "写入WAV文件头完成")
                
                // 写入音频数据
                FileOutputStream(tempFile, true).use { fos ->
                    fos.write(audioData)
                }
                Log.d(TAG, "写入音频数据完成，文件大小: ${tempFile.length()} 字节")
                
                // 发送到后端
                Log.d(TAG, "准备发送请求到后端: $API_URL")
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "audio",
                        "audio.wav",
                        tempFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .build()
                
                Log.d(TAG, "发送HTTP请求到后端...")
                val response = httpClient.newCall(request).execute()
                Log.d(TAG, "收到后端响应，状态码: ${response.code}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "后端响应内容: $responseBody")
                    
                    if (responseBody != null && responseBody.isNotEmpty()) {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            
                            if (jsonResponse.has("success") && jsonResponse.getBoolean("success")) {
                                val transcription = jsonResponse.getString("transcription")
                                Log.d(TAG, "语音识别成功: $transcription")
                                return@withContext transcription
                            } else {
                                val error = jsonResponse.optString("error", "未知错误")
                                Log.e(TAG, "语音识别失败: $error")
                                return@withContext null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析后端响应失败: $e")
                            Log.e(TAG, "响应内容: $responseBody")
                            return@withContext null
                        }
                    } else {
                        Log.e(TAG, "后端响应为空")
                        return@withContext null
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "API请求失败: ${response.code}, 错误信息: $errorBody")
                    return@withContext null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "语音识别请求失败", e)
                return@withContext null
            }
        }
    }
    
    /**
     * 写入WAV文件头
     */
    private fun writeWavHeader(file: File, audioDataSize: Int) {
        val totalSize = 36 + audioDataSize
        val sampleRate = SAMPLE_RATE
        val channels = 1
        val bitsPerSample = 16
        
        FileOutputStream(file).use { fos ->
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToLittleEndian(totalSize))
            
            // WAVE header
            fos.write("WAVE".toByteArray())
            
            // fmt chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToLittleEndian(16)) // fmt chunk size
            fos.write(shortToLittleEndian(1)) // audio format (PCM)
            fos.write(shortToLittleEndian(channels)) // number of channels
            fos.write(intToLittleEndian(sampleRate)) // sample rate
            fos.write(intToLittleEndian(sampleRate * channels * bitsPerSample / 8)) // byte rate
            fos.write(shortToLittleEndian(channels * bitsPerSample / 8)) // block align
            fos.write(shortToLittleEndian(bitsPerSample)) // bits per sample
            
            // data chunk
            fos.write("data".toByteArray())
            fos.write(intToLittleEndian(audioDataSize))
        }
    }
    
    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        stopStatusMonitoring()
        audioRecord?.release()
        audioRecord = null
    }
}
