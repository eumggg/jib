package com.jib.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ReportKind(val wire: String, val label: String) {
    BROKEN("broken", "Broken"),
    CLOSED("closed", "Closed"),
    INCORRECT_INFO("incorrect_info", "Incorrect Info"),
}

data class ReportUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val justSubmitted: Boolean = false,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: CommunityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    fun submit(stationId: String, kind: ReportKind, notes: String?) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        val key = UUID.randomUUID().toString()
        val job = viewModelScope.launch {
            repository.createReport(key, stationId, kind.wire, notes?.takeIf { it.isNotBlank() })
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, justSubmitted = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = e.message ?: "Couldn't submit report")
                    }
                }
        }
        activeJobs += job
    }

    fun consumeJustSubmitted() {
        _uiState.update { it.copy(justSubmitted = false) }
    }

    public override fun onCleared() {
        super.onCleared()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
}
