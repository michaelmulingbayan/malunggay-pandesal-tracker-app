package com.example.malunggaytrackerapp.ui.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.RiderPositionDto
import com.example.malunggaytrackerapp.data.remote.UserDto
import com.example.malunggaytrackerapp.databinding.FragmentHomeBinding
import com.example.malunggaytrackerapp.databinding.ItemStatTileBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.RiderMarkerIcon
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.initialsOf
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.example.malunggaytrackerapp.ui.distribution.DistributeStockActivity
import com.example.malunggaytrackerapp.ui.production.ProductionActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Home tab: who is signed in, plus (for admins) today's dashboard —
 * total sales, produced / available / distributed / active riders, actions and the rider map.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val sessionViewModel: SessionViewModel by activityViewModels { SessionViewModel.Factory }
    private val dashboardViewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }

    private var isAdmin = false
    private var mapView: MapView? = null

    /** Positions currently drawn, so markers are only rebuilt when something changed. */
    private var shownPositions: List<RiderPositionDto>? = null

    /** Zoom to fit riders only the first time; after that, keep whatever the admin panned to. */
    private var hasFramedRiders = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHomeBinding.bind(view)
        binding.header.padTopForStatusBar()

        binding.logoutButton.setOnClickListener { sessionViewModel.logout() }
        binding.retryButton.setOnClickListener { sessionViewModel.loadUser() }
        binding.recordProductionButton.setOnClickListener {
            startActivity(ProductionActivity.newIntent(requireContext()))
        }
        binding.distributeStockButton.setOnClickListener {
            startActivity(DistributeStockActivity.newIntent(requireContext()))
        }

        setupTile(binding.statProduced, R.drawable.ic_production, R.string.dashboard_produced)
        setupTile(binding.statAvailable, R.drawable.ic_nav_stock, R.string.dashboard_available)
        setupTile(binding.statDistributed, R.drawable.ic_distribute, R.string.dashboard_distributed)
        setupTile(binding.statRiders, R.drawable.ic_nav_riders, R.string.dashboard_active_riders)
        setupMap(binding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { sessionViewModel.state.collect { state -> render(binding, state) } }
                launch { dashboardViewModel.state.collect { state -> renderDashboard(binding, state) } }
            }
        }

        // Near real-time: refresh rider positions every 30 s while the dashboard is on screen.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    if (isAdmin && !isHidden) dashboardViewModel.refreshLocations()
                    delay(MAP_REFRESH_MS)
                }
            }
        }
    }

    /** Refresh the numbers when coming back from Record production / Distribute stock. */
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        if (isAdmin && !isHidden) dashboardViewModel.load()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView?.onDetach()
        mapView = null
        shownPositions = null
        hasFramedRiders = false
        super.onDestroyView()
    }

    /** Tabs are kept alive and hidden; refresh whenever Home is shown again. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdmin) {
            dashboardViewModel.load()
            dashboardViewModel.refreshLocations()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap(binding: FragmentHomeBinding) {
        val map = binding.mapView
        mapView = map
        // OpenStreetMap tiles: no API key; the app identifies itself (user agent set in MalunggayApp).
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true) // pinch to zoom
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // our own + / − buttons
        map.isTilesScaledToDpi = true
        map.minZoomLevel = MIN_ZOOM
        map.maxZoomLevel = MAX_ZOOM
        map.controller.setZoom(DEFAULT_ZOOM)
        map.controller.setCenter(MANILA)

        // Keep the dashboard scrollable: one finger scrolls the page (horizontal drags still pan the map);
        // two fingers belong to the map so pinch-zoom and two-finger panning work.
        map.setOnTouchListener { v, event ->
            if (event.pointerCount >= 2) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        binding.zoomInButton.setOnClickListener { map.controller.zoomIn() }
        binding.zoomOutButton.setOnClickListener { map.controller.zoomOut() }
        binding.recenterButton.setOnClickListener {
            frameRiders(map, dashboardViewModel.state.value.riderPositions)
        }

        val context = requireContext()
        binding.legendAvailableDot.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.emerald_700))
        binding.legendUnavailableDot.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.neutral_500))
    }

    private fun render(binding: FragmentHomeBinding, state: SessionState) {
        binding.progress.isVisible = state is SessionState.Loading
        binding.logoutButton.isEnabled = state !is SessionState.Loading && state !is SessionState.LoggingOut
        binding.errorState.isVisible = state is SessionState.Error

        when (state) {
            is SessionState.Ready -> showUser(binding, state.user)
            is SessionState.Error -> binding.messageText.setText(state.message)
            else -> Unit
        }
    }

    private fun showUser(binding: FragmentHomeBinding, user: UserDto) {
        // Header greeting (everyone)
        binding.headerAvatarText.text = initialsOf(user.name)
        binding.greetingText.setText(greetingForNow())
        binding.headerNameText.text = user.name
        binding.dateText.text = DateLabels.longToday()

        // Riders see their profile card; admins get the dashboard instead.
        binding.avatarText.text = initialsOf(user.name)
        binding.welcomeText.text = getString(R.string.home_welcome, user.name.substringBefore(' '))
        binding.emailText.text = user.email
        binding.roleText.text = user.role.replaceFirstChar { it.uppercase() }
        binding.profileCard.isVisible = !user.isAdmin
        binding.quickActions.isVisible = user.isAdmin

        val becameAdmin = user.isAdmin && !isAdmin
        isAdmin = user.isAdmin
        if (becameAdmin) {
            dashboardViewModel.load()
            dashboardViewModel.refreshLocations()
        }
    }

    private fun renderDashboard(binding: FragmentHomeBinding, state: DashboardUiState) {
        binding.dashboardProgress.isVisible = state.isLoading && !state.hasLoaded
        binding.salesTotalText.text = formatPeso(state.salesTotal)
        binding.salesPiecesText.text = getString(R.string.dashboard_pieces_sold, formatCount(state.piecesSold))
        binding.salesShareText.text = getString(
            R.string.dashboard_of_produced,
            if (state.produced > 0) state.piecesSold * 100 / state.produced else 0,
        )
        binding.salesShareText.isVisible = state.produced > 0
        binding.statProduced.valueText.text = formatCount(state.produced)
        binding.statAvailable.valueText.text = formatCount(state.available)
        binding.statDistributed.valueText.text = formatCount(state.distributed)
        binding.statRiders.valueText.text = formatCount(state.activeRiders)

        renderMap(binding, state)
    }

    private fun renderMap(binding: FragmentHomeBinding, state: DashboardUiState) {
        val positions = state.riderPositions
        binding.mapEmpty.isVisible = state.positionsLoaded && positions.isEmpty()
        binding.recenterButton.isVisible = positions.isNotEmpty()
        binding.mapCountText.text = resources.getQuantityString(R.plurals.map_riders_count, positions.size, positions.size)
        binding.mapRefreshedText.text = state.positionsRefreshedAt?.let {
            getString(R.string.map_refreshed, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)))
        }.orEmpty()

        val map = mapView ?: return
        if (positions == shownPositions) return
        shownPositions = positions

        map.overlays.removeAll { it is Marker }
        positions.forEach { rider ->
            val marker = Marker(map).apply {
                position = GeoPoint(rider.latitude, rider.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = RiderMarkerIcon.create(requireContext(), rider.name, rider.isAvailable)
                title = rider.name
                snippet = getString(
                    R.string.map_marker_snippet,
                    getString(if (rider.isAvailable) R.string.map_legend_available else R.string.map_legend_unavailable),
                    DateLabels.relativeDateTime(requireContext(), rider.recordedAt),
                )
            }
            map.overlays.add(marker)
        }
        map.invalidate()

        if (!hasFramedRiders && positions.isNotEmpty()) {
            hasFramedRiders = true
            frameRiders(map, positions)
        }
    }

    /** Zooms so every rider is visible (or centers on the only one). */
    private fun frameRiders(map: MapView, positions: List<RiderPositionDto>) {
        if (positions.isEmpty()) return
        map.post {
            if (positions.size == 1) {
                map.controller.setZoom(SINGLE_RIDER_ZOOM)
                map.controller.animateTo(GeoPoint(positions[0].latitude, positions[0].longitude))
            } else {
                val box = BoundingBox.fromGeoPoints(positions.map { GeoPoint(it.latitude, it.longitude) })
                map.zoomToBoundingBox(box.increaseByScale(1.5f), true)
            }
        }
    }

    @StringRes
    private fun greetingForNow(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    private fun setupTile(tile: ItemStatTileBinding, @DrawableRes icon: Int, @StringRes label: Int) {
        tile.iconImage.setImageResource(icon)
        tile.labelText.setText(label)
        tile.valueText.text = "–"
    }

    private companion object {
        const val MAP_REFRESH_MS = 30_000L
        const val DEFAULT_ZOOM = 11.0
        const val SINGLE_RIDER_ZOOM = 15.0
        const val MIN_ZOOM = 5.0
        const val MAX_ZOOM = 19.0
        val MANILA = GeoPoint(14.5995, 120.9842)
    }
}
