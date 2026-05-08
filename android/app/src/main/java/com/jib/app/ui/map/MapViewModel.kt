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

data class Bounds(val swLat: Double, val swLng: Double, val neLat: Double, val neLng: Double)

data class MapUiState(
    val stations: List<Station> = emptyList(),
    val isOffline: Boolean = false,
    val selectedConnector: ConnectorType? = null,
    /** True after at least one fetch has completed and the result was empty. */
    val isEmptyResult: Boolean = false,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    // Last viewport bounds — kept so a filter change re-fetches against the
    // user's current map view without waiting for the next camera-idle event.
    private var lastBounds: Bounds? = null

    // Kept for unit-test assertions on cancellation.
    internal var fetchJob: Job? = null
        private set

    // Map-viewport-batching lens: called from onCameraIdle with buffered bounds.
    fun fetchStations(swLat: Double, swLng: Double, neLat: Double, neLng: Double) {
        lastBounds = Bounds(swLat, swLng, neLat, neLng)
        refetch()
    }

    /** Filter persists across pan/zoom because every refetch reads it from state. */
    fun setConnectorFilter(connector: ConnectorType?) {
        if (_uiState.value.selectedConnector == connector) return
        _uiState.value = _uiState.value.copy(selectedConnector = connector)
        refetch()
    }

    private fun refetch() {
        val bounds = lastBounds ?: return
        fetchJob?.cancel()
        val connectorWire = _uiState.value.selectedConnector?.wireValue
        fetchJob = viewModelScope.launch {
            var first = true
            repository.getStations(
                swLat = bounds.swLat,
                swLng = bounds.swLng,
                neLat = bounds.neLat,
                neLng = bounds.neLng,
                connectorType = connectorWire,
            ).collect { stations ->
                _uiState.value = _uiState.value.copy(
                    stations = stations,
                    // First emission is from Room: an empty cache here means we
                    // are offline (or have no data yet), not a filtered-out result.
                    isOffline = first && stations.isEmpty() && connectorWire == null,
                    isEmptyResult = !first && stations.isEmpty(),
                )
                first = false
            }
        }
    }

    public override fun onCleared() {
        super.onCleared()
        fetchJob?.cancel()
    }
}
