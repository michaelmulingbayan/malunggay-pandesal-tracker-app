package com.example.malunggaytrackerapp.ui.reports

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.PiecesSoldReportDto
import com.example.malunggaytrackerapp.databinding.FragmentReportsBinding
import com.example.malunggaytrackerapp.databinding.ItemReportLinkBinding
import com.example.malunggaytrackerapp.ui.common.BarChartView
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import com.example.malunggaytrackerapp.ui.common.padTopForStatusBar
import kotlinx.coroutines.launch

/**
 * Admin › Reports: pieces sold per day (last 7 days), week (last 8 weeks) or month (last 6 months), plus the
 * five detailed reports, which open with the same filter.
 */
class ReportsFragment : Fragment(R.layout.fragment_reports) {

    private val viewModel: ReportsViewModel by viewModels { ReportsViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentReportsBinding.bind(view)
        binding.header.padTopForStatusBar()

        binding.dailyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Daily) }
        binding.weeklyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Weekly) }
        binding.monthlyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Monthly) }
        binding.retryButton.setOnClickListener { viewModel.load() }

        bindLink(binding.productionReport, ReportType.Production)
        bindLink(binding.salesReport, ReportType.Sales)
        bindLink(binding.inventoryReport, ReportType.Inventory)
        bindLink(binding.distributionReport, ReportType.Distribution)
        bindLink(binding.riderPerformanceReport, ReportType.RiderPerformance)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(binding, it) }
            }
        }
    }

    /** Tabs are kept alive and hidden; refresh whenever Reports is shown again. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) viewModel.load()
    }

    private fun render(binding: FragmentReportsBinding, state: ReportsUiState) {
        renderToggle(binding.dailyOption, selected = state.period == ReportPeriod.Daily)
        renderToggle(binding.weeklyOption, selected = state.period == ReportPeriod.Weekly)
        renderToggle(binding.monthlyOption, selected = state.period == ReportPeriod.Monthly)
        binding.rangeText.setText(
            when (state.period) {
                ReportPeriod.Daily -> R.string.reports_last_7_days
                ReportPeriod.Weekly -> R.string.reports_last_8_weeks
                ReportPeriod.Monthly -> R.string.reports_last_6_months
            },
        )

        val report = state.report
        binding.chartProgress.isVisible = state.isLoading && report == null
        binding.chart.visibility = if (report == null) View.INVISIBLE else View.VISIBLE
        binding.totalText.isVisible = report != null
        if (report != null) {
            binding.totalText.text = getString(
                R.string.reports_total, formatCount(report.piecesSold), formatPeso(report.totalAmount),
            )
            showChart(binding.chart, report, state.period)
        }

        val error = state.error?.asString(requireContext())
        binding.errorText.text = error
        binding.errorBanner.isVisible = error != null
    }

    private fun showChart(chart: BarChartView, report: PiecesSoldReportDto, period: ReportPeriod) {
        val bars = report.points.map { point ->
            BarChartView.Bar(
                label = when (period) {
                    ReportPeriod.Daily -> DateLabels.weekdayShort(point.start)
                    ReportPeriod.Weekly -> DateLabels.monthDay(point.start) // the week's Monday
                    ReportPeriod.Monthly -> DateLabels.monthName(point.start)
                },
                value = point.piecesSold,
                valueLabel = formatCount(point.piecesSold),
            )
        }
        val description = getString(
            R.string.reports_chart_description,
            bars.joinToString(", ") { getString(R.string.reports_chart_point, it.label, it.valueLabel) },
        )
        chart.setBars(bars, description)
    }

    private fun renderToggle(option: TextView, selected: Boolean) {
        option.setBackgroundResource(if (selected) R.drawable.bg_segmented_thumb else 0)
        option.isSelected = selected
        option.alpha = if (selected) 1f else 0.75f
    }

    /** Each report opens with the filter currently picked on this tab. */
    private fun bindLink(link: ItemReportLinkBinding, type: ReportType) {
        link.titleText.setText(type.title)
        link.root.setOnClickListener {
            startActivity(ReportDetailActivity.newIntent(requireContext(), type, viewModel.state.value.period))
        }
    }
}
