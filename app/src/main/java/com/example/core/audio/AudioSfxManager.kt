package com.example.core.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioSfxManager {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    enum class SfxType {
        AIRHORN,
        APPLAUSE,
        GOAL,
        WHISTLE,
        LAUGH,
        DRUMROLL,
        CHEER,
        BUZZER
    }

    fun playSfx(type: SfxType) {
        scope.launch {
            try {
                val sampleRate = 44100
                val pcmData = when (type) {
                    SfxType.AIRHORN -> generateAirhorn(sampleRate)
                    SfxType.APPLAUSE -> generateApplause(sampleRate)
                    SfxType.GOAL -> generateGoal(sampleRate)
                    SfxType.WHISTLE -> generateWhistle(sampleRate)
                    SfxType.LAUGH -> generateLaugh(sampleRate)
                    SfxType.DRUMROLL -> generateDrumroll(sampleRate)
                    SfxType.CHEER -> generateCheer(sampleRate)
                    SfxType.BUZZER -> generateBuzzer(sampleRate)
                }

                playPcmBuffer(pcmData, sampleRate)
            } catch (e: Exception) {
                Log.e("AudioSfxManager", "Error playing SFX ${type.name}: ${e.message}")
            }
        }
    }

    private fun playPcmBuffer(pcmData: ShortArray, sampleRate: Int) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmData.size * 2)

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STATIC
        )

        audioTrack.write(pcmData, 0, pcmData.size)
        audioTrack.play()

        // Automatically release track after playback completes
        val durationMs = (pcmData.size.toDouble() / sampleRate * 1000).toLong() + 200
        scope.launch {
            kotlinx.coroutines.delay(durationMs)
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    private fun generateAirhorn(sampleRate: Int): ShortArray {
        val durationSec = 1.0
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        val freq1 = 370.0 // F#4
        val freq2 = 466.0 // A#4

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val sample = (sin(2.0 * Math.PI * freq1 * t) + sin(2.0 * Math.PI * freq2 * t)) * 0.4
            val envelope = if (t > 0.8) (1.0 - t) / 0.2 else 1.0
            buffer[i] = (sample * envelope * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateApplause(sampleRate: Int): ShortArray {
        val durationSec = 1.8
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val random = java.util.Random()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val noise = (random.nextDouble() * 2.0 - 1.0)
            val rhythm = (sin(2.0 * Math.PI * 12.0 * t) + 1.0) / 2.0
            val envelope = when {
                t < 0.2 -> t / 0.2
                t > 1.4 -> (1.8 - t) / 0.4
                else -> 1.0
            }
            val sample = noise * (0.3 + 0.7 * rhythm) * envelope * 0.35
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateGoal(sampleRate: Int): ShortArray {
        val durationSec = 1.5
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val currentFreq = 220.0 + (t / durationSec) * 580.0 // 220Hz to 800Hz
            val sample = sin(2.0 * Math.PI * currentFreq * t) * 0.45
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateLaugh(sampleRate: Int): ShortArray {
        val durationSec = 1.4
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val staccato = if ((t * 8.0).toInt() % 2 == 0) 1.0 else 0.15
            val sample = sin(2.0 * Math.PI * 400.0 * t) * staccato * 0.4
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateCheer(sampleRate: Int): ShortArray {
        val durationSec = 1.5
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        val chord = listOf(523.25, 659.25, 783.99) // C5 chord

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            for (freq in chord) {
                sum += sin(2.0 * Math.PI * freq * t)
            }
            val envelope = if (t > 1.0) (1.5 - t) / 0.5 else 1.0
            val sample = (sum / chord.size) * envelope * 0.4
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateWhistle(sampleRate: Int): ShortArray {
        val durationSec = 1.2
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val tremolo = (sin(2.0 * Math.PI * 25.0 * t) + 1.0) / 2.0
            val freq = 2600.0 + tremolo * 400.0
            val env = when {
                t < 0.08 -> t / 0.08
                t > 1.0 -> (1.2 - t) / 0.2
                else -> 1.0
            }
            val sample = sin(2.0 * Math.PI * freq * t) * env * 0.45
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateDrumroll(sampleRate: Int): ShortArray {
        val durationSec = 1.8
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val random = java.util.Random()
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val tapRate = 18.0 + (t / durationSec) * 12.0
            val tapPhase = (t * tapRate) % 1.0
            val tapEnv = Math.exp(-tapPhase * 8.0)
            val noise = (random.nextDouble() * 2.0 - 1.0)
            val buildUp = (0.3 + 0.7 * (t / durationSec))
            val sample = noise * tapEnv * buildUp * 0.4
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateBuzzer(sampleRate: Int): ShortArray {
        val durationSec = 1.0
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val tone1 = sin(2.0 * Math.PI * 140.0 * t)
            val tone2 = sin(2.0 * Math.PI * 210.0 * t)
            val tone3 = sin(2.0 * Math.PI * 350.0 * t)
            val env = if (t > 0.85) (1.0 - t) / 0.15 else 1.0
            val sample = (tone1 * 0.5 + tone2 * 0.3 + tone3 * 0.2) * env * 0.45
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}
