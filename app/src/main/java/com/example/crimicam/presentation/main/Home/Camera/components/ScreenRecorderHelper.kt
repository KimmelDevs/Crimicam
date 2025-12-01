package com.example.crimicam.presentation.main.Home.Camera.components

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecorderHelper(
    private val context: Context,
    private val onRecordingComplete: (Uri?) -> Unit,
    private val onRecordingError: (String) -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isRecording = false
    private var outputUri: Uri? = null

    private val displayMetrics = DisplayMetrics()
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        }
    }

    fun startRecording(resultCode: Int, data: Intent) {
        if (isRecording) {
            Log.w("ScreenRecorder", "Already recording")
            return
        }

        Log.d("ScreenRecorder", "Starting screen recording...")

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val displayName = "CrimiCam_$timestamp.mp4"

            Log.d("ScreenRecorder", "Creating video file: $displayName")

            // Create MediaStore entry
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/CrimiCam")
                put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            outputUri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (outputUri == null) {
                throw IllegalStateException("Failed to create MediaStore entry")
            }

            Log.d("ScreenRecorder", "MediaStore URI created: $outputUri")

            val fileDescriptor = context.contentResolver.openFileDescriptor(outputUri!!, "w")

            if (fileDescriptor == null) {
                throw IllegalStateException("Failed to open file descriptor")
            }

            Log.d("ScreenRecorder", "File descriptor opened")

            // Setup MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(fileDescriptor.fileDescriptor)
                setVideoSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(8_000_000) // 8 Mbps
                setVideoFrameRate(30)

                Log.d("ScreenRecorder", "MediaRecorder configured - Size: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")

                prepare()
                Log.d("ScreenRecorder", "MediaRecorder prepared")
            }

            // Setup MediaProjection
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            Log.d("ScreenRecorder", "MediaProjection obtained")

            // Create virtual display
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "CrimiCamRecorder",
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            Log.d("ScreenRecorder", "VirtualDisplay created")

            // Start recording
            mediaRecorder?.start()
            isRecording = true

            Log.d("ScreenRecorder", "Screen recording started successfully!")

        } catch (e: Exception) {
            Log.e("ScreenRecorder", "Failed to start recording", e)
            e.printStackTrace()
            cleanup()
            outputUri?.let { context.contentResolver.delete(it, null, null) }
            outputUri = null
            onRecordingError("Failed to start recording: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.w("ScreenRecorder", "Not currently recording")
            return
        }

        Log.d("ScreenRecorder", "Stopping recording...")

        // Set flag immediately to prevent double-stop
        isRecording = false

        // Add a small delay to ensure all frames are written
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                // Stop media recorder first
                mediaRecorder?.apply {
                    try {
                        stop()
                        Log.d("ScreenRecorder", "MediaRecorder stopped")
                    } catch (e: Exception) {
                        Log.e("ScreenRecorder", "Error stopping MediaRecorder", e)
                    }
                    release()
                    Log.d("ScreenRecorder", "MediaRecorder released")
                }

                // Release virtual display
                virtualDisplay?.release()
                Log.d("ScreenRecorder", "VirtualDisplay released")

                // Stop media projection
                mediaProjection?.stop()
                Log.d("ScreenRecorder", "MediaProjection stopped")

                // Mark as complete in MediaStore
                outputUri?.let { uri ->
                    Log.d("ScreenRecorder", "Processing output URI: $uri")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                        }
                        val updated = context.contentResolver.update(uri, values, null, null)
                        Log.d("ScreenRecorder", "Updated IS_PENDING flag, rows affected: $updated")
                    }

                    // Trigger media scan
                    context.sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
                    )

                    Log.d("ScreenRecorder", "Recording saved successfully: $uri")
                    onRecordingComplete(uri)
                } ?: run {
                    Log.e("ScreenRecorder", "Output URI is null!")
                    onRecordingError("Recording failed - no output file")
                }

            } catch (e: Exception) {
                Log.e("ScreenRecorder", "Error stopping recording", e)
                e.printStackTrace()
                outputUri?.let { context.contentResolver.delete(it, null, null) }
                onRecordingError("Failed to stop recording: ${e.message}")
            } finally {
                cleanup()
            }
        }, 300) // 300ms delay to ensure buffer is flushed
    }

    private fun cleanup() {
        isRecording = false
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
        outputUri = null
    }

    fun isRecordingActive() = isRecording

    companion object {
        const val SCREEN_RECORD_REQUEST_CODE = 1001
    }
}