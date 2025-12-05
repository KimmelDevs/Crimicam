package com.example.crimicam.presentation.main.Map

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crimicam.data.service.CriminalLocation
import com.example.crimicam.data.service.FirestoreCaptureService
import com.example.crimicam.data.service.LocationHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapState(
    val isLoading: Boolean = false,
    val criminalLocations: List<CriminalLocation> = emptyList(),
    val selectedCriminalHistory: List<LocationHistory> = emptyList(),
    val error: String? = null
)

class MapViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

    private val firestoreService = FirestoreCaptureService(context)

    companion object {
        private const val TAG = "MapViewModel"
    }

    /**
     * Load all criminal locations from Firestore
     */
    fun loadCriminalLocations() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val result = firestoreService.getCriminalLocations()

                result.onSuccess { locations ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        criminalLocations = locations,
                        error = null
                    )
                    Log.d(TAG, "Loaded ${locations.size} criminal locations")
                }.onFailure { exception ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load criminal locations"
                    )
                    Log.e(TAG, "Error loading criminal locations", exception)
                }

            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
                Log.e(TAG, "Exception loading criminal locations", e)
            }
        }
    }

    /**
     * Load location history for a specific criminal
     */
    fun loadLocationHistory(criminalId: String) {
        viewModelScope.launch {
            try {
                val result = firestoreService.getCriminalLocationHistory(criminalId)

                result.onSuccess { history ->
                    _state.value = _state.value.copy(
                        selectedCriminalHistory = history
                    )
                    Log.d(TAG, "Loaded ${history.size} location history entries for $criminalId")
                }.onFailure { exception ->
                    Log.e(TAG, "Error loading location history", exception)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading location history", e)
            }
        }
    }

    /**
     * Refresh criminal locations
     */
    fun refresh() {
        loadCriminalLocations()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}