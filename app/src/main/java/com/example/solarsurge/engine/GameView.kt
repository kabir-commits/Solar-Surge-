package com.example.solarsurge.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.solarsurge.audio.SoundManager
import com.example.solarsurge.model.Obstacle
import com.example.solarsurge.model.ObstacleManager
import com.example.solarsurge.model.ParticleSystem
import com.example.solarsurge.model.StarPlayer
import com.example.solarsurge.model.Starfield
import kotlin.math.sin
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val density = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("solar_surge_prefs", Context.MODE_PRIVATE)

    // Vibrator system
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Audio system
    val soundManager = SoundManager(context)
    val backgroundMusicManager = com.example.solarsurge.audio.BackgroundMusicManager(context)

    // Entities
    private val starfield = Starfield()
    val particleSystem = ParticleSystem(600)
    val player = StarPlayer(density)
    private val obstacleManager = ObstacleManager(density)

    // Game loop thread
    private var gameLoopThread: GameLoopThread? = null

    // Game states
    enum class State {
        IDLE,
        PLAYING,
        FREEZE_FRAME,
        SUPERNOVA,
        DEAD
    }

    var currentState = State.IDLE
        private set

    // Scores
    var currentScore: Int = 0
        private set
    var highScore: Int = prefs.getInt("high_score", 0)
        private set
    private var isNewHighScore: Boolean = false
    private var highScoreBannerTimer: Float = 0f
    private var gravitySurgeBannerTimer: Float = 0f

    // Juiciness & Polish
    private var screenShakeTimer: Float = 0f
    private val maxShakePx = 18f * (density / 2.75f).coerceAtLeast(1f)
    private var freezeFrameTimer: Float = 0f
    private var supernovaTimer: Float = 0f
    private val supernovaDuration: Float = 0.85f

    // Boundary barrier height
    private val barrierHeight = 22f * density

    // Paints
    private val backgroundPaint = Paint().apply {
        color = 0xFF070913.toInt()
        style = Paint.Style.FILL
    }
    private val barrierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barrierGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    // HUD Text Paints
    private val scoreBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x2200F3FF.toInt()
        textSize = 100f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val scoreBgGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x18FFD700.toInt()
        textSize = 100f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(25f * density, 0f, 0f, 0xFF00F3FF.toInt())
    }

    private val hudSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textSize = 15f * density
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val hudGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD700.toInt()
        textSize = 16f * density
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(10f * density, 0f, 0f, 0xFFFF007F.toInt())
    }

    // Overlay Paints
    private val overlayPaint = Paint().apply {
        color = 0xCC070913.toInt()
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00F3FF.toInt()
        textSize = 42f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(18f * density, 0f, 0f, 0xFFFF007F.toInt())
    }
    private val titleCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD700.toInt()
        textSize = 17f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 19f * density
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(12f * density, 0f, 0f, 0xFF00F3FF.toInt())
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE610142A.toInt()
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00F3FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    // Sound toggle button rect in top-left
    private val soundBtnRect = RectF(16f * density, 24f * density, 64f * density, 72f * density)
    private val soundIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        color = 0xFF00F3FF.toInt()
    }

    private var globalAnimTime: Float = 0f
    private var surfaceReady = false

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
        backgroundMusicManager.play()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        starfield.initIfNeeded(w, h)
        obstacleManager.init(w, h)
        if (currentState == State.IDLE) {
            player.reset(w * 0.22f, h * 0.5f)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    private fun startThread() {
        stopThread()
        gameLoopThread = GameLoopThread(holder, this).apply {
            isRunning = true
            start()
        }
    }

    private fun stopThread() {
        gameLoopThread?.let {
            it.isRunning = false
            var retry = true
            while (retry) {
                try {
                    it.join(300)
                    retry = false
                } catch (_: InterruptedException) { }
            }
        }
        gameLoopThread = null
    }

    fun pause() {
        stopThread()
        backgroundMusicManager.pause()
    }

    fun resume() {
        if (surfaceReady && gameLoopThread == null) {
            startThread()
        }
        backgroundMusicManager.resume()
    }

    // -------------------------------------------------------------
    // Input Handling
    // -------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ex = event.x
        val ey = event.y

        // Check sound mute button click
        if (event.action == MotionEvent.ACTION_DOWN && soundBtnRect.contains(ex, ey)) {
            soundManager.isMuted = !soundManager.isMuted
            backgroundMusicManager.setMuted(soundManager.isMuted)
            return true
        }

        when (currentState) {
            State.IDLE -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startGame()
                }
            }

            State.PLAYING -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        player.isHolding = true
                        soundManager.playGravitonPulse()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        player.isHolding = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        player.isHolding = false
                    }
                }
            }

            State.DEAD -> {
                // Instant restart loop (< 150ms, zero lag, no Activity restart)
                if (event.action == MotionEvent.ACTION_DOWN) {
                    restartGame()
                }
            }

            State.FREEZE_FRAME, State.SUPERNOVA -> {
                // Brief death animation active
            }
        }
        return true
    }

    private fun startGame() {
        currentState = State.PLAYING
        currentScore = 0
        isNewHighScore = false
        highScoreBannerTimer = 0f
        gravitySurgeBannerTimer = 0f
        screenShakeTimer = 0f
        particleSystem.clear()
        obstacleManager.reset()

        val startX = (width.toFloat() * 0.22f).coerceAtLeast(150f)
        val startY = height.toFloat() * 0.5f
        player.reset(startX, startY)
        player.isHolding = true // Initial touch starts graviton thrust

        soundManager.playReignite()
    }

    private fun restartGame() {
        startGame()
    }

    // -------------------------------------------------------------
    // Physics & Game Loop Update
    // -------------------------------------------------------------
    fun update(dt: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        globalAnimTime += dt
        particleSystem.update(dt)

        if (highScoreBannerTimer > 0f) {
            highScoreBannerTimer -= dt
        }
        if (gravitySurgeBannerTimer > 0f) {
            gravitySurgeBannerTimer -= dt
        }

        when (currentState) {
            State.IDLE -> {
                // Star gently floats in place with subtle vertical buoyancy sine wave
                val idleY = (h * 0.5f) + sin(globalAnimTime * 3f) * (20f * density)
                player.y = idleY
                player.update(dt, particleSystem)
                starfield.update(dt, player.vx * 0.5f, w, h)
            }

            State.PLAYING -> {
                player.update(dt, particleSystem)
                starfield.update(dt, player.vx, w, h)

                // Update obstacles & detect scoring
                obstacleManager.update(dt, player.vx, player.x, currentScore) { scoredObs: Obstacle ->
                    currentScore++
                    player.updateScoreSpeed(currentScore)
                    soundManager.playScore()
                    particleSystem.emitScoreSparks(player.x, player.y, 16)

                    // Check for gravity surge threshold (every 50 points)
                    if (currentScore % 50 == 0 && currentScore > 0) {
                        gravitySurgeBannerTimer = 2.2f
                        particleSystem.emitScoreSparks(player.x, player.y, 35)
                        soundManager.playGravitonPulse()
                    }

                    // Check for new high score
                    if (currentScore > highScore) {
                        val wasBeaten = !isNewHighScore
                        highScore = currentScore
                        isNewHighScore = true
                        prefs.edit().putInt("high_score", highScore).apply()
                        if (wasBeaten && currentScore > 1) {
                            highScoreBannerTimer = 2.4f
                            soundManager.playHighScore()
                            particleSystem.emitScoreSparks(player.x, player.y, 40)
                        }
                    }
                }

                // Check Cosmic Boundary Collision (Ceiling / Floor)
                val hitCeiling = player.y - player.hitboxRadius <= barrierHeight
                val hitFloor = player.y + player.hitboxRadius >= (h - barrierHeight)

                // Check Obstacle Collision
                val hitObstacle = obstacleManager.checkCollision(player.x, player.y, player.hitboxRadius)

                if (hitCeiling || hitFloor || hitObstacle) {
                    triggerDeath()
                }
            }

            State.FREEZE_FRAME -> {
                freezeFrameTimer -= dt
                if (freezeFrameTimer <= 0f) {
                    // Enter supernova burst
                    currentState = State.SUPERNOVA
                    supernovaTimer = supernovaDuration
                    particleSystem.emitSupernova(player.x, player.y)
                }
            }

            State.SUPERNOVA -> {
                supernovaTimer -= dt
                if (screenShakeTimer > 0f) {
                    screenShakeTimer -= dt
                }
                player.update(dt, particleSystem)
                if (supernovaTimer <= 0f) {
                    currentState = State.DEAD
                }
            }

            State.DEAD -> {
                if (screenShakeTimer > 0f) {
                    screenShakeTimer -= dt
                }
            }
        }
    }

    private fun triggerDeath() {
        currentState = State.FREEZE_FRAME
        freezeFrameTimer = 0.040f // 40ms freeze frame
        screenShakeTimer = 0.25f // 250ms screen shake
        player.startDeath()

        // Haptic feedback: Trigger short impulse vibration (50ms)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Exception) { }

        soundManager.playExplosion()
    }

    // -------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------
    fun render(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.save()

        // Screen Shake: Translate Canvas randomly by ±18px on player impact
        if (screenShakeTimer > 0f) {
            val shakeProgress = (screenShakeTimer / 0.25f).coerceIn(0f, 1f)
            val currentMaxShake = maxShakePx * shakeProgress
            val shakeX = (Random.nextFloat() * 2f - 1f) * currentMaxShake
            val shakeY = (Random.nextFloat() * 2f - 1f) * currentMaxShake
            canvas.translate(shakeX, shakeY)
        }

        // 1. Space Background (#070913)
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        // 2. Parallax Starfield
        starfield.draw(canvas)

        // 3. In-Game Background Score HUD (Zero cluttered UI boxes)
        if (currentState == State.PLAYING || currentState == State.FREEZE_FRAME || currentState == State.SUPERNOVA) {
            val scoreStr = currentScore.toString()
            val scoreY = h * 0.42f
            canvas.drawText(scoreStr, w * 0.5f, scoreY, scoreBgGlow)
            canvas.drawText(scoreStr, w * 0.5f, scoreY, scoreBgPaint)
        }

        // 4. Obstacles
        obstacleManager.draw(canvas)

        // 5. Particles
        particleSystem.draw(canvas)

        // 6. Star Player
        player.draw(canvas)

        // 7. Cosmic Boundary Containment Barriers (Top and Bottom)
        drawCosmicBarriers(canvas, w, h)

        // 8. Subtle Top-Right High Score Tracker
        drawHighScoreHUD(canvas, w)

        // 9. Sound Mute Toggle Icon
        drawSoundToggle(canvas)

        // 10. Floating High Score or Gravity Surge Celebration Banner
        if (highScoreBannerTimer > 0f && currentState == State.PLAYING) {
            drawHighScoreBreakBanner(canvas, w, h)
        } else if (gravitySurgeBannerTimer > 0f && currentState == State.PLAYING) {
            drawGravitySurgeBanner(canvas, w, h)
        }

        // 11. Overlays (Idle / Game Over)
        if (currentState == State.IDLE) {
            drawIdleOverlay(canvas, w, h)
        } else if (currentState == State.DEAD) {
            drawGameOverOverlay(canvas, w, h)
        }

        canvas.restore()
    }

    private fun drawCosmicBarriers(canvas: Canvas, w: Float, h: Float) {
        // Top Barrier
        barrierPaint.color = 0x88001026.toInt()
        canvas.drawRect(0f, 0f, w, barrierHeight, barrierPaint)

        barrierGlowPaint.color = 0xFF00F3FF.toInt()
        val topWave = sin(globalAnimTime * 4f) * (2f * density)
        canvas.drawLine(0f, barrierHeight + topWave, w, barrierHeight + topWave, barrierGlowPaint)

        // Bottom Barrier
        val bottomY = h - barrierHeight
        canvas.drawRect(0f, bottomY, w, h, barrierPaint)

        barrierGlowPaint.color = 0xFFFF007F.toInt()
        val bottomWave = sin(globalAnimTime * 4f + 2f) * (2f * density)
        canvas.drawLine(0f, bottomY + bottomWave, w, bottomY + bottomWave, barrierGlowPaint)
    }

    private fun drawHighScoreHUD(canvas: Canvas, w: Float) {
        val marginX = w - (20f * density)
        val textY = 46f * density
        canvas.drawText("BEST  ★ $highScore", marginX, textY, if (isNewHighScore) hudGoldPaint else hudSmallPaint)
    }

    private fun drawSoundToggle(canvas: Canvas) {
        val cx = soundBtnRect.centerX()
        val cy = soundBtnRect.centerY()
        val r = 12f * density

        // Speaker icon body
        soundIconPaint.color = if (soundManager.isMuted) 0x66FFFFFF.toInt() else 0xFF00F3FF.toInt()
        canvas.drawCircle(cx, cy, r * 1.5f, barrierPaint)

        // Speaker cone
        canvas.drawLine(cx - 6f * density, cy - 4f * density, cx - 2f * density, cy - 4f * density, soundIconPaint)
        canvas.drawLine(cx - 2f * density, cy - 7f * density, cx + 3f * density, cy - 7f * density, soundIconPaint)
        canvas.drawLine(cx - 6f * density, cy + 4f * density, cx - 2f * density, cy + 4f * density, soundIconPaint)
        canvas.drawLine(cx - 2f * density, cy + 7f * density, cx + 3f * density, cy + 7f * density, soundIconPaint)
        canvas.drawLine(cx - 6f * density, cy - 4f * density, cx - 6f * density, cy + 4f * density, soundIconPaint)
        canvas.drawLine(cx + 3f * density, cy - 7f * density, cx + 3f * density, cy + 7f * density, soundIconPaint)

        if (soundManager.isMuted) {
            // Diagonal strike
            soundIconPaint.color = 0xFFFF007F.toInt()
            canvas.drawLine(cx - 8f * density, cy - 8f * density, cx + 8f * density, cy + 8f * density, soundIconPaint)
        } else {
            // Sound wave arc
            canvas.drawArc(
                cx + 2f * density, cy - 6f * density,
                cx + 9f * density, cy + 6f * density,
                -60f, 120f, false, soundIconPaint
            )
        }
    }

    private fun drawHighScoreBreakBanner(canvas: Canvas, w: Float, h: Float) {
        val bannerY = h * 0.24f
        val pulse = 1f + sin(globalAnimTime * 8f) * 0.08f
        canvas.save()
        canvas.scale(pulse, pulse, w * 0.5f, bannerY)
        hudGoldPaint.textSize = 24f * density
        hudGoldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("⚡ NEW HIGH SCORE! ⚡", w * 0.5f, bannerY, hudGoldPaint)
        hudGoldPaint.textSize = 16f * density
        hudGoldPaint.textAlign = Paint.Align.RIGHT
        canvas.restore()
    }

    private fun drawGravitySurgeBanner(canvas: Canvas, w: Float, h: Float) {
        val bannerY = h * 0.24f
        val pulse = 1f + sin(globalAnimTime * 8f) * 0.08f
        canvas.save()
        canvas.scale(pulse, pulse, w * 0.5f, bannerY)
        val surgeTier = currentScore / 50
        titlePaint.textSize = 20f * density
        titlePaint.color = 0xFF00F3FF.toInt()
        titlePaint.setShadowLayer(14f * density, 0f, 0f, 0xFFFF007F.toInt())
        canvas.drawText("⚡ GRAVITY SURGE: TIER $surgeTier ⚡", w * 0.5f, bannerY, titlePaint)
        titlePaint.textSize = 42f * density
        canvas.restore()
    }

    private fun drawIdleOverlay(canvas: Canvas, w: Float, h: Float) {
        // Semi-transparent dark frosted overlay (#CC070913)
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        val centerY = h * 0.36f

        // Title: SOLAR SURGE with Neon Glow
        canvas.drawText("SOLAR SURGE", w * 0.5f, centerY, titlePaint)
        canvas.drawText("SOLAR SURGE", w * 0.5f, centerY, titleCorePaint)

        canvas.drawText("ARCADE GRAVITY RUNNER", w * 0.5f, centerY + 36f * density, subtitlePaint)

        // Instruction Card
        val cardLeft = w * 0.12f
        val cardRight = w * 0.88f
        val cardTop = centerY + 70f * density
        val cardBottom = cardTop + 130f * density
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        val corner = 16f * density

        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, cardStrokePaint)

        val instructPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val cyanBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00F3FF.toInt()
            textSize = 15f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawText("✦ TOUCH & HOLD : DIVE (GRAVITON PULL)", w * 0.5f, cardTop + 38f * density, cyanBold)
        canvas.drawText("✦ RELEASE : SURGE UPWARD (BUOYANCY)", w * 0.5f, cardTop + 68f * density, cyanBold)
        canvas.drawText("Navigate through Black Holes, Lasers & Asteroids", w * 0.5f, cardTop + 102f * density, instructPaint)

        // Pulsing Start Prompt
        val pulseAlpha = ((sin(globalAnimTime * 5f) + 1f) * 0.5f * 175 + 80).toInt()
        promptPaint.alpha = pulseAlpha
        canvas.drawText("TOUCH SCREEN TO LAUNCH", w * 0.5f, h * 0.78f, promptPaint)
    }

    private fun drawGameOverOverlay(canvas: Canvas, w: Float, h: Float) {
        // Semi-transparent dark frosted overlay (#CC070913)
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        val centerY = h * 0.34f

        titlePaint.color = 0xFFFF007F.toInt()
        titlePaint.setShadowLayer(22f * density, 0f, 0f, 0xFF00F3FF.toInt())
        canvas.drawText("SUPERNOVA", w * 0.5f, centerY, titlePaint)
        canvas.drawText("SUPERNOVA", w * 0.5f, centerY, titleCorePaint)

        // Score Card
        val cardLeft = w * 0.12f
        val cardRight = w * 0.88f
        val cardTop = centerY + 35f * density
        val cardBottom = cardTop + 160f * density
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        val corner = 18f * density

        cardStrokePaint.color = if (isNewHighScore) 0xFFFFD700.toInt() else 0xFFFF007F.toInt()
        canvas.drawRoundRect(cardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, cardStrokePaint)

        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawText("FINAL SCORE", w * 0.32f, cardTop + 45f * density, statLabelPaint)
        canvas.drawText(currentScore.toString(), w * 0.32f, cardTop + 90f * density, statValuePaint)

        canvas.drawText("BEST SCORE", w * 0.68f, cardTop + 45f * density, statLabelPaint)
        statValuePaint.color = 0xFFFFD700.toInt()
        canvas.drawText(highScore.toString(), w * 0.68f, cardTop + 90f * density, statValuePaint)

        if (isNewHighScore) {
            val recPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFD700.toInt()
                textSize = 14f * density
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setShadowLayer(10f * density, 0f, 0f, 0xFFFF007F.toInt())
            }
            canvas.drawText("✦ NEW PERSONAL RECORD REACHED ✦", w * 0.5f, cardTop + 132f * density, recPaint)
        }

        // Custom Overlay Prompt: "HOLD SCREEN TO REIGNITE"
        val pulseAlpha = ((sin(globalAnimTime * 6f) + 1f) * 0.5f * 185 + 70).toInt()
        promptPaint.alpha = pulseAlpha
        promptPaint.color = 0xFF00F3FF.toInt()
        promptPaint.textSize = 20f * density
        canvas.drawText("HOLD SCREEN TO REIGNITE", w * 0.5f, h * 0.76f, promptPaint)

        val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88FFFFFF.toInt()
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("Instant restart enabled", w * 0.5f, h * 0.80f, subtextPaint)
    }
}
