package com.cricri.espace.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundEngine {

    private val executor = Executors.newSingleThreadExecutor()
    private val sampleRate = 22050
    @Volatile var isMuted: Boolean = false

    fun playLaunch() = playTone(startFreq = 190.0, endFreq = 540.0, durationMs = 150, noiseMix = 0.15)
    fun playAbilityDash() = playTone(startFreq = 520.0, endFreq = 1380.0, durationMs = 180, noiseMix = 0.1)
    fun playAbilitySplit() = playTone(startFreq = 680.0, endFreq = 980.0, durationMs = 130, noiseMix = 0.05)
    fun playAbilityDrop() = playTone(startFreq = 320.0, endFreq = 85.0, durationMs = 210, noiseMix = 0.2)
    fun playExplosion() = playTone(startFreq = 140.0, endFreq = 36.0, durationMs = 340, noiseMix = 0.78)
    fun playBlockBreak(isGlass: Boolean) {
        if (isGlass) {
            playTone(startFreq = 1450.0, endFreq = 820.0, durationMs = 110, noiseMix = 0.45)
        } else {
            playTone(startFreq = 260.0, endFreq = 95.0, durationMs = 130, noiseMix = 0.55)
        }
    }
    fun playEnemyDefeat() = playTone(startFreq = 440.0, endFreq = 880.0, durationMs = 190, noiseMix = 0.12)
    fun playVictory() {
        if (isMuted || executor.isShutdown) return
        executor.execute {
            playBuffer(generateMelody(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 110))
        }
    }

    private fun playTone(startFreq: Double, endFreq: Double, durationMs: Int, noiseMix: Double) {
        if (isMuted || executor.isShutdown) return
        executor.execute {
            val numSamples = (sampleRate * durationMs) / 1000
            val samples = ShortArray(numSamples)
            val rng = Random()
            var phase = 0.0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / numSamples
                val freq = startFreq + (endFreq - startFreq) * t
                phase += 2.0 * PI * freq / sampleRate
                val env = exp(-3.2 * t)
                val tone = sin(phase) * (1.0 - noiseMix)
                val noise = (rng.nextDouble() * 2.0 - 1.0) * noiseMix
                val sample = ((tone + noise) * env * 14000.0).toInt().coerceIn(-32767, 32767)
                samples[i] = sample.toShort()
            }
            playBuffer(samples)
        }
    }

    private fun generateMelody(notes: DoubleArray, noteMs: Int): ShortArray {
        val perNote = (sampleRate * noteMs) / 1000
        val out = ShortArray(perNote * notes.size)
        for ((nIdx, freq) in notes.withIndex()) {
            var phase = 0.0
            for (i in 0 until perNote) {
                val t = i.toDouble() / perNote
                phase += 2.0 * PI * freq / sampleRate
                val env = exp(-2.2 * t)
                val valSample = (sin(phase) * env * 13000.0).toInt().coerceIn(-32767, 32767)
                out[nIdx * perNote + i] = valSample.toShort()
            }
        }
        return out
    }

    private fun playBuffer(samples: ShortArray) {
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((samples.size * 1000L) / sampleRate + 20L)
        } catch (_: Exception) {
        } finally {
            try {
                track?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }
}
