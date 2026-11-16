package com.example.malunggaytrackerapp.ui.inventory

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.StockReturnDto
import com.example.malunggaytrackerapp.databinding.FragmentInventoryBinding
import com.example.malunggaytrackerapp.databinding.ItemLegendBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Admin › Stock tab: today's produced / distributed / returned chart and the returns to verify. */
class InventoryFragment : Fragment(R.layout.fragment_inventory) {

    private val viewModel: InventoryViewModel by viewModels { InventoryViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentInventoryBinding.bind(view)
        binding.header.padTopForStatusBar()
        binding.dateText.text = DateLabels.longToday()
        binding.retryButton.setOnClickListener { viewModel.load() }

        val adapter = StockReturnAdapter(onVerify = ::confirmVerify)
        binding.returnList.adapter = adapter
        binding.returnList.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.production_divider_inset)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_xl)
                isLastItemDecorated = false
            },
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, adapter, it) } }
                launch { viewModel.messages.collect(::showMessage) }
            }
        }
    }

    /** Tabs are kept alive and hidden; refresh whenever the Stock tab is shown again. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.load()
    }

    private fun render(binding: FragmentInventoryBinding, adapter: StockReturnAdapter, state: InventoryUiState) {
        val summary = state.summary

        binding.chartProgress.isVisible = state.isLoading && summary == null
        if (summary != null) {
            // Real recorded sales (matches the Sales tab and the dashboard).
            val sold = summary.sold

            binding.producedTotalText.text = formatCount(summary.produced)
            binding.percentSoldText.text = getString(
                R.string.inventory_percent_sold,
                if (summary.produced > 0) (sold * 100 / summary.produced) else 0,
            )
            binding.flowBar.setValues(
                produced = summary.produced,
                sold = sold,
                returned = summary.returned,
                description = getString(
                    R.string.inventory_flow_description,
                    formatCount(summary.produced), formatCount(sold), formatCount(summary.returned),
                ),
            )

            val produced = summary.produced
            bindLegend(binding.legendProduced, R.string.inventory_legend_produced, produced, produced, R.color.chart_produced)
            bindLegend(binding.legendSold, R.string.inventory_legend_sold, sold, produced, R.color.chart_sold)
            bindLegend(binding.legendReturned, R.string.inventory_legend_returned, summary.returned, produced, R.color.chart_returned)

            binding.pendingText.text = if (summary.returnedPending > 0) {
                getString(R.string.inventory_note_pending, formatCount(summary.returnedPending))
            } else {
                getString(R.string.inventory_note_all_clear)
            }
        }
        binding.pendingText.isVisible = summary != null && summary.returned > 0

        val error = state.error?.asString(requireContext())
        binding.errorText.text = error
        binding.errorBanner.isVisible = error != null

        adapter.submitList(state.rows)
        binding.returnList.isVisible = state.returns.isNotEmpty()
        binding.returnsEmptyText.isVisible = !state.isLoading && state.returns.isEmpty() && state.error == null
    }

    /** One category row: swatch matching the bar segment, name, pieces and share of production. */
    private fun bindLegend(
        legend: ItemLegendBinding,
        @StringRes labelRes: Int,
        value: Int,
        produced: Int,
        @ColorRes colorRes: Int,
    ) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        val strokeWidth = (resources.displayMetrics.density).toInt().coerceAtLeast(1)

        legend.labelText.setText(labelRes)
        legend.valueText.text = getString(R.string.inventory_pcs, formatCount(value))
        legend.percentText.text = getString(R.string.inventory_percent, if (produced > 0) value * 100 / produced else 0)
        // Rounded swatch with a slightly darker edge so the soft production color stays visible on white.
        legend.dot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4 * resources.displayMetrics.density
            setColor(color)
            setStroke(strokeWidth, ColorUtils.blendARGB(color, Color.BLACK, 0.18f))
        }
    }

    private fun confirmVerify(stockReturn: StockReturnDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.inventory_verify_title)
            .setMessage(
                getString(R.string.inventory_verify_message, formatCount(stockReturn.quantityReturned), stockReturn.rider.name),
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.inventory_verify) { _, _ -> viewModel.verify(stockReturn) }
            .show()
    }

    private fun showMessage(message: UiText) {
        val view = view ?: return
        Snackbar.make(view, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }
}
