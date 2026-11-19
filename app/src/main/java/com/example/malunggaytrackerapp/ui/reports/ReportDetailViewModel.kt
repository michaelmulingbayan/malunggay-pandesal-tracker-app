package com.example.malunggaytrackerapp.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.ReportsRepository
import com.example.malunggaytrackerapp.data.remote.ReportPeriodDto
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val type: ReportType,
    val period: ReportPeriod,
    /** Any day inside the shown period ("yyyy-MM-dd"). */
    val date: String,
    val isLoading: Boolean = true,
    /** The report for [period] / [date]; null until it loads (or after the filter changes). */
    val report: ReportPeriodDto? = null,
    val error: UiText? = null,
) {
    /** First and last day of the shown period. */
    val range: Pair<String, String> get() = period.rangeOf(date)

    /** True when the shown period contains today. */
    val isCurrent: Boolean get() = DateLabels.todayIso() in range.first..range.second

    /** Reports don't go into the future: "next" stops at the period that contains today. */
    val canGoNext: Boolean get() = range.second < DateLabels.todayIso()
}

/** One detailed report, filtered by day / week / month and stepped back and forth in time. */
class ReportDetailViewModel(
    private val repository: ReportsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReportDetailUiState(
            type = ReportType.entries[savedState.get<Int>(ReportDetailActivity.EXTRA_TYPE) ?: 0],
            period = ReportPeriod.fromApiValue(savedState.get<String>(ReportDetailActivity.EXTRA_PERIOD)),
            date = DateLabels.todayIso(),
        ),
    )
    val state: StateFlow<ReportDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun setPeriod(period: ReportPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, report = null) }
        load()
    }

    fun previous() = step(-1)

    fun next() {
        if (_state.value.canGoNext) step(1)
    }

    /** Back to the period that contains today. */
    fun jumpToToday() {
        if (_state.value.isCurrent) return
        _state.update { it.copy(date = DateLabels.todayIso(), report = null) }
        load()
    }

    /** Starting a new load cancels an in-flight one, so a slow older response can't overwrite a newer filter. */
    fun load() {
        loadJob?.cancel()
        val (type, period, date) = _state.value.let { Triple(it.type, it.period, it.date) }
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            val result: ApiResult<ReportPeriodDto> = when (type) {
                ReportType.Production -> repository.production(period.apiValue, date)
                ReportType.Sales -> repository.sales(period.apiValue, date)
                ReportType.Inventory -> repository.inventory(period.apiValue, date)
                ReportType.Distribution -> repository.distribution(period.apiValue, date)
                ReportType.RiderPerformance -> repository.riders(period.apiValue, date)
            }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(isLoading = false, report = result.data)
                    else -> it.copy(isLoading = false, error = result.errorMessage())
                }
            }
        }
    }

    private fun step(steps: Int) {
        _state.update { it.copy(date = it.period.shift(it.date, steps), report = null) }
        load()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                ReportDetailViewModel(app.reportsRepository, createSavedStateHandle())
            }
        }
    }
}
