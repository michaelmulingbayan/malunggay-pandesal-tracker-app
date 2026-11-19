package com.example.malunggaytrackerapp.ui.rider

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.malunggaytrackerapp.MalunggayApp
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.MyStockRepository
import com.example.malunggaytrackerapp.data.remote.LocationDto
import com.example.malunggaytrackerapp.data.remote.MyStockDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class RiderUiState(
    val isLoading: Boolean = true,
    val summary: MyStockDto? = null,
    val error: UiText? = null,
    val isSelling: Boolean = false,
    val sellError: UiText? = null,
    val isReturning: Boolean = false,
    val returnError: UiText? = null,
    val isUpdatingAvailability: Boolean = false,
    /** The rider's choice to share location (remembered on this phone). */
    val isSharingEnabled: Boolean = false,
    /** True while GPS updates are actually flowing (switch on + permission + app open). */
    val isTracking: Boolean = false,
    /** Newest point successfully sent to the server. */
    val lastSharedLocation: LocationDto? = null,
) {
    val remaining: Int get() = summary?.remaining ?: 0

    /** What to show as "my location": just shared, otherwise the last one the server has. */
    val currentLocation: LocationDto? get() = lastSharedLocation ?: summary?.lastLocation
}

sealed interface RiderEvent {
    data class Sold(val message: UiText) : RiderEvent
    data class Returned(val message: UiText) : RiderEvent
    data class Message(val message: UiText) : RiderEvent
}

