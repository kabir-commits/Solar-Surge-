package com.example.solarsurge.engine

import android.graphics.Canvas
import android.os.Build
import android.view.SurfaceHolder

class GameLoopThread(
    private val surfaceHolder: SurfaceHolder,
    val gameView: GameView
) : Thread("SolarSurgeGameLoop") {

    @Volatile
    var isRunning: Boolean = false

    private val targetFPS = 60
    private val targetFrameTimeMs = 1000L / targetFPS
    private var lastGravityTier = -1

    /**
     * Progressively increases the buoyancy acceleration and graviton pull values
     * as the player's score passes every 50-point threshold to escalate the challenge.
     */
    fun gravityRamp(score: Int) {
        val tier = (score / 50).coerceAtLeast(0)
        if (tier != lastGravityTier) {
            lastGravityTier = tier
            val rampMultiplier = 1.0f + tier * 0.15f
            gameView.player.setGravityRamp(rampMultiplier)
        }
    }

    /**
     * Alias for gravity ramp execution.
     */
    fun applyGravityRamp(score: Int) {
        gravityRamp(score)
    }

    override fun run() {
        var lastTime = System.nanoTime()

        while (isRunning) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000.0f
            lastTime = now

            // Clamp delta time to avoid large physics steps during hitch/pause
            if (dt > 0.05f) {
                dt = 0.05f
            } else if (dt <= 0f) {
                dt = 0.001f
            }

            // Apply gravity ramp progression based on score threshold
            gravityRamp(gameView.currentScore)

            // 1. Update Game Physics & State
            gameView.update(dt)

            // 2. Render Canvas Frame
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        surfaceHolder.lockHardwareCanvas()
                    } catch (_: Exception) {
                        surfaceHolder.lockCanvas()
                    }
                } else {
                    surfaceHolder.lockCanvas()
                }

                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.render(canvas)
                    }
                }
            } catch (_: Exception) {
                // Ignore transient surface errors during resize or destroy
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                        // Surface was destroyed while rendering
                    }
                }
            }

            // 3. Maintain 60 FPS target
            val frameTimeMs = (System.nanoTime() - now) / 1_000_000L
            val sleepTimeMs = targetFrameTimeMs - frameTimeMs
            if (sleepTimeMs > 0) {
                try {
                    sleep(sleepTimeMs)
                } catch (_: InterruptedException) {
                    // Thread interrupted during sleep
                }
            }
        }
    }
}
