package com.example.malunggaytrackerapp.ui.home

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
import com.example.malunggaytrackerapp.data.remote.UserDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data class Ready(val user: UserDto) : SessionState
    data class Error(@StringRes val message: Int) : SessionState

    /** Token was rejected by the server; the user must sign in again. */
    data object Expired : SessionState
    data object LoggedOut : SessionState
}

/** The signed-in user, shared by the tab shell and every tab (via activityViewModels). */
class SessionViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Last known user, kept while retrying so the admin navigation doesn't flicker away. */
    var lastUser: UserDto? = null
        private set

    init {
        loadUser()
    }

    private var loadJob: Job? = null

    /** Starting a new load cancels an in-flight one, so a slow stale response can't overwrite a fresh result. */
    fun loadUser() {
        loadJob?.cancel()
        _state.value = SessionState.Loading
        loadJob = viewModelScope.launch {
            _state.value = when (val result = authRepository.currentUser()) {
                is ApiResult.Success -> SessionState.Ready(result.data).also { lastUser = result.data }
                ApiResult.Unauthorized -> SessionState.Expired
                ApiResult.NetworkError -> SessionState.Error(R.string.error_network)
                else -> SessionState.Error(R.string.error_unexpected)
            }
        }
    }

    fun logout() {
        loadJob?.cancel()
        _state.value = SessionState.Loading
        viewModelScope.launch {
            authRepository.logout()
            _state.value = SessionState.LoggedOut
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                SessionViewModel(app.authRepository)
            }
        }
    }
}
