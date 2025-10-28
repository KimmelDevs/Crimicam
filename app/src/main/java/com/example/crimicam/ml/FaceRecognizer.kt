package com.example.crimicam.ml


import com.example.crimicam.data.model.KnownPerson
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
     * Find the best match from a list of known people
     * Returns pair of (matched person, confidence score)
     */
    fun findBestMatch(
        capturedFeatures: Map<String, Float>,
        knownPeople: List<KnownPerson>,
        threshold: Float = 0.6f // Minimum confidence to consider a match
    ): Pair<KnownPerson?, Float> {
        if (knownPeople.isEmpty()) return Pair(null, 0f)

        var bestMatch: KnownPerson? = null
        var bestScore = 0f

        knownPeople.forEach { person ->
            val score = compareFaces(capturedFeatures, person.faceFeatures)
            if (score > bestScore) {
                bestScore = score
                bestMatch = person
            }
        }

        // Only return match if score is above threshold
        return if (bestScore >= threshold) {
            Pair(bestMatch, bestScore)
        } else {
            Pair(null, bestScore)
        }
    }
}