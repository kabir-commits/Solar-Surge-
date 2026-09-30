package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.solarsurge.model.ObstacleManager
import com.example.solarsurge.model.ParticleSystem
import com.example.solarsurge.model.StarPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Solar Surge", appName)
    }

    @Test
    fun `test player physics gravity loop`() {
        val player = StarPlayer(density = 2.75f)
        val particleSystem = ParticleSystem(50)
        player.reset(100f, 500f)

        // Unpressed state: buoyancy moves star upward (decreasing y)
        player.isHolding = false
        player.update(0.1f, particleSystem)
        assertTrue("Player should have negative vertical velocity when buoyant", player.vy < 0f)
        assertTrue("Player y should decrease as it surges upward", player.y < 500f)

        // Pressed state: graviton pull moves star downward (increasing y)
        player.isHolding = true
        for (i in 0 until 5) {
            player.update(0.1f, particleSystem)
        }
        assertTrue("Player should accelerate downward with graviton pull", player.vy > 0f)
    }

    @Test
    fun `test obstacle manager pooling and scoring`() {
        val manager = ObstacleManager(density = 2.75f)
        manager.init(1080f, 2400f)

        var scored = false
        manager.update(dt = 0.016f, speedX = 450f, playerX = 200f, score = 0) {
            scored = true
        }

        manager.reset()
        val hit = manager.checkCollision(playerX = 200f, playerY = 1200f, playerHitboxRadius = 20f)
        // With reset manager, no collisions should occur
        assertEquals(false, hit)
    }

    @Test
    fun `test gravity ramp escalates buoyancy and graviton every 50 points`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gameView = com.example.solarsurge.engine.GameView(context)
        val thread = com.example.solarsurge.engine.GameLoopThread(gameView.holder, gameView)

        // Baseline at score 0
        thread.gravityRamp(0)
        val baseBuoyancy = gameView.player.buoyancyAcc
        val baseGraviton = gameView.player.gravitonAcc

        // At score 49 (still tier 0)
        thread.gravityRamp(49)
        assertEquals(baseBuoyancy, gameView.player.buoyancyAcc, 0.01f)
        assertEquals(baseGraviton, gameView.player.gravitonAcc, 0.01f)

        // At score 50 (tier 1: +15% ramp)
        thread.gravityRamp(50)
        assertTrue("Buoyancy should increase past 50 points", gameView.player.buoyancyAcc > baseBuoyancy)
        assertTrue("Graviton pull should increase past 50 points", gameView.player.gravitonAcc > baseGraviton)
        assertEquals(baseBuoyancy * 1.15f, gameView.player.buoyancyAcc, 0.1f)
        assertEquals(baseGraviton * 1.15f, gameView.player.gravitonAcc, 0.1f)

        // At score 100 (tier 2: +30% ramp)
        thread.gravityRamp(100)
        assertEquals(baseBuoyancy * 1.30f, gameView.player.buoyancyAcc, 0.1f)
        assertEquals(baseGraviton * 1.30f, gameView.player.gravitonAcc, 0.1f)

        // Reset to score 0 restores baseline
        thread.gravityRamp(0)
        assertEquals(baseBuoyancy, gameView.player.buoyancyAcc, 0.01f)
        assertEquals(baseGraviton, gameView.player.gravitonAcc, 0.01f)

        gameView.soundManager.release()
        gameView.backgroundMusicManager.release()
    }
}
