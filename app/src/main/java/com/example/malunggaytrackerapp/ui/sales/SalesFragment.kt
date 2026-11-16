package com.example.malunggaytrackerapp.ui.sales

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.DialogSetPriceBinding
import com.example.malunggaytrackerapp.databinding.FragmentSalesBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Admin › Sales tab: price per piece, today's total sales, pieces sold, riders, per-rider breakdown. */
class SalesFragment : Fragment(R.layout.fragment_sales) {

    private val viewModel: SalesViewModel by viewModels { SalesViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSalesBinding.bind(view)
        binding.header.padTopForStatusBar()
        binding.dateText.text = DateLabels.longToday()
        binding.retryButton.setOnClickListener { viewModel.load() }
        binding.changePriceButton.setOnClickListener { showPriceDialog() }

        val adapter = RiderSalesAdapter()
        binding.riderList.adapter = adapter
        binding.riderList.addItemDecoration(
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

    /** Tabs are kept alive and hidden; refresh whenever the Sales tab is shown again. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.load()
    }

    private fun render(binding: FragmentSalesBinding, adapter: RiderSalesAdapter, state: SalesUiState) {
        val summary = state.summary
        val price = state.price

        // Price per piece
        binding.priceText.text = price?.let { getString(R.string.price_value, formatPeso(it.pricePerPiece)) }
            ?: getString(R.string.price_not_set)
        binding.priceDetailText.text = when {
            price != null -> getString(
                R.string.price_set_detail,
                price.setBy?.name.orEmpty(),
                DateLabels.relativeDateTime(requireContext(), price.createdAt),
            )
            state.isPriceLoaded -> getString(R.string.price_not_set_detail)
            else -> ""
        }
        binding.changePriceButton.setText(if (price == null) R.string.price_set else R.string.price_change)

        // Totals
        binding.loadProgress.isVisible = state.isLoading && summary == null
        binding.totalText.text = formatPeso(summary?.totalAmount ?: "0")
        binding.piecesText.text = formatCount(summary?.piecesSold ?: 0)
        binding.ridersText.text = formatCount(summary?.ridersCount ?: 0)

        val error = state.error?.asString(requireContext())
        binding.errorText.text = error
        binding.errorBanner.isVisible = error != null

        // Per rider
        val rows = state.rows
        adapter.submitList(rows)
        binding.riderList.isVisible = rows.isNotEmpty()
        binding.emptyText.isVisible = summary != null && rows.isEmpty()
    }

    /** Dialog that stays open until the price is valid and saved. */
    private fun showPriceDialog() {
        val dialogBinding = DialogSetPriceBinding.inflate(layoutInflater)
        viewModel.state.value.price?.let { dialogBinding.priceInput.setText(it.pricePerPiece) }
        dialogBinding.priceInput.doAfterTextChanged { dialogBinding.priceLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.price_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.price_save, null)
            .create()

        val save = {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = viewModel.setPrice(dialogBinding.priceInput.text?.toString().orEmpty())) {
                    PriceSaveResult.Saved -> dialog.dismiss()
                    is PriceSaveResult.Invalid -> {
                        dialogBinding.priceLayout.error = result.message.asString(requireContext())
                        saveButton.isEnabled = true
                    }
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            dialogBinding.priceInput.requestFocus()
            dialogBinding.priceInput.setSelection(dialogBinding.priceInput.text?.length ?: 0)
        }
        dialogBinding.priceInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    private fun showMessage(message: UiText) {
        val view = view ?: return
        Snackbar.make(view, message.asString(requireContext()), Snackbar.LENGTH_SHORT)
            .setAnchorView(requireActivity().findViewById(R.id.bottomNavContainer))
            .show()
    }
}
