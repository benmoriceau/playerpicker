package com.example.chwazi2

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin
import kotlin.math.PI

class RisingTonePlayer {
    private var currentTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    private val sampleRate = 44100
    
    fun start(durationMs: Long) {
         stop()
         isPlaying = true
         Thread {
             try {
                 val bufferSize = AudioTrack.getMinBufferSize(
                     sampleRate,
                     AudioFormat.CHANNEL_OUT_MONO,
                     AudioFormat.ENCODING_PCM_16BIT
                 )
                 val finalBufferSize = if (bufferSize > 0) bufferSize else 2048
                 
                 val track = AudioTrack.Builder()
                     .setAudioAttributes(
                          AudioAttributes.Builder()
                              .setUsage(AudioAttributes.USAGE_GAME)
                              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                              .build()
                     )
                     .setAudioFormat(
                          AudioFormat.Builder()
                              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                              .setSampleRate(sampleRate)
                              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                              .build()
                     )
                     .setBufferSizeInBytes(finalBufferSize)
                     .setTransferMode(AudioTrack.MODE_STREAM)
                     .build()
                 
                 currentTrack = track
                 track.play()
                 
                 val totalSamples = (sampleRate * durationMs / 1000).toInt()
                 val buffer = ShortArray(finalBufferSize)
                 var sampleIndex = 0
                 
                 // Frequencies
                 val startFreq = 100.0
                 val endFreq = 300.0
                 var phase = 0.0
                 
                 while (isPlaying && sampleIndex < totalSamples) {
                     var i = 0
                     while (i < buffer.size && sampleIndex < totalSamples && isPlaying) {
                         val progress = sampleIndex.toDouble() / totalSamples
                         val currentFreq = startFreq + (endFreq - startFreq) * progress
                         
                         phase += 2.0 * Math.PI * currentFreq / sampleRate
                         if (phase > 2.0 * Math.PI) {
                             phase -= 2.0 * Math.PI
                         }
                         
                         val sampleValue = (sin(phase) * Short.MAX_VALUE * 0.5).toInt().toShort()
                         buffer[i] = sampleValue
                         
                         i++
                         sampleIndex++
                     }
                     if (i > 0) {
                         track.write(buffer, 0, i)
                     }
                 }
                 
             } catch (e: Exception) {
                 e.printStackTrace()
             } finally {
                 release()
             }
         }.start()
    }
    
    fun stop() {
        isPlaying = false
        try {
             currentTrack?.pause()
             currentTrack?.flush()
        } catch (e: Exception) {}
    }
    
    private fun release() {
         try {
             currentTrack?.release()
         } catch (e: Exception) {}
         currentTrack = null
    }
}
