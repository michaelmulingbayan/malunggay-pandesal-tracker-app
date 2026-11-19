package com.example.malunggaytrackerapp.ui.riders

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.FragmentRidersBinding
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Admin › Riders: list of riders with an active toggle, and a "+" button to add one. */
class RidersFragment : Fragment(R.layout.fragment_riders) {

    private val viewModel: RidersViewModel by viewModels { RidersViewModel.Factory }

    private val addRider = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.load()
            showMessage(UiText.Resource(R.string.riders_added))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentRidersBinding.bind(view)
        binding.header.padTopForStatusBar()

        val adapter = RiderAdapter(onActiveChanged = viewModel::setActive)
        binding.riderList.adapter = adapter
        binding.riderList.itemAnimator?.changeDuration = 0
        binding.riderList.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.rider_divider_inset)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.screen_padding)
                isLastItemDecorated = false
            },
        )

        binding.addButton.setOnClickListener { addRider.launch(AddRiderActivity.newIntent(requireContext())) }
        binding.retryButton.setOnClickListener { viewModel.load() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, adapter, it) } }
                launch { viewModel.messages.collect(::showMessage) }
            }
        }
    }

    private fun render(binding: FragmentRidersBinding, adapter: RiderAdapter, state: RidersUiState) {
        val hasRiders = state.riders.isNotEmpty()

        adapter.submitList(state.rows)
        binding.riderList.isVisible = hasRiders
        binding.progress.isVisible = state.isLoading && !hasRiders
        binding.errorState.isVisible = state.error != null && !hasRiders
        binding.emptyState.isVisible = !state.isLoading && state.error == null && !hasRiders
        binding.errorText.text = state.error?.asString(requireContext())

        binding.subtitleText.text = if (hasRiders) {
            getString(R.string.riders_count, state.activeCount, state.riders.size)
        } else {
            getString(R.string.section_riders_subtitle)
        }
    }

    private fun showMessage(message: UiText) {
        val view = view ?: return
        Snackbar.make(view, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }
}
