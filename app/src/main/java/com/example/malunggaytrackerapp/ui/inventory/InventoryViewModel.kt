package com.example.malunggaytrackerapp.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.InventoryRepository
import com.example.malunggaytrackerapp.data.remote.InventorySummaryDto
import com.example.malunggaytrackerapp.data.remote.StockReturnDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import com.example.malunggaytrackerapp.ui.common.formatCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReturnRow(val stockReturn: StockReturnDto, val isVerifying: Boolean)

data class InventoryUiState(
    val isLoading: Boolean = true,
    val summary: InventorySummaryDto? = null,
    val returns: List<StockReturnDto> = emptyList(),
    val verifyingIds: Set<Long> = emptySet(),
    val error: UiText? = null,
) {
    val rows: List<ReturnRow> get() = returns.map { ReturnRow(it, it.id in verifyingIds) }
}

class InventoryViewModel(private val inventoryRepository: InventoryRepository) : ViewModel() {

    private val _state = MutableStateFlow(InventoryUiState())
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Loads today's totals and the returns list in parallel. */
    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
            val summaryCall = async { inventoryRepository.todaySummary() }
            val returnsCall = async { inventoryRepository.returns() }
            val summary = summaryCall.await()
            val returns = returnsCall.await()

            _state.update { state ->
                state.copy(
                    isLoading = false,
                    summary = (summary as? ApiResult.Success)?.data ?: state.summary,
                    returns = (returns as? ApiResult.Success)?.data ?: state.returns,
                    error = listOf(summary, returns).firstOrNull { it !is ApiResult.Success }?.errorMessage(),
                )
            }
        }
    }

    fun verify(stockReturn: StockReturnDto) {
        if (stockReturn.id in _state.value.verifyingIds || !stockReturn.isPending) return
        _state.update { it.copy(verifyingIds = it.verifyingIds + stockReturn.id) }

        viewModelScope.launch {
            val result = inventoryRepository.verify(stockReturn.id)

            _state.update { state ->
                val returns = if (result is ApiResult.Success) {
                    state.returns.map { if (it.id == stockReturn.id) result.data else it }
                } else {
                    state.returns
                }
                state.copy(returns = returns, verifyingIds = state.verifyingIds - stockReturn.id)
            }

            if (result is ApiResult.Success) {
                _messages.send(
                    UiText.Resource(
                        R.string.inventory_verify_done,
                        listOf(formatCount(stockReturn.quantityReturned), stockReturn.rider.name),
                    ),
                )
                // Totals (pending vs verified) changed; refresh the summary quietly.
                (inventoryRepository.todaySummary() as? ApiResult.Success)?.let { summary ->
                    _state.update { it.copy(summary = summary.data) }
                }
            } else {
                _messages.send(result.errorMessage())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                InventoryViewModel(app.inventoryRepository)
            }
        }
    }
}
