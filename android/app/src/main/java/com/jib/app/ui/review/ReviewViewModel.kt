package com.jib.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.data.remote.ReviewDto
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

data class ReviewUiState(
    val reviews: List<ReviewDto> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val justSubmitted: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: CommunityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    fun loadReviews(stationId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val job = viewModelScope.launch {
            repository.listReviews(stationId)
                .onSuccess { list ->
                    _uiState.update { it.copy(reviews = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
        activeJobs += job
    }

    fun submitReview(stationId: String, rating: Int, body: String?) {
        if (_uiState.value.isSubmitting) return
        if (rating !in 1..5) {
            _uiState.update { it.copy(errorMessage = "Please pick a star rating") }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        val key = UUID.randomUUID().toString()
        val job = viewModelScope.launch {
            repository.createReview(key, stationId, rating, body?.takeIf { it.isNotBlank() })
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            reviews = listOf(created) + it.reviews,
                            justSubmitted = true,
                        )
                    }
                }
                .onFailure { e ->
                    // Backend returns 409 on duplicate user-on-station. Surface a clean message.
                    val msg = if (e.message?.contains("409") == true) {
                        "You've already reviewed this station."
                    } else {
                        e.message ?: "Couldn't submit review"
                    }
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = msg) }
                }
        }
        activeJobs += job
    }

    fun consumeJustSubmitted() {
        _uiState.update { it.copy(justSubmitted = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    public override fun onCleared() {
        super.onCleared()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
}
