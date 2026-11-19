package com.example.malunggaytrackerapp.ui.production

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityProductionBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Admin › Record production: today's input on top, production history below. */
class ProductionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductionBinding
    private val viewModel: ProductionViewModel by viewModels { ProductionViewModel.Factory }
    private val adapter = ProductionAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.dateText.text = DateLabels.longToday()
        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save() }
        binding.historyRetryButton.setOnClickListener { viewModel.loadHistory() }
        binding.quantityInput.doAfterTextChanged { viewModel.clearQuantityError() }

        binding.historyList.adapter = adapter
        binding.historyList.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.production_divider_inset)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_xl)
                isLastItemDecorated = false
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ProductionEvent.Recorded -> onRecorded(event)
                        }
                    }
                }
            }
        }
    }

    private fun save() {
        viewModel.record(
            quantityText = binding.quantityInput.text?.toString().orEmpty(),
            remarks = binding.remarksInput.text?.toString().orEmpty(),
        )
    }

    private fun onRecorded(event: ProductionEvent.Recorded) {
        binding.quantityInput.text = null
        binding.remarksInput.text = null
        binding.quantityInput.clearFocus()
        binding.remarksInput.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        Snackbar.make(binding.root, event.message.asString(this), Snackbar.LENGTH_SHORT).show()
    }

    private fun render(state: ProductionUiState) {
        // Today summary
        binding.todaySummary.isVisible = state.todayBatchCount > 0
        binding.todaySummaryText.text = getString(R.string.production_today_summary, formatCount(state.todayTotal))
        binding.todayBatchesText.text = resources.getQuantityString(
            R.plurals.production_batches, state.todayBatchCount, state.todayBatchCount,
        )

        // Form
        binding.quantityLayout.error = state.quantityError?.asString(this)
        binding.remarksLayout.error = state.remarksError?.asString(this)
        binding.quantityLayout.isEnabled = !state.isSaving
        binding.remarksLayout.isEnabled = !state.isSaving

        val formError = state.formError?.asString(this)
        binding.errorText.text = formError
        binding.errorBanner.isVisible = formError != null

        binding.saveProgress.visibility = if (state.isSaving) View.VISIBLE else View.GONE
        binding.saveButton.text = if (state.isSaving) "" else getString(R.string.production_save)
        binding.saveButton.contentDescription = if (state.isSaving) getString(R.string.production_saving) else null
        binding.saveButton.isClickable = !state.isSaving

        // History
        val hasHistory = state.history.isNotEmpty()
        adapter.submitList(state.history)
        binding.historyList.isVisible = hasHistory
        binding.historyProgress.isVisible = state.isLoadingHistory && !hasHistory
        binding.historyErrorState.isVisible = state.historyError != null && !hasHistory
        binding.historyErrorText.text = state.historyError?.asString(this)
        binding.historyEmptyText.isVisible = !state.isLoadingHistory && state.historyError == null && !hasHistory
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ProductionActivity::class.java)
    }
}
