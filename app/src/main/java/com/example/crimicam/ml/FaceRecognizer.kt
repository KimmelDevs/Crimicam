package com.example.crimicam.ml

import com.example.crimicam.data.model.PersonImage
import kotlin.math.pow
import kotlin.math.sqrt

object FaceRecognizer {

    /**
     * Compare two face feature maps and return similarity score (0-1)
     * Higher score = more similar
     */
    fun compareFaces(
        features1: Map<String, Float>,
        features2: Map<String, Float>
    ): Float {
        if (features1.isEmpty() || features2.isEmpty()) return 0f

        val commonKeys = features1.keys.intersect(features2.keys)
        if (commonKeys.isEmpty()) return 0f

        // Calculate Euclidean distance
        var sumSquaredDiff = 0f
        var count = 0

        commonKeys.forEach { key ->
            val diff = features1[key]!! - features2[key]!!
            sumSquaredDiff += diff.pow(2)
            count++
        }

        val distance = sqrt(sumSquaredDiff / count)

        // Convert distance to similarity score (0-1)
        // Smaller distance = higher similarity
        val similarity = 1f / (1f + distance)

        return similarity
    }

    /**
     * Find the best match from a list of person images
     * Returns triple of (personId, personName, confidence score)
     */
    fun findBestMatch(
        capturedFeatures: Map<String, Float>,
        personImages: List<PersonImage>,
        threshold: Float = 0.6f // Minimum confidence to consider a match
    ): Triple<String?, String?, Float> {
        if (personImages.isEmpty()) return Triple(null, null, 0f)

        var bestPersonId: String? = null
        var bestScore = 0f

        // Compare against all images and find the best match
        personImages.forEach { image ->
            val score = compareFaces(capturedFeatures, image.faceFeatures)
            if (score > bestScore) {
                bestScore = score
                bestPersonId = image.personId
            }
        }

        // Only return match if score is above threshold
        return if (bestScore >= threshold && bestPersonId != null) {
            Triple(bestPersonId, null, bestScore) // Name will be fetched separately
        } else {
            Triple(null, null, bestScore)
        }
    }

    /**
     * Compare captured features against a single person's all images
     * Returns the best match score among all their images
     */
    fun matchAgainstPerson(
        capturedFeatures: Map<String, Float>,
        personImages: List<PersonImage>
    ): Float {
        if (personImages.isEmpty()) return 0f

        var bestScore = 0f
        personImages.forEach { image ->
            val score = compareFaces(capturedFeatures, image.faceFeatures)
            if (score > bestScore) {
                bestScore = score
            }
        }

        return bestScore
    }

    /**
     * Calculate average similarity across multiple images of the same person
     * More robust than single image comparison
     */
    fun averageMatchScore(
        capturedFeatures: Map<String, Float>,
        personImages: List<PersonImage>
    ): Float {
        if (personImages.isEmpty()) return 0f

        var totalScore = 0f
        personImages.forEach { image ->
            totalScore += compareFaces(capturedFeatures, image.faceFeatures)
        }

        return totalScore / personImages.size
    }
}