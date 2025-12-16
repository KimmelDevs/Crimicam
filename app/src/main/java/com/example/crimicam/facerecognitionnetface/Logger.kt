package com.example.crimicam.facerecognitionnetface

import android.util.Log

/**
 * Modern logger that uses Android's Log system
 * Compatible with both View and Compose architectures
 */
class Logger {

    companion object {
        private const val TAG = "FaceRecognition"

        // Log levels
        fun log(message: String) {
            Log.d(TAG, message)
        }

        fun logDebug(message: String) {
            Log.d(TAG, message)
        }

        fun logInfo(message: String) {
            Log.i(TAG, message)
        }

        fun logWarning(message: String) {
            Log.w(TAG, message)
        }

        fun logError(message: String, throwable: Throwable? = null) {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }

        // For backwards compatibility if other code still uses it
        @Deprecated("Use log() or specific log level methods")
        fun logToTextView(message: String) {
            log(message)
        }
    }
}