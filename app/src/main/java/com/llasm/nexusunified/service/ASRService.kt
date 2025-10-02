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

class ASRService(private val context: Context) {
    
    companion object {
        private const val TAG = "ASRService"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var shouldStopRecording = false
    private var recordingThread: Thread? = null
    private var collectedAudioData = mutableListOf<Byte>()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * 开始录音
     */
    fun startRecording(): Boolean {
        android.util.Log.d(TAG, "startRecording() 被调用")
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
            
            audioRecord?.startRecording()
            isRecording = true
            
            android.util.Log.d(TAG, "开始录音，启动数据收集线程")
            startDataCollection()
            
            android.util.Log.d(TAG, "录音启动成功")
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "录音启动异常: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 停止录音并获取音频数据
     */
    fun stopRecording(): ByteArray? {
        if (!isRecording) {
            return null
        }
        
        shouldStopRecording = true
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
        
        recordingThread?.join(1000)
        recordingThread = null
        
        return collectAudioData()
    }
    
    /**
     * 启动数据收集线程
     */
    private fun startDataCollection() {
        recordingThread = Thread {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val buffer = ByteArray(bufferSize * 2) // 16-bit = 2 bytes per sample
            
            while (isRecording && !shouldStopRecording) {
                try {
                    if (audioRecord == null) {
                        break
                    }
                    
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        synchronized(collectedAudioData) {
                            for (i in 0 until bytesRead) {
                                collectedAudioData.add(buffer[i])
                            }
                        }
                        android.util.Log.d(TAG, "读取音频数据: $bytesRead 字节，总收集: ${collectedAudioData.size} 字节")
                    }
                    Thread.sleep(10)
                } catch (e: Exception) {
                    break
                }
            }
        }
        recordingThread?.start()
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
     * 释放资源
     */
    fun release() {
        shouldStopRecording = true
        isRecording = false
        
        recordingThread?.interrupt()
        recordingThread = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        collectedAudioData.clear()
    }
}