package com.example.malunggaytrackerapp.ui.riders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.remote.RiderAccountDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RiderRow(val rider: RiderAccountDto, val isUpdating: Boolean)

data class RidersUiState(
    val isLoading: Boolean = true,
    val riders: List<RiderAccountDto> = emptyList(),
    val updatingIds: Set<Long> = emptySet(),
    val error: UiText? = null,
) {
    val rows: List<RiderRow> get() = riders.map { RiderRow(it, it.id in updatingIds) }
    val activeCount: Int get() = riders.count { it.isActive }
}

class RidersViewModel(private val riderRepository: RiderRepository) : ViewModel() {

    private val _state = MutableStateFlow(RidersUiState())
    val state: StateFlow<RidersUiState> = _state.asStateFlow()

    /** One-off messages for a snackbar. */
    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = riderRepository.riders()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, riders = result.data) }
                else -> _state.update { it.copy(isLoading = false, error = result.errorMessage()) }
            }
        }
    }

    /**
     * Optimistically flips the toggle, then confirms with the server.
     * If the request fails, the toggle snaps back and an error is shown.
     */
    fun setActive(rider: RiderAccountDto, isActive: Boolean) {
        if (rider.id in _state.value.updatingIds || rider.isActive == isActive) return

        _state.update { state ->
            state.copy(
                riders = state.riders.replace(rider.id) { it.copy(isActive = isActive) },
                updatingIds = state.updatingIds + rider.id,
            )
        }

        viewModelScope.launch {
            val result = riderRepository.setActive(rider.id, isActive)

            _state.update { state ->
                val riders = when (result) {
                    is ApiResult.Success -> state.riders.replace(rider.id) { result.data }
                    else -> state.riders.replace(rider.id) { it.copy(isActive = !isActive) }
                }
                state.copy(riders = riders, updatingIds = state.updatingIds - rider.id)
            }

            _messages.send(
                when (result) {
                    is ApiResult.Success -> UiText.Resource(
                        if (isActive) R.string.riders_activated else R.string.riders_deactivated,
                        listOf(rider.name),
                    )
                    else -> result.errorMessage()
                },
            )
        }
    }

    private fun List<RiderAccountDto>.replace(id: Long, transform: (RiderAccountDto) -> RiderAccountDto) =
        map { if (it.id == id) transform(it) else it }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                RidersViewModel(app.riderRepository)
            }
        }
    }
}
