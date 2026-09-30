package com.example.solarsurge.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ObstacleType {
    BINARY_BLACK_HOLES,
    SOLAR_FLARE,
    ASTEROID_CLUSTER
}

class AsteroidRock {
    var relX: Float = 0f
    var relY: Float = 0f
    var radius: Float = 0f
    var rotAngle: Float = 0f
    var rotSpeed: Float = 0f
    val baseVertices = FloatArray(16) // 8 points (x, y) relative to 1.0 unit
    val worldVertices = FloatArray(16)

    fun initRock(rx: Float, ry: Float, rad: Float, speed: Float) {
        relX = rx
        relY = ry
        radius = rad
        rotAngle = Random.nextFloat() * 6.28f
        rotSpeed = speed

        // Generate jagged polygonal vertices
        val numPoints = 8
        for (i in 0 until numPoints) {
            val angle = i * (2f * PI.toFloat() / numPoints)
            val dist = Random.nextFloat() * 0.35f + 0.75f
            baseVertices[i * 2] = cos(angle) * dist
            baseVertices[i * 2 + 1] = sin(angle) * dist
        }
    }

    fun update(dt: Float) {
        rotAngle += rotSpeed * dt
    }
}

class Obstacle(private val density: Float) {
    var type: ObstacleType = ObstacleType.BINARY_BLACK_HOLES
    var x: Float = 0f
    var isActive: Boolean = false
    var isScored: Boolean = false

    // Common bounds / size
    var width: Float = 90f * density

    // 1. Binary Black Holes
    var gapCenterY: Float = 0f
    var gapHeight: Float = 0f
    var singularityRadius: Float = 55f * density

    // 2. Solar Flare
    var flareSafeY: Float = 0f
    var flareSafeHeight: Float = 0f
    var flarePhase: Float = 0f
    var flareFrequency: Float = 2.8f
    var flareBaseWidth: Float = 26f * density

    // 3. Asteroid Cluster
    val rocks = Array(4) { AsteroidRock() }
    var rockCount: Int = 3

    // Shared Reusable Paints to avoid allocation in draw()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val laserCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val laserAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val tempRect = RectF()
    private val tempPath = Path()

    fun resetAsBlackHoles(startX: Float, centerY: Float, gap: Float) {
        type = ObstacleType.BINARY_BLACK_HOLES
        x = startX
        gapCenterY = centerY
        gapHeight = gap
        singularityRadius = (58f * density)
        width = singularityRadius * 2.2f
        isActive = true
        isScored = false
    }

    fun resetAsSolarFlare(startX: Float, safeY: Float, safeGap: Float, phase: Float) {
        type = ObstacleType.SOLAR_FLARE
        x = startX
        flareSafeY = safeY
        flareSafeHeight = safeGap
        flarePhase = phase
        flareBaseWidth = 24f * density
        width = flareBaseWidth * 3f
        isActive = true
        isScored = false
    }

    fun resetAsAsteroidCluster(startX: Float, centerY: Float, gap: Float) {
        type = ObstacleType.ASTEROID_CLUSTER
        x = startX
        isActive = true
        isScored = false
        width = 110f * density

        rockCount = Random.nextInt(3, 5)
        // Distribute rocks above and below safe passage
        for (i in 0 until rockCount) {
            val isTop = i % 2 == 0
            val rockRad = (Random.nextFloat() * 18f + 26f) * density
            val rx = (Random.nextFloat() * 70f - 35f) * density
            val ry = if (isTop) {
                centerY - (gap * 0.5f) - rockRad - (Random.nextFloat() * 80f * density)
            } else {
                centerY + (gap * 0.5f) + rockRad + (Random.nextFloat() * 80f * density)
            }
            val rotSpd = (Random.nextFloat() * 1.5f + 0.5f) * (if (Random.nextBoolean()) 1f else -1f)
            rocks[i].initRock(rx, ry, rockRad, rotSpd)
        }
    }

    fun update(dt: Float, speedX: Float) {
        if (!isActive) return
        x -= speedX * dt

        if (type == ObstacleType.ASTEROID_CLUSTER) {
            for (i in 0 until rockCount) {
                rocks[i].update(dt)
            }
        }
    }

