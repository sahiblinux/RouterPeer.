package com.example.ui.mesh

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility for recording and playing voice notes, compressing photos, and handling video attachments
 * for end-to-end encrypted mesh transmission.
 */
object MediaUtils {
    private const val TAG = "MediaUtils"

    /**
     * Compresses a photo from an Android content Uri to an encrypted-mesh-ready JPEG (max ~1024px, 75% quality).
     */
    suspend fun processPickedImage(context: Context, imageUri: Uri): Pair<File, ByteArray>? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val originalBmp = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBmp == null) return@withContext null

            // Scale down if larger than 1024px
            val maxDim = 1024
            val width = originalBmp.width
            val height = originalBmp.height
            val scaledBmp = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio > 1f) {
                    targetW = maxDim
                    targetH = (maxDim / ratio).toInt()
                } else {
                    targetH = maxDim
                    targetW = (maxDim * ratio).toInt()
                }
                Bitmap.createScaledBitmap(originalBmp, targetW, targetH, true)
            } else {
                originalBmp
            }

            val baos = ByteArrayOutputStream()
            scaledBmp.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()

            val mediaDir = File(context.filesDir, "mesh_media").apply { mkdirs() }
            val outputFile = File(mediaDir, "img_${UUID.randomUUID().toString().take(8)}.jpg")
            outputFile.writeBytes(bytes)

            Pair(outputFile, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            null
        }
    }

    /**
     * Reads a video from an Android content Uri, saves to local app directory, and checks metadata.
     */
    suspend fun processPickedVideo(context: Context, videoUri: Uri): Triple<File, ByteArray, Long>? = withContext(Dispatchers.IO) {
        try {
            val mediaDir = File(context.filesDir, "mesh_media").apply { mkdirs() }
            val outputFile = File(mediaDir, "vid_${UUID.randomUUID().toString().take(8)}.mp4")

            val inputStream = context.contentResolver.openInputStream(videoUri) ?: return@withContext null
            val outputStream = FileOutputStream(outputFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            val bytes = outputFile.readBytes()

            var durationMs = 0L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(outputFile.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durStr?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract video duration", e)
            }

            Triple(outputFile, bytes, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing video", e)
            null
        }
    }
}

/**
 * Controller for recording Voice Notes with live timer tracking.
 */
class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimestamp = 0L

    fun startRecording(): Boolean {
        try {
            val mediaDir = File(context.filesDir, "mesh_media").apply { mkdirs() }
            val file = File(mediaDir, "voice_${UUID.randomUUID().toString().take(8)}.m4a")
            currentOutputFile = file

            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(32000)
            rec.setAudioSamplingRate(24000)
            rec.setOutputFile(file.absolutePath)

            rec.prepare()
            rec.start()
            recorder = rec
            startTimestamp = System.currentTimeMillis()
            return true
        } catch (e: Exception) {
            Log.e("VoiceNoteRecorder", "Failed to start recording", e)
            currentOutputFile?.delete()
            currentOutputFile = null
            recorder?.release()
            recorder = null
            return false
        }
    }

    fun stopRecording(): Triple<File, ByteArray, Long>? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null

            val durationMs = (System.currentTimeMillis() - startTimestamp).coerceAtLeast(500L)
            val file = currentOutputFile
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                Triple(file, bytes, durationMs)
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            Log.e("VoiceNoteRecorder", "Failed to stop recording", e)
            currentOutputFile?.delete()
            recorder?.release()
            recorder = null
            null
        } finally {
            currentOutputFile = null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
    }
}

/**
 * Global audio player manager for chat audio playback.
 */
object AudioPlaybackManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentlyPlayingUri = MutableStateFlow<String?>(null)
    val currentlyPlayingUri: StateFlow<String?> = _currentlyPlayingUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressFraction = MutableStateFlow(0f)
    val progressFraction: StateFlow<Float> = _progressFraction.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    fun togglePlay(filePath: String) {
        if (_currentlyPlayingUri.value == filePath && _isPlaying.value) {
            pause()
        } else if (_currentlyPlayingUri.value == filePath && !_isPlaying.value) {
            resume()
        } else {
            play(filePath)
        }
    }

    fun play(filePath: String) {
        stop()
        try {
            val player = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progressFraction.value = 0f
                    _currentPositionMs.value = 0L
                    progressJob?.cancel()
                }
                start()
            }
            mediaPlayer = player
            _currentlyPlayingUri.value = filePath
            _isPlaying.value = true

            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlaybackManager", "Failed to play audio file: $filePath", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun resume() {
        mediaPlayer?.start()
        _isPlaying.value = true
        startProgressTracker()
    }

    fun stop() {
        progressJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _currentlyPlayingUri.value = null
        _isPlaying.value = false
        _progressFraction.value = 0f
        _currentPositionMs.value = 0L
    }

    fun seekToFraction(fraction: Float) {
        mediaPlayer?.let { player ->
            val total = player.duration
            if (total > 0) {
                val targetMs = (total * fraction).toInt()
                player.seekTo(targetMs)
                _progressFraction.value = fraction
                _currentPositionMs.value = targetMs.toLong()
            }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        val total = player.duration
                        if (total > 0) {
                            _progressFraction.value = pos.toFloat() / total.toFloat()
                            _currentPositionMs.value = pos.toLong()
                        }
                    }
                }
                delay(100)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
