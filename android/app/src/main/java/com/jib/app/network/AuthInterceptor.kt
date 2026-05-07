package com.jib.app.network

import com.jib.app.auth.AuthRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// JWT-expiry-discipline lens:
//  1. Attach a fresh (but not force-refreshed) token to every request.
//  2. On 401, close the response, force-refresh the token, and retry exactly once.
//  3. Never cache raw tokens to disk — Firebase SDK handles caching internally.
class AuthInterceptor @Inject constructor(
    private val authRepository: AuthRepository,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authRepository.getIdTokenBlocking(forceRefresh = false)
            ?: return chain.proceed(chain.request())

        val response = chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )

        if (response.code != 401) return response

        // 401 — force-refresh and retry once
        response.close()
        val freshToken = authRepository.getIdTokenBlocking(forceRefresh = true)
            ?: return chain.proceed(chain.request())

        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer $freshToken")
                .build()
        )
    }
}
