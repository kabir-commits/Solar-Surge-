package com.example.solarsurge.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manages zero-latency game sound effects using native AudioTrack with preloaded static buffers.
 * Bypasses MediaCodec and Stagefright to prevent hardware/software component query errors.
 */
class SoundManager(private val context: Context) {

    private val tag = "SoundManager"
    var isMuted: Boolean = false

    private val sampleRate = 44100

    private var scoreTrack: StaticVoicePool? = null
    private var explosionTrack: StaticVoicePool? = null
    private var reigniteTrack: StaticVoicePool? = null
    private var highScoreTrack: StaticVoicePool? = null
    private var gravitonTrack: StaticVoicePool? = null

    private var lastGravitonPlayTime: Long = 0

    init {
        try {
            // 1. Score Chime (Neon Crystal Ping)
            val scoreSamples = generateAudio(sampleRate, 0.14f) { t, dur ->
                val progress = t / dur
                val freq = 880.0 + progress * 520.0
                val env = exp((-t * 16.0).toDouble()).toFloat()
                val sine = sin(2.0 * PI * freq * t).toFloat()
                sine * env * 0.85f
            }
            scoreTrack = StaticVoicePool(sampleRate, scoreSamples, poolSize = 3)

            // 2. Supernova Explosion Boom
            val explosionSamples = generateAudio(sampleRate, 0.42f) { t, _ ->
                val env = exp((-t * 6.5).toDouble()).toFloat()
                val noise = (Random.nextFloat() * 2f - 1f) * 0.45f
                val rumble = sin(2.0 * PI * (75.0 - t * 40.0).coerceAtLeast(30.0) * t).toFloat() * 0.7f
                (rumble + noise) * env * 0.95f
            }
            explosionTrack = StaticVoicePool(sampleRate, explosionSamples, poolSize = 2)

            // 3. Reignite / Surge Sound
            val reigniteSamples = generateAudio(sampleRate, 0.22f) { t, dur ->
                val progress = t / dur
                val freq = 240.0 + progress * 480.0
                val env = sin(PI * progress).toFloat()
                val sine = sin(2.0 * PI * freq * t).toFloat()
                sine * env * 0.8f
            }
            reigniteTrack = StaticVoicePool(sampleRate, reigniteSamples, poolSize = 2)

            // 4. High Score Flourish
            val highScoreSamples = generateAudio(sampleRate, 0.36f) { t, dur ->
                val stage = (t / dur * 3f).toInt().coerceIn(0, 2)
                val freq = when (stage) {
                    0 -> 587.33 // D5
                    1 -> 739.99 // F#5
                    else -> 880.00 // A5
                }
                val subT = t - (stage * (dur / 3f))
                val env = exp((-subT * 12.0).toDouble()).toFloat()
                val sine = sin(2.0 * PI * freq * t).toFloat()
                sine * env * 0.85f
            }
            highScoreTrack = StaticVoicePool(sampleRate, highScoreSamples, poolSize = 2)

            // 5. Graviton Thruster Hum
            val gravitonSamples = generateAudio(sampleRate, 0.16f) { t, dur ->
                val env = sin(PI * (t / dur)).toFloat()
                val fundamental = sin(2.0 * PI * 110.0 * t).toFloat() * 0.6f
                val sub = sin(2.0 * PI * 55.0 * t).toFloat() * 0.4f
                (fundamental + sub) * env * 0.7f
            }
            gravitonTrack = StaticVoicePool(sampleRate, gravitonSamples, poolSize = 2)

        } catch (e: Exception) {
            Log.e(tag, "Error initializing sound effects", e)
        }
    }

    private fun generateAudio(
        sampleRate: Int,
        durationSeconds: Float,
        generator: (timeSec: Float, durationSec: Float) -> Float
    ): ShortArray {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val sampleVal = generator(t, durationSeconds).coerceIn(-1.0f, 1.0f)
            samples[i] = (sampleVal * 32767.0f).toInt().toShort()
        }
        return samples
    }

    fun playScore() {
        if (isMuted) return
        scoreTrack?.play(0.85f)
    }

    fun playExplosion() {
        if (isMuted) return
        explosionTrack?.play(1.0f)
    }

    fun playReignite() {
        if (isMuted) return
        reigniteTrack?.play(0.9f)
    }

    fun playHighScore() {
        if (isMuted) return
        highScoreTrack?.play(1.0f)
    }

    fun playGravitonPulse() {
        if (isMuted) return
        val now = System.currentTimeMillis()
        if (now - lastGravitonPlayTime > 160) {
            lastGravitonPlayTime = now
            gravitonTrack?.play(0.35f)
        }
    }

    fun release() {
        scoreTrack?.release()
        explosionTrack?.release()
        reigniteTrack?.release()
        highScoreTrack?.release()
        gravitonTrack?.release()
    }

    /**
     * Helper pool of AudioTracks pre-loaded in memory for instant zero-latency playback.
     */
    private class StaticVoicePool(
        sampleRate: Int,
        samples: ShortArray,
        poolSize: Int = 2
    ) {
        private val tracks = ArrayList<AudioTrack>()
        private var currentIndex = 0

        init {
            val bufferSize = samples.size * 2
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            for (i in 0 until poolSize) {
                try {
                    val track = AudioTrack(
                        attributes,
                        format,
                        bufferSize,
                        AudioTrack.MODE_STATIC,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                    track.write(samples, 0, samples.size)
                    tracks.add(track)
                } catch (e: Exception) {
                    Log.w("StaticVoicePool", "Could not create static voice track", e)
                }
            }
        }

        fun play(volume: Float) {
            if (tracks.isEmpty()) return
            try {
                val track = tracks[currentIndex]
                currentIndex = (currentIndex + 1) % tracks.size
                track.stop()
                track.reloadStaticData()
                track.setVolume(volume)
                track.play()
            } catch (_: Exception) {}
        }

        fun release() {
            for (track in tracks) {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
            tracks.clear()
        }
    }
}
