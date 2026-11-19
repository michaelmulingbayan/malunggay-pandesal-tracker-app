package com.example.malunggaytrackerapp.ui.customer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityFindRiderBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.RiderMarkerIcon
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatDistance
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker

/**
 * Customer › Find a rider (no login).
 *
 * Shows available riders on a map with the straight-line distance from the customer.
 * The customer's location is used only on this phone to measure distance.
 */
class FindRiderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindRiderBinding
    private val viewModel: FindRiderViewModel by viewModels { FindRiderViewModel.Factory }
    private val adapter = NearbyRiderAdapter(onClick = ::focusRider)

    private var myMarker: Marker? = null
    private val riderMarkers = mutableMapOf<Long, Marker>()
    private var hasFramed = false
    private var hasFramedWithMe = false
    private var lastError: String? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindRiderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.backButton.setOnClickListener { finish() }
        binding.allowLocationButton.setOnClickListener { requestLocation() }
        binding.myLocationCard.setOnClickListener { binding.myLocationButton.performClick() }
        setupMap()

        binding.riderList.adapter = adapter
        binding.riderList.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.space_lg)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_lg)
                isLastItemDecorated = false
            },
        )

        if (hasLocationPermission()) viewModel.startLocating() else requestLocation()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    while (isActive) {
                        viewModel.loadRiders()
                        delay(REFRESH_MILLIS)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (hasLocationPermission()) viewModel.startLocating()
    }

    override fun onPause() {
        binding.mapView.onPause()
        viewModel.stopLocating()
        super.onPause()
    }

    override fun onDestroy() {
        binding.mapView.onDetach()
        super.onDestroy()
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.isTilesScaledToDpi = true
        map.minZoomLevel = 5.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(12.0)
        map.controller.setCenter(MANILA)

        binding.zoomInButton.setOnClickListener { map.controller.zoomIn() }
        binding.zoomOutButton.setOnClickListener { map.controller.zoomOut() }
        binding.myLocationButton.setOnClickListener {
            val me = viewModel.state.value.myLocation
            if (me == null) {
                requestLocation()
            } else {
                map.controller.animateTo(GeoPoint(me.latitude, me.longitude), 16.0, 600L)
            }
        }
    }

    private fun render(state: FindRiderUiState) {
        val rows = state.rows
        binding.updatedText.text = state.refreshedAt
            ?.let { getString(R.string.customer_subtitle, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it))) }
            ?: ""
        binding.permissionCard.isVisible = state.myLocation == null && !hasLocationPermission()
        renderMyLocation(state)
        binding.emptyState.isVisible = !state.isLoading && rows.isEmpty()
        binding.riderList.isVisible = rows.isNotEmpty()
        adapter.submitList(rows)

        renderMarkers(state)
        // Frame riders when they load, then once more when the customer's own location arrives,
        // so both the blue dot and the riders are on screen.
        if (!hasFramed && !state.isLoading && (rows.isNotEmpty() || state.myLocation != null)) {
            frameAll(state)
            hasFramed = true
            hasFramedWithMe = state.myLocation != null
        } else if (hasFramed && !hasFramedWithMe && state.myLocation != null) {
            frameAll(state)
            hasFramedWithMe = true
        }

        val error = state.error?.asString(this)
        if (error != null && error != lastError) {
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
        }
        lastError = error
    }

    private fun renderMarkers(state: FindRiderUiState) {
        val map = binding.mapView
        val rows = state.rows

        // Riders: add/update/remove without rebuilding everything (keeps open info windows).
        val ids = rows.map { it.rider.riderId }.toSet()
        riderMarkers.keys.filter { it !in ids }.forEach { id ->
            riderMarkers.remove(id)?.let { it.closeInfoWindow(); map.overlays.remove(it) }
        }
        rows.forEach { row ->
            val marker = riderMarkers.getOrPut(row.rider.riderId) {
                Marker(map).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = RiderMarkerIcon.create(this@FindRiderActivity, row.rider.firstName, isAvailable = true)
                    map.overlays.add(this)
                }
            }
            marker.position = GeoPoint(row.rider.latitude, row.rider.longitude)
            marker.title = row.rider.firstName
            val seen = getString(R.string.customer_seen, DateLabels.ago(this, row.rider.recordedAt))
            marker.snippet = row.distanceMeters
                ?.let { getString(R.string.customer_distance_away, getString(R.string.customer_distance, formatDistance(it))) + " · " + seen }
                ?: seen
        }

        // The customer: a blue dot, always drawn above the riders.
        val me = state.myLocation
        if (me != null) {
            val marker = myMarker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(this@FindRiderActivity, R.drawable.ic_my_location_dot)
                title = getString(R.string.customer_you)
                myMarker = this
            }
            marker.position = GeoPoint(me.latitude, me.longitude)
            marker.snippet = state.myAddress
            map.overlays.remove(marker)
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    private fun renderMyLocation(state: FindRiderUiState) {
        val me = state.myLocation
        binding.myLocationCard.isVisible = me != null || hasLocationPermission()
        if (me == null) {
            binding.myLocationText.text = getString(R.string.customer_locating)
            binding.myLocationDetail.isVisible = false
            return
        }
        val coordinates = String.format(Locale.US, "%.5f, %.5f", me.latitude, me.longitude)
        val accuracy = if (me.hasAccuracy()) getString(R.string.customer_accuracy, me.accuracy.roundToInt()) else null
        binding.myLocationText.text = state.myAddress ?: coordinates
        binding.myLocationDetail.text = listOfNotNull(coordinates.takeIf { state.myAddress != null }, accuracy)
            .joinToString(" · ")
        binding.myLocationDetail.isVisible = binding.myLocationDetail.text.isNotEmpty()
    }

    private fun frameAll(state: FindRiderUiState) {
        val points = state.rows.map { GeoPoint(it.rider.latitude, it.rider.longitude) } +
            listOfNotNull(state.myLocation?.let { GeoPoint(it.latitude, it.longitude) })
        val map = binding.mapView
        when {
            points.isEmpty() -> Unit
            points.size == 1 -> map.controller.animateTo(points.first(), 15.0, 600L)
            else -> map.post {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points).increaseByScale(1.4f), true)
            }
        }
    }

    private fun focusRider(row: NearbyRiderRow) {
        val marker = riderMarkers[row.rider.riderId] ?: return
        binding.mapView.controller.animateTo(marker.position, 16.0, 600L)
        marker.showInfoWindow()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        locationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    private fun onPermissionResult() {
        if (hasLocationPermission()) {
            hasFramedWithMe = false
            viewModel.startLocating()
        }
        render(viewModel.state.value)
    }

    companion object {
        private const val REFRESH_MILLIS = 30_000L
        private val MANILA = GeoPoint(14.5995, 120.9842)

        fun intent(context: Context): Intent = Intent(context, FindRiderActivity::class.java)
    }
}
