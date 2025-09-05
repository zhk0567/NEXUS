package com.llasm.voiceassistant.service

import android.content.Context
import android.util.Log
import com.llasm.voiceassistant.audio.MediaAudioPlayer
import com.llasm.voiceassistant.audio.AudioRecorder
import com.llasm.voiceassistant.network.NetworkModule
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class VoiceService(private val context: Context) {
    
    private val audioRecorder = AudioRecorder(context)
    private val audioPlayer = MediaAudioPlayer(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var recordedAudioData = ByteArrayOutputStream()
    private var isRecording = false
    
    companion object {
        private const val TAG = "VoiceService"
    }
    
    fun startVoiceRecording(
        onRecordingStarted: () -> Unit = {},
        onRecordingStopped: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        recordedAudioData.reset()
        isRecording = true
        
        audioRecorder.startRecording(
            onData = { data ->
                if (isRecording) {
                    recordedAudioData.write(data)
                }
            },
            onError = { error ->
                isRecording = false
                onError(error)
            }
        )
        
        onRecordingStarted()
        Log.d(TAG, "Voice recording started")
    }
    
    fun stopVoiceRecording(
        onTranscriptionComplete: (String) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return
        }
        
        isRecording = false
        val audioData = recordedAudioData.toByteArray()
        audioRecorder.stopRecording()
        
        if (audioData.isEmpty()) {
            onError(Exception("No audio data recorded"))
            return
        }
        
        // 发送音频到后端进行语音识别
        scope.launch {
            try {
                transcribeAudio(audioData, onTranscriptionComplete, onError)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
        
        Log.d(TAG, "Voice recording stopped, audio data size: ${audioData.size}")
    }
    
    private suspend fun transcribeAudio(
        audioData: ByteArray,
        onTranscriptionComplete: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            // 创建临时文件
            val tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
            FileOutputStream(tempFile).use { fos ->
                // 写入WAV文件头
                writeWavHeader(fos, audioData.size)
                fos.write(audioData)
            }
            
            // 创建MultipartBody
            val requestBody = tempFile.asRequestBody("audio/wav".toMediaType())
            val audioPart = MultipartBody.Part.createFormData("audio", "audio.wav", requestBody)
            
            // 发送到后端
            val apiService = NetworkModule.getApiService()
            val response = apiService.transcribeAudio(audioPart)
            
            if (response.isSuccessful) {
                val transcriptionResponse = response.body()
                if (transcriptionResponse?.success == true) {
                    withContext(Dispatchers.Main) {
                        onTranscriptionComplete(transcriptionResponse.transcription)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError(Exception("Transcription failed"))
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError(Exception("Transcription request failed: ${response.code()}"))
                }
            }
            
            // 清理临时文件
            tempFile.delete()
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }
    
    fun playTextToSpeech(
        text: String,
        onPlaybackComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Requesting TTS for text: $text")
                val apiService = NetworkModule.getApiService()
                val response = apiService.textToSpeech(mapOf("text" to text))
                
                if (response.isSuccessful) {
                    val audioData = response.body()?.bytes()
                    if (audioData != null && audioData.isNotEmpty()) {
                        Log.d(TAG, "Received audio data: ${audioData.size} bytes")
                        
                        // 直接播放音频数据（MediaPlayer可以处理多种格式）
                        audioPlayer.playAudioData(
                            audioData = audioData,
                            onComplete = onPlaybackComplete,
                            onError = onError
                        )
                    } else {
                        Log.w(TAG, "Empty audio data received")
                        withContext(Dispatchers.Main) {
                            onError(Exception("Empty audio data received"))
                        }
                    }
                } else {
                    Log.e(TAG, "TTS request failed: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        onError(Exception("TTS request failed: ${response.code()}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in TTS request", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
    
    private fun isValidAudioData(audioData: ByteArray): Boolean {
        // 检查数据大小
        if (audioData.size < 12) return false
        
        try {
            // 检查WAV格式
            val header = String(audioData.sliceArray(0..3))
            if (header == "RIFF" && audioData.size >= 44) {
                val format = String(audioData.sliceArray(8..11))
                if (format == "WAVE") {
                    Log.d(TAG, "Valid WAV format detected")
                    return true
                }
            }
            
            // 检查MP3格式
            if (audioData.size >= 3) {
                val mp3Header = audioData.sliceArray(0..2)
                if (mp3Header[0] == 0xFF.toByte() && (mp3Header[1].toInt() and 0xE0) == 0xE0) {
                    Log.d(TAG, "MP3 format detected, will be converted")
                    return true
                }
            }
            
            // 检查其他常见音频格式
            if (audioData.size >= 4) {
                val header4 = String(audioData.sliceArray(0..3))
                when (header4) {
                    "OggS" -> {
                        Log.d(TAG, "OGG format detected")
                        return true
                    }
                    "fLaC" -> {
                        Log.d(TAG, "FLAC format detected")
                        return true
                    }
                }
            }
            
            Log.w(TAG, "Unknown audio format")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating audio data", e)
            return false
        }
    }
    
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Int) {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalSize = 36 + dataSize
        
        // WAV文件头
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToLittleEndian(totalSize))
        outputStream.write("WAVE".toByteArray())
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToLittleEndian(16)) // fmt chunk size
        outputStream.write(shortToLittleEndian(1)) // audio format (PCM)
        outputStream.write(shortToLittleEndian(channels.toShort()))
        outputStream.write(intToLittleEndian(sampleRate))
        outputStream.write(intToLittleEndian(byteRate))
        outputStream.write(shortToLittleEndian(blockAlign.toShort()))
        outputStream.write(shortToLittleEndian(bitsPerSample.toShort()))
        outputStream.write("data".toByteArray())
        outputStream.write(intToLittleEndian(dataSize))
    }
    
    private fun intToLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    private fun shortToLittleEndian(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }
    
    fun isRecording(): Boolean = isRecording
    
    fun isPlaying(): Boolean = audioPlayer.isPlaying()
    
    fun release() {
        scope.cancel()
        audioRecorder.release()
        audioPlayer.release()
    }
}
