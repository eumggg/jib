package com.jib.app.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor() {

    private val auth: FirebaseAuth = Firebase.auth

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                auth.signInWithEmailAndPassword(email, password).await().user!!
            }
        }

    suspend fun signUp(email: String, password: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                auth.createUserWithEmailAndPassword(email, password).await().user!!
            }
        }

    suspend fun signInWithGoogle(googleIdToken: String): Result<FirebaseUser> =
        withContext(Dispatchers.IO) {
            runCatching {
                val credential: AuthCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(credential).await().user!!
            }
        }

    fun signOut() = auth.signOut()

    // JWT-expiry-discipline lens: caller passes forceRefresh=false normally;
    // force-refresh only after receiving a 401 from the API (handled in AuthInterceptor).
    suspend fun getIdToken(forceRefresh: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
            }.getOrNull()
        }

    // Blocking variant for use in OkHttp interceptor (already on a background thread).
    fun getIdTokenBlocking(forceRefresh: Boolean = false): String? = runCatching {
        auth.currentUser?.let { Tasks.await(it.getIdToken(forceRefresh))?.token }
    }.getOrNull()
}
