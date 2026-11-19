package com.example.malunggaytrackerapp.data

import android.os.Build
import com.example.malunggaytrackerapp.data.local.TokenStore
import com.example.malunggaytrackerapp.data.remote.AuthApi
import com.example.malunggaytrackerapp.data.remote.LoginRequest
import com.example.malunggaytrackerapp.data.remote.UserDto
import kotlinx.coroutines.withTimeoutOrNull

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
) {
    val isLoggedIn: Boolean
        get() = tokenStore.hasToken

    suspend fun login(email: String, password: String): ApiResult<UserDto> {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(255)

        return when (val result = safeApiCall { api.login(LoginRequest(email.trim(), password, deviceName)) }) {
            is ApiResult.Success -> {
                tokenStore.saveToken(result.data.token)
                ApiResult.Success(result.data.user)
            }
            else -> result.asFailure()
        }
    }

    suspend fun currentUser(): ApiResult<UserDto> =
        when (val result = safeApiCall { api.me() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.asFailure()
        }

    /**
     * Revokes the token on the server (best effort) and always clears it locally.
     * The server call is capped so a dead connection can't keep the user stuck on "Logging out…".
     */
    suspend fun logout() {
        withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { safeApiCall { api.logout() } }
        tokenStore.clear()
    }

    private companion object {
        const val LOGOUT_TIMEOUT_MS = 5_000L
    }
}
