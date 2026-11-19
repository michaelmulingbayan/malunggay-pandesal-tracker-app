package com.example.malunggaytrackerapp.ui.rider

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.FragmentSellBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Rider › Sell: record how many pieces were sold; price comes from the admin-set price. */
class SellFragment : Fragment(R.layout.fragment_sell) {

    private val viewModel: RiderViewModel by activityViewModels { RiderViewModel.Factory }
    private val adapter = MyActivityAdapter()
    private var binding: FragmentSellBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSellBinding.bind(view).also { binding = it }
        binding.header.padTopForStatusBar()
        binding.dateText.text = DateLabels.longToday()

        binding.salesList.adapter = adapter
        binding.salesList.addItemDecoration(divider())
        binding.salesEmpty.emptyIcon.setImageResource(R.drawable.ic_nav_sales)
        binding.salesEmpty.emptyTitle.setText(R.string.sell_empty_title)
        binding.salesEmpty.emptyBody.setText(R.string.sell_empty_body)

        binding.quantityInput.doAfterTextChanged {
            viewModel.clearSellError()
            updateTotal(binding)
        }
        binding.quantityInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sell(binding)
                true
            } else {
                false
            }
        }
        binding.chipPlus5.setOnClickListener { addToQuantity(binding, 5) }
        binding.chipPlus10.setOnClickListener { addToQuantity(binding, 10) }
        binding.chipPlus20.setOnClickListener { addToQuantity(binding, 20) }
        binding.chipAll.setOnClickListener { setQuantity(binding, viewModel.state.value.remaining) }
        binding.sellButton.setOnClickListener { sell(binding) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(binding, it) } }
                launch {
                    viewModel.events.collect { event ->
                        if (event is RiderEvent.Sold) onSold(binding, event.message)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.load()
    }

    private fun sell(binding: FragmentSellBinding) = viewModel.sell(binding.quantityInput.text?.toString().orEmpty())

    private fun addToQuantity(binding: FragmentSellBinding, amount: Int) {
        val current = binding.quantityInput.text?.toString()?.toIntOrNull() ?: 0
        setQuantity(binding, current + amount)
    }

    private fun setQuantity(binding: FragmentSellBinding, value: Int) {
        val text = value.coerceAtLeast(0).toString()
        binding.quantityInput.setText(text)
        binding.quantityInput.setSelection(text.length)
    }

    private fun updateTotal(binding: FragmentSellBinding) {
        val quantity = binding.quantityInput.text?.toString()?.toIntOrNull()
        val price = viewModel.state.value.summary?.pricePerPiece
        binding.totalText.text = getString(R.string.sell_total, formatPeso(RiderViewModel.totalFor(quantity, price)))
    }

    private fun onSold(binding: FragmentSellBinding, message: UiText) {
        binding.quantityInput.text = null
        binding.quantityInput.clearFocus()
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }

    private fun render(binding: FragmentSellBinding, state: RiderUiState) {
        val summary = state.summary

        binding.priceText.text = summary?.pricePerPiece?.let(::formatPeso)
            ?: getString(if (summary == null) R.string.price_not_set else R.string.price_not_set)
        binding.remainingText.text = formatCount(state.remaining)
        updateTotal(binding)

        binding.quantityLayout.error = (state.sellError ?: state.error)?.asString(requireContext())
        binding.quantityLayout.isEnabled = !state.isSelling
        binding.sellProgress.visibility = if (state.isSelling) View.VISIBLE else View.GONE
        binding.sellButton.text = if (state.isSelling) "" else getString(R.string.sell_button)
        binding.sellButton.contentDescription = if (state.isSelling) getString(R.string.sell_recording) else null
        binding.sellButton.isClickable = !state.isSelling

        val rows = summary?.sales.orEmpty().map { sale ->
            ActivityRow(
                id = sale.id,
                icon = R.drawable.ic_nav_sales,
                title = getString(R.string.sell_row_title, formatCount(sale.quantitySold), formatPeso(sale.pricePerPiece)),
                time = DateLabels.relativeDateTime(requireContext(), sale.saleDate),
                trailing = formatPeso(sale.totalAmount),
                trailingColorAttr = com.google.android.material.R.attr.colorOnSurface,
            )
        }
        adapter.submitList(rows)
        binding.salesList.isVisible = rows.isNotEmpty()
        binding.salesEmpty.root.isVisible = summary != null && rows.isEmpty()
    }

    private fun divider() = MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL).apply {
        dividerInsetStart = resources.getDimensionPixelSize(R.dimen.activity_divider_inset)
        dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_lg)
        isLastItemDecorated = false
    }
}
