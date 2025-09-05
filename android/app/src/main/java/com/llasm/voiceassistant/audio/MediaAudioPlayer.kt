package com.llasm.voiceassistant.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class MediaAudioPlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var playbackJob: Job? = null
    
    companion object {
        private const val TAG = "MediaAudioPlayer"
    }
    
    fun playAudioData(
        audioData: ByteArray,
        onComplete: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (isPlaying) {
            Log.w(TAG, "Already playing")
            return
        }
        
        try {
            // 检查音频数据是否有效
            if (audioData.isEmpty()) {
                onError(Exception("Empty audio data"))
                return
            }
            
            Log.d(TAG, "Audio data size: ${audioData.size} bytes")
            
            // 创建临时文件
            val tempFile = File.createTempFile("audio_", ".wav", context.cacheDir)
            tempFile.deleteOnExit()
            
            // 写入音频数据到临时文件
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioData)
            }
            
            // 创建MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer prepared")
                    mp.start()
                    this@MediaAudioPlayer.isPlaying = true
                }
                
                setOnCompletionListener { mp ->
                    Log.d(TAG, "Playback completed")
                    this@MediaAudioPlayer.isPlaying = false
                    onComplete()
                    cleanup()
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    this@MediaAudioPlayer.isPlaying = false
                    onError(Exception("MediaPlayer error: what=$what, extra=$extra"))
                    cleanup()
                    true
                }
                
                prepareAsync()
            }
            
            Log.d(TAG, "Playback started with MediaPlayer")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            onError(e)
        }
    }
    
    fun stopPlayback() {
        if (!isPlaying) {
            return
        }
        
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        
        mediaPlayer?.stop()
        cleanup()
        
        Log.d(TAG, "Playback stopped")
    }
    
    private fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    fun isPlaying(): Boolean = isPlaying
    
    fun release() {
        stopPlayback()
    }
}
