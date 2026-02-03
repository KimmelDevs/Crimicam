package com.example.crimicam.presentation.main.Map

import android.location.Location
import android.util.Log
import org.osmdroid.util.GeoPoint

/**
 * Monitors whether the user is moving away from their destination.
 *
 * How it works:
 *  - You call update(currentLocation) every time a GPS tick comes in.
 *  - Internally it compares the new distance-to-destination vs the previous distance.
 *  - If the new distance is LARGER → consecutiveFartherCount goes up by 1.
 *  - If the new distance is SMALLER or EQUAL → counter resets to 0.
 *  - At count == 1 or 2  → fires onWrongDirectionWarning(count)   (notify self)
 *  - At count == 3       → fires onNotifyFriends()                 (notify friends)
 *  - After firing onNotifyFriends the monitor stops automatically.
 *
 * Call reset() when navigation ends or the user removes the destination.
 */
class NavigationMonitor(
    private val destination: GeoPoint,
    private val onWrongDirectionWarning: (Int) -> Unit,   // count = 1 or 2
    private val onNotifyFriends: () -> Unit               // count hit 3
) {
    companion object {
        private const val TAG = "NavigationMonitor"

        /** Minimum distance change in metres to count as "farther" (avoids GPS jitter) */
        private const val DISTANCE_THRESHOLD_METERS = 20.0
    }

    // ── internal state ────────────────────────────────────────────────
    private var previousDistance: Double? = null   // metres to destination last tick
    private var consecutiveFartherCount = 0
    private var isActive = true                   // set to false after friends notified

    // ── public API ────────────────────────────────────────────────────

    /**
     * Feed the latest user location in.  Call this every time your LocationCallback fires.
     * Does nothing if the monitor has already fired onNotifyFriends (i.e. is no longer active).
     */
    fun update(currentLocation: GeoPoint) {
        if (!isActive) return

        val currentDistance = distanceBetween(currentLocation, destination)   // metres
        val prev = previousDistance

        Log.d(TAG, "update → currentDist=${"%.1f".format(currentDistance)}m, prevDist=${prev?.let { "%.1f".format(it) } ?: "null"}, count=$consecutiveFartherCount")

        if (prev != null) {
            val diff = currentDistance - prev

            if (diff > DISTANCE_THRESHOLD_METERS) {
                // ── moving FARTHER ────────────────────────────────────
                consecutiveFartherCount++
                Log.d(TAG, "⬆️  farther by ${"%.1f".format(diff)}m → count=$consecutiveFartherCount")

                when (consecutiveFartherCount) {
                    1, 2 -> {
                        Log.d(TAG, "⚠️  firing onWrongDirectionWarning($consecutiveFartherCount)")
                        onWrongDirectionWarning(consecutiveFartherCount)
                    }
                    3 -> {
                        Log.d(TAG, "🚨 firing onNotifyFriends (count=3)")
                        isActive = false          // stop monitoring after this
                        onNotifyFriends()
                    }
                    // > 3 should never happen because isActive is false, but just in case
                }
            } else if (diff < -DISTANCE_THRESHOLD_METERS) {
                // ── moving CLOSER ─────────────────────────────────────
                if (consecutiveFartherCount > 0) {
                    Log.d(TAG, "⬇️  closer by ${"%.1f".format(-diff)}m → reset count (was $consecutiveFartherCount)")
                }
                consecutiveFartherCount = 0
            }
            // else: within threshold → do nothing (GPS noise)
        }

        previousDistance = currentDistance
    }

    /**
     * Fully reset the monitor — call when navigation ends or destination is removed.
     * After this the monitor can be reused if you call update() again.
     */
    fun reset() {
        previousDistance = null
        consecutiveFartherCount = 0
        isActive = true
        Log.d(TAG, "🔄 reset")
    }

    /** Returns true if the monitor has already fired onNotifyFriends and stopped. */
    fun isStopped(): Boolean = !isActive

    /** How many consecutive "farther" ticks have been recorded right now. */
    fun currentCount(): Int = consecutiveFartherCount

    // ── private helpers ───────────────────────────────────────────────
    /**
     * Returns the distance in metres between two GeoPoints using the
     * Android Location API (works on every osmdroid version).
     */
    private fun distanceBetween(a: GeoPoint, b: GeoPoint): Double {
        val result = FloatArray(1)
        Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            result
        )
        return result[0].toDouble()
    }
}