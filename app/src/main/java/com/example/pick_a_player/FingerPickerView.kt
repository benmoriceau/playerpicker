package com.example.pick_a_player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.sin
import kotlin.math.PI

class FingerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentGameMode = GameMode.STARTING_PLAYER

    private val fingers = mutableMapOf<Int, Finger>() // pointerId -> Finger
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator
    
    private var isFinished = false
    private var winnerPointerIds: List<Int>? = null 
    private var winnerColor: Int? = null

    // 5 seconds timeout
    private val TIMEOUT_MS = 5000L
    private val SOUND_START_DELAY_MS = 1000L
    
    // Circle radius in dp
    private val CIRCLE_RADIUS_DP = 60f
    private var circleRadiusPx: Float = 0f
    
    private val tonePlayer = RisingTonePlayer()
    
    // Background colors
    private val defaultBackgroundColor = Color.parseColor("#2C2C2C")
    private var currentBackgroundColor = defaultBackgroundColor
    
    // Firework animation
    private var fireworkCenter: Finger? = null
    private var fireworkStartTime = 0L
    private val fireworkParticles = mutableListOf<Particle>()
    private val NUM_PARTICLES = 100
    private val FIREWORK_DURATION = 2000L // ms

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var color: Int,
        var alpha: Int = 255
    )

    private var modeChangeListener: ((GameMode) -> Unit)? = null
    
    private var vibrationStartTime = 0L
    private val vibrationRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val elapsed = currentTime - vibrationStartTime
            val remaining = (TIMEOUT_MS - SOUND_START_DELAY_MS) - elapsed
            
            if (remaining <= 0) return 
            
            val progress = 1f - (remaining.toFloat() / (TIMEOUT_MS - SOUND_START_DELAY_MS))
            val delay = (500 - (450 * progress)).toLong().coerceAtLeast(50)
            
            triggerTickVibration()
            
            handler.postDelayed(this, delay)
        }
    }

    private val pickRunnable = Runnable {
        performPick()
    }

    private val startSoundRunnable = Runnable {
        tonePlayer.start(TIMEOUT_MS - SOUND_START_DELAY_MS)
        startProgressiveVibration()
    }

    init {
        setBackgroundColor(defaultBackgroundColor)
        val density = context.resources.displayMetrics.density
        circleRadiusPx = CIRCLE_RADIUS_DP * density

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun setGameMode(mode: GameMode) {
        currentGameMode = mode
        resetGame()
        modeChangeListener?.invoke(mode)
    }
    
    fun setOnGameModeChangeListener(listener: (GameMode) -> Unit) {
        modeChangeListener = listener
        listener(currentGameMode)
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
                val color = if (currentGameMode == GameMode.GROUP) Color.LTGRAY else generateRandomColor()
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
                if (winnerPointerIds?.contains(pointerId) == true) {
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
        
        if (fingers.size >= 2) {
            handler.postDelayed(pickRunnable, TIMEOUT_MS)
            handler.postDelayed(startSoundRunnable, SOUND_START_DELAY_MS)
        }
    }
    
    private fun resetTimers() {
        handler.removeCallbacks(pickRunnable)
        handler.removeCallbacks(startSoundRunnable)
        handler.removeCallbacks(vibrationRunnable)
    }
    
    private fun startProgressiveVibration() {
        vibrationStartTime = System.currentTimeMillis()
        handler.post(vibrationRunnable)
    }

    private fun performPick() {
        tonePlayer.stop()
        handler.removeCallbacks(vibrationRunnable)
        
        if (fingers.size < 2) return

        val keys = fingers.keys.toList()
        if (keys.isNotEmpty()) {
            isFinished = true
            
            if (currentGameMode == GameMode.GROUP && keys.size > 1) {
                performGroupPick(keys)
            } else {
                performSinglePick(keys)
            }

            triggerVibration()
            
            // Start Firework
            winnerColor?.let { color ->
                 // Center is average of winners or just screen center? 
                 // User said "firework of the select circle color".
                 // Let's use the winner(s) positions as emitters or just the center of screen?
                 // "filling of the background ... to something that look like a firework".
                 // Let's emit from the winner finger(s).
                 
                 fireworkStartTime = System.currentTimeMillis()
                 fireworkParticles.clear()
                 
                 winnerPointerIds?.forEach { id ->
                     fingers[id]?.let { finger ->
                         for (i in 0 until NUM_PARTICLES) {
                             val angle = random.nextDouble() * 2 * PI
                             val speed = random.nextFloat() * 20f + 5f
                             val vx = (speed * kotlin.math.cos(angle)).toFloat()
                             val vy = (speed * sin(angle)).toFloat()
                             fireworkParticles.add(Particle(finger.x, finger.y, vx, vy, color))
                         }
                     }
                 }
            }
            
            invalidate()
        }
    }
    
    private fun performSinglePick(keys: List<Int>) {
        val winnerIndex = random.nextInt(keys.size)
        val winnerId = keys[winnerIndex]
        winnerPointerIds = listOf(winnerId)
        winnerColor = fingers[winnerId]?.color
    }
    
    private fun performGroupPick(keys: List<Int>) {
        val shuffledKeys = keys.shuffled(random)
        val mid = shuffledKeys.size / 2
        
        val group1Keys = shuffledKeys.subList(0, mid)
        val group2Keys = shuffledKeys.subList(mid, shuffledKeys.size)
        
        val color1 = Color.CYAN
        val color2 = Color.MAGENTA
        
        group1Keys.forEach { id -> 
            val finger = fingers[id]
            finger?.groupColor = color1 
            finger?.color = color1 
        }
        group2Keys.forEach { id -> 
            val finger = fingers[id]
            finger?.groupColor = color2 
            finger?.color = color2 
        }
        
        val winningGroupIndex = random.nextInt(2)
        if (winningGroupIndex == 0) {
            winnerPointerIds = group1Keys
            winnerColor = color1
        } else {
            winnerPointerIds = group2Keys
            winnerColor = color2
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
    
    private fun triggerTickVibration() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)) 
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun resetGame() {
        resetTimers()
        tonePlayer.stop()
        isFinished = false
        winnerPointerIds = null
        winnerColor = null
        fingers.clear()
        fireworkParticles.clear()
        
        setBackgroundColor(defaultBackgroundColor)
        
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

        if (isFinished) {
            // Draw Fireworks
            if (fireworkParticles.isNotEmpty()) {
                val elapsed = currentTime - fireworkStartTime
                if (elapsed < FIREWORK_DURATION) {
                    updateAndDrawFireworks(canvas)
                    postInvalidateOnAnimation()
                } else {
                     // Keep last frame or clear? 
                     // If we stop drawing, it disappears. 
                     // Maybe fill background with static color at the end or fade out?
                     // "filling of the background ... to something that look like a firework"
                     // Usually fireworks fade out.
                     // Let's clear particles to stop drawing them.
                     fireworkParticles.clear()
                     // If particles are gone, we might want to set background to winner color or keep it dark?
                     // Prompt: "instead of a basic fill of the background".
                     // So maybe we just return to dark or keep the particles?
                     // I'll clear them, so it goes back to dark background with just the winner circle.
                }
            }
        
            winnerPointerIds?.let { ids ->
                ids.forEach { id ->
                    fingers[id]?.let { finger ->
                        paint.color = finger.color
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(finger.x, finger.y, circleRadiusPx, paint)
                        
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 15f
                        paint.color = defaultBackgroundColor
                        canvas.drawCircle(finger.x, finger.y, circleRadiusPx, paint)
                    }
                }
            }
        } else {
            for (finger in fingers.values) {
                val elapsed = currentTime - finger.startTime
                val scale = getBounceScale(elapsed)
                drawFingerCircle(canvas, finger, scale)
            }
            if (fingers.isNotEmpty()) {
                postInvalidateOnAnimation()
            }
        }
    }
    
    private fun updateAndDrawFireworks(canvas: Canvas) {
        val iterator = fireworkParticles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.5f // Gravity? Maybe slight gravity
            
            // Fade out
            p.alpha -= 2
            if (p.alpha <= 0) {
                iterator.remove()
                continue
            }
            
            paint.color = p.color
            paint.alpha = p.alpha
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, 10f, paint)
        }
        // Reset paint alpha
        paint.alpha = 255
    }
    
    private fun getBounceScale(elapsed: Long): Float {
        return 1f + 0.05f * sin(2 * PI * elapsed / 600.0).toFloat()
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
}
