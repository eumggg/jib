package com.jib.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jib.app.auth.AuthRepository
import com.jib.app.data.remote.UserActivityResponse
import com.jib.app.data.remote.UserDto
import com.jib.app.data.repository.CommunityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserDto? = null,
    val activity: UserActivityResponse = UserActivityResponse(),
    val isLoading: Boolean = false,
    val isSavingName: Boolean = false,
    val errorMessage: String? = null,
    // Falls back to Firebase Auth when the backend hasn't returned yet.
    val firebasePhotoUrl: String? = null,
    val firebaseEmail: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    init {
        val fbUser = authRepository.currentUser
        _uiState.update {
            it.copy(
                firebasePhotoUrl = fbUser?.photoUrl?.toString(),
                firebaseEmail = fbUser?.email,
            )
        }
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val job = viewModelScope.launch {
            repository.getCurrentUser()
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(errorMessage = e.message) }
                }
            repository.getCurrentUserActivity()
                .onSuccess { activity ->
                    _uiState.update { it.copy(activity = activity, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: it.errorMessage)
                    }
                }
        }
        activeJobs += job
    }

    fun saveDisplayName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || _uiState.value.isSavingName) return
        if (trimmed == _uiState.value.user?.displayName) return

        _uiState.update { it.copy(isSavingName = true, errorMessage = null) }
        val job = viewModelScope.launch {
            repository.updateDisplayName(trimmed)
                .onSuccess { user ->
                    _uiState.update { it.copy(user = user, isSavingName = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSavingName = false, errorMessage = e.message ?: "Save failed")
                    }
                }
        }
        activeJobs += job
    }

    public override fun onCleared() {
        super.onCleared()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
}
