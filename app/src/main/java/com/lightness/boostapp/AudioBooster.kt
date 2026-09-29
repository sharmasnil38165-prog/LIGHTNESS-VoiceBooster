package com.lightness.boostapp

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioBooster(private val audioManager: AudioManager) {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordedData: ByteArray? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    var boostLevel = 5.0f

    fun startAutoRecord(durationMs: Long = 2000, onComplete: (() -> Unit)? = null) {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()

                Thread {
                    recordAudioForDuration(durationMs, onComplete)
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun recordAudioForDuration(durationMs: Long, onComplete: (() -> Unit)?) {
        val sampleCount = (sampleRate * durationMs / 1000).toInt()
        val audioBuffer = ShortArray(sampleCount)
        var totalRead = 0

        while (totalRead < sampleCount) {
            val toRead = minOf(bufferSize / 2, sampleCount - totalRead)
            val readBuffer = ShortArray(toRead)
            val readSize = audioRecord?.read(readBuffer, 0, toRead) ?: 0

            if (readSize > 0) {
                readBuffer.copyInto(audioBuffer, totalRead, 0, readSize)
                totalRead += readSize
            }
        }

        audioRecord?.stop()
        audioRecord?.release()

        recordedData = shortArrayToByteArray(audioBuffer)

        Handler(Looper.getMainLooper()).post {
            playbackWithBoost()
            onComplete?.invoke()
        }
    }

    private fun playbackWithBoost() {
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                audioFormat,
                recordedData!!.size,
                AudioTrack.MODE_STATIC
            )

            val boosted = boostAudioData(recordedData!!)
            audioTrack?.write(boosted, 0, boosted.size)
            audioTrack?.play()

            Handler(Looper.getMainLooper()).postDelayed({
                audioTrack?.stop()
                audioTrack?.release()
            }, (recordedData!!.size / (sampleRate * 2)).toLong() * 1000)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playAudioFileWithBoost(filePath: String, onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                val file = File(filePath)
                val fileBytes = file.readBytes()
                val audioData = if (fileBytes.size > 44) fileBytes.copyOfRange(44, fileBytes.size) else fileBytes

                Handler(Looper.getMainLooper()).post {
                    playbackAudioData(audioData)
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete?.invoke()
            }
        }.start()
    }

    private fun playbackAudioData(audioData: ByteArray) {
        try {
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                audioFormat,
                audioData.size,
                AudioTrack.MODE_STATIC
            )

            val boosted = boostAudioData(audioData)
            audioTrack?.write(boosted, 0, boosted.size)
            audioTrack?.play()

            Handler(Looper.getMainLooper()).postDelayed({
                audioTrack?.stop()
                audioTrack?.release()
            }, (audioData.size / (sampleRate * 2)).toLong() * 1000)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun boostAudioData(rawAudio: ByteArray): ByteArray {
        val boosted = ByteArray(rawAudio.size)

        for (i in 0 until rawAudio.size step 2) {
            if (i + 1 < rawAudio.size) {
                var sample = ((rawAudio[i + 1].toInt() and 0xFF) shl 8) or
                    (rawAudio[i].toInt() and 0xFF)

                if (sample > 32767) sample -= 65536

                var amplified = (sample * boostLevel).toInt()

                amplified = when {
                    amplified > 32767 -> 32767
                    amplified < -32768 -> -32768
                    else -> amplified
                }

                boosted[i] = (amplified and 0xFF).toByte()
                boosted[i + 1] = ((amplified shr 8) and 0xFF).toByte()
            }
        }

        return boosted
    }

    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            byteArray[i * 2] = (shortArray[i] and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((shortArray[i].toInt() shr 8) and 0xFF).toByte()
        }
        return byteArray
    }
}
