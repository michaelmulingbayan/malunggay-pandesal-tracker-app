package com.example.malunggaytrackerapp.ui.home

import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.UserDto
import com.example.malunggaytrackerapp.databinding.FragmentHomeBinding
import com.example.malunggaytrackerapp.databinding.ItemStatTileBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.initialsOf
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.example.malunggaytrackerapp.ui.distribution.DistributeStockActivity
import com.example.malunggaytrackerapp.ui.production.ProductionActivity
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Home tab: who is signed in, plus (for admins) today's dashboard —
 * total sales, produced / available / distributed / active riders, actions and the map preview.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val sessionViewModel: SessionViewModel by activityViewModels { SessionViewModel.Factory }
    private val dashboardViewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }

    private var isAdmin = false

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { sessionViewModel.state.collect { state -> render(binding, state) } }
                launch { dashboardViewModel.state.collect { state -> renderDashboard(binding, state) } }
            }
        }
    }

    /** Refresh the numbers when coming back from Record production / Distribute stock. */
    override fun onResume() {
        super.onResume()
        if (isAdmin && !isHidden) dashboardViewModel.load()
    }

    /** Tabs are kept alive and hidden; refresh whenever Home is shown again. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdmin) dashboardViewModel.load()
    }

    private fun render(binding: FragmentHomeBinding, state: SessionState) {
        binding.progress.isVisible = state is SessionState.Loading
        binding.logoutButton.isEnabled = state !is SessionState.Loading
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
        if (becameAdmin) dashboardViewModel.load()
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
}
