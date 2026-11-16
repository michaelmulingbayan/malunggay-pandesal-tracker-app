package com.example.malunggaytrackerapp.ui.distribution

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.RiderAccountDto
import com.example.malunggaytrackerapp.databinding.ActivityDistributeStockBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.UiText
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Admin › Distribute stock: available stock on top, assign form, today's assignments below. */
class DistributeStockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDistributeStockBinding
    private val viewModel: DistributeStockViewModel by viewModels { DistributeStockViewModel.Factory }
    private val assignmentAdapter = AssignmentAdapter()

    /** Riders currently shown in the dropdown (adapter is only rebuilt when this changes). */
    private var shownRiders: List<RiderAccountDto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDistributeStockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.dateText.text = DateLabels.longToday()
        binding.backButton.setOnClickListener { finish() }
        binding.retryButton.setOnClickListener { viewModel.load() }
        binding.assignButton.setOnClickListener { assign() }
        binding.quantityInput.doAfterTextChanged { viewModel.clearQuantityError() }
        binding.quantityInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                assign()
                true
            } else {
                false
            }
        }
        binding.riderInput.setOnItemClickListener { _, _, position, _ ->
            shownRiders.getOrNull(position)?.let { viewModel.selectRider(it.id) }
        }

        binding.assignmentList.adapter = assignmentAdapter
        binding.assignmentList.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL).apply {
                dividerInsetStart = resources.getDimensionPixelSize(R.dimen.production_divider_inset)
                dividerInsetEnd = resources.getDimensionPixelSize(R.dimen.space_xl)
                isLastItemDecorated = false
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.assigned.collect(::onAssigned) }
            }
        }
    }

    private fun assign() {
        viewModel.assign(binding.quantityInput.text?.toString().orEmpty())
    }

    private fun onAssigned(message: UiText) {
        binding.quantityInput.text = null
        binding.riderInput.setText("", false)
        binding.quantityInput.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(binding.root.windowToken, 0)
        Snackbar.make(binding.root, message.asString(this), Snackbar.LENGTH_SHORT).show()
    }

    private fun render(state: DistributeUiState) {
        renderSummary(state)
        renderRiders(state)

        binding.loadProgress.isVisible = state.isLoading

        // Form
        binding.riderLayout.error = state.riderError?.asString(this)
        binding.quantityLayout.error = state.quantityError?.asString(this)
        binding.riderLayout.isEnabled = !state.isAssigning
        binding.quantityLayout.isEnabled = !state.isAssigning

        val bannerError = (state.formError ?: state.loadError)?.asString(this)
        binding.errorText.text = bannerError
        binding.errorBanner.isVisible = bannerError != null
        binding.retryButton.isVisible = state.formError == null && state.loadError != null

        binding.assignProgress.visibility = if (state.isAssigning) View.VISIBLE else View.GONE
        binding.assignButton.text = if (state.isAssigning) "" else getString(R.string.distribute_assign)
        binding.assignButton.contentDescription = if (state.isAssigning) getString(R.string.distribute_assigning) else null
        binding.assignButton.isClickable = !state.isAssigning

        // Assigned today
        assignmentAdapter.submitList(state.assignments)
        binding.assignmentList.isVisible = state.assignments.isNotEmpty()
        binding.assignmentsEmptyText.isVisible = !state.isLoading && state.assignments.isEmpty()
    }

    private fun renderSummary(state: DistributeUiState) {
        val summary = state.summary
        binding.availableText.text = getString(R.string.distribute_available_value, formatCount(summary?.available ?: 0))
        binding.availableDetailText.text = when {
            summary == null -> ""
            summary.produced == 0 -> getString(R.string.distribute_none_produced)
            else -> getString(
                R.string.distribute_available_detail,
                formatCount(summary.produced),
                formatCount(summary.distributed),
            )
        }
    }

    private fun renderRiders(state: DistributeUiState) {
        if (state.riders != shownRiders) {
            shownRiders = state.riders
            binding.riderInput.setAdapter(
                ArrayAdapter(this, R.layout.item_dropdown_option, state.riders.map { it.name }),
            )
        }

        val selectedName = state.riders.firstOrNull { it.id == state.selectedRiderId }?.name.orEmpty()
        if (binding.riderInput.text.toString() != selectedName) {
            binding.riderInput.setText(selectedName, false)
        }

        binding.riderLayout.helperText = if (!state.isLoading && state.loadError == null && state.riders.isEmpty()) {
            getString(R.string.distribute_no_riders)
        } else {
            null
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, DistributeStockActivity::class.java)
    }
}
