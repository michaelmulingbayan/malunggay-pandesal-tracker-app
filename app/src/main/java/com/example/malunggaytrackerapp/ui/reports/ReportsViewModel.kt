package com.example.malunggaytrackerapp.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.SalesRepository
import com.example.malunggaytrackerapp.data.remote.PiecesSoldReportDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.Daily,
    val isLoading: Boolean = true,
    /** Latest chart for the selected period; kept while reloading so the chart doesn't blink. */
    val report: PiecesSoldReportDto? = null,
    val error: UiText? = null,
)

/** Reports tab: pieces sold per day (last 7 days), week (last 8 weeks) or month (last 6 months). */
class ReportsViewModel(private val salesRepository: SalesRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun setPeriod(period: ReportPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, report = null) }
        load()
    }

    /** Starting a new load cancels an in-flight one, so a slow Daily response can't overwrite Monthly. */
    fun load() {
        loadJob?.cancel()
        val period = _state.value.period
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = salesRepository.piecesSoldReport(period.apiValue)) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, report = result.data) }
                else -> _state.update { it.copy(isLoading = false, error = result.errorMessage()) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                ReportsViewModel(app.salesRepository)
            }
        }
    }
}
