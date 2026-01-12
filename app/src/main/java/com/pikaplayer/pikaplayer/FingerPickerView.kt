package com.pikaplayer.pikaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.MediaPlayer
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
import kotlin.math.sqrt

class FingerPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentGameMode = GameMode.STARTING_PLAYER

    private val fingers = mutableMapOf<Int, Finger>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random()
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator
    
    private var isFinished = false
    private var winnerPointerIds: List<Int>? = null 
    private var winnerColor: Int? = null

    private val TIMEOUT_MS = 5000L
    private val SOUND_START_DELAY_MS = 1000L
    
    private val CIRCLE_RADIUS_DP = 60f
    private var circleRadiusPx: Float = 0f
    
    private val tonePlayer = RisingTonePlayer()
    private var winSoundPlayer: MediaPlayer? = null
    
    private val defaultBackgroundColor = Color.parseColor("#2C2C2C")
    
    private var fireworkStartTime = 0L
    private val fireworkParticles = mutableListOf<Particle>()
    private val NUM_PARTICLES = 100
    private val FIREWORK_DURATION = 2000L

    private var countdownStartTime = 0L

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
        winSoundPlayer?.release()
        winSoundPlayer = null
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
            countdownStartTime = System.currentTimeMillis()
            handler.postDelayed(pickRunnable, TIMEOUT_MS)
            handler.postDelayed(startSoundRunnable, SOUND_START_DELAY_MS)
        }
    }
    
    private fun resetTimers() {
        handler.removeCallbacks(pickRunnable)
        handler.removeCallbacks(startSoundRunnable)
        handler.removeCallbacks(vibrationRunnable)
        countdownStartTime = 0L
        invalidate()
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
            
            if (winSoundPlayer == null) {
                winSoundPlayer = MediaPlayer.create(context, R.raw.good_result)
            }
            winSoundPlayer?.setVolume(0.25f, 0.25f)
            winSoundPlayer?.start()
            
            fireworkStartTime = System.currentTimeMillis()
            fireworkParticles.clear()
            
            winnerPointerIds?.forEach { id ->
                 fingers[id]?.let { finger ->
                     for (i in 0 until NUM_PARTICLES) {
                         val angle = random.nextDouble() * 2 * PI
                         val speed = random.nextFloat() * 20f + 5f
                         val vx = (speed * kotlin.math.cos(angle)).toFloat()
                         val vy = (speed * sin(angle)).toFloat()
                         fireworkParticles.add(Particle(finger.x, finger.y, vx, vy, winnerColor ?: finger.color))
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

    fun resetGamePublic() {
        resetGame()
    }

    private fun resetGame() {
        resetTimers()
        tonePlayer.stop()
        winSoundPlayer?.release()
        winSoundPlayer = null
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
            if (fireworkParticles.isNotEmpty()) {
                val elapsed = currentTime - fireworkStartTime
                if (elapsed < FIREWORK_DURATION) {
                    updateAndDrawFireworks(canvas)
                    postInvalidateOnAnimation()
                } else {
                     fireworkParticles.clear()
                }
            }
        
            winnerPointerIds?.let { ids ->
                ids.forEach { id ->
                    fingers[id]?.let { finger ->
                        // 1. Draw the circle
                        paint.color = finger.color
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(finger.x, finger.y, circleRadiusPx, paint)

                        // 2. Draw full progress ring with finger color
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 15f
                        paint.color = finger.color
                        paint.alpha = 150 // semi-transparent

                        val arcRadius = circleRadiusPx + 20f
                        val rect = RectF(finger.x - arcRadius, finger.y - arcRadius, finger.x + arcRadius, finger.y + arcRadius)
                        canvas.drawArc(rect, -90f, 360f, false, paint)
                        
                        paint.alpha = 255 // Reset alpha

                        // 3. Draw the winner arrow on top
                        drawWinnerArrow(canvas, finger)
                    }
                }
            }
        } else {
            for (finger in fingers.values) {
                val elapsed = currentTime - finger.startTime
                val scale = getBounceScale(elapsed)
                drawFingerCircle(canvas, finger, scale)
            }

            if (countdownStartTime > 0L) {
                val elapsed = currentTime - countdownStartTime
                if (elapsed < TIMEOUT_MS) {
                    val progress = elapsed.toFloat() / TIMEOUT_MS
                    val sweepAngle = 360f * progress

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 15f
                    paint.alpha = 150

                    for (finger in fingers.values) {
                        paint.color = finger.color
                        val arcRadius = circleRadiusPx + 20f
                        val rect = RectF(finger.x - arcRadius, finger.y - arcRadius, finger.x + arcRadius, finger.y + arcRadius)
                        canvas.drawArc(rect, -90f, sweepAngle, false, paint)
                    }
                    paint.alpha = 255
                }
            }

            if (fingers.isNotEmpty()) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawWinnerArrow(canvas: Canvas, finger: Finger) {
        val cx = finger.x
        val cy = finger.y
        val r = circleRadiusPx

        // Find the furthest corner to determine direction
        val corners = listOf(
            Pair(0f, 0f), Pair(width.toFloat(), 0f),
            Pair(0f, height.toFloat()), Pair(width.toFloat(), height.toFloat())
        )
        var furthestCorner = corners[0]
        var maxDistSq = -1f

        for (corner in corners) {
            val distSq = (cx - corner.first) * (cx - corner.first) + (cy - corner.second) * (cy - corner.second)
            if (distSq > maxDistSq) {
                maxDistSq = distSq
                furthestCorner = corner
            }
        }

        // Vector from corner to circle
        val dirX = cx - furthestCorner.first
        val dirY = cy - furthestCorner.second
        val dist = sqrt(dirX * dirX + dirY * dirY)
        val normDx = dirX / dist
        val normDy = dirY / dist

        // Define arrow dimensions
        val gap = 40f 
        val headLength = r * 0.8f
        val bodyLength = r * 1.2f
        val headWidth = r * 0.8f // This is half-width
        val bodyWidth = r * 0.4f // This is half-width

        // Tip of the arrow (points towards the circle)
        val tipX = cx - normDx * (r + gap)
        val tipY = cy - normDy * (r + gap)

        // Point where head meets body
        val headBaseX = tipX - normDx * headLength
        val headBaseY = tipY - normDy * headLength

        // Back of the arrow
        val arrowBackX = headBaseX - normDx * bodyLength
        val arrowBackY = headBaseY - normDy * bodyLength

        // Perpendicular vector for width calculations
        val perpX = -normDy
        val perpY = normDx

        val path = Path().apply {
            moveTo(tipX, tipY)
            lineTo(headBaseX + perpX * headWidth, headBaseY + perpY * headWidth)
            lineTo(headBaseX + perpX * bodyWidth, headBaseY + perpY * bodyWidth)
            lineTo(arrowBackX + perpX * bodyWidth, arrowBackY + perpY * bodyWidth)
            lineTo(arrowBackX - perpX * bodyWidth, arrowBackY - perpY * bodyWidth)
            lineTo(headBaseX - perpX * bodyWidth, headBaseY - perpY * bodyWidth)
            lineTo(headBaseX - perpX * headWidth, headBaseY - perpY * headWidth)
            close()
        }

        paint.color = finger.color
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }
    
    private fun updateAndDrawFireworks(canvas: Canvas) {
        val iterator = fireworkParticles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.5f
            
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
    }
}
