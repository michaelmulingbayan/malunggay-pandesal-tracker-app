package com.example.malunggaytrackerapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.InventoryRepository
import com.example.malunggaytrackerapp.data.RiderRepository
import com.example.malunggaytrackerapp.data.SalesRepository
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    /** Exact 2-decimal string from the server, e.g. "1275.00". */
    val salesTotal: String = "0",
    val piecesSold: Int = 0,
    val produced: Int = 0,
    val available: Int = 0,
    val distributed: Int = 0,
    val activeRiders: Int = 0,
    val error: UiText? = null,
)

/** Today's numbers for the admin dashboard, gathered from the sales, inventory and riders APIs. */
class DashboardViewModel(
    private val salesRepository: SalesRepository,
    private val inventoryRepository: InventoryRepository,
    private val riderRepository: RiderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }

        loadJob = viewModelScope.launch {
            val salesCall = async { salesRepository.todaySummary() }
            val inventoryCall = async { inventoryRepository.todaySummary() }
            val ridersCall = async { riderRepository.riders() }
            val sales = salesCall.await()
            val inventory = inventoryCall.await()
            val riders = ridersCall.await()

            _state.update { state ->
                var next = state.copy(isLoading = false, hasLoaded = true)
                if (sales is ApiResult.Success) {
                    next = next.copy(salesTotal = sales.data.totalAmount, piecesSold = sales.data.piecesSold)
                }
                if (inventory is ApiResult.Success) {
                    next = next.copy(
                        produced = inventory.data.produced,
                        distributed = inventory.data.distributed,
                        available = inventory.data.undistributed,
                    )
                }
                if (riders is ApiResult.Success) {
                    next = next.copy(activeRiders = riders.data.count { it.isActive })
                }
                next.copy(error = listOf(sales, inventory, riders).firstOrNull { it !is ApiResult.Success }?.errorMessage())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                DashboardViewModel(app.salesRepository, app.inventoryRepository, app.riderRepository)
            }
        }
    }
}
