package com.example.malunggaytrackerapp.data

import android.os.Build
import com.example.malunggaytrackerapp.data.local.TokenStore
import com.example.malunggaytrackerapp.data.remote.ApiErrorResponse
import com.example.malunggaytrackerapp.data.remote.AuthApi
import com.example.malunggaytrackerapp.data.remote.LoginRequest
import com.example.malunggaytrackerapp.data.remote.UserDto
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

/** Result of an API call, already mapped to something the UI can show. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    /** 422: Laravel validation errors keyed by field name (e.g. "email"). */
    data class ValidationError(val message: String?, val fieldErrors: Map<String, String>) : ApiResult<Nothing>

    /** 401: not logged in, or the token was revoked/expired. */
    data object Unauthorized : ApiResult<Nothing>

    /** 403: e.g. inactive account. */
    data class Forbidden(val message: String?) : ApiResult<Nothing>

    /** 429: rate limited. */
    data object TooManyRequests : ApiResult<Nothing>

    data object NetworkError : ApiResult<Nothing>

    data class UnexpectedError(val code: Int?) : ApiResult<Nothing>
}

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
) {
    private val gson = Gson()

    val isLoggedIn: Boolean
        get() = tokenStore.hasToken

    suspend fun login(email: String, password: String): ApiResult<UserDto> {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(255)
        val result = call { api.login(LoginRequest(email.trim(), password, deviceName)) }

        return when (result) {
            is ApiResult.Success -> {
                tokenStore.saveToken(result.data.token)
                ApiResult.Success(result.data.user)
            }
            else -> result.mapFailure()
        }
    }

    suspend fun currentUser(): ApiResult<UserDto> =
        when (val result = call { api.me() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.data)
            else -> result.mapFailure()
        }

    /** Revokes the token on the server (best effort) and always clears it locally. */
    suspend fun logout() {
        call { api.logout() }
        tokenStore.clear()
    }

    private suspend fun <T> call(request: suspend () -> Response<T>): ApiResult<T> {
        return try {
            val response = request()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> ApiResult.Success(body)
                response.isSuccessful -> ApiResult.UnexpectedError(response.code())
                else -> parseError(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.NetworkError
        } catch (e: Exception) {
            ApiResult.UnexpectedError(null)
        }
    }

    private fun parseError(response: Response<*>): ApiResult<Nothing> {
        val error = try {
            response.errorBody()?.charStream()?.use { gson.fromJson(it, ApiErrorResponse::class.java) }
        } catch (e: Exception) {
            null
        }

        return when (response.code()) {
            401 -> ApiResult.Unauthorized
            403 -> ApiResult.Forbidden(error?.message)
            422 -> ApiResult.ValidationError(
                message = error?.message,
                fieldErrors = error?.errors.orEmpty().mapNotNull { (field, messages) ->
                    messages.firstOrNull()?.let { field to it }
                }.toMap(),
            )
            429 -> ApiResult.TooManyRequests
            else -> ApiResult.UnexpectedError(response.code())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> ApiResult<*>.mapFailure(): ApiResult<T> = this as ApiResult<T>
}
