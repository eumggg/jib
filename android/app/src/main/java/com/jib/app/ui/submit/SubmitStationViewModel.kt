package com.jib.app.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class SubmitStep { Location, Details, Review }

sealed interface SubmitResult {
    data object Idle : SubmitResult
    data object Submitting : SubmitResult
    data class Success(val stationId: String) : SubmitResult
    data class Error(val message: String) : SubmitResult
}

data class SubmitStationUiState(
    val step: SubmitStep = SubmitStep.Location,
    // Location step
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    // Details step
    val name: String = "",
    val connectorTypes: Set<String> = emptySet(),
    val powerKw: String = "",          // raw text — parsed on submit
    val networkOperator: String = "",
    // Validation
    val nameError: Boolean = false,
    val locationError: Boolean = false,
    // Submission
    val result: SubmitResult = SubmitResult.Idle,
    // Stable client id used as idempotency key. Stays constant across retries.
    val idempotencyKey: String = UUID.randomUUID().toString(),
)

@HiltViewModel
class SubmitStationViewModel @Inject constructor(
    private val repository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitStationUiState())
    val uiState: StateFlow<SubmitStationUiState> = _uiState.asStateFlow()

    fun setLocation(lat: Double, lng: Double) {
        _uiState.update { it.copy(latitude = lat, longitude = lng, locationError = false) }
    }

    fun setAddress(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name, nameError = false) }
    }

    fun toggleConnector(type: String) {
        _uiState.update {
            val next = it.connectorTypes.toMutableSet().apply {
                if (!add(type)) remove(type)
            }
            it.copy(connectorTypes = next)
        }
    }

    fun setPowerKw(text: String) {
        _uiState.update { it.copy(powerKw = text) }
    }

    fun setNetworkOperator(text: String) {
        _uiState.update { it.copy(networkOperator = text) }
    }

    fun goTo(step: SubmitStep) {
        _uiState.update { it.copy(step = step) }
    }

    /** Move forward one step, applying step-specific validation. Returns true if advanced. */
    fun advance(): Boolean {
        val s = _uiState.value
        return when (s.step) {
            SubmitStep.Location -> {
                if (s.latitude == null || s.longitude == null) {
                    _uiState.update { it.copy(locationError = true) }
                    false
                } else {
                    _uiState.update { it.copy(step = SubmitStep.Details) }
                    true
                }
            }
            SubmitStep.Details -> {
                if (s.name.isBlank()) {
                    _uiState.update { it.copy(nameError = true) }
                    false
                } else {
                    _uiState.update { it.copy(step = SubmitStep.Review) }
                    true
                }
            }
            SubmitStep.Review -> false
        }
    }

    fun back() {
        _uiState.update {
            val prev = when (it.step) {
                SubmitStep.Location -> SubmitStep.Location
                SubmitStep.Details -> SubmitStep.Location
                SubmitStep.Review -> SubmitStep.Details
            }
            it.copy(step = prev)
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.latitude == null || s.longitude == null) {
            _uiState.update { it.copy(locationError = true, step = SubmitStep.Location) }
            return
        }
        if (s.name.isBlank()) {
            _uiState.update { it.copy(nameError = true, step = SubmitStep.Details) }
            return
        }
        _uiState.update { it.copy(result = SubmitResult.Submitting) }
        viewModelScope.launch {
            // Reuses idempotencyKey across retries — backend returns the existing record (200) on duplicate.
            val outcome = repository.submitStation(
                idempotencyKey = s.idempotencyKey,
                name = s.name.trim(),
                latitude = s.latitude,
                longitude = s.longitude,
                connectorTypes = s.connectorTypes.toList(),
                powerKw = s.powerKw.toDoubleOrNull(),
                networkOperator = s.networkOperator.trim().ifBlank { null },
            )
            _uiState.update {
                outcome.fold(
                    onSuccess = { station -> it.copy(result = SubmitResult.Success(station.id)) },
                    onFailure = { err -> it.copy(result = SubmitResult.Error(err.message ?: "Submission failed")) },
                )
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(result = SubmitResult.Idle) }
        submit()
    }

    fun acknowledgeResult() {
        _uiState.update { it.copy(result = SubmitResult.Idle) }
    }
}
