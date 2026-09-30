package com.example.solarsurge.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var radius: Float = 4f
    var initialRadius: Float = 4f
    var color: Int = Color.WHITE
    var life: Float = 0f
    var maxLife: Float = 1f
    var isActive: Boolean = false

    fun reset(
        startX: Float,
        startY: Float,
        velX: Float,
        velY: Float,
        rad: Float,
        pColor: Int,
        duration: Float
    ) {
        x = startX
        y = startY
        vx = velX
        vy = velY
        radius = rad
        initialRadius = rad
        color = pColor
        life = duration
        maxLife = duration
        isActive = true
    }

    fun update(dt: Float): Boolean {
        if (!isActive) return false
        life -= dt
        if (life <= 0f) {
            isActive = false
            return false
        }
        x += vx * dt
        y += vy * dt
        // Apply cosmic deceleration/drag
        vx *= (1f - 0.4f * dt)
        vy *= (1f - 0.4f * dt)
        // Shrink particle size over lifespan
        val ratio = (life / maxLife).coerceIn(0f, 1f)
        radius = initialRadius * ratio
        return true
    }
}

class ParticleSystem(maxParticles: Int = 600) {
    private val pool = Array(maxParticles) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Color definitions
    private val colorCyan = 0xFF00F3FF.toInt()
    private val colorMagenta = 0xFFFF007F.toInt()
    private val colorGold = 0xFFFFD700.toInt()
    private val colorWhite = 0xFFFFFFFF.toInt()

    private fun spawnParticle(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        radius: Float,
        color: Int,
        duration: Float
    ) {
        for (p in pool) {
            if (!p.isActive) {
                p.reset(x, y, vx, vy, radius, color, duration)
                break
            }
        }
    }

    fun emitTrail(x: Float, y: Float, speedX: Float, isGraviton: Boolean) {
        // Emit 2-3 trailing particles
        val count = if (isGraviton) 4 else 2
        for (i in 0 until count) {
            val offsetX = (Random.nextFloat() * 8f - 4f)
            val offsetY = (Random.nextFloat() * 14f - 7f)
            val angle = PI.toFloat() + (Random.nextFloat() * 0.7f - 0.35f)
            val speed = Random.nextFloat() * 120f + 60f
            val pVx = -speedX * 0.35f + cos(angle) * speed
            val pVy = sin(angle) * speed * 0.6f

            val color = if (isGraviton) {
                // Graviton thrust flares magenta, gold, and white
                when (Random.nextInt(3)) {
                    0 -> colorMagenta
                    1 -> colorGold
                    else -> colorWhite
                }
            } else {
                // Buoyant drift flares cyan, gold, white
                when (Random.nextInt(3)) {
                    0 -> colorCyan
                    1 -> colorGold
                    else -> colorWhite
                }
            }
            val radius = Random.nextFloat() * 4.5f + 2f
            val life = Random.nextFloat() * 0.45f + 0.25f
            spawnParticle(x + offsetX, y + offsetY, pVx, pVy, radius, color, life)
        }
    }

    /**
     * Supernova Death Burst: Upon collision, emit 150 bright particle sparks spreading in 360 degrees
     */
    fun emitSupernova(originX: Float, originY: Float) {
        val totalSparks = 150
        for (i in 0 until totalSparks) {
            val angle = Random.nextFloat() * (2f * PI.toFloat())
            val speed = Random.nextFloat() * 850f + 160f
            val pVx = cos(angle) * speed
            val pVy = sin(angle) * speed
            val color = when (i % 4) {
                0 -> colorCyan
                1 -> colorMagenta
                2 -> colorGold
                else -> colorWhite
            }
            val radius = Random.nextFloat() * 7.5f + 3f
            val duration = Random.nextFloat() * 0.9f + 0.6f
            spawnParticle(originX, originY, pVx, pVy, radius, color, duration)
        }
    }

    /**
     * Celebration / Score Chime burst
     */
    fun emitScoreSparks(originX: Float, originY: Float, count: Int = 24) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * (2f * PI.toFloat())
            val speed = Random.nextFloat() * 320f + 80f
            val pVx = cos(angle) * speed
            val pVy = sin(angle) * speed
            val color = if (Random.nextBoolean()) colorGold else colorCyan
            val radius = Random.nextFloat() * 4.5f + 2f
            val duration = Random.nextFloat() * 0.5f + 0.3f
            spawnParticle(originX, originY, pVx, pVy, radius, color, duration)
        }
    }

    fun update(dt: Float) {
        for (p in pool) {
            if (p.isActive) {
                p.update(dt)
            }
        }
    }

    fun draw(canvas: Canvas) {
        for (p in pool) {
            if (p.isActive && p.radius > 0.3f) {
                val alphaRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
                val alpha = (alphaRatio * 255).toInt()
                paint.color = p.color
                paint.alpha = alpha
                canvas.drawCircle(p.x, p.y, p.radius, paint)
            }
        }
    }

    fun clear() {
        for (p in pool) {
            p.isActive = false
        }
    }
}
