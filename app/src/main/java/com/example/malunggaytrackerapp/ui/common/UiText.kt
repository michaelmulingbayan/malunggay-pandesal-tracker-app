package com.example.malunggaytrackerapp.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult

/** A message is either a string resource (local text, optionally formatted) or raw text from the server. */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Raw -> text
}

/** A user-facing message for a failed API call. */
fun ApiResult<*>.errorMessage(): UiText = when (this) {
    ApiResult.NetworkError -> UiText.Resource(R.string.error_network)
    ApiResult.Unauthorized -> UiText.Resource(R.string.home_session_expired)
    is ApiResult.Forbidden -> message?.let(UiText::Raw) ?: UiText.Resource(R.string.error_forbidden)
    ApiResult.TooManyRequests -> UiText.Resource(R.string.error_too_many_requests)
    is ApiResult.ValidationError -> message?.let(UiText::Raw) ?: UiText.Resource(R.string.error_unexpected)
    is ApiResult.UnexpectedError, is ApiResult.Success -> UiText.Resource(R.string.error_unexpected)
}

/** "Juan Dela Cruz" → "JD". */
fun initialsOf(name: String): String =
    name.split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
