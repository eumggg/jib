package com.jib.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.jib.app.data.places.PlaceSuggestion
import com.jib.app.data.places.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val isExpanded: Boolean = false,
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _placeSelected = MutableSharedFlow<LatLng>(extraBufferCapacity = 1)
    val placeSelected: SharedFlow<LatLng> = _placeSelected.asSharedFlow()

    // Per Places billing: one session token spans the autocomplete predictions
    // plus the final fetchPlace call. Rotated after every successful selection.
    private var sessionToken: AutocompleteSessionToken = AutocompleteSessionToken.newInstance()

    // Debounced query stream — autocomplete only fires after the user pauses.
    // Tracked so onCleared() cancels it deterministically.
    internal val autocompleteJob: Job = _uiState
        .map { it.query }
        .distinctUntilChanged()
        .debounce(DEBOUNCE_MS)
        .flatMapLatest { query ->
            flow {
                if (query.isBlank()) {
                    emit(emptyList())
                } else {
                    emit(placesRepository.autocomplete(query, sessionToken))
                }
            }
        }
        .onEach { suggestions ->
            _uiState.value = _uiState.value.copy(suggestions = suggestions)
        }
        .launchIn(viewModelScope)

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, isExpanded = query.isNotBlank())
    }

    fun onExpandedChange(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isExpanded = expanded)
    }

    fun onSuggestionSelected(suggestion: PlaceSuggestion) {
        viewModelScope.launch {
            val latLng = placesRepository.fetchLatLng(suggestion.placeId, sessionToken)
            sessionToken = AutocompleteSessionToken.newInstance()
            _uiState.value = _uiState.value.copy(
                query = suggestion.primaryText,
                suggestions = emptyList(),
                isExpanded = false,
            )
            if (latLng != null) {
                _placeSelected.tryEmit(latLng)
            }
        }
    }

    fun clearQuery() {
        _uiState.value = SearchUiState()
    }

    override fun onCleared() {
        super.onCleared()
        autocompleteJob.cancel()
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
