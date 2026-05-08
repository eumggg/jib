package com.jib.app.ui.photo

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.jib.app.data.remote.PhotoDto
import com.jib.app.data.repository.CommunityRepository
import com.jib.app.data.util.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

sealed interface UploadState {
    data object Idle : UploadState
    data class Uploading(val progress: Float) : UploadState
    data object Done : UploadState
    data class Error(val message: String) : UploadState
}

data class PhotoUiState(
    val photos: List<PhotoDto> = emptyList(),
    val isLoading: Boolean = false,
    val uploadState: UploadState = UploadState.Idle,
    val errorMessage: String? = null,
)

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val repository: CommunityRepository,
    private val compressor: ImageCompressor,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    private val activeJobs = mutableListOf<Job>()

    fun loadPhotos(stationId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val job = viewModelScope.launch {
            repository.listPhotos(stationId)
                .onSuccess { list ->
                    _uiState.update { it.copy(photos = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
        }
        activeJobs += job
    }

    /**
     * Compress the picked image, upload to Firebase Storage at
     * `stations/{stationId}/{uuid}.jpg`, then POST to /photos with the download URL.
     */
    fun uploadPhoto(stationId: String, uri: Uri) {
        if (_uiState.value.uploadState is UploadState.Uploading) return
        _uiState.update { it.copy(uploadState = UploadState.Uploading(0f)) }

        val job = viewModelScope.launch {
            val compressed = compressor.compress(appContext, uri)
            val bytes = compressed.getOrElse { e ->
                _uiState.update {
                    it.copy(uploadState = UploadState.Error(e.message ?: "Compression failed"))
                }
                return@launch
            }

            val uuid = UUID.randomUUID().toString()
            val ref = Firebase.storage.reference
                .child("stations/$stationId/$uuid.jpg")

            try {
                val downloadUrl = withContext(Dispatchers.IO) {
                    val task = ref.putBytes(bytes)
                    task.addOnProgressListener { snap ->
                        val total = snap.totalByteCount.takeIf { it > 0 } ?: 1L
                        val pct = (snap.bytesTransferred.toFloat() / total).coerceIn(0f, 1f)
                        _uiState.update { it.copy(uploadState = UploadState.Uploading(pct)) }
                    }
                    task.await()
                    ref.downloadUrl.await().toString()
                }

                val key = UUID.randomUUID().toString()
                repository.createPhoto(key, stationId, downloadUrl)
                    .onSuccess { created ->
                        _uiState.update {
                            it.copy(
                                uploadState = UploadState.Done,
                                photos = listOf(created) + it.photos,
                            )
                        }
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(uploadState = UploadState.Error(e.message ?: "Save failed"))
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uploadState = UploadState.Error(e.message ?: "Upload failed"))
                }
            }
        }
        activeJobs += job
    }

    fun resetUploadState() {
        _uiState.update { it.copy(uploadState = UploadState.Idle) }
    }

    public override fun onCleared() {
        super.onCleared()
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
}
