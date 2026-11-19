package com.example.malunggaytrackerapp.ui.customer

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
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
import com.example.malunggaytrackerapp.data.ApiResult
import com.example.malunggaytrackerapp.data.NearbyRidersRepository
import com.example.malunggaytrackerapp.data.remote.NearbyRiderDto
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.errorMessage
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A rider with the straight-line distance from the customer (null until we know where they are). */
data class NearbyRiderRow(val rider: NearbyRiderDto, val distanceMeters: Float?)

data class FindRiderUiState(
    val isLoading: Boolean = true,
    val riders: List<NearbyRiderDto> = emptyList(),
    val refreshedAt: Long? = null,
    val error: UiText? = null,
    /** The customer's own position. Stays on the phone; never sent to the server. */
    val myLocation: Location? = null,
    /** Street/barangay for [myLocation], looked up on the phone (null until known or if unavailable). */
    val myAddress: String? = null,
) {
    /** Nearest first; riders without a known distance keep server order at the end. */
    val rows: List<NearbyRiderRow>
        get() = riders
            .map { rider -> NearbyRiderRow(rider, myLocation?.let { distanceBetween(it, rider) }) }
            .sortedWith(compareBy(nullsLast()) { it.distanceMeters })

    private fun distanceBetween(me: Location, rider: NearbyRiderDto): Float {
        val result = FloatArray(1)
        Location.distanceBetween(me.latitude, me.longitude, rider.latitude, rider.longitude, result)
        return result[0]
    }
}

/** Customer › Find a rider (no login). */
class FindRiderViewModel(
    private val repository: NearbyRidersRepository,
    app: Application,
) : ViewModel() {

    private val _state = MutableStateFlow(FindRiderUiState())
    val state: StateFlow<FindRiderUiState> = _state.asStateFlow()

    private val locationManager: LocationManager? = app.getSystemService(LocationManager::class.java)
    private val geocoder: Geocoder? = if (Geocoder.isPresent()) Geocoder(app, Locale.getDefault()) else null
    private var geocodedAt: Location? = null
    private val locationListener = LocationListener { location -> onMyLocation(location) }
    private var isLocating = false
    private var loadJob: Job? = null

    fun loadRiders() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            when (val result = repository.nearbyRiders()) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, riders = result.data, refreshedAt = System.currentTimeMillis(), error = null)
                }
                else -> _state.update { it.copy(isLoading = false, error = result.errorMessage()) }
            }
        }
    }

    @SuppressLint("MissingPermission") // Only called after the location permission was granted.
    fun startLocating() {
        val manager = locationManager ?: return
        if (isLocating) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 10_000L, 20f, locationListener, Looper.getMainLooper())
            }
            providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
                ?.let { location -> onMyLocation(location) }
            isLocating = providers.isNotEmpty()
        } catch (e: SecurityException) {
            isLocating = false
        }
    }

    private fun onMyLocation(location: Location) {
        _state.update { it.copy(myLocation = location) }
        // Look the address up again only after moving ~100 m.
        val last = geocodedAt
        if (geocoder == null || (last != null && last.distanceTo(location) < 100f)) return
        geocodedAt = location
        viewModelScope.launch {
            val address = withContext(Dispatchers.IO) { addressOf(location) }
            if (address != null) _state.update { it.copy(myAddress = address) }
        }
    }

    @Suppress("DEPRECATION") // The async overload needs API 33; this runs on Dispatchers.IO.
    private fun addressOf(location: Location): String? = runCatching {
        val address = geocoder?.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            ?: return@runCatching null
        listOfNotNull(address.thoroughfare, address.subLocality, address.locality)
            .distinct()
            .joinToString(", ")
            .ifBlank { address.getAddressLine(0) }
    }.getOrNull()

    fun stopLocating() {
        locationManager?.removeUpdates(locationListener)
        isLocating = false
    }

    override fun onCleared() {
        stopLocating()
        super.onCleared()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MalunggayApp
                FindRiderViewModel(app.nearbyRidersRepository, app)
            }
        }
    }
}
