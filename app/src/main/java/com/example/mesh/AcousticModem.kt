package com.example.mesh

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Acoustic Near-Ultrasonic Data Modem.
 * Provides an air-gapped acoustic fallback data channel operating in the 17.5 kHz - 19.5 kHz band.
 * Operates when RF radios (Bluetooth / Wi-Fi) are disabled, jammed, or in Faraday environments.
 */
class AcousticModem(private val context: Context) {

    private val TAG = "AcousticModem"
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastReceivedData = MutableStateFlow<String?>(null)
    val lastReceivedData: StateFlow<String?> = _lastReceivedData.asStateFlow()

    private var listeningJob: Job? = null

    companion object {
        const val SAMPLE_RATE = 44100
        const val TONE_DURATION_MS = 60
        const val PREAMBLE_FREQ = 18200.0
        const val BIT_0_FREQ = 17600.0
        const val BIT_1_FREQ = 18800.0
        const val STOP_FREQ = 19400.0
    }

    /**
     * Transmits an acoustic data packet over device speaker.
     */
    suspend fun transmitAcousticData(payload: String, onComplete: (() -> Unit)? = null) {
        if (_isTransmitting.value) return
        _isTransmitting.value = true

        withContext(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val samplesPerTone = (SAMPLE_RATE * TONE_DURATION_MS / 1000)
                val totalSamples = mutableListOf<Short>()

                fun appendTone(frequency: Double, durationMultiplier: Int = 1) {
                    val count = samplesPerTone * durationMultiplier
                    for (i in 0 until count) {
                        val angle = 2.0 * PI * i / (SAMPLE_RATE / frequency)
                        // Smooth envelope window at edges to eliminate speaker clicks
                        val window = sin(PI * i / count)
                        val sample = (sin(angle) * Short.MAX_VALUE * 0.85 * window).toInt().toShort()
                        totalSamples.add(sample)
                    }
                }

                // 1. Preamble sync chirp
                appendTone(PREAMBLE_FREQ, durationMultiplier = 3)

                // 2. Transmit bytes bit by bit
                val bytes = payload.toByteArray(Charsets.UTF_8)
                for (b in bytes) {
                    for (bitIndex in 7 downTo 0) {
                        val bit = (b.toInt() shr bitIndex) and 1
                        if (bit == 1) {
                            appendTone(BIT_1_FREQ)
                        } else {
                            appendTone(BIT_0_FREQ)
                        }
                    }
                }

                // 3. Stop delimiter tone
                appendTone(STOP_FREQ, durationMultiplier = 2)

                val audioData = totalSamples.toShortArray()
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, audioData.size * 2)

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
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(audioData, 0, audioData.size)
                track.play()

                // Wait for playback to complete
                val playDurationMs = (audioData.size * 1000L / SAMPLE_RATE) + 200
                kotlinx.coroutines.delay(playDurationMs)

            } catch (e: Exception) {
                Log.e(TAG, "Acoustic transmission failed", e)
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (ignored: Exception) {}
                _isTransmitting.value = false
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Starts listening for near-ultrasonic acoustic transmissions using device microphone.
     */
    fun startListening(onMessageDecoded: (String) -> Unit) {
        if (_isListening.value) return
        _isListening.value = true

        listeningJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, SAMPLE_RATE / 4)
            var recorder: AudioRecord? = null

            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord failed to initialize")
                    _isListening.value = false
                    return@launch
                }

                recorder.startRecording()
                val audioBuffer = ShortArray(SAMPLE_RATE * TONE_DURATION_MS / 1000)
                val bitStream = mutableListOf<Int>()
                var inPreamble = false

                while (isActive && _isListening.value) {
                    val read = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (read <= 0) continue

                    val pMag = goertzelMagnitude(audioBuffer, read, PREAMBLE_FREQ)
                    val bit0Mag = goertzelMagnitude(audioBuffer, read, BIT_0_FREQ)
                    val bit1Mag = goertzelMagnitude(audioBuffer, read, BIT_1_FREQ)
                    val stopMag = goertzelMagnitude(audioBuffer, read, STOP_FREQ)

                    val threshold = 2.5e6

                    if (!inPreamble) {
                        if (pMag > threshold && pMag > bit0Mag * 1.5 && pMag > bit1Mag * 1.5) {
                            inPreamble = true
                            bitStream.clear()
                        }
                    } else {
                        if (stopMag > threshold && stopMag > bit0Mag * 1.5 && stopMag > bit1Mag * 1.5) {
                            inPreamble = false
                            // Decode accumulated bits
                            val decoded = decodeBits(bitStream)
                            if (decoded.isNotBlank()) {
                                _lastReceivedData.value = decoded
                                withContext(Dispatchers.Main) {
                                    onMessageDecoded(decoded)
                                }
                            }
                            bitStream.clear()
                        } else if (bit1Mag > threshold && bit1Mag > bit0Mag * 1.2) {
                            bitStream.add(1)
                        } else if (bit0Mag > threshold && bit0Mag > bit1Mag * 1.2) {
                            bitStream.add(0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in acoustic listener", e)
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (ignored: Exception) {}
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        listeningJob?.cancel()
        listeningJob = null
    }

    private fun decodeBits(bits: List<Int>): String {
        val bytes = mutableListOf<Byte>()
        for (i in 0 until bits.size - 7 step 8) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i + j]
            }
            bytes.add(b.toByte())
        }
        return try {
            String(bytes.toByteArray(), Charsets.UTF_8).filter { it.code in 32..126 }
        } catch (e: Exception) {
            ""
        }
    }

    private fun goertzelMagnitude(samples: ShortArray, length: Int, targetFreq: Double): Double {
        val k = (0.5 + (length * targetFreq / SAMPLE_RATE)).toInt()
        val omega = (2.0 * PI * k) / length
        val coeff = 2.0 * cos(omega)

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until length) {
            q0 = coeff * q1 - q2 + samples[i]
            q2 = q1
            q1 = q0
        }

        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }
}
