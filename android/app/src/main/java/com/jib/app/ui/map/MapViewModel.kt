package com.jib.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.model.Station
import com.jib.app.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val isOffline: Boolean = false,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Kept for unit-test assertions on cancellation.
    internal var fetchJob: Job? = null
        private set

    // Map-viewport-batching lens: called from onCameraIdle with buffered bounds.
    fun fetchStations(swLat: Double, swLng: Double, neLat: Double, neLng: Double) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            var first = true
            repository.getStations(swLat, swLng, neLat, neLng).collect { stations ->
                // First emission is from Room (possibly empty cache); subsequent
                // emissions after the network refresh arrive via Room's Flow.
                _uiState.value = MapUiState(
                    stations = stations,
                    isOffline = first && stations.isEmpty(),
                )
                first = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fetchJob?.cancel()
    }
}
