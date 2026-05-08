package com.jib.app.ui.station

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.model.Station
import com.jib.app.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationDetailUiState(
    val station: Station? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class StationDetailViewModel @Inject constructor(
    private val repository: StationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val stationId: String = checkNotNull(savedStateHandle["stationId"]) {
        "stationId nav argument is required"
    }

    private val _uiState = MutableStateFlow(StationDetailUiState())
    val uiState: StateFlow<StationDetailUiState> = _uiState.asStateFlow()

    /**
     * Exposes only the station for callers who want the simple value (the issue
     * spec asks for `station: StateFlow<StationDetail?>`). Backed by uiState.
     */
    private val _station = MutableStateFlow<Station?>(null)
    val station: StateFlow<Station?> = _station.asStateFlow()

    // Visible to tests; production code goes through load().
    internal var collectJob: Job? = null
        private set

    init {
        load()
    }

    fun load() {
        collectJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        collectJob = viewModelScope.launch {
            // Surface a network error explicitly even though getStation() swallows it,
            // so the UI can offer Retry while still rendering whatever the cache has.
            try {
                repository.refreshStation(stationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Network error") }
            }
            repository.getStation(stationId).collect { cached ->
                _station.value = cached
                _uiState.update {
                    it.copy(
                        station = cached,
                        // Done loading once we either have a row or we have an error to show.
                        isLoading = cached == null && it.errorMessage == null,
                    )
                }
            }
        }
    }

    fun retry() = load()

    public override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
    }
}
