package com.example.malunggaytrackerapp.data

import android.os.Build
import com.example.malunggaytrackerapp.data.local.TokenStore
import com.example.malunggaytrackerapp.data.remote.AuthApi
import com.example.malunggaytrackerapp.data.remote.LoginRequest
import com.example.malunggaytrackerapp.data.remote.UserDto

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

    /** Revokes the token on the server (best effort) and always clears it locally. */
    suspend fun logout() {
        safeApiCall { api.logout() }
        tokenStore.clear()
    }
}
