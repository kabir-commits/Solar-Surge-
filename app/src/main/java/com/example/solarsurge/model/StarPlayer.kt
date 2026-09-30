package com.example.solarsurge.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StarPlayer(
    val density: Float
) {
    // Proportional dimensions
    val baseVisualRadius = 28f * density
    val hitboxRadius = 12f * density

    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 450f * (density / 2.75f).coerceAtLeast(0.85f)
    var vy: Float = 0f

    // Physical acceleration parameters (scaled)
    private val densityScale = (density / 2.75f).coerceIn(0.8f, 1.4f)
    val baseBuoyancyAcc = 1800.0f * densityScale
    val baseGravitonAcc = 3200.0f * densityScale
    val baseTerminalVelocity = 1100.0f * densityScale
    private val baseVx = 450.0f * densityScale
    private val vxPer10Pts = 15.0f * densityScale

    var buoyancyAcc = baseBuoyancyAcc
        private set
    var gravitonAcc = baseGravitonAcc
        private set
    var terminalVelocity = baseTerminalVelocity
        private set

    var isAlive: Boolean = true
    var isHolding: Boolean = false
    var currentVisualRadius: Float = baseVisualRadius

    // Pulsing and animation
    private var animTime: Float = 0f
    private var contractionTime: Float = 0f
    private val contractionDuration: Float = 0.12f

    // Rendering paints
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val coronaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var cachedRadius = -1f
    private var cachedAuraShader: RadialGradient? = null

    fun reset(startX: Float, startY: Float) {
        x = startX
        y = startY
        vy = 0f
        vx = baseVx
        buoyancyAcc = baseBuoyancyAcc
        gravitonAcc = baseGravitonAcc
        terminalVelocity = baseTerminalVelocity
        isAlive = true
        isHolding = false
        currentVisualRadius = baseVisualRadius
        animTime = 0f
        contractionTime = 0f
    }

    fun setGravityRamp(multiplier: Float) {
        buoyancyAcc = baseBuoyancyAcc * multiplier
        gravitonAcc = baseGravitonAcc * multiplier
        terminalVelocity = baseTerminalVelocity * (1.0f + (multiplier - 1.0f) * 0.5f)
    }

    fun updateScoreSpeed(score: Int) {
        vx = baseVx + (score / 10) * vxPer10Pts
    }

    fun startDeath() {
        isAlive = false
        contractionTime = 0f
    }

    fun update(dt: Float, particleSystem: ParticleSystem) {
        animTime += dt

        if (!isAlive) {
            // Contraction to 0 radius during death burst
            contractionTime += dt
            val progress = (contractionTime / contractionDuration).coerceIn(0f, 1f)
            currentVisualRadius = baseVisualRadius * (1f - progress)
            return
        }

        // Custom Gravity Loop
        if (isHolding) {
            // Downward graviton pull
            vy += gravitonAcc * dt
        } else {
            // Upward buoyancy acceleration
            vy -= buoyancyAcc * dt
        }

        // Clamp to terminal velocity
        vy = vy.coerceIn(-terminalVelocity, terminalVelocity)

        // Integrate vertical position
        y += vy * dt

        // Emit continuous particle tail behind star movement vector
        particleSystem.emitTrail(x - currentVisualRadius * 0.7f, y, vx, isHolding)
    }

    fun draw(canvas: Canvas) {
        if (currentVisualRadius <= 0.5f) return

        val pulse = sin(animTime * 6f) * (2f * density)
        val renderRadius = currentVisualRadius + pulse

        // 1. Outer Radial Glow (Coronal Aura)
        if (cachedAuraShader == null || cachedRadius != renderRadius) {
            cachedRadius = renderRadius
            val colors = intArrayOf(
                0xFFFFFFFF.toInt(), // Pure white core
                0xFFFFD700.toInt(), // Electric Gold
                0xFF00F3FF.toInt(), // Neon Cyan
                0x00070913          // Transparent space
            )
            val stops = floatArrayOf(0f, 0.28f, 0.65f, 1.0f)
            cachedAuraShader = RadialGradient(
                0f, 0f, renderRadius * 1.55f,
                colors, stops,
                Shader.TileMode.CLAMP
            )
        }

        canvas.save()
        canvas.translate(x, y)

        // Draw outer aura
        coronaPaint.shader = cachedAuraShader
        canvas.drawCircle(0f, 0f, renderRadius * 1.55f, coronaPaint)

        // 2. Coronal Flare Rays (Cosmic Neon Arcs)
        val rayCount = 6
        flarePaint.strokeWidth = 2.5f * density
        for (i in 0 until rayCount) {
            val angle = (animTime * 1.8f) + (i * (2f * PI.toFloat() / rayCount))
            val startDist = renderRadius * 0.75f
            val endDist = renderRadius * (1.25f + sin(animTime * 8f + i) * 0.15f)
            val sx = cos(angle) * startDist
            val sy = sin(angle) * startDist
            val ex = cos(angle) * endDist
            val ey = sin(angle) * endDist

            flarePaint.color = if (i % 2 == 0) 0xFF00F3FF.toInt() else 0xFFFF007F.toInt()
            flarePaint.alpha = 180
            canvas.drawLine(sx, sy, ex, ey, flarePaint)
        }

        // 3. Middle Gold Corona
        coronaPaint.shader = null
        coronaPaint.color = if (isHolding) 0xFFFF007F.toInt() else 0xFFFFD700.toInt()
        coronaPaint.alpha = 220
        canvas.drawCircle(0f, 0f, renderRadius * 0.65f, coronaPaint)

        // 4. White-Hot Nuclear Core
        corePaint.alpha = 255
        canvas.drawCircle(0f, 0f, hitboxRadius, corePaint)

        canvas.restore()
    }
}
