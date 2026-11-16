package com.example.malunggaytrackerapp.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.SalesRepository
import com.example.malunggaytrackerapp.data.remote.PandesalPriceDto
import com.example.malunggaytrackerapp.data.remote.RiderSalesDto
import com.example.malunggaytrackerapp.data.remote.SalesSummaryDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import com.example.malunggaytrackerapp.ui.common.formatPeso
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
import java.math.BigDecimal
import java.math.RoundingMode

/** A rider's row with their share of today's total sales (0–100). */
data class RiderSalesRow(val sales: RiderSalesDto, val sharePercent: Int)

data class SalesUiState(
    val isLoading: Boolean = true,
    val summary: SalesSummaryDto? = null,
    val price: PandesalPriceDto? = null,
    val isPriceLoaded: Boolean = false,
    val error: UiText? = null,
) {
    val rows: List<RiderSalesRow>
        get() {
            val total = summary?.totalAmount?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            return summary?.riders.orEmpty().map { rider ->
                val amount = rider.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val share = if (total.signum() > 0) {
                    amount.multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
                } else {
                    0
                }
                RiderSalesRow(rider, share)
            }
        }
}

/** Result of trying to save a new price, for the dialog to react to. */
sealed interface PriceSaveResult {
    data object Saved : PriceSaveResult
    data class Invalid(val message: UiText) : PriceSaveResult
}

class SalesViewModel(private val salesRepository: SalesRepository) : ViewModel() {

    private val _state = MutableStateFlow(SalesUiState())
    val state: StateFlow<SalesUiState> = _state.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Loads today's sales and the current price in parallel. */
    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            val summaryCall = async { salesRepository.todaySummary() }
            val priceCall = async { salesRepository.currentPrice() }
            val summary = summaryCall.await()
            val price = priceCall.await()

            _state.update { state ->
                state.copy(
                    isLoading = false,
                    summary = (summary as? ApiResult.Success)?.data ?: state.summary,
                    price = if (price is ApiResult.Success) price.data else state.price,
                    isPriceLoaded = state.isPriceLoaded || price is ApiResult.Success,
                    error = listOf(summary, price).firstOrNull { it !is ApiResult.Success }?.errorMessage(),
                )
            }
        }
    }

    /**
     * Validates and saves a new price typed by the admin (e.g. "3.5").
     * Returns [PriceSaveResult.Invalid] so the dialog can stay open and show the problem.
     */
    suspend fun setPrice(input: String): PriceSaveResult {
        val price = input.trim().toBigDecimalOrNull()
        val localError = when {
            input.isBlank() -> R.string.price_error_required
            price == null || price.signum() <= 0 -> R.string.price_error_invalid
            price.stripTrailingZeros().scale() > 2 -> R.string.price_error_decimals
            price > BigDecimal(10_000) -> R.string.price_error_invalid
            else -> null
        }
        if (localError != null || price == null) return PriceSaveResult.Invalid(UiText.Resource(localError!!))

        val plain = price.setScale(2, RoundingMode.UNNECESSARY).toPlainString()
        return when (val result = salesRepository.setPrice(plain)) {
            is ApiResult.Success -> {
                _state.update { it.copy(price = result.data, isPriceLoaded = true) }
                _messages.send(UiText.Resource(R.string.price_saved, listOf(formatPeso(result.data.pricePerPiece))))
                PriceSaveResult.Saved
            }
            is ApiResult.ValidationError -> PriceSaveResult.Invalid(
                result.fieldErrors["price_per_piece"]?.let(UiText::Raw) ?: result.errorMessage(),
            )
            else -> PriceSaveResult.Invalid(result.errorMessage())
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                SalesViewModel(app.salesRepository)
            }
        }
    }
}