    fun checkCollision(playerX: Float, playerY: Float, playerHitboxRadius: Float, screenHeight: Float): Boolean {
        if (!isActive) return false

        when (type) {
            ObstacleType.BINARY_BLACK_HOLES -> {
                // Top singularity
                val topCenterY = gapCenterY - (gapHeight * 0.5f) - singularityRadius
                val distSqTop = distSquared(playerX, playerY, x, topCenterY)
                val hitDistTop = singularityRadius + playerHitboxRadius
                if (distSqTop < hitDistTop * hitDistTop) return true

                // Bottom singularity
                val bottomCenterY = gapCenterY + (gapHeight * 0.5f) + singularityRadius
                val distSqBottom = distSquared(playerX, playerY, x, bottomCenterY)
                val hitDistBottom = singularityRadius + playerHitboxRadius
                if (distSqBottom < hitDistBottom * hitDistBottom) return true

                return false
            }

            ObstacleType.SOLAR_FLARE -> {
                // Vertical laser beams pulsing on sine wave
                val pulse = sin(flarePhase) * (18f * density)
                val currentSafeY = flareSafeY + pulse
                val halfSafe = flareSafeHeight * 0.5f

                // Hazard exists outside the safe vertical gap
                val beamLeft = x - (flareBaseWidth * 0.45f)
                val beamRight = x + (flareBaseWidth * 0.45f)

                if (playerX + playerHitboxRadius > beamLeft && playerX - playerHitboxRadius < beamRight) {
                    val inSafeGap = playerY - playerHitboxRadius > (currentSafeY - halfSafe) &&
                            playerY + playerHitboxRadius < (currentSafeY + halfSafe)
                    if (!inSafeGap) {
                        return true
                    }
                }
                return false
            }

            ObstacleType.ASTEROID_CLUSTER -> {
                for (i in 0 until rockCount) {
                    val rock = rocks[i]
                    val worldRockX = x + rock.relX
                    val worldRockY = rock.relY
                    val dSq = distSquared(playerX, playerY, worldRockX, worldRockY)
                    val collDist = (rock.radius * 0.85f) + playerHitboxRadius
                    if (dSq < collDist * collDist) {
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun distSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    fun draw(canvas: Canvas, screenHeight: Float, globalTime: Float) {
        if (!isActive) return

        when (type) {
            ObstacleType.BINARY_BLACK_HOLES -> drawBlackHoles(canvas, globalTime)
            ObstacleType.SOLAR_FLARE -> drawSolarFlare(canvas, screenHeight, globalTime)
            ObstacleType.ASTEROID_CLUSTER -> drawAsteroidCluster(canvas)
        }
    }

    private fun drawBlackHoles(canvas: Canvas, globalTime: Float) {
        // Draw top singularity
        val topCenterY = gapCenterY - (gapHeight * 0.5f) - singularityRadius
        drawSingularity(canvas, x, topCenterY, globalTime, isTop = true)

        // Draw bottom singularity
        val bottomCenterY = gapCenterY + (gapHeight * 0.5f) + singularityRadius
        drawSingularity(canvas, x, bottomCenterY, globalTime, isTop = false)

        // Gravitational flux arches connecting them subtly
        strokePaint.color = 0xFF5800FF.toInt()
        strokePaint.strokeWidth = 2f * density
        strokePaint.alpha = 90
        canvas.drawLine(x - 8f, topCenterY + singularityRadius, x - 8f, bottomCenterY - singularityRadius, strokePaint)
        canvas.drawLine(x + 8f, topCenterY + singularityRadius, x + 8f, bottomCenterY - singularityRadius, strokePaint)
    }

    private fun drawSingularity(canvas: Canvas, cx: Float, cy: Float, time: Float, isTop: Boolean) {
        val rad = singularityRadius

        // 1. Accretion Disk Outer Violet Glow
        fillPaint.shader = null
        fillPaint.color = 0xFF8B00FF.toInt()
        fillPaint.alpha = 60
        canvas.drawCircle(cx, cy, rad * 1.55f, fillPaint)

        // 2. Swirling Lensing Ring
        strokePaint.color = 0xFFB388FF.toInt()
        strokePaint.strokeWidth = 3f * density
        strokePaint.alpha = 190

        val spin = time * 3.5f * (if (isTop) 1f else -1f)
        tempRect.set(cx - rad * 1.25f, cy - rad * 0.65f, cx + rad * 1.25f, cy + rad * 0.65f)
        canvas.save()
        canvas.rotate(if (isTop) 25f else -25f, cx, cy)
        canvas.drawOval(tempRect, strokePaint)
        canvas.restore()

        // 3. Electric Accretion Halo
        strokePaint.color = 0xFF00F3FF.toInt()
        strokePaint.strokeWidth = 1.8f * density
        strokePaint.alpha = 220
        canvas.drawCircle(cx, cy, rad * 1.05f, strokePaint)

        // 4. Pitch-black Event Horizon Core
        fillPaint.color = 0xFF000000.toInt()
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, rad, fillPaint)

        // 5. Deep Purple Core Rim
        strokePaint.color = 0xFF9D00FF.toInt()
        strokePaint.strokeWidth = 2.5f * density
        strokePaint.alpha = 255
        canvas.drawCircle(cx, cy, rad * 0.95f, strokePaint)
    }

    private fun drawSolarFlare(canvas: Canvas, screenHeight: Float, globalTime: Float) {
        val pulse = sin(flarePhase) * (18f * density)
        val currentSafeY = flareSafeY + pulse
        val halfSafe = flareSafeHeight * 0.5f

        val safeTop = currentSafeY - halfSafe
        val safeBottom = currentSafeY + halfSafe

        val beamThickness = flareBaseWidth * (1f + sin(globalTime * 12f) * 0.15f)

        // Outer Coronal Plasma Glow (Magenta)
        laserAuraPaint.color = 0xFFFF007F.toInt()
        laserAuraPaint.strokeWidth = beamThickness * 1.8f
        laserAuraPaint.alpha = 110

        // Upper beam: 0 to safeTop
        if (safeTop > 0f) {
            canvas.drawLine(x, 0f, x, safeTop, laserAuraPaint)
        }
        // Lower beam: safeBottom to screenHeight
        if (safeBottom < screenHeight) {
            canvas.drawLine(x, safeBottom, x, screenHeight, laserAuraPaint)
        }

        // Inner White/Cyan Plasma Core
        laserCorePaint.color = 0xFF00F3FF.toInt()
        laserCorePaint.strokeWidth = beamThickness * 0.65f
        laserCorePaint.alpha = 230

        if (safeTop > 0f) {
            canvas.drawLine(x, 0f, x, safeTop, laserCorePaint)
        }
        if (safeBottom < screenHeight) {
            canvas.drawLine(x, safeBottom, x, screenHeight, laserCorePaint)
        }

        // Safe corridor emitter nodes
        fillPaint.color = 0xFFFFD700.toInt()
        fillPaint.alpha = 255
        canvas.drawCircle(x, safeTop, 7f * density, fillPaint)
        canvas.drawCircle(x, safeBottom, 7f * density, fillPaint)

        // Coronal discharge arcs at the tips
        strokePaint.color = 0xFFFFFFFF.toInt()
        strokePaint.strokeWidth = 2f * density
        strokePaint.alpha = 200
        canvas.drawCircle(x, safeTop, 11f * density, strokePaint)
        canvas.drawCircle(x, safeBottom, 11f * density, strokePaint)
    }

    private fun drawAsteroidCluster(canvas: Canvas) {
        for (i in 0 until rockCount) {
            val rock = rocks[i]
            val wx = x + rock.relX
            val wy = rock.relY

            tempPath.reset()
            val cosA = cos(rock.rotAngle)
            val sinA = sin(rock.rotAngle)

            for (p in 0 until 8) {
                val bx = rock.baseVertices[p * 2] * rock.radius
                val by = rock.baseVertices[p * 2 + 1] * rock.radius
                // Rotate
                val rx = bx * cosA - by * sinA
                val ry = bx * sinA + by * cosA
                if (p == 0) {
                    tempPath.moveTo(wx + rx, wy + ry)
                } else {
                    tempPath.lineTo(wx + rx, wy + ry)
                }
            }
            tempPath.close()

            // Rock Body: Dark Space Rock
            fillPaint.color = 0xFF181C2E.toInt()
            fillPaint.alpha = 255
            canvas.drawPath(tempPath, fillPaint)

            // Neon Cyan / Gold Mineral Fissure Outline
            strokePaint.color = if (i % 2 == 0) 0xFF00F3FF.toInt() else 0xFFFFD700.toInt()
            strokePaint.strokeWidth = 2.2f * density
            strokePaint.alpha = 220
            canvas.drawPath(tempPath, strokePaint)

            // Inner glowing crater core
            fillPaint.color = 0xFF070913.toInt()
            fillPaint.alpha = 200
            canvas.drawCircle(wx, wy, rock.radius * 0.35f, fillPaint)
        }
    }
}
