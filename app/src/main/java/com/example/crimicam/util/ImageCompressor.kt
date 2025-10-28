package com.example.crimicam.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageCompressor {

    /**
     * Compress bitmap with max dimensions and quality
     */
    fun compressBitmap(
        bitmap: Bitmap,
        maxWidth: Int = 800,
        maxHeight: Int = 800,
        quality: Int = 70
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val scaleFactor = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convert bitmap to base64 string
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Convert base64 string to bitmap
     */
    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate size of compressed image
     */
    fun calculateSize(base64String: String): String {
        val sizeInBytes = base64String.length * 3 / 4
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            else -> String.format("%.2f MB", sizeInBytes / (1024f * 1024f))
        }
    }

    /**
     * Compress specifically for face crops (smaller)
     */
    fun compressFaceCrop(bitmap: Bitmap): Bitmap {
        return compressBitmap(bitmap, maxWidth = 300, maxHeight = 300, quality = 80)
    }
}