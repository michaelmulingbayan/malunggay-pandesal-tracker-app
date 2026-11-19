package com.example.malunggaytrackerapp.data

import com.example.malunggaytrackerapp.data.remote.ApiErrorResponse
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

    /** 403: not allowed (e.g. inactive account, or not an admin). */
    data class Forbidden(val message: String?) : ApiResult<Nothing>

    /** 429: rate limited. */
    data object TooManyRequests : ApiResult<Nothing>

    data object NetworkError : ApiResult<Nothing>

    data class UnexpectedError(val code: Int?) : ApiResult<Nothing>
}

private val errorGson = Gson()

/** Runs a Retrofit call and maps HTTP/network failures to [ApiResult]. */
suspend fun <T> safeApiCall(request: suspend () -> Response<T>): ApiResult<T> {
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

/** Re-types a failed result so it can be returned from a function with a different success type. */
@Suppress("UNCHECKED_CAST")
fun <T> ApiResult<*>.asFailure(): ApiResult<T> {
    check(this !is ApiResult.Success<*>) { "asFailure() called on a successful result" }
    return this as ApiResult<T>
}

private fun parseError(response: Response<*>): ApiResult<Nothing> {
    val error = try {
        response.errorBody()?.charStream()?.use { errorGson.fromJson(it, ApiErrorResponse::class.java) }
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
