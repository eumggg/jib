package com.jib.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    // Survives config changes. Emits null when signed out, non-null when signed in.
    // Android-Jetpack-lifecycle lens: flow is scoped to viewModelScope; cancelled in onCleared().
    val currentUser: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        Firebase.auth.addAuthStateListener(listener)
        awaitClose { Firebase.auth.removeAuthStateListener(listener) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Firebase.auth.currentUser,
    )

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _authState.value = authRepository.signIn(email, password).fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(it.message ?: "Sign in failed") },
            )
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _authState.value = authRepository.signUp(email, password).fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(it.message ?: "Registration failed") },
            )
        }
    }

    fun signInWithGoogle(googleIdToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            _authState.value = authRepository.signInWithGoogle(googleIdToken).fold(
                onSuccess = { AuthState.Success(it) },
                onFailure = { AuthState.Error(it.message ?: "Google sign-in failed") },
            )
        }
    }

    fun signOut() {
        authRepository.signOut()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }
}
