package com.example.malunggaytrackerapp.ui.riders

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.remote.CreateRiderRequest
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Field keys match the Laravel request fields, so server validation errors map 1:1. */
object RiderField {
    const val NAME = "name"
    const val EMAIL = "email"
    const val PASSWORD = "password"
    const val PHONE = "phone_number"
    const val ADDRESS = "address"
}

data class AddRiderUiState(
    val isSaving: Boolean = false,
    val fieldErrors: Map<String, UiText> = emptyMap(),
    val generalError: UiText? = null,
    val isSaved: Boolean = false,
)

class AddRiderViewModel(private val riderRepository: RiderRepository) : ViewModel() {

    private val _state = MutableStateFlow(AddRiderUiState())
    val state: StateFlow<AddRiderUiState> = _state.asStateFlow()

    fun save(name: String, email: String, password: String, phone: String, address: String) {
        if (_state.value.isSaving) return

        val request = CreateRiderRequest(
            name = name.trim(),
            email = email.trim(),
            password = password,
            phoneNumber = phone.trim(),
            address = address.trim(),
        )

        val errors = validate(request)
        if (errors.isNotEmpty()) {
            _state.value = AddRiderUiState(fieldErrors = errors)
            return
        }

        _state.value = AddRiderUiState(isSaving = true)

        viewModelScope.launch {
            _state.value = when (val result = riderRepository.addRider(request)) {
                is ApiResult.Success -> AddRiderUiState(isSaved = true)
                is ApiResult.ValidationError -> AddRiderUiState(
                    fieldErrors = result.fieldErrors.mapValues { (_, message) -> UiText.Raw(message) },
                    generalError = if (result.fieldErrors.isEmpty()) result.errorMessage() else null,
                )
                else -> AddRiderUiState(generalError = result.errorMessage())
            }
        }
    }

    fun clearFieldError(field: String) {
        if (field in _state.value.fieldErrors) {
            _state.value = _state.value.copy(fieldErrors = _state.value.fieldErrors - field)
        }
    }

    private fun validate(request: CreateRiderRequest): Map<String, UiText> = buildMap {
        if (request.name.isBlank()) put(RiderField.NAME, UiText.Resource(R.string.error_name_required))
        when {
            request.email.isBlank() -> put(RiderField.EMAIL, UiText.Resource(R.string.error_email_required))
            !Patterns.EMAIL_ADDRESS.matcher(request.email).matches() ->
                put(RiderField.EMAIL, UiText.Resource(R.string.error_email_invalid))
        }
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            put(RiderField.PASSWORD, UiText.Resource(R.string.error_password_min))
        }
        if (request.phoneNumber.isBlank()) put(RiderField.PHONE, UiText.Resource(R.string.error_phone_required))
        if (request.address.isBlank()) put(RiderField.ADDRESS, UiText.Resource(R.string.error_address_required))
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                AddRiderViewModel(app.riderRepository)
            }
        }
    }
}
