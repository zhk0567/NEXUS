package com.llasm.voiceassistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*

class AudioPlayer(private val context: Context) {
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackJob: Job? = null
    
    companion object {
        private const val TAG = "AudioPlayer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
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
            
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * 2 // 增加缓冲区大小
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                onError(Exception("AudioTrack initialization failed"))
                return
            }
            
            audioTrack?.play()
            isPlaying = true
            
            playbackJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val chunkSize = 4096 // 增加块大小
                    var offset = 0
                    
                    while (offset < audioData.size && isPlaying) {
                        val remainingBytes = audioData.size - offset
                        val bytesToWrite = minOf(chunkSize, remainingBytes)
                        
                        val bytesWritten = audioTrack?.write(
                            audioData, offset, bytesToWrite
                        ) ?: 0
                        
                        if (bytesWritten < 0) {
                            throw Exception("AudioTrack write failed: $bytesWritten")
                        }
                        
                        offset += bytesWritten
                        
                        // 等待缓冲区有空间
                        while (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            val bufferSizeInFrames = audioTrack?.bufferSizeInFrames ?: 0
                            val bufferSizeInBytes = bufferSizeInFrames * 2 // 16-bit = 2 bytes per sample
                            val availableSpace = bufferSizeInBytes - (audioTrack?.playbackHeadPosition ?: 0) * 2
                            
                            if (availableSpace > chunkSize) {
                                break
                            }
                            delay(20)
                        }
                    }
                    
                    // 等待播放完成
                    while (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        delay(100)
                    }
                    
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during playback", e)
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                } finally {
                    stopPlayback()
                }
            }
            
            Log.d(TAG, "Playback started")
            
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
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        Log.d(TAG, "Playback stopped")
    }
    
    fun isPlaying(): Boolean = isPlaying
    
    fun release() {
        stopPlayback()
    }
}
