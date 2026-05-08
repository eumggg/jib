package com.jib.app.ui.checkin

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.remote.CheckInDto
import com.jib.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val PREFS_NAME = "checkin_cooldowns"
private const val COOLDOWN_MILLIS = 2L * 60 * 60 * 1000 // 2 hours

data class CheckInUiState(
    val recentCheckIns: List<CheckInDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    // ms-since-epoch when the cooldown expires (0 = no cooldown)
    val cooldownUntilMs: Long = 0L,
    val errorMessage: String? = null,
    val justSubmitted: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: CommunityRepository,
    @ApplicationContext appContext: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    fun loadRecentCheckIns(stationId: String) {
        _uiState.update {
            it.copy(
                isLoading = true,
                cooldownUntilMs = prefs.getLong(stationId, 0L),
                errorMessage = null,
            )
        }
        val job = viewModelScope.launch {
            repository.listCheckIns(stationId)
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(recentCheckIns = list.take(5), isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message)
                    }
                }
        }
        activeJobs += job
    }

    fun submitCheckIn(stationId: String, comment: String? = null) {
        if (_uiState.value.isSubmitting) return
        val now = System.currentTimeMillis()
        val cooldownUntil = prefs.getLong(stationId, 0L)
        if (cooldownUntil > now) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        val job = viewModelScope.launch {
            val key = UUID.randomUUID().toString()
            repository.createCheckIn(key, stationId, comment)
                .onSuccess { created ->
                    val expiry = System.currentTimeMillis() + COOLDOWN_MILLIS
                    prefs.edit().putLong(stationId, expiry).apply()
                    val updated = (listOf(created) + _uiState.value.recentCheckIns).take(5)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            recentCheckIns = updated,
                            cooldownUntilMs = expiry,
                            justSubmitted = true,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = e.message ?: "Check-in failed")
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
