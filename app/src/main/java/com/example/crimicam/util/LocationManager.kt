package com.example.crimicam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Location data class with coordinates and address
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val address: String? = null
)

class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    companion object {
        private const val TAG = "LocationManager"
        private const val LOCATION_TIMEOUT_MS = 10000L // 10 seconds
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current location with timeout and proper error handling
     * Returns null if permissions are not granted or location cannot be retrieved
     */
    suspend fun getCurrentLocation(): LocationData? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return try {
            // Use timeout to prevent hanging
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                getCurrentLocationInternal()
            } ?: run {
                Log.w(TAG, "Location request timed out")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location", e)
            null
        }
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun getCurrentLocationInternal(): LocationData? {
        val cancellationTokenSource = CancellationTokenSource()

        return try {
            // Try to get current location
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            if (location != null) {
                Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                val address = getAddressFromLocation(location)
                LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = address
                )
            } else {
                // Fallback to last known location
                Log.d(TAG, "Current location null, trying last known location")
                getLastKnownLocation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCurrentLocationInternal", e)
            // Try last known location as fallback
            getLastKnownLocation()
        } finally {
            cancellationTokenSource.cancel()
        }
    }

    /**
     * Fallback to last known location if current location fails
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ]
    )
    private suspend fun getLastKnownLocation(): LocationData? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                Log.d(TAG, "Using last known location: ${location.latitude}, ${location.longitude}")
                val address = getAddressFromLocation(location)
                LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    address = address
                )
            } else {
                Log.w(TAG, "No last known location available")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            null
        }
    }

    /**
     * Convert location to address with support for both old and new Geocoder APIs
     */
    private suspend fun getAddressFromLocation(location: Location): String? {
        if (!Geocoder.isPresent()) {
            Log.w(TAG, "Geocoder not available on this device")
            return null
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use new API for Android 13+
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1
                    ) { addresses ->
                        val address = addresses.firstOrNull()?.let { formatAddress(it) }
                        continuation.resume(address)
                    }
                }
            } else {
                // Use deprecated API for older versions
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                addresses?.firstOrNull()?.let { formatAddress(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting address from location", e)
            null
        }
    }

    /**
     * Format address into a readable string
     */
    private fun formatAddress(address: Address): String {
        return buildString {
            // Try different address components in order of preference
            when {
                // Full address line if available
                address.getAddressLine(0) != null -> {
                    append(address.getAddressLine(0))
                }
                // Build from components
                else -> {
                    address.thoroughfare?.let { append(it) }
                    address.subLocality?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.locality?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.adminArea?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                    address.countryName?.let {
                        if (isNotEmpty()) append(", ")
                        append(it)
                    }
                }
            }
        }.takeIf { it.isNotEmpty() } ?: "Unknown Location"
    }

    /**
     * Get coordinates as a formatted string
     */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    /**
     * Get Google Maps URL for the given coordinates
     */
    fun getGoogleMapsUrl(latitude: Double, longitude: Double): String {
        return "https://www.google.com/maps?q=$latitude,$longitude"
    }

    /**
     * Get Google Maps URL with zoom level
     */
    fun getGoogleMapsUrl(latitude: Double, longitude: Double, zoom: Int = 15): String {
        return "https://www.google.com/maps?q=$latitude,$longitude&z=$zoom"
    }

    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}