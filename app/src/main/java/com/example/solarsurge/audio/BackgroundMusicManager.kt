package com.example.solarsurge.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manages atmospheric synth-wave background music with seamless dual-player cross-fading,
 * lifecycle integration, and smooth volume transitions.
 */
class BackgroundMusicManager(private val context: Context) {

    private val tag = "BGMManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null
    private var isPlayerAActive = true

    private var musicFile: File? = null
    private var trackDurationMs: Int = 0

    // Volume configuration
    private val maxMusicVolume = 0.55f
    private var currentTargetVolume = maxMusicVolume
    var isMuted: Boolean = false
        private set

    private var crossFadeJob: Job? = null
    private var fadeTransitionJob: Job? = null
    private var isPlayingRequested = false
    private var isInitialized = false

    private val crossFadeDurationMs = 1400L // 1.4s cross-fade transition between loop cycles

    init {
        scope.launch(Dispatchers.IO) {
            prepareTrack()
            scope.launch(Dispatchers.Main) {
                initPlayers()
                isInitialized = true
                if (isPlayingRequested && !isMuted) {
                    startPlaybackWithFadeIn()
                }
            }
        }
    }

    private fun prepareTrack() {
        try {
            val cacheDir = context.cacheDir
            val musicFile = File(cacheDir, "ss_synthwave_loop.wav")

            // Generate track if not already cached
            if (!musicFile.exists() || musicFile.length() < 100_000) {
                val sampleRate = 44100
                val bpm = 110.0
                val beatDur = 60.0 / bpm
                val barDur = beatDur * 4.0
                val totalBars = 4
                val totalDurationSec = (barDur * totalBars).toFloat()

                val numSamples = (sampleRate * totalDurationSec).toInt()
                val samples = ShortArray(numSamples)

                // Chord definitions (D minor, Bb major, C major, A minor)
                val chordFrequencies = arrayOf(
                    doubleArrayOf(146.83, 174.61, 220.00, 73.42), // Dm: D3, F3, A3, Bass D2
                    doubleArrayOf(116.54, 146.83, 174.61, 58.27), // Bb: Bb2, D3, F3, Bass Bb1
                    doubleArrayOf(130.81, 164.81, 196.00, 65.41), // C: C3, E3, G3, Bass C2
                    doubleArrayOf(110.00, 130.81, 164.81, 55.00)  // Am: A2, C3, E3, Bass A1
                )

                val arpNotes = arrayOf(
                    doubleArrayOf(293.66, 349.23, 440.00, 523.25, 587.33, 440.00, 349.23, 440.00), // Dm arp
                    doubleArrayOf(233.08, 293.66, 349.23, 466.16, 587.33, 349.23, 293.66, 349.23), // Bb arp
                    doubleArrayOf(261.63, 329.63, 392.00, 523.25, 659.25, 392.00, 329.63, 392.00), // C arp
                    doubleArrayOf(220.00, 261.63, 329.63, 440.00, 523.25, 329.63, 261.63, 329.63)  // Am arp
                )

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val currentBar = ((t / barDur).toInt()).coerceIn(0, totalBars - 1)
                    val tInBar = t - (currentBar * barDur)
                    val currentBeatInBar = tInBar / beatDur
                    val beatIndex = currentBeatInBar.toInt()
                    val tInBeat = tInBar - (beatIndex * beatDur)

                    // 1. Rolling 8th-note Analog Bassline
                    val bassT = tInBar % (beatDur * 0.5)
                    val bassEnv = exp(-bassT * 9.0).toFloat()
                    val bassFreq = chordFrequencies[currentBar][3]
                    val bassWave = (
                        sin(2.0 * PI * bassFreq * t) * 0.65 +
                        sin(2.0 * PI * bassFreq * 2.0 * t) * 0.35 +
                        sin(2.0 * PI * bassFreq * 3.0 * t) * 0.15
                    ).toFloat() * bassEnv * 0.42f

                    // 2. Lush Ambient Polyphonic Pad (Detuned Chorus)
                    val chord = chordFrequencies[currentBar]
                    val padSwell = (0.75 + 0.25 * sin(2.0 * PI * 0.35 * t)).toFloat()
                    var padWave = 0f
                    for (c in 0 until 3) {
                        val f = chord[c]
                        val tone1 = sin(2.0 * PI * f * t).toFloat()
                        val tone2 = sin(2.0 * PI * (f * 1.004) * t).toFloat()
                        padWave += (tone1 + tone2) * 0.5f
                    }
                    padWave = (padWave / 3f) * padSwell * 0.22f

                    // 3. Cosmic 16th-note Arpeggio
                    val sixteenthDur = beatDur * 0.25
                    val arpIndex = ((tInBar / sixteenthDur).toInt()) % 8
                    val arpT = tInBar % sixteenthDur
                    val arpEnv = exp(-arpT * 22.0).toFloat()
                    val arpFreq = arpNotes[currentBar][arpIndex]
                    val arpWave = sin(2.0 * PI * arpFreq * t).toFloat() * arpEnv * 0.18f

                    // 4. Subtle Synthwave Drums
                    // Kick on beat 0 and beat 2 (1 and 3)
                    var drumWave = 0f
                    if (beatIndex == 0 || beatIndex == 2) {
                        if (tInBeat < 0.22) {
                            val kickEnv = exp(-tInBeat * 16.0).toFloat()
                            val kickFreq = 120.0 * exp(-tInBeat * 24.0) + 42.0
                            drumWave += sin(2.0 * PI * kickFreq * tInBeat).toFloat() * kickEnv * 0.48f
                        }
                    }

                    // Snare on beat 1 and beat 3 (2 and 4)
                    if (beatIndex == 1 || beatIndex == 3) {
                        if (tInBeat < 0.18) {
                            val snareEnv = exp(-tInBeat * 20.0).toFloat()
                            val noise = (Random.nextFloat() * 2f - 1f) * 0.6f
                            val tone = sin(2.0 * PI * 185.0 * tInBeat).toFloat() * 0.4f
                            drumWave += (noise + tone) * snareEnv * 0.26f
                        }
                    }

                    // Shimmering Hi-Hat on every 16th note
                    if (arpT < 0.045) {
                        val hatEnv = exp(-arpT * 85.0).toFloat()
                        val hatNoise = (Random.nextFloat() * 2f - 1f) * hatEnv * 0.08f
                        drumWave += hatNoise
                    }

                    // Master mix
                    var mixed = (bassWave + padWave + arpWave + drumWave).coerceIn(-1.0f, 1.0f)

                    // Edge smoothing for seamless loop
                    val fadeSamples = (sampleRate * 0.04).toInt()
                    if (i < fadeSamples) {
                        mixed *= (i.toFloat() / fadeSamples)
                    } else if (i > numSamples - fadeSamples) {
                        mixed *= ((numSamples - i).toFloat() / fadeSamples)
                    }

                    samples[i] = (mixed * 32767.0f).toInt().toShort()
                }

                writeWav(musicFile, sampleRate, samples)
            }

            this@BackgroundMusicManager.musicFile = musicFile
        } catch (e: Exception) {
            Log.e(tag, "Failed to generate synthwave track", e)
        }
    }

    private fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
        val totalAudioLen = samples.size * 2
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = 1; header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0
        header[34] = 16; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                byteBuffer.putShort(sample)
            }
            fos.write(byteBuffer.array())
        }
    }

    private fun createMediaPlayer(): MediaPlayer? {
        val file = musicFile ?: return null
        if (!file.exists() || file.length() < 1000) return null
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnErrorListener { _, what, extra ->
                    Log.w(tag, "MediaPlayer handled error: what=$what, extra=$extra")
                    true
                }
                java.io.FileInputStream(file).use { fis ->
                    setDataSource(fis.fd, 0, file.length())
                }
                prepare()
                setVolume(0f, 0f)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error creating MediaPlayer", e)
            null
        }
    }

    private fun initPlayers() {
        playerA?.release()
        playerB?.release()

        playerA = createMediaPlayer()
        playerB = createMediaPlayer()

        trackDurationMs = playerA?.duration ?: 0
        isPlayerAActive = true
    }

    // -------------------------------------------------------------------------
    // Playback Controls with Cross-Fade Loop
    // -------------------------------------------------------------------------

    fun play() {
        isPlayingRequested = true
        if (!isInitialized || isMuted) return
        startPlaybackWithFadeIn()
    }

    private fun startPlaybackWithFadeIn() {
        try {
            val active = if (isPlayerAActive) playerA else playerB
            if (active == null) return

            active.setVolume(0f, 0f)
            active.start()

            // Smooth fade-in
            fadeVolume(active, 0f, targetVolume = maxMusicVolume, durationMs = 1200L)

            // Start cross-fade loop monitor
            scheduleNextCrossFade()
        } catch (e: Exception) {
            Log.e(tag, "Error starting playback", e)
        }
    }

    private fun scheduleNextCrossFade() {
        crossFadeJob?.cancel()
        crossFadeJob = scope.launch {
            while (isActive && isPlayingRequested && !isMuted) {
                val current = if (isPlayerAActive) playerA else playerB
                val next = if (isPlayerAActive) playerB else playerA

                if (current == null || next == null || !current.isPlaying) {
                    delay(300)
                    continue
                }

                val currentPos = try { current.currentPosition } catch (_: Exception) { 0 }
                val timeRemaining = trackDurationMs - currentPos

                // Trigger cross-fade when nearing end of track
                if (timeRemaining <= crossFadeDurationMs && timeRemaining > 0) {
                    performCrossFade(outgoing = current, incoming = next)
                    // Wait until cross-fade completes plus buffer before polling next cycle
                    delay(crossFadeDurationMs + 500L)
                } else {
                    // Poll periodically
                    val checkInterval = (timeRemaining - crossFadeDurationMs).coerceIn(50L, 250L)
                    delay(checkInterval)
                }
            }
        }
    }

    private fun performCrossFade(outgoing: MediaPlayer, incoming: MediaPlayer) {
        try {
            incoming.seekTo(0)
            incoming.setVolume(0f, 0f)
            incoming.start()

            val steps = 25
            val stepDelay = crossFadeDurationMs / steps

            scope.launch {
                for (i in 0..steps) {
                    if (!isActive || !isPlayingRequested || isMuted) break
                    val fraction = i.toFloat() / steps
                    val inVol = fraction * maxMusicVolume
                    val outVol = (1f - fraction) * maxMusicVolume

                    try {
                        incoming.setVolume(inVol, inVol)
                        outgoing.setVolume(outVol, outVol)
                    } catch (_: Exception) {}

                    delay(stepDelay)
                }

                // Finish outgoing player
                try {
                    outgoing.pause()
                    outgoing.seekTo(0)
                    outgoing.setVolume(0f, 0f)
                } catch (_: Exception) {}

                // Toggle active player pointer
                isPlayerAActive = !isPlayerAActive
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during cross-fade transition", e)
        }
    }

    private fun fadeVolume(player: MediaPlayer, fromVol: Float, targetVolume: Float, durationMs: Long) {
        fadeTransitionJob?.cancel()
        fadeTransitionJob = scope.launch {
            val steps = 20
            val stepDelay = durationMs / steps
            for (i in 0..steps) {
                if (!isActive) break
                val fraction = i.toFloat() / steps
                val vol = fromVol + fraction * (targetVolume - fromVol)
                try {
                    player.setVolume(vol, vol)
                } catch (_: Exception) {
                    break
                }
                delay(stepDelay)
            }
        }
    }

    fun pause() {
        isPlayingRequested = false
        crossFadeJob?.cancel()
        fadeTransitionJob?.cancel()

        val active = if (isPlayerAActive) playerA else playerB
        if (active != null && active.isPlaying) {
            scope.launch {
                // Smooth fade-out before pausing
                val steps = 15
                val stepDelay = 600L / steps
                for (i in 0..steps) {
                    val fraction = 1f - (i.toFloat() / steps)
                    val vol = fraction * maxMusicVolume
                    try {
                        active.setVolume(vol, vol)
                    } catch (_: Exception) { break }
                    delay(stepDelay)
                }
                try {
                    active.pause()
                } catch (_: Exception) {}
            }
        }
    }

    fun resume() {
        isPlayingRequested = true
        if (!isMuted && isInitialized) {
            val active = if (isPlayerAActive) playerA else playerB
            if (active != null && !active.isPlaying) {
                try {
                    active.start()
                    fadeVolume(active, fromVol = 0f, targetVolume = maxMusicVolume, durationMs = 1000L)
                    scheduleNextCrossFade()
                } catch (e: Exception) {
                    Log.e(tag, "Error resuming background music", e)
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            // Fade to silence
            val active = if (isPlayerAActive) playerA else playerB
            active?.let {
                fadeVolume(it, fromVol = maxMusicVolume, targetVolume = 0f, durationMs = 400L)
            }
        } else {
            // Fade back in
            if (isPlayingRequested) {
                val active = if (isPlayerAActive) playerA else playerB
                if (active != null) {
                    if (!active.isPlaying) {
                        try { active.start() } catch (_: Exception) {}
                    }
                    fadeVolume(active, fromVol = 0f, targetVolume = maxMusicVolume, durationMs = 800L)
                    scheduleNextCrossFade()
                }
            }
        }
    }

    fun release() {
        isPlayingRequested = false
        crossFadeJob?.cancel()
        fadeTransitionJob?.cancel()
        scope.cancel()

        try {
            playerA?.stop()
            playerA?.release()
        } catch (_: Exception) {}
        playerA = null

        try {
            playerB?.stop()
            playerB?.release()
        } catch (_: Exception) {}
        playerB = null
    }
}
