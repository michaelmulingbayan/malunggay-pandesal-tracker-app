package com.example.malunggaytrackerapp.ui.rider

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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.MyReturnDto
import com.example.malunggaytrackerapp.databinding.FragmentRiderStockBinding
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

/** Rider › Stock: what I was assigned vs sold / returned / remaining, return unsold stock, my returns. */
class RiderStockFragment : Fragment(R.layout.fragment_rider_stock) {

    private val viewModel: RiderViewModel by activityViewModels { RiderViewModel.Factory }
    private val adapter = MyActivityAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentRiderStockBinding.bind(view)
        binding.header.padTopForStatusBar()
        binding.dateText.text = DateLabels.longToday()

        binding.returnsList.adapter = adapter
        binding.returnsList.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.activity_divider_inset)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_lg)
                isLastItemDecorated = false
            },
        )

        binding.returnsEmpty.emptyIcon.setImageResource(R.drawable.ic_nav_stock)
        binding.returnsEmpty.emptyTitle.setText(R.string.my_stock_returns_empty_title)
        binding.returnsEmpty.emptyBody.setText(R.string.my_stock_returns_empty_body)

        binding.returnButton.setOnClickListener { confirmReturnRemaining() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        if (event is RiderEvent.Returned) onReturned(binding, event.message)
                    }
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.load()
    }

    private fun confirmReturnRemaining() {
        val remaining = viewModel.state.value.remaining
        if (remaining <= 0) return
        viewModel.clearReturnError()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.my_stock_return_confirm_title, formatCount(remaining)))
            .setMessage(R.string.my_stock_return_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.my_stock_return_confirm) { _, _ -> viewModel.returnRemaining() }
            .show()
    }

    private fun onReturned(binding: FragmentRiderStockBinding, message: UiText) {
        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }

    private fun render(binding: FragmentRiderStockBinding, state: RiderUiState) {
        val summary = state.summary
        val assigned = summary?.assigned ?: 0
        val sold = summary?.sold ?: 0
        val returned = summary?.returned ?: 0
        val remaining = summary?.remaining ?: 0

        binding.assignedTotalText.text = formatCount(assigned)
        binding.flowBar.setValues(
            produced = assigned,
            sold = sold,
            returned = returned,
            description = getString(
                R.string.inventory_flow_description, formatCount(assigned), formatCount(sold), formatCount(returned),
            ),
        )
        bindLegend(binding.legendSold, R.string.rider_sold, sold, assigned, R.color.chart_sold)
        bindLegend(binding.legendReturned, R.string.rider_returned, returned, assigned, R.color.chart_returned)
        bindLegend(binding.legendRemaining, R.string.rider_remaining, remaining, assigned, R.color.chart_produced)

        renderReturn(binding, state)

        val rows = summary?.returns.orEmpty().map(::toRow)
        adapter.submitList(rows)
        binding.returnsList.isVisible = rows.isNotEmpty()
        binding.returnsEmpty.root.isVisible = summary != null && rows.isEmpty()
    }

    /** One tap returns everything left, so the rider never counts pieces. */
    private fun renderReturn(binding: FragmentRiderStockBinding, state: RiderUiState) {
        val remaining = state.remaining
        val hasStock = remaining > 0

        binding.returnLeftText.text = if (hasStock || state.summary == null) {
            getString(R.string.my_stock_return_left, formatCount(remaining))
        } else {
            getString(R.string.my_stock_return_nothing)
        }
        binding.returnHintText.setText(
            if (hasStock || state.summary == null) R.string.my_stock_return_helper else R.string.my_stock_return_nothing_hint,
        )

        val error = state.returnError?.asString(requireContext())
        binding.returnErrorText.text = error
        binding.returnErrorText.isVisible = error != null

        binding.returnProgress.visibility = if (state.isReturning) View.VISIBLE else View.GONE
        binding.returnButton.text = when {
            state.isReturning -> ""
            hasStock -> getString(R.string.my_stock_return_all_button, formatCount(remaining))
            else -> getString(R.string.my_stock_return_button)
        }
        binding.returnButton.contentDescription = if (state.isReturning) getString(R.string.my_stock_returning) else null
        binding.returnButton.isEnabled = hasStock || state.isReturning
        binding.returnButton.isClickable = !state.isReturning
    }

    private fun toRow(item: MyReturnDto): ActivityRow {
        val (label, colorAttr, icon) = when (item.status) {
            "verified" -> Triple(R.string.inventory_verified, androidx.appcompat.R.attr.colorPrimary, R.drawable.ic_check)
            "rejected" -> Triple(R.string.inventory_rejected, androidx.appcompat.R.attr.colorError, 0)
            else -> Triple(R.string.return_status_pending, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        }
        return ActivityRow(
            id = item.id,
            icon = R.drawable.ic_nav_stock,
            title = getString(R.string.my_stock_return_row, formatCount(item.quantityReturned)),
            time = DateLabels.relativeDateTime(requireContext(), item.returnDate),
            trailing = getString(label),
            trailingColorAttr = colorAttr,
            trailingIcon = icon,
        )
    }

    /** Same legend row style as the admin Inventory screen. */
    private fun bindLegend(legend: ItemLegendBinding, @StringRes label: Int, value: Int, total: Int, @ColorRes colorRes: Int) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        legend.labelText.setText(label)
        legend.valueText.text = getString(R.string.inventory_pcs, formatCount(value))
        legend.percentText.text = getString(R.string.inventory_percent, if (total > 0) value * 100 / total else 0)
        legend.dot.background = GradientDrawable().apply {
            cornerRadius = 4 * resources.displayMetrics.density
            setColor(color)
            setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1), ColorUtils.blendARGB(color, Color.BLACK, 0.18f))
        }
    }
}
