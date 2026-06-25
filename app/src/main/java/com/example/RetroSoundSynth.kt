package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object RetroSoundSynth {
    private const val SAMPLE_RATE = 44100

    fun playSound(soundType: String) {
        val buffer = when (soundType.lowercase()) {
            "click", "keypress" -> generateClick()
            "lock" -> generateLock()
            "unlock" -> generateUnlock()
            "camera" -> generateCamera()
            "low_battery" -> generateLowBattery()
            "tick" -> generateTick(false)
            "tock" -> generateTick(true)
            "simple tick" -> generateSimpleTick()
            "jelly bean ack" -> generateJellyBeanAck()
            "double beep" -> generateDoubleBeep()
            "bubble pop" -> generateBubblePop()
            "ceres", "ceres (classic)" -> generateCeres()
            "pixie dust" -> generatePixieDust()
            "teardrop" -> generateTeardrop()
            "multimedia" -> generateMultimedia()
            "ringtone" -> generateRingtone()
            else -> return
        }
        playPcm(buffer)
    }

    private fun playPcm(buffer: ShortArray) {
        Thread {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                val durationMs = (buffer.size * 1000L) / SAMPLE_RATE
                Thread.sleep(durationMs + 100)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }.start()
    }

    // Classic Android 4.1 touch tick/click sound (Wood-block pop)
    private fun generateClick(): ShortArray {
        val durationMs = 15
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val envelope = exp(-t * 220f)
            // Combined woody sound frequencies (1100 Hz and 180 Hz)
            val wave = 0.7f * sin(2 * PI * 1100 * t) + 0.3f * sin(2 * PI * 180 * t)
            val sampleVal = (wave * envelope * 32767 * 0.35f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Lock sound: metallic mechanical latch clack
    private fun generateLock(): ShortArray {
        val durationMs = 90
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val click1Delay = 0
        val click2Delay = (SAMPLE_RATE * 15) / 1000 // 15ms latch delay

        for (i in 0 until numSamples) {
            var sampleVal = 0.0

            // Click 1 (sharp metal trigger)
            if (i >= click1Delay) {
                val t = (i - click1Delay).toFloat() / SAMPLE_RATE
                val envelope = exp(-t * 350f)
                sampleVal += 0.5 * sin(2 * PI * 1900 * t) * envelope
            }

            // Click 2 (lower resonator chamber)
            if (i >= click2Delay) {
                val t = (i - click2Delay).toFloat() / SAMPLE_RATE
                val envelope = exp(-t * 120f)
                sampleVal += 0.45 * sin(2 * PI * 720 * t) * envelope
            }

            samples[i] = (sampleVal * 32767 * 0.4f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Unlock sound: upward bright spring-release latch
    private fun generateUnlock(): ShortArray {
        val durationMs = 110
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val click1Delay = 0
        val click2Delay = (SAMPLE_RATE * 10) / 1000 // 10ms latch delay

        for (i in 0 until numSamples) {
            var sampleVal = 0.0

            // Click 1 (initial release trigger)
            if (i >= click1Delay) {
                val t = (i - click1Delay).toFloat() / SAMPLE_RATE
                val envelope = exp(-t * 180f)
                sampleVal += 0.45 * sin(2 * PI * 650 * t) * envelope
            }

            // Click 2 (upward bright ring)
            if (i >= click2Delay) {
                val t = (i - click2Delay).toFloat() / SAMPLE_RATE
                val envelope = exp(-t * 150f)
                sampleVal += 0.55 * sin(2 * PI * 2100 * t) * envelope
            }

            samples[i] = (sampleVal * 32767 * 0.4f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Camera shutter: mirror-flip + physical curtain slide
    private fun generateCamera(): ShortArray {
        val durationMs = 160
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val random = Random()

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            var valPercent = 0.0

            // Phase 1: White noise burst for mirror lift (0 - 55ms)
            if (t < 0.055) {
                val env = sin((t / 0.055) * PI)
                val noise = random.nextFloat() * 2f - 1f
                valPercent += noise * env * 0.28
            }

            // Phase 2: Dual mechanical click-slits (35ms - 110ms)
            if (t >= 0.035 && t < 0.110) {
                val tShutter = t - 0.035f
                val env = exp(-tShutter * 90f)
                valPercent += sin(2 * PI * 1750 * tShutter) * env * 0.22
                valPercent += sin(2 * PI * 850 * tShutter) * env * 0.15
            }

            // Phase 3: Settle noise (90ms - 160ms)
            if (t >= 0.090) {
                val tSettle = t - 0.090f
                val env = exp(-tSettle * 130f)
                val noise = random.nextFloat() * 2f - 1f
                valPercent += noise * env * 0.12
            }

            samples[i] = (valPercent * 32767 * 0.55f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Low battery warn: low dual beep
    private fun generateLowBattery(): ShortArray {
        val durationMs = 450
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        val b1Start = 0
        val b1End = (SAMPLE_RATE * 130) / 1000
        val b2Start = (SAMPLE_RATE * 230) / 1000
        val b2End = (SAMPLE_RATE * 360) / 1000

        for (i in 0 until numSamples) {
            var sampleVal = 0.0
            if (i in b1Start until b1End) {
                val t = (i - b1Start).toFloat() / SAMPLE_RATE
                val progress = (i - b1Start).toFloat() / (b1End - b1Start)
                val env = sin(progress * PI)
                sampleVal = sin(2 * PI * 460 * t) * env
            } else if (i in b2Start until b2End) {
                val t = (i - b2Start).toFloat() / SAMPLE_RATE
                val progress = (i - b2Start).toFloat() / (b2End - b2Start)
                val env = sin(progress * PI)
                sampleVal = sin(2 * PI * 460 * t) * env
            }
            samples[i] = (sampleVal * 32767 * 0.45f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Dry clock tick/tock sound
    private fun generateTick(isTock: Boolean): ShortArray {
        val durationMs = 10
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        val freq = if (isTock) 550f else 750f
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 500f)
            val sampleVal = (sin(2 * PI * freq * t) * env * 32767 * 0.25f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Simple Tick
    private fun generateSimpleTick(): ShortArray {
        val durationMs = 60
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / (durationMs.toFloat() / 1000)
            val env = sin(progress * PI) * exp(-progress * 1.5f)
            val sampleVal = (sin(2 * PI * 980 * t) * env * 32767 * 0.35f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Jelly Bean Ack (Classic two-note warm chime)
    private fun generateJellyBeanAck(): ShortArray {
        val durationMs = 240
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        val note1Start = 0
        val note1End = (SAMPLE_RATE * 110) / 1000
        val note2Start = (SAMPLE_RATE * 80) / 1000
        val note2End = (SAMPLE_RATE * 240) / 1000

        for (i in 0 until numSamples) {
            var sampleVal = 0.0

            if (i in note1Start until note1End) {
                val t = (i - note1Start).toFloat() / SAMPLE_RATE
                val progress = (i - note1Start).toFloat() / (note1End - note1Start)
                val env = sin(progress * PI)
                sampleVal += 0.45 * sin(2 * PI * 880 * t) * env
            }

            if (i >= note2Start && i < note2End) {
                val t = (i - note2Start).toFloat() / SAMPLE_RATE
                val progress = (i - note2Start).toFloat() / (note2End - note2Start)
                val env = sin(progress * PI)
                sampleVal += 0.45 * sin(2 * PI * 1320 * t) * env
            }

            samples[i] = (sampleVal * 32767 * 0.45f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Double Beep
    private fun generateDoubleBeep(): ShortArray {
        val durationMs = 250
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        val b1Start = 0
        val b1End = (SAMPLE_RATE * 70) / 1000
        val b2Start = (SAMPLE_RATE * 110) / 1000
        val b2End = (SAMPLE_RATE * 180) / 1000

        for (i in 0 until numSamples) {
            var sampleVal = 0.0
            if (i in b1Start until b1End) {
                val t = (i - b1Start).toFloat() / SAMPLE_RATE
                val progress = (i - b1Start).toFloat() / (b1End - b1Start)
                val env = sin(progress * PI)
                sampleVal = sin(2 * PI * 1440 * t) * env
            } else if (i in b2Start until b2End) {
                val t = (i - b2Start).toFloat() / SAMPLE_RATE
                val progress = (i - b2Start).toFloat() / (b2End - b2Start)
                val env = sin(progress * PI)
                sampleVal = sin(2 * PI * 1440 * t) * env
            }
            samples[i] = (sampleVal * 32767 * 0.4f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Bubble Pop
    private fun generateBubblePop(): ShortArray {
        val durationMs = 120
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val duration = durationMs.toFloat() / 1000
            val progress = t / duration
            val env = sin(progress * PI) * exp(-progress * 2.8f)
            val phase = 2 * PI * (360f * t + 0.5f * (1150f - 360f) * t * t / duration)
            val sampleVal = (sin(phase) * env * 32767 * 0.45f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Ceres (Beautiful three-note smooth bell chime)
    private fun generateCeres(): ShortArray {
        val durationMs = 500
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        val n1Start = 0
        val n1End = (SAMPLE_RATE * 150) / 1000
        val n2Start = (SAMPLE_RATE * 90) / 1000
        val n2End = (SAMPLE_RATE * 240) / 1000
        val n3Start = (SAMPLE_RATE * 180) / 1000
        val n3End = (SAMPLE_RATE * 500) / 1000

        for (i in 0 until numSamples) {
            var sampleVal = 0.0

            // G5 (784 Hz)
            if (i in n1Start until n1End) {
                val t = (i - n1Start).toFloat() / SAMPLE_RATE
                val duration = (n1End - n1Start).toFloat() / SAMPLE_RATE
                val env = sin((t / duration) * PI)
                sampleVal += 0.35 * sin(2 * PI * 784 * t) * env
            }

            // C6 (1046.5 Hz)
            if (i in n2Start until n2End) {
                val t = (i - n2Start).toFloat() / SAMPLE_RATE
                val duration = (n2End - n2Start).toFloat() / SAMPLE_RATE
                val env = sin((t / duration) * PI)
                sampleVal += 0.35 * sin(2 * PI * 1046.5 * t) * env
            }

            // E6 (1318.5 Hz)
            if (i in n3Start until n3End) {
                val t = (i - n3Start).toFloat() / SAMPLE_RATE
                val duration = (n3End - n3Start).toFloat() / SAMPLE_RATE
                val env = if (t < duration * 0.3f) {
                    sin((t / (duration * 0.3f)) * (PI / 2))
                } else {
                    val decayT = t - (duration * 0.3f)
                    val decayDur = duration * 0.7f
                    cos((decayT / decayDur) * (PI / 2))
                }
                sampleVal += 0.42 * sin(2 * PI * 1318.5 * t) * env
            }

            samples[i] = (sampleVal * 32767 * 0.65f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Pixie Dust (Sparkly high bell glissando)
    private fun generatePixieDust(): ShortArray {
        val durationMs = 320
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        val pitches = listOf(2200f, 2750f, 3300f, 3850f, 4400f)
        val delaysMs = listOf(0, 35, 70, 105, 140)

        for (i in 0 until numSamples) {
            var sampleVal = 0.0
            for (idx in pitches.indices) {
                val delaySamples = (SAMPLE_RATE * delaysMs[idx]) / 1000
                if (i >= delaySamples) {
                    val t = (i - delaySamples).toFloat() / SAMPLE_RATE
                    val env = exp(-t * 35f)
                    if (env > 0.01f) {
                        sampleVal += 0.18 * sin(2 * PI * pitches[idx] * t) * env
                    }
                }
            }
            samples[i] = (sampleVal * 32767 * 0.55f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Notification: Teardrop (Liquid droplet down sweep)
    private fun generateTeardrop(): ShortArray {
        val durationMs = 170
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val duration = durationMs.toFloat() / 1000
            val progress = t / duration
            val freqStart = 1350f
            val freqEnd = 450f
            val phase = 2 * PI * (freqStart * t + 0.5f * (freqEnd - freqStart) * t * t / duration)
            val env = sin(progress * PI) * exp(-progress * 1.8f)
            val sampleVal = (sin(phase) * env * 32767 * 0.42f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Multimedia beep chime (synthesized 150ms dual tone)
    private fun generateMultimedia(): ShortArray {
        val durationMs = 150
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / (durationMs.toFloat() / 1000)
            val env = sin(progress * PI)
            val wave = 0.5f * sin(2 * PI * 880 * t) + 0.5f * sin(2 * PI * 1100 * t)
            val sampleVal = (wave * env * 32767 * 0.35f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    // Ringtone preview chime (classic synthesizer ring)
    private fun generateRingtone(): ShortArray {
        val durationMs = 500
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = t / (durationMs.toFloat() / 1000)
            val env = sin(progress * PI) * exp(-progress * 0.8f)
            // Harmonious retro ring modulation
            val wave = 0.6f * sin(2 * PI * 587.33 * t) + 0.4f * sin(2 * PI * 880 * t)
            val tremolo = 1.0f + 0.3f * sin(2 * PI * 12 * t)
            val sampleVal = (wave * env * tremolo * 32767 * 0.4f).toInt()
            samples[i] = sampleVal.coerceIn(-32768, 32767).toShort()
        }
        return samples
    }
}
