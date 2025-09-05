package com.llasm.voiceassistant.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AudioRecorder(private val context: Context) {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    fun startRecording(
        onData: (ByteArray) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) {
            onError(Exception("录音权限未授予，请在设置中允许录音权限"))
            return
        }
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                onError(Exception("无法获取音频缓冲区大小"))
                return
            }
            
            val actualBufferSize = bufferSize * BUFFER_SIZE_FACTOR
            
            Log.d(TAG, "Buffer size: $actualBufferSize")
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                actualBufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val errorMsg = when (audioRecord?.state) {
                    AudioRecord.STATE_UNINITIALIZED -> "AudioRecord未初始化"
                    AudioRecord.STATE_INITIALIZED -> "AudioRecord已初始化"
                    else -> "AudioRecord状态未知: ${audioRecord?.state}"
                }
                Log.e(TAG, "AudioRecord initialization failed: $errorMsg")
                onError(Exception("AudioRecord初始化失败: $errorMsg"))
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(actualBufferSize)
                
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = audioRecord?.read(buffer, 0, actualBufferSize) ?: 0
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        onData(data)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read error: $bytesRead")
                        break
                    }
                }
            }
            
            Log.d(TAG, "Recording started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onError(Exception("录音启动失败: ${e.message}"))
        }
    }
    
    fun stopRecording(): ByteArray? {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return null
        }
        
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        Log.d(TAG, "Recording stopped")
        return null // 实际应用中这里应该返回录制的音频数据
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun release() {
        stopRecording()
    }
}
