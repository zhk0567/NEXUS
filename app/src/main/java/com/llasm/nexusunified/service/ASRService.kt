package com.llasm.nexusunified.service

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

// 录音回调接口 - 参照WXSoundRecord实现
interface RecordingCallback {
    fun onRecordingStart()
    fun onRecordingStop(audioData: ByteArray?)
    fun onRecordingCancel()
    fun onRecordingTimeUpdate(seconds: Int) // 录音时长更新（秒）
    fun onRecordingCountdown(remainingSeconds: Int) // 倒计时提醒（剩余秒数）
    fun onRecordingTimeout() // 超时自动停止
}

class ASRService(private val context: Context) {
    
    companion object {
        private const val TAG = "ASRService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_TIME = 60 // 最大录音时长60秒（参照微信）
        private const val COUNTDOWN_START = 10 // 倒计时开始时间（剩余10秒时开始倒计时）
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var shouldStopRecording = false
    private var recordingThread: Thread? = null
    private var timeUpdateThread: Thread? = null
    private var collectedAudioData = mutableListOf<Byte>()
    private var recordingCallback: RecordingCallback? = null
    private var recordingStartTime: Long = 0
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 设置录音回调 - 参照WXSoundRecord实现
     */
    fun setRecordingCallback(callback: RecordingCallback?) {
        this.recordingCallback = callback
    }
    
    /**
     * 开始录音 - 参照WXSoundRecord和CSDN文章实现
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            android.util.Log.d(TAG, "已经在录音中，返回true")
            return true
        }
        
        try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val hasPermission = ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
            
            android.util.Log.d(TAG, "录音权限检查: $hasPermission")
            if (!hasPermission) {
                android.util.Log.e(TAG, "没有录音权限")
                return false
            }
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            android.util.Log.d(TAG, "AudioRecord 创建完成，状态: ${audioRecord?.state}")
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e(TAG, "AudioRecord 初始化失败")
                return false
            }
            
            collectedAudioData.clear()
            shouldStopRecording = false
            recordingStartTime = System.currentTimeMillis()
            
            audioRecord?.startRecording()
            isRecording = true
            
            android.util.Log.d(TAG, "开始录音，启动数据收集线程")
            startDataCollection()
            startTimeUpdate()
            
            // 回调录音开始
            recordingCallback?.onRecordingStart()
            
            android.util.Log.d(TAG, "录音启动成功")
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "录音启动异常: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 停止录音并获取音频数据 - 参照WXSoundRecord实现
     */
    fun stopRecording(): ByteArray? {
        if (!isRecording) {
            android.util.Log.d(TAG, "当前未在录音，返回null")
            return null
        }
        
        android.util.Log.d(TAG, "开始停止录音")
        shouldStopRecording = true
        isRecording = false
        
        // 停止时间更新线程
        timeUpdateThread?.interrupt()
        try {
            timeUpdateThread?.join(500)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "等待时间更新线程结束异常: ${e.message}")
        }
        timeUpdateThread = null
        
        // 停止AudioRecord
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "停止AudioRecord异常: ${e.message}")
        }
        audioRecord = null
        
        // 等待录音线程结束
        try {
            recordingThread?.join(1000)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "等待录音线程结束异常: ${e.message}")
        }
        recordingThread = null
        
        val audioData = collectAudioData()
        
        android.util.Log.d(TAG, "录音停止完成，音频数据大小: ${audioData?.size ?: 0} 字节")
        
        // 回调录音停止
        recordingCallback?.onRecordingStop(audioData)
        
        return audioData
    }
    
    /**
     * 取消录音 - 参照WXSoundRecord实现
     */
    fun cancelRecording() {
        if (!isRecording) {
            android.util.Log.d(TAG, "当前未在录音，无需取消")
            return
        }
        
        android.util.Log.d(TAG, "开始取消录音")
        shouldStopRecording = true
        isRecording = false
        
        // 停止时间更新线程
        timeUpdateThread?.interrupt()
        try {
            timeUpdateThread?.join(500)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "等待时间更新线程结束异常: ${e.message}")
        }
        timeUpdateThread = null
        
        // 停止AudioRecord
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "停止AudioRecord异常: ${e.message}")
        }
        audioRecord = null
        
        // 等待录音线程结束
        try {
            recordingThread?.join(1000)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "等待录音线程结束异常: ${e.message}")
        }
        recordingThread = null
        
        // 清空音频数据
        synchronized(collectedAudioData) {
            collectedAudioData.clear()
        }
        
        android.util.Log.d(TAG, "录音取消完成")
        
        // 回调录音取消
        recordingCallback?.onRecordingCancel()
    }
    
    /**
     * 启动数据收集线程 - 参照WXSoundRecord实现
     */
    private fun startDataCollection() {
        // 确保之前的线程已停止
        recordingThread?.interrupt()
        recordingThread = null
        
        recordingThread = Thread {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                if (bufferSize <= 0) {
                    android.util.Log.e(TAG, "无效的bufferSize: $bufferSize")
                    return@Thread
                }
                
                val buffer = ByteArray(bufferSize * 2) // 16-bit = 2 bytes per sample
                
                while (isRecording && !shouldStopRecording) {
                    try {
                        if (audioRecord == null) {
                            android.util.Log.d(TAG, "AudioRecord为null，退出数据收集")
                            break
                        }
                        
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (bytesRead > 0) {
                            synchronized(collectedAudioData) {
                                for (i in 0 until bytesRead) {
                                    collectedAudioData.add(buffer[i])
                                }
                            }
                        } else if (bytesRead < 0) {
                            // 读取错误
                            android.util.Log.e(TAG, "读取音频数据错误: $bytesRead")
                            break
                        }
                        
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        android.util.Log.d(TAG, "数据收集线程被中断")
                        break
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "数据收集异常: ${e.message}", e)
                        break
                    }
                }
                
                android.util.Log.d(TAG, "数据收集线程结束，总收集: ${collectedAudioData.size} 字节")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "数据收集线程启动异常: ${e.message}", e)
            }
        }
        recordingThread?.start()
    }
    
    /**
     * 启动时间更新线程 - 参照CSDN文章实现超时截取和倒计时提醒
     */
    private fun startTimeUpdate() {
        // 确保之前的线程已停止
        timeUpdateThread?.interrupt()
        timeUpdateThread = null
        
        timeUpdateThread = Thread {
            try {
                while (isRecording && !shouldStopRecording) {
                    val elapsedSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                    
                    // 更新录音时长
                    recordingCallback?.onRecordingTimeUpdate(elapsedSeconds)
                    
                    // 倒计时提醒（剩余10秒时开始倒计时）
                    val remainingSeconds = MAX_RECORDING_TIME - elapsedSeconds
                    if (remainingSeconds <= COUNTDOWN_START && remainingSeconds > 0) {
                        recordingCallback?.onRecordingCountdown(remainingSeconds)
                    } else {
                        // 不在倒计时范围内，清除倒计时
                        recordingCallback?.onRecordingCountdown(0)
                    }
                    
                    // 检查是否超时（60秒自动停止）
                    if (elapsedSeconds >= MAX_RECORDING_TIME) {
                        android.util.Log.d(TAG, "录音超时，自动停止")
                        // 先回调超时，然后停止录音
                        recordingCallback?.onRecordingTimeout()
                        break
                    }
                    
                    Thread.sleep(1000) // 每秒更新一次
                }
            } catch (e: InterruptedException) {
                // 线程被中断，正常退出
                android.util.Log.d(TAG, "时间更新线程被中断")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "时间更新线程异常: ${e.message}", e)
            }
        }
        timeUpdateThread?.start()
    }
    
    /**
     * 获取已收集的音频数据
     */
    private fun collectAudioData(): ByteArray? {
        synchronized(collectedAudioData) {
            val audioData = collectedAudioData.toByteArray()
            android.util.Log.d(TAG, "收集到音频数据大小: ${audioData.size} 字节")
            return audioData
        }
    }
    
    /**
     * 创建WAV文件头
     */
    private fun createWavHeader(dataSize: Int): ByteArray {
        val header = ByteArray(44)
        var offset = 0
        
        // RIFF header
        header[offset++] = 'R'.toByte()
        header[offset++] = 'I'.toByte()
        header[offset++] = 'F'.toByte()
        header[offset++] = 'F'.toByte()
        
        // File size
        val fileSize = dataSize + 36
        header[offset++] = (fileSize and 0xFF).toByte()
        header[offset++] = ((fileSize shr 8) and 0xFF).toByte()
        header[offset++] = ((fileSize shr 16) and 0xFF).toByte()
        header[offset++] = ((fileSize shr 24) and 0xFF).toByte()
        
        // WAVE
        header[offset++] = 'W'.toByte()
        header[offset++] = 'A'.toByte()
        header[offset++] = 'V'.toByte()
        header[offset++] = 'E'.toByte()
        
        // fmt chunk
        header[offset++] = 'f'.toByte()
        header[offset++] = 'm'.toByte()
        header[offset++] = 't'.toByte()
        header[offset++] = ' '.toByte()
        
        // fmt chunk size
        header[offset++] = 16
        header[offset++] = 0
        header[offset++] = 0
        header[offset++] = 0
        
        // Audio format (PCM)
        header[offset++] = 1
        header[offset++] = 0
        
        // Number of channels
        header[offset++] = 1
        header[offset++] = 0
        
        // Sample rate
        header[offset++] = (SAMPLE_RATE and 0xFF).toByte()
        header[offset++] = ((SAMPLE_RATE shr 8) and 0xFF).toByte()
        header[offset++] = ((SAMPLE_RATE shr 16) and 0xFF).toByte()
        header[offset++] = ((SAMPLE_RATE shr 24) and 0xFF).toByte()
        
        // Byte rate
        val byteRate = SAMPLE_RATE * 1 * 2
        header[offset++] = (byteRate and 0xFF).toByte()
        header[offset++] = ((byteRate shr 8) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 16) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 24) and 0xFF).toByte()
        
        // Block align
        header[offset++] = 2
        header[offset++] = 0
        
        // Bits per sample
        header[offset++] = 16
        header[offset++] = 0
        
        // data chunk
        header[offset++] = 'd'.toByte()
        header[offset++] = 'a'.toByte()
        header[offset++] = 't'.toByte()
        header[offset++] = 'a'.toByte()
        
        // Data size
        header[offset++] = (dataSize and 0xFF).toByte()
        header[offset++] = ((dataSize shr 8) and 0xFF).toByte()
        header[offset++] = ((dataSize shr 16) and 0xFF).toByte()
        header[offset++] = ((dataSize shr 24) and 0xFF).toByte()
        
        return header
    }
    
    /**
     * 转录音频数据
     */
    suspend fun transcribeAudio(audioData: ByteArray): String? {
        return withContext(Dispatchers.IO) {
            try {
                val tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
                tempFile.deleteOnExit()
                
                android.util.Log.d(TAG, "开始创建WAV文件，原始数据大小: ${audioData.size} 字节")
                
                val outputStream = FileOutputStream(tempFile)
                
                // 添加WAV文件头
                val wavHeader = createWavHeader(audioData.size)
                outputStream.write(wavHeader)
                outputStream.write(audioData)
                outputStream.close()
                
                android.util.Log.d(TAG, "WAV文件创建完成: ${tempFile.absolutePath}")
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "audio",
                        tempFile.name,
                        tempFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(ServerConfig.getApiUrl(ServerConfig.Endpoints.TRANSCRIBE))
                    .post(requestBody)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody ?: "")
                    
                    if (jsonResponse.getBoolean("success")) {
                        return@withContext jsonResponse.getString("transcription")
                    }
                }
                
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 释放资源 - 参照WXSoundRecord实现
     */
    fun release() {
        shouldStopRecording = true
        isRecording = false
        
        timeUpdateThread?.interrupt()
        timeUpdateThread = null
        
        recordingThread?.interrupt()
        recordingThread = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        collectedAudioData.clear()
        recordingCallback = null
    }
}