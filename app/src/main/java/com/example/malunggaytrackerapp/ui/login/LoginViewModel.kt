package com.example.malunggaytrackerapp.ui.login

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message is either a string resource (local text) or raw text from the server. */
sealed interface UiText {
    data class Resource(@StringRes val id: Int) : UiText
    data class Raw(val text: String) : UiText
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val generalError: UiText? = null,
    val isLoggedIn: Boolean = false,
)

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(isLoggedIn = authRepository.isLoggedIn))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return

        val emailError = when {
            email.isBlank() -> UiText.Resource(R.string.error_email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> UiText.Resource(R.string.error_email_invalid)
            else -> null
        }
        val passwordError = if (password.isEmpty()) UiText.Resource(R.string.error_password_required) else null

        if (emailError != null || passwordError != null) {
            _uiState.update { LoginUiState(emailError = emailError, passwordError = passwordError) }
            return
        }

        _uiState.value = LoginUiState(isLoading = true)

        viewModelScope.launch {
            _uiState.value = when (val result = authRepository.login(email, password)) {
                is ApiResult.Success -> LoginUiState(isLoggedIn = true)
                is ApiResult.ValidationError -> LoginUiState(
                    emailError = result.fieldErrors["email"]?.let(UiText::Raw),
                    passwordError = result.fieldErrors["password"]?.let(UiText::Raw),
                    generalError = if (result.fieldErrors.isEmpty()) result.message?.let(UiText::Raw) else null,
                )
                is ApiResult.Forbidden -> LoginUiState(
                    generalError = result.message?.let(UiText::Raw) ?: UiText.Resource(R.string.error_unexpected),
                )
                ApiResult.TooManyRequests -> LoginUiState(generalError = UiText.Resource(R.string.error_too_many_attempts))
                ApiResult.NetworkError -> LoginUiState(generalError = UiText.Resource(R.string.error_network))
                ApiResult.Unauthorized,
                is ApiResult.UnexpectedError -> LoginUiState(generalError = UiText.Resource(R.string.error_unexpected))
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                LoginViewModel(app.authRepository)
            }
        }
    }
}
