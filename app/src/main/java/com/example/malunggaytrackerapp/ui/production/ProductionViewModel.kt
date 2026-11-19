package com.example.malunggaytrackerapp.ui.production

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.ProductionRepository
import com.example.malunggaytrackerapp.data.remote.ProductionDto
import com.example.malunggaytrackerapp.data.remote.RecordProductionRequest
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import com.example.malunggaytrackerapp.ui.common.formatCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductionUiState(
    val isLoadingHistory: Boolean = true,
    val history: List<ProductionDto> = emptyList(),
    val historyError: UiText? = null,
    val isSaving: Boolean = false,
    val quantityError: UiText? = null,
    val remarksError: UiText? = null,
    val formError: UiText? = null,
) {
    private val todaysBatches: List<ProductionDto>
        get() = history.filter { DateLabels.daysAgo(it.productionDate) == 0 }

    val todayTotal: Int get() = todaysBatches.sumOf { it.quantityProduced }
    val todayBatchCount: Int get() = todaysBatches.size
}

sealed interface ProductionEvent {
    /** A batch was saved: clear the form and confirm. */
    data class Recorded(val message: UiText) : ProductionEvent
}

class ProductionViewModel(private val productionRepository: ProductionRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProductionUiState())
    val state: StateFlow<ProductionUiState> = _state.asStateFlow()

    private val _events = Channel<ProductionEvent>(Channel.BUFFERED)
    val events: Flow<ProductionEvent> = _events.receiveAsFlow()

    private var historyJob: Job? = null

    init {
        loadHistory()
    }

    fun loadHistory() {
        historyJob?.cancel()
        _state.update { it.copy(isLoadingHistory = true, historyError = null) }
        historyJob = viewModelScope.launch {
            when (val result = productionRepository.history()) {
                is ApiResult.Success -> _state.update { it.copy(isLoadingHistory = false, history = result.data) }
                else -> _state.update { it.copy(isLoadingHistory = false, historyError = result.errorMessage()) }
            }
        }
    }

    /** Records a batch for today's date (device local date). */
    fun record(quantityText: String, remarks: String) {
        if (_state.value.isSaving) return

        val quantity = quantityText.trim().toIntOrNull()
        val quantityError = when {
            quantityText.isBlank() -> UiText.Resource(R.string.error_quantity_required)
            quantity == null || quantity <= 0 -> UiText.Resource(R.string.error_quantity_invalid)
            else -> null
        }
        if (quantityError != null || quantity == null) {
            _state.update { it.copy(quantityError = quantityError, formError = null) }
            return
        }

        _state.update { it.copy(isSaving = true, quantityError = null, remarksError = null, formError = null) }

        viewModelScope.launch {
            val request = RecordProductionRequest(
                productionDate = DateLabels.todayIso(),
                quantityProduced = quantity,
                remarks = remarks.trim().ifEmpty { null },
            )

            when (val result = productionRepository.record(request)) {
                is ApiResult.Success -> {
                    // Show the new batch at the top immediately, then refresh from the server.
                    _state.update { it.copy(isSaving = false, history = listOf(result.data) + it.history) }
                    _events.send(
                        ProductionEvent.Recorded(
                            UiText.Resource(R.string.production_saved, listOf(formatCount(result.data.quantityProduced))),
                        ),
                    )
                    loadHistory()
                }
                is ApiResult.ValidationError -> _state.update {
                    it.copy(
                        isSaving = false,
                        quantityError = result.fieldErrors["quantity_produced"]?.let(UiText::Raw),
                        remarksError = result.fieldErrors["remarks"]?.let(UiText::Raw),
                        // A date error (e.g. phone clock in the future) has no field of its own.
                        formError = (result.fieldErrors["production_date"] ?: result.message.takeIf { result.fieldErrors.isEmpty() })
                            ?.let(UiText::Raw),
                    )
                }
                else -> _state.update { it.copy(isSaving = false, formError = result.errorMessage()) }
            }
        }
    }

    fun clearQuantityError() {
        if (_state.value.quantityError != null) _state.update { it.copy(quantityError = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                ProductionViewModel(app.productionRepository)
            }
        }
    }
}
