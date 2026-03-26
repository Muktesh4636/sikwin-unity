package com.sikwin.app.data.api

import com.sikwin.app.data.auth.SessionManager
import com.sikwin.app.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = Constants.BASE_URL
    private const val AUTH_RETRY_HEADER = "X-Sikwin-Auth-Retry"
    private var sessionManager: SessionManager? = null

    fun init(manager: SessionManager) {
        sessionManager = manager
    }

    fun getSessionManager(): SessionManager? = sessionManager

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var r: okhttp3.Response? = response
        var count = 1
        while (r?.priorResponse != null) {
            count++
            r = r.priorResponse
        }
        return count
    }

    private fun looksLikeSessionInvalidated(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("session_invalidated") ||
            lower.contains("logged in on another device")
    }

    private fun looksLikeAuthError(body: String): Boolean {
        val lower = body.lowercase()
        return looksLikeSessionInvalidated(body) ||
            lower.contains("token_not_valid") ||
            lower.contains("not authenticated") ||
            lower.contains("authentication") ||
            lower.contains("unauthorized")
    }

    private fun tryUnityRecoveryAndBuildRetry(response: okhttp3.Response): okhttp3.Request? {
        return try {
            val allowUnityRecovery = try { sessionManager?.wasUnityLaunchRecent(windowMs = 10 * 60 * 1000L) == true } catch (_: Exception) { false }
            if (!allowUnityRecovery) return null

            val recovered = try { sessionManager?.syncAuthFromUnityPrefs() == true } catch (_: Exception) { false }
            val newAccess = sessionManager?.fetchAuthToken()
            if (recovered && !newAccess.isNullOrBlank()) {
                android.util.Log.w("RetrofitClient", "Unity recovery: adopted newer token; retrying once")
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .header(AUTH_RETRY_HEADER, "1")
                    .build()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(logging)
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            sessionManager?.fetchAuthToken()?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            val resp = chain.proceed(requestBuilder.build())
            // Do NOT hard-logout on generic 403 (could be a permission issue or a transient edge case).
            // Only force logout when the response clearly indicates auth/session invalidation.
            if (resp.code == 403) {
                try {
                    val body = resp.peekBody(64 * 1024).string()
                    if (looksLikeAuthError(body)) {
                        android.util.Log.w("RetrofitClient", "Forcing logout due to HTTP 403 auth error on ${resp.request.url}")
                        sessionManager?.forceLogout("http_403_auth_error ${resp.request.url}")
                    }
                } catch (_: Exception) {}
            }
            resp
        }
        .authenticator { _, response ->
            // This runs when we get a 401 Unauthorized
            if (response.code == 401) {
                // Prevent infinite loops (OkHttp will throw "Too many follow-up requests").
                try {
                    if (response.request.header(AUTH_RETRY_HEADER) == "1") {
                        android.util.Log.w("RetrofitClient", "Stopping auth retry loop (already retried once)")
                        sessionManager?.forceLogout("auth_retry_loop_header")
                        return@authenticator null
                    }
                    if (responseCount(response) >= 2) {
                        android.util.Log.w("RetrofitClient", "Stopping auth retry loop (priorResponse chain too long)")
                        sessionManager?.forceLogout("auth_retry_loop_prior_response")
                        return@authenticator null
                    }
                } catch (_: Exception) {}

                // If backend explicitly says session invalidated (login on another device),
                // first try adopting newer token from Unity PlayerPrefs (Unity may have re-logged-in),
                // otherwise force logout.
                try {
                    val body = response.peekBody(1024 * 1024).string()
                    if (looksLikeSessionInvalidated(body)) {
                        tryUnityRecoveryAndBuildRetry(response)?.let { return@authenticator it }

                        android.util.Log.w("RetrofitClient", "Forcing logout due to session_invalidated 401 (other device login)")
                        sessionManager?.forceLogout("session_invalidated_401")
                        return@authenticator null
                    }
                } catch (_: Exception) {
                    // Ignore parse errors and continue with normal refresh flow.
                }

                val refreshToken = sessionManager?.fetchRefreshToken()
                if (refreshToken != null) {
                    // Try to refresh the token synchronously
                    val refreshResponse = try {
                        // We need a separate retrofit instance or service for refresh to avoid infinite loops
                        val refreshService = Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService::class.java)

                        // Use runBlocking for synchronous call in authenticator
                        kotlinx.coroutines.runBlocking {
                            refreshService.refreshToken(mapOf("refresh" to refreshToken))
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (refreshResponse?.isSuccessful == true) {
                        val body = refreshResponse.body()
                        val newAccessToken = body?.get("access")
                        val newRefreshToken = body?.get("refresh") ?: body?.get("refresh_token")
                        if (newAccessToken != null) {
                            // IMPORTANT: backend rotates refresh tokens; persist both when provided.
                            if (!newRefreshToken.isNullOrBlank()) {
                                sessionManager?.saveTokens(newAccessToken, newRefreshToken)
                            } else {
                                sessionManager?.saveAuthToken(newAccessToken)
                            }
                            // Sync to Unity as well
                            sessionManager?.syncAuthToUnity()

                            // Retry the request with the new token
                            return@authenticator response.request.newBuilder()
                                .header("Authorization", "Bearer $newAccessToken")
                                .header(AUTH_RETRY_HEADER, "1")
                                .build()
                        }
                    } else {
                        // Refresh failed - session really expired
                        android.util.Log.e("RetrofitClient", "Refresh token expired or invalid")
                        // We can't call viewModel.logout() here directly easily
                        // But we can clear the session manager
                        android.util.Log.w("RetrofitClient", "Forcing logout due to refresh failure")
                        tryUnityRecoveryAndBuildRetry(response)?.let { return@authenticator it }
                        sessionManager?.forceLogout("refresh_failed_401")
                    }
                } else {
                    // No refresh token
                    android.util.Log.w("RetrofitClient", "Forcing logout due to missing refresh token on 401")
                    tryUnityRecoveryAndBuildRetry(response)?.let { return@authenticator it }
                    sessionManager?.forceLogout("missing_refresh_token_401")
                }
            }
            null
        }
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}
