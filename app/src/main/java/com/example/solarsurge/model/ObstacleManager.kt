package com.example.solarsurge.model

import android.graphics.Canvas
import kotlin.math.max
import kotlin.random.Random

class ObstacleManager(
    private val density: Float
) {
    // Object pool of pre-allocated obstacles
    private val poolSize = 16
    private val pool = Array(poolSize) { Obstacle(density) }

    private val densityScale = (density / 2.75f).coerceIn(0.85f, 1.35f)

    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    private var lastSpawnX: Float = 0f
    private var spawnDistance: Float = 600f * densityScale
    private var globalTime: Float = 0f
    private var obstacleSequenceCounter: Int = 0

    fun init(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        spawnDistance = width * 0.72f
        reset()
    }

    fun reset() {
        for (obs in pool) {
            obs.isActive = false
            obs.isScored = false
        }
        lastSpawnX = screenWidth + 150f * density
        globalTime = 0f
        obstacleSequenceCounter = 0
    }

    fun update(
        dt: Float,
        speedX: Float,
        playerX: Float,
        score: Int,
        onScore: (Obstacle) -> Unit
    ) {
        if (screenWidth <= 0f || screenHeight <= 0f) return
        globalTime += dt

        // Update active obstacles and recycle off-screen ones
        for (obs in pool) {
            if (obs.isActive) {
                obs.update(dt, speedX)

                // Scoring check
                if (!obs.isScored && playerX > obs.x) {
                    obs.isScored = true
                    onScore(obs)
                }

                // Recycle when past screen left edge
                if (obs.x < -obs.width - 150f) {
                    obs.isActive = false
                }
            }
        }

        // Spawn logic: spawn when distance from last spawn exceeds spacing
        lastSpawnX -= speedX * dt
        if (lastSpawnX <= screenWidth) {
            spawnNextObstacle(score)
        }
    }

    private fun spawnNextObstacle(score: Int) {
        val freeObs = pool.firstOrNull { !it.isActive } ?: return

        val spawnX = screenWidth + 180f * density

        // Difficulty scaling: gap = max(220f, 380f - score * 2.5f)
        val baseGap = max(220f, 380f - score * 2.5f)
        val scaledGap = baseGap * densityScale

        // Vertical margin from screen edges
        val topMargin = 120f * density
        val bottomMargin = screenHeight - (120f * density)
        val safeCenterYRange = (bottomMargin - topMargin - scaledGap).coerceAtLeast(40f)
        val centerY = topMargin + (scaledGap * 0.5f) + Random.nextFloat() * safeCenterYRange

        // Procedural sequence with variety
        obstacleSequenceCounter++
        val type = when (obstacleSequenceCounter % 3) {
            0 -> ObstacleType.BINARY_BLACK_HOLES
            1 -> ObstacleType.SOLAR_FLARE
            else -> ObstacleType.ASTEROID_CLUSTER
        }

        when (type) {
            ObstacleType.BINARY_BLACK_HOLES -> {
                freeObs.resetAsBlackHoles(spawnX, centerY, scaledGap)
            }
            ObstacleType.SOLAR_FLARE -> {
                val phase = Random.nextFloat() * 6.28f
                freeObs.resetAsSolarFlare(spawnX, centerY, scaledGap * 1.1f, phase)
            }
            ObstacleType.ASTEROID_CLUSTER -> {
                freeObs.resetAsAsteroidCluster(spawnX, centerY, scaledGap * 1.15f)
            }
        }

        // Spacing scales slightly with speed to preserve fair reaction windows
        val dynamicSpacing = screenWidth * (0.68f + (score / 40f) * 0.05f).coerceAtMost(0.85f)
        lastSpawnX = spawnX + dynamicSpacing
    }

    fun checkCollision(playerX: Float, playerY: Float, playerHitboxRadius: Float): Boolean {
        for (obs in pool) {
            if (obs.isActive && obs.checkCollision(playerX, playerY, playerHitboxRadius, screenHeight)) {
                return true
            }
        }
        return false
    }

    fun draw(canvas: Canvas) {
        for (obs in pool) {
            if (obs.isActive) {
                obs.draw(canvas, screenHeight, globalTime)
            }
        }
    }
}
