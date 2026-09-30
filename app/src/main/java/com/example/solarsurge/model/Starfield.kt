package com.example.solarsurge.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

class Starfield {

    private class Star(
        var x: Float,
        var y: Float,
        var radius: Float,
        var baseAlpha: Float,
        var twinkleOffset: Float,
        var color: Int
    )

    private class WarpStreak(
        var x: Float,
        var y: Float,
        var length: Float,
        var strokeWidth: Float,
        var color: Int,
        var alpha: Int
    )

    private val distantStars = ArrayList<Star>()
    private val midStars = ArrayList<Star>()
    private val warpStreaks = ArrayList<WarpStreak>()

    private val distantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var initialized = false
    private var globalTime = 0f

    fun initIfNeeded(width: Float, height: Float) {
        if (initialized || width <= 0f || height <= 0f) return
        initialized = true

        distantStars.clear()
        midStars.clear()
        warpStreaks.clear()

        // Layer 1: Distant dim stars (75 stars)
        for (i in 0 until 75) {
            val sx = Random.nextFloat() * width
            val sy = Random.nextFloat() * height
            val r = Random.nextFloat() * 1.5f + 0.8f
            val baseA = Random.nextFloat() * 0.45f + 0.25f
            val to = Random.nextFloat() * 6.28f
            val col = when (Random.nextInt(4)) {
                0 -> 0xFF8AA3D6.toInt()
                1 -> 0xFF7289BA.toInt()
                2 -> 0xFF586D9B.toInt()
                else -> 0xFF9EBAE8.toInt()
            }
            distantStars.add(Star(sx, sy, r, baseA, to, col))
        }

        // Layer 2: Midfield stars with cosmic tints (45 stars)
        for (i in 0 until 45) {
            val sx = Random.nextFloat() * width
            val sy = Random.nextFloat() * height
            val r = Random.nextFloat() * 2.2f + 1.2f
            val baseA = Random.nextFloat() * 0.5f + 0.5f
            val to = Random.nextFloat() * 6.28f
            val col = when (Random.nextInt(5)) {
                0 -> 0xFF00F3FF.toInt() // Neon Cyan
                1 -> 0xFFFF007F.toInt() // Deep Magenta
                2 -> 0xFFFFD700.toInt() // Electric Gold
                3 -> 0xFFB388FF.toInt() // Cosmic Violet
                else -> 0xFFFFFFFF.toInt()
            }
            midStars.add(Star(sx, sy, r, baseA, to, col))
        }

        // Layer 3: High-speed warp stardust streaks (25 streaks)
        for (i in 0 until 25) {
            val sx = Random.nextFloat() * width
            val sy = Random.nextFloat() * height
            val len = Random.nextFloat() * 28f + 14f
            val sw = Random.nextFloat() * 2.0f + 1.0f
            val col = if (Random.nextBoolean()) 0xFF00F3FF.toInt() else 0xFFFFD700.toInt()
            val a = (Random.nextFloat() * 120 + 80).toInt()
            warpStreaks.add(WarpStreak(sx, sy, len, sw, col, a))
        }
    }

    fun update(dt: Float, baseVx: Float, width: Float, height: Float) {
        if (!initialized || width <= 0f || height <= 0f) return
        globalTime += dt

        // 1. Distant layer (0.12x speed)
        val speedDistant = baseVx * 0.12f * dt
        for (star in distantStars) {
            star.x -= speedDistant
            if (star.x < -10f) {
                star.x = width + Random.nextFloat() * 20f
                star.y = Random.nextFloat() * height
            }
        }

        // 2. Midfield layer (0.35x speed)
        val speedMid = baseVx * 0.35f * dt
        for (star in midStars) {
            star.x -= speedMid
            if (star.x < -15f) {
                star.x = width + Random.nextFloat() * 30f
                star.y = Random.nextFloat() * height
            }
        }

        // 3. Foreground Warp Streaks (0.85x speed)
        val speedWarp = baseVx * 0.85f * dt
        for (streak in warpStreaks) {
            streak.x -= speedWarp
            if (streak.x < -50f) {
                streak.x = width + Random.nextFloat() * 60f
                streak.y = Random.nextFloat() * height
            }
        }
    }

    fun draw(canvas: Canvas) {
        if (!initialized) return

        // Draw distant stars
        for (star in distantStars) {
            val twinkle = (sin(globalTime * 2.0 + star.twinkleOffset).toFloat() * 0.2f)
            val alpha = ((star.baseAlpha + twinkle).coerceIn(0.1f, 0.9f) * 255).toInt()
            distantPaint.color = star.color
            distantPaint.alpha = alpha
            canvas.drawCircle(star.x, star.y, star.radius, distantPaint)
        }

        // Draw midfield stars with halos
        for (star in midStars) {
            val twinkle = (sin(globalTime * 3.5 + star.twinkleOffset).toFloat() * 0.25f)
            val alpha = ((star.baseAlpha + twinkle).coerceIn(0.2f, 1f) * 255).toInt()
            midPaint.color = star.color
            midPaint.alpha = alpha
            canvas.drawCircle(star.x, star.y, star.radius, midPaint)

            // Faint outer corona
            midPaint.alpha = (alpha * 0.25f).toInt()
            canvas.drawCircle(star.x, star.y, star.radius * 2.4f, midPaint)
        }

        // Draw foreground warp streaks
        for (streak in warpStreaks) {
            streakPaint.color = streak.color
            streakPaint.alpha = streak.alpha
            streakPaint.strokeWidth = streak.strokeWidth
            canvas.drawLine(streak.x, streak.y, streak.x + streak.length, streak.y, streakPaint)
        }
    }

    fun resize(width: Float, height: Float) {
        initialized = false
        initIfNeeded(width, height)
    }
}
