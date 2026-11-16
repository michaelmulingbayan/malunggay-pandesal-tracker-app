package com.example.malunggaytrackerapp.ui.distribution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.StockRepository
import com.example.malunggaytrackerapp.data.remote.RiderAccountDto
import com.example.malunggaytrackerapp.data.remote.StockDistributionDto
import com.example.malunggaytrackerapp.data.remote.StockDistributionsResponse
import com.example.malunggaytrackerapp.data.remote.StockSummaryDto
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

/** Today's stock for one rider, summed across their assignments. */
data class RiderAssignment(
    val riderId: Long,
    val riderName: String,
    val totalQuantity: Int,
    val assignmentCount: Int,
    /** Latest assignment's UTC timestamp. */
    val lastAssignedAt: String,
)

data class DistributeUiState(
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val summary: StockSummaryDto? = null,
    /** Active riders only: inactive riders can't receive stock. */
    val riders: List<RiderAccountDto> = emptyList(),
    val assignments: List<RiderAssignment> = emptyList(),
    val selectedRiderId: Long? = null,
    val isAssigning: Boolean = false,
    val riderError: UiText? = null,
    val quantityError: UiText? = null,
    val formError: UiText? = null,
) {
    val available: Int get() = summary?.available ?: 0
}

class DistributeStockViewModel(
    private val stockRepository: StockRepository,
    private val riderRepository: RiderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DistributeUiState())
    val state: StateFlow<DistributeUiState> = _state.asStateFlow()

    /** One-off confirmations; the screen clears the form when one arrives. */
    private val _assigned = Channel<UiText>(Channel.BUFFERED)
    val assigned: Flow<UiText> = _assigned.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Loads today's stock summary, today's assignments and the active riders in parallel. */
    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadError = null) }

        loadJob = viewModelScope.launch {
            val stockCall = async { stockRepository.today() }
            val ridersCall = async { riderRepository.riders() }
            val stock = stockCall.await()
            val riders = ridersCall.await()

            _state.update { state ->
                var next = state.copy(isLoading = false)
                if (stock is ApiResult.Success) next = next.withStock(stock.data)
                if (riders is ApiResult.Success) {
                    val active = riders.data.filter { it.isActive }
                    next = next.copy(
                        riders = active,
                        selectedRiderId = next.selectedRiderId?.takeIf { id -> active.any { it.id == id } },
                    )
                }
                val failure = listOf(stock, riders).firstOrNull { it !is ApiResult.Success }
                next.copy(loadError = failure?.errorMessage())
            }
        }
    }

    fun selectRider(riderId: Long) {
        _state.update { it.copy(selectedRiderId = riderId, riderError = null) }
    }

    fun clearQuantityError() {
        if (_state.value.quantityError != null) _state.update { it.copy(quantityError = null) }
    }

    fun assign(quantityText: String) {
        val state = _state.value
        if (state.isAssigning) return

        val riderId = state.selectedRiderId
        val quantity = quantityText.trim().toIntOrNull()

        val riderError = if (riderId == null) UiText.Resource(R.string.error_rider_required) else null
        val quantityError = when {
            quantityText.isBlank() -> UiText.Resource(R.string.error_quantity_required)
            quantity == null || quantity <= 0 -> UiText.Resource(R.string.error_quantity_invalid)
            state.summary != null && state.available == 0 -> UiText.Resource(R.string.error_nothing_available)
            state.summary != null && quantity > state.available ->
                UiText.Resource(R.string.error_quantity_exceeds, listOf(formatCount(state.available)))
            else -> null
        }

        if (riderError != null || quantityError != null || riderId == null || quantity == null) {
            _state.update { it.copy(riderError = riderError, quantityError = quantityError, formError = null) }
            return
        }

        _state.update { it.copy(isAssigning = true, riderError = null, quantityError = null, formError = null) }

        viewModelScope.launch {
            when (val result = stockRepository.assign(riderId, quantity)) {
                is ApiResult.Success -> {
                    val riderName = state.riders.firstOrNull { it.id == riderId }?.name.orEmpty()
                    _state.update { it.copy(isAssigning = false, selectedRiderId = null, summary = result.data.meta) }
                    _assigned.send(UiText.Resource(R.string.distribute_assigned, listOf(formatCount(quantity), riderName)))
                    refreshAssignments()
                }
                is ApiResult.ValidationError -> _state.update {
                    it.copy(
                        isAssigning = false,
                        riderError = result.fieldErrors["rider_id"]?.let(UiText::Raw),
                        quantityError = result.fieldErrors["quantity"]?.let(UiText::Raw),
                        formError = if (result.fieldErrors.isEmpty()) result.errorMessage() else null,
                    )
                }
                else -> _state.update { it.copy(isAssigning = false, formError = result.errorMessage()) }
            }
        }
    }

    /** Re-fetches today's list so the "Assigned today" section and totals match the server. */
    private suspend fun refreshAssignments() {
        val result = stockRepository.today()
        if (result is ApiResult.Success) _state.update { it.withStock(result.data) }
    }

    private fun DistributeUiState.withStock(response: StockDistributionsResponse) =
        copy(summary = response.meta, assignments = groupByRider(response.data))

    /** One row per rider, biggest total first. API rows are newest first, so the first per rider is the latest. */
    private fun groupByRider(distributions: List<StockDistributionDto>): List<RiderAssignment> =
        distributions
            .groupBy { it.rider.id }
            .map { (riderId, rows) ->
                RiderAssignment(
                    riderId = riderId,
                    riderName = rows.first().rider.name,
                    totalQuantity = rows.sumOf { it.quantityDistributed },
                    assignmentCount = rows.size,
                    lastAssignedAt = rows.first().createdAt,
                )
            }
            .sortedByDescending { it.totalQuantity }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                DistributeStockViewModel(app.stockRepository, app.riderRepository)
            }
        }
    }
}
