package com.example.chwazi2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

class FingerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Finger(
        var x: Float,
        var y: Float,
        val color: Int,
        val startTime: Long = System.currentTimeMillis()
    )

    private val fingers = mutableMapOf<Int, Finger>() // pointerId -> Finger
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator
    
    private var isFinished = false
    private var winnerPointerId: Int? = null

    // 5 seconds timeout
    private val TIMEOUT_MS = 5000L
    private val SOUND_START_DELAY_MS = 1000L
    
    // Circle radius in dp
    private val CIRCLE_RADIUS_DP = 60f
    private var circleRadiusPx: Float = 0f
    
    // Bounce animation duration
    private val BOUNCE_DURATION_MS = 500L

    private val tonePlayer = RisingTonePlayer()

    private val pickRunnable = Runnable {
        performPick()
    }

    private val startSoundRunnable = Runnable {
        tonePlayer.start(TIMEOUT_MS - SOUND_START_DELAY_MS)
    }

    init {
        // Very dark grey background
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        val density = context.resources.displayMetrics.density
        circleRadiusPx = CIRCLE_RADIUS_DP * density

        // Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetTimers()
        tonePlayer.stop()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isFinished) {
            handleFinishedState(event)
            return true
        }

        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val color = generateRandomColor()
                fingers[pointerId] = Finger(event.getX(pointerIndex), event.getY(pointerIndex), color)
                onFingerCountChanged()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    fingers[id]?.let {
                        it.x = event.getX(i)
                        it.y = event.getY(i)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                fingers.remove(pointerId)
                onFingerCountChanged()
            }
            MotionEvent.ACTION_CANCEL -> {
                resetGame()
            }
        }

        invalidate()
        return true
    }

    private fun handleFinishedState(event: MotionEvent) {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == winnerPointerId) {
                    resetGame()
                } else {
                    fingers.remove(pointerId)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                 resetGame()
            }
        }
    }

    private fun onFingerCountChanged() {
        resetTimers()
        tonePlayer.stop()
        
        if (fingers.isNotEmpty()) {
            handler.postDelayed(pickRunnable, TIMEOUT_MS)
            handler.postDelayed(startSoundRunnable, SOUND_START_DELAY_MS)
        }
    }
    
    private fun resetTimers() {
        handler.removeCallbacks(pickRunnable)
        handler.removeCallbacks(startSoundRunnable)
    }

    private fun performPick() {
        tonePlayer.stop()
        if (fingers.isEmpty()) return

        val keys = fingers.keys.toList()
        if (keys.isNotEmpty()) {
            val winnerIndex = random.nextInt(keys.size)
            winnerPointerId = keys[winnerIndex]
            isFinished = true
            
            triggerVibration()
            
            invalidate()
        }
    }
    
    private fun triggerVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun resetGame() {
        resetTimers()
        tonePlayer.stop()
        isFinished = false
        winnerPointerId = null
        fingers.clear()
        invalidate()
    }

    private fun generateRandomColor(): Int {
        var color: Int
        var attempts = 0
        do {
            val r = random.nextInt(256)
            val g = random.nextInt(256)
            val b = random.nextInt(256)
            color = Color.rgb(r, g, b)
            attempts++
        } while ((isColorTooDark(r, g, b) || isColorTooClose(color)) && attempts < 100)
        return color
    }
    
    private fun isColorTooDark(r: Int, g: Int, b: Int): Boolean {
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance < 60 
    }

    private fun isColorTooClose(newColor: Int): Boolean {
        for (finger in fingers.values) {
            if (colorsAreClose(newColor, finger.color)) return true
        }
        return false
    }

    private fun colorsAreClose(c1: Int, c2: Int): Boolean {
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        
        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)
        
        val diff = abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
        return diff < 120
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentTime = System.currentTimeMillis()
        var needsInvalidate = false

        if (isFinished) {
            winnerPointerId?.let { id ->
                fingers[id]?.let { finger ->
                    // Winner is always full scale
                    drawFingerCircle(canvas, finger, 1f)
                }
            }
        } else {
            for (finger in fingers.values) {
                val elapsed = currentTime - finger.startTime
                val scale = getBounceScale(elapsed)
                
                if (elapsed < BOUNCE_DURATION_MS) {
                    needsInvalidate = true
                }
                
                drawFingerCircle(canvas, finger, scale)
            }
        }
        
        if (needsInvalidate) {
            postInvalidateOnAnimation()
        }
    }
    
    private fun getBounceScale(elapsed: Long): Float {
        if (elapsed >= BOUNCE_DURATION_MS) return 1f
        
        val t = elapsed.toFloat() / BOUNCE_DURATION_MS
        // Damped sine wave for bounce effect
        // scale = 1 - exp(-5t) * cos(10t)
        // At t=0, scale=0. At t=1, scale ~= 1
        return (1f - exp(-5f * t) * cos(10f * t))
    }

    private fun drawFingerCircle(canvas: Canvas, finger: Finger, scale: Float) {
        val radius = circleRadiusPx * scale
        
        paint.color = finger.color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(finger.x, finger.y, radius, paint)
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f * scale
        paint.color = Color.WHITE
        canvas.drawCircle(finger.x, finger.y, radius, paint)
    }
    
    private inner class RisingTonePlayer {
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
                     // Ensure bufferSize is reasonable
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
}