/** Shared by the rider's Home, Sell and Stock tabs so all three always show the same numbers. */
class RiderViewModel(
    private val repository: MyStockRepository,
    app: Application,
) : ViewModel() {

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val locationManager: LocationManager? = app.getSystemService(LocationManager::class.java)
    private val locationListener = LocationListener { location -> onLocation(location) }
    private var lastSentAt = 0L
    private var lastSent: Location? = null

    private val _state = MutableStateFlow(RiderUiState(isSharingEnabled = prefs.getBoolean(KEY_SHARE_LOCATION, false)))
    val state: StateFlow<RiderUiState> = _state.asStateFlow()

    private val _events = Channel<RiderEvent>(Channel.BUFFERED)
    val events: Flow<RiderEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.today()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, summary = result.data) }
                else -> _state.update { it.copy(isLoading = false, error = result.errorMessage()) }
            }
        }
    }

    fun sell(quantityText: String) {
        val state = _state.value
        if (state.isSelling) return

        val quantity = quantityText.trim().toIntOrNull()
        val error = when {
            state.summary?.pricePerPiece == null && state.summary != null -> UiText.Resource(R.string.sell_error_no_price)
            else -> validateQuantity(quantityText, quantity, state.remaining)
        }
        if (error != null || quantity == null) {
            _state.update { it.copy(sellError = error) }
            return
        }

        _state.update { it.copy(isSelling = true, sellError = null) }
        viewModelScope.launch {
            when (val result = repository.sell(quantity)) {
                is ApiResult.Success -> {
                    loadJob?.cancel()
                    _state.update { it.copy(isSelling = false, summary = result.data) }
                    val amount = totalFor(quantity, result.data.pricePerPiece)
                    _events.send(
                        RiderEvent.Sold(UiText.Resource(R.string.sell_done, listOf(formatCount(quantity), formatPeso(amount)))),
                    )
                }
                is ApiResult.ValidationError -> _state.update {
                    it.copy(isSelling = false, sellError = result.fieldErrors["quantity_sold"]?.let(UiText::Raw) ?: result.errorMessage())
                }
                else -> _state.update { it.copy(isSelling = false, sellError = result.errorMessage()) }
            }
        }
    }

    /**
     * Returns every piece the rider still holds (the usual end-of-duty return), so nothing has to be counted.
     * Sends the exact number the rider confirmed; the server rejects it if the stock changed meanwhile.
     */
    fun returnRemaining() {
        val state = _state.value
        if (state.isReturning || state.summary == null) return

        val quantity = state.remaining
        if (quantity <= 0) {
            _state.update { it.copy(returnError = UiText.Resource(R.string.rider_error_no_stock)) }
            return
        }

        _state.update { it.copy(isReturning = true, returnError = null) }
        viewModelScope.launch {
            when (val result = repository.returnStock(quantity)) {
                is ApiResult.Success -> {
                    loadJob?.cancel()
                    _state.update { it.copy(isReturning = false, summary = result.data) }
                    _events.send(RiderEvent.Returned(UiText.Resource(R.string.return_done, listOf(formatCount(quantity)))))
                }
                is ApiResult.ValidationError -> _state.update {
                    it.copy(
                        isReturning = false,
                        returnError = result.fieldErrors["quantity_returned"]?.let(UiText::Raw) ?: result.errorMessage(),
                    )
                }
                else -> _state.update { it.copy(isReturning = false, returnError = result.errorMessage()) }
            }
        }
    }

    /** Optimistically flips availability, then confirms with the server (reverts on failure). */
    fun setAvailable(isAvailable: Boolean) {
        val summary = _state.value.summary ?: return
        if (summary.isAvailable == isAvailable || _state.value.isUpdatingAvailability) return

        _state.update { it.copy(isUpdatingAvailability = true, summary = summary.copy(isAvailable = isAvailable)) }
        viewModelScope.launch {
            when (val result = repository.setAvailability(isAvailable)) {
                is ApiResult.Success -> _state.update { it.copy(isUpdatingAvailability = false, summary = result.data) }
                else -> {
                    _state.update {
                        it.copy(isUpdatingAvailability = false, summary = it.summary?.copy(isAvailable = !isAvailable))
                    }
                    _events.send(RiderEvent.Message(result.errorMessage()))
                }
            }
        }
    }

    /** The rider turned "Share my location" on or off. [hasPermission]: location permission granted. */
    fun setSharingEnabled(enabled: Boolean, hasPermission: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_LOCATION, enabled).apply()
        _state.update { it.copy(isSharingEnabled = enabled) }
        if (enabled && hasPermission) startTracking() else stopTracking()
    }

    /** Screen became visible: resume GPS updates if the rider chose to share. */
    fun resumeSharing(hasPermission: Boolean) {
        if (_state.value.isSharingEnabled && hasPermission) startTracking()
    }

    /** App went to the background: stop GPS updates (sharing is foreground-only). */
    fun pauseSharing() = stopTracking()

    @SuppressLint("MissingPermission") // Callers only start tracking after the permission was granted.
    private fun startTracking() {
        val manager = locationManager ?: return
        if (_state.value.isTracking) return

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            viewModelScope.launch { _events.send(RiderEvent.Message(UiText.Resource(R.string.location_turned_off))) }
            return
        }

        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, UPDATE_DISTANCE_M, locationListener, Looper.getMainLooper())
            }
            providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }?.let(::onLocation)
            _state.update { it.copy(isTracking = true) }
        } catch (e: SecurityException) {
            _state.update { it.copy(isTracking = false) }
        }
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(locationListener)
        _state.update { it.copy(isTracking = false) }
    }

    /** Sends a point when the rider moved enough or enough time passed (saves battery and data). */
    private fun onLocation(location: Location) {
        val now = System.currentTimeMillis()
        val previous = lastSent
        val movedEnough = previous == null || previous.distanceTo(location) >= SEND_DISTANCE_M
        val waitedEnough = now - lastSentAt >= SEND_INTERVAL_MS
        if (!movedEnough && !waitedEnough) return

        lastSentAt = now
        lastSent = location
        viewModelScope.launch {
            val result = repository.shareLocation(location.latitude, location.longitude)
            if (result is ApiResult.Success) _state.update { it.copy(lastSharedLocation = result.data) }
        }
    }

    override fun onCleared() {
        stopTracking()
        super.onCleared()
    }

    fun clearSellError() {
        if (_state.value.sellError != null) _state.update { it.copy(sellError = null) }
    }

    fun clearReturnError() {
        if (_state.value.returnError != null) _state.update { it.copy(returnError = null) }
    }

    private fun validateQuantity(text: String, quantity: Int?, remaining: Int): UiText? = when {
        text.isBlank() -> UiText.Resource(R.string.error_quantity_required)
        quantity == null || quantity <= 0 -> UiText.Resource(R.string.error_quantity_invalid)
        _state.value.summary != null && remaining == 0 -> UiText.Resource(R.string.rider_error_no_stock)
        _state.value.summary != null && quantity > remaining ->
            UiText.Resource(R.string.rider_error_too_many, listOf(formatCount(remaining)))
        else -> null
    }

    companion object {
        /** Live total for the Sell screen: quantity × price, exact (BigDecimal). Returns "0" if unknown. */
        fun totalFor(quantity: Int?, price: String?): String {
            val unit = price?.toBigDecimalOrNull() ?: return "0"
            if (quantity == null || quantity <= 0) return "0"
            return unit.multiply(BigDecimal(quantity)).toPlainString()
        }

        private const val PREFS_NAME = "rider_prefs"
        private const val KEY_SHARE_LOCATION = "share_location"
        private const val UPDATE_INTERVAL_MS = 15_000L
        private const val UPDATE_DISTANCE_M = 10f
        private const val SEND_INTERVAL_MS = 30_000L
        private const val SEND_DISTANCE_M = 30f

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                RiderViewModel(app.myStockRepository, app)
            }
        }
    }
}
