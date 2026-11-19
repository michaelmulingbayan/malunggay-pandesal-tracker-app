package com.example.malunggaytrackerapp.ui.rider

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.LocationDto
import com.example.malunggaytrackerapp.databinding.FragmentRiderHomeBinding
import com.example.malunggaytrackerapp.databinding.ItemStatTileVerticalBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.example.malunggaytrackerapp.ui.home.SessionState
import com.example.malunggaytrackerapp.ui.home.SessionViewModel
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Rider › Home: date, "Hi, Juan", availability toggle with live location, remaining stock
 * with a progress bar, Assigned / Sold / Sales cards, and the "Share my location" toggle.
 */
class RiderHomeFragment : Fragment(R.layout.fragment_rider_home) {

    private val sessionViewModel: SessionViewModel by activityViewModels { SessionViewModel.Factory }
    private val riderViewModel: RiderViewModel by activityViewModels { RiderViewModel.Factory }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.any { it }
        riderViewModel.setSharingEnabled(enabled = granted, hasPermission = granted)
        if (!granted) showMessage(UiText.Resource(R.string.location_permission_needed))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentRiderHomeBinding.bind(view)
        binding.header.padTopForStatusBar()
        binding.dateText.text = DateLabels.longToday()

        binding.logoutButton.setOnClickListener {
            riderViewModel.pauseSharing()
            sessionViewModel.logout()
        }
        binding.retryButton.setOnClickListener { riderViewModel.load() }

        setupTile(binding.statAssigned, R.drawable.ic_distribute, R.string.rider_assigned)
        setupTile(binding.statSold, R.drawable.ic_production, R.string.rider_sold)
        setupTile(binding.statSales, R.drawable.ic_nav_sales, R.string.rider_sales)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sessionViewModel.state.collect { state ->
                        binding.logoutButton.isEnabled = state !is SessionState.LoggingOut
                        if (state is SessionState.Ready) {
                            binding.greetingText.text = getString(R.string.rider_hi, state.user.name.substringBefore(' '))
                        }
                    }
                }
                launch { riderViewModel.state.collect { render(binding, it) } }
                launch {
                    riderViewModel.events.collect { event ->
                        if (event is RiderEvent.Message) showMessage(event.message)
                    }
                }
            }
        }
    }

    /** Location is shared only while the app is in the foreground. */
    override fun onStart() {
        super.onStart()
        riderViewModel.resumeSharing(hasLocationPermission())
    }

    override fun onStop() {
        riderViewModel.pauseSharing()
        super.onStop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) riderViewModel.load()
    }

    private fun render(binding: FragmentRiderHomeBinding, state: RiderUiState) {
        val summary = state.summary
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val muted = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutline)

        // Availability
        val available = summary?.isAvailable ?: false
        binding.availabilityTitle.setText(if (available) R.string.rider_available else R.string.rider_unavailable)
        binding.availabilityBody.setText(if (available) R.string.rider_available_body else R.string.rider_unavailable_body)
        binding.availabilityDot.backgroundTintList = ColorStateList.valueOf(if (available) primary else muted)
        binding.availabilitySwitch.setOnCheckedChangeListener(null)
        binding.availabilitySwitch.isChecked = available
        binding.availabilitySwitch.isEnabled = summary != null && !state.isUpdatingAvailability
        binding.availabilitySwitch.setOnCheckedChangeListener { _, checked -> riderViewModel.setAvailable(checked) }

        // Live location
        renderLocation(binding, state)
        binding.liveBadge.isVisible = state.isTracking
        binding.liveDot.backgroundTintList = ColorStateList.valueOf(primary)

        // Remaining stock
        val assigned = summary?.assigned ?: 0
        val remaining = summary?.remaining ?: 0
        val percentLeft = if (assigned > 0) remaining * 100 / assigned else 0
        binding.remainingText.text = getString(R.string.rider_remaining_value, formatCount(remaining))
        binding.remainingOfText.text = getString(R.string.rider_remaining_of, formatCount(assigned))
        binding.remainingBar.setProgressCompat(percentLeft, true)
        binding.remainingDetailText.text = getString(
            R.string.rider_remaining_detail, formatCount(summary?.sold ?: 0), formatCount(summary?.returned ?: 0), percentLeft,
        )
        binding.noStockText.isVisible = summary != null && assigned == 0
        binding.loadProgress.isVisible = state.isLoading && summary == null

        // Assigned / Sold / Sales
        binding.statAssigned.valueText.text = formatCount(assigned)
        binding.statSold.valueText.text = formatCount(summary?.sold ?: 0)
        binding.statSales.valueText.text = formatPeso(summary?.salesAmount ?: "0")

        // Share my location
        binding.shareSwitch.setOnCheckedChangeListener(null)
        binding.shareSwitch.isChecked = state.isSharingEnabled
        binding.shareSwitch.setOnCheckedChangeListener { _, checked -> onShareToggled(checked) }

        val error = state.error?.asString(requireContext())
        binding.errorText.text = error
        binding.errorBanner.isVisible = error != null
    }

    private fun renderLocation(binding: FragmentRiderHomeBinding, state: RiderUiState) {
        val location: LocationDto? = state.currentLocation
        when {
            location != null -> {
                binding.locationText.text = getString(
                    R.string.rider_location_value,
                    String.format(Locale.US, "%.5f", location.latitude),
                    String.format(Locale.US, "%.5f", location.longitude),
                )
                binding.locationUpdatedText.text = getString(
                    R.string.rider_location_updated, DateLabels.relativeDateTime(requireContext(), location.recordedAt),
                )
            }
            state.isTracking -> {
                binding.locationText.setText(R.string.rider_location_waiting)
                binding.locationUpdatedText.text = ""
            }
            !state.isSharingEnabled -> {
                binding.locationText.setText(R.string.rider_location_off)
                binding.locationUpdatedText.text = ""
            }
            else -> {
                binding.locationText.setText(R.string.rider_location_none)
                binding.locationUpdatedText.text = ""
            }
        }
        binding.locationUpdatedText.isVisible = binding.locationUpdatedText.text.isNotEmpty()
    }

    private fun onShareToggled(enabled: Boolean) {
        when {
            !enabled -> riderViewModel.setSharingEnabled(enabled = false, hasPermission = hasLocationPermission())
            hasLocationPermission() -> riderViewModel.setSharingEnabled(enabled = true, hasPermission = true)
            else -> locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        return listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showMessage(message: UiText) {
        val view = view ?: return
        Snackbar.make(view, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }

    private fun setupTile(tile: ItemStatTileVerticalBinding, @DrawableRes icon: Int, @StringRes label: Int) {
        tile.iconImage.setImageResource(icon)
        tile.labelText.setText(label)
        tile.valueText.text = "–"
    }
}
