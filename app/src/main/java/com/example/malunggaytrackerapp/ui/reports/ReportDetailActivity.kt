package com.example.malunggaytrackerapp.ui.reports

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.databinding.ActivityReportDetailBinding
import com.example.malunggaytrackerapp.databinding.ItemReportRowBinding
import com.example.malunggaytrackerapp.databinding.ItemReportSectionBinding
import com.example.malunggaytrackerapp.databinding.ItemReportStatBinding
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.asString
import com.example.malunggaytrackerapp.ui.common.setupBrandedEdgeToEdge
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

/**
 * A detailed report for a day, a week (Monday–Sunday) or a month, with ◀ ▶ to look at earlier periods.
 * Opened from the Reports tab with the tab's current filter.
 */
class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val viewModel: ReportDetailViewModel by viewModels { ReportDetailViewModel.Factory }
    private val contentBuilder by lazy { ReportContentBuilder(this) }

    /** Last report drawn, so tiles and lists are only rebuilt when the data changed. */
    private var shownReport: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupBrandedEdgeToEdge(root = binding.root, header = binding.header)

        binding.titleText.setText(viewModel.state.value.type.title)
        binding.backButton.setOnClickListener { finish() }
        binding.dailyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Daily) }
        binding.weeklyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Weekly) }
        binding.monthlyOption.setOnClickListener { viewModel.setPeriod(ReportPeriod.Monthly) }
        binding.previousButton.setOnClickListener { viewModel.previous() }
        binding.nextButton.setOnClickListener { viewModel.next() }
        binding.periodLabel.setOnClickListener { viewModel.jumpToToday() }
        binding.retryButton.setOnClickListener { viewModel.load() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ReportDetailUiState) {
        renderToggle(binding.dailyOption, state.period == ReportPeriod.Daily)
        renderToggle(binding.weeklyOption, state.period == ReportPeriod.Weekly)
        renderToggle(binding.monthlyOption, state.period == ReportPeriod.Monthly)
        renderPeriod(state)

        val report = state.report
        binding.progress.isVisible = state.isLoading && report == null
        val error = state.error?.asString(this)
        binding.errorText.text = error
        binding.errorBanner.isVisible = error != null

        if (report == null) {
            binding.content.isVisible = false
            binding.emptyWrapper.isVisible = false
            shownReport = null
            return
        }
        if (report == shownReport) return
        shownReport = report

        val content = contentBuilder.build(report)
        binding.content.isVisible = !content.isEmpty
        binding.emptyWrapper.isVisible = content.isEmpty
        if (content.isEmpty) {
            showEmpty(state)
            return
        }
        showTiles(content.tiles)
        showChart(content.chart)
        showSections(content.sections)
    }

    private fun renderPeriod(state: ReportDetailUiState) {
        val (from, to) = state.range
        binding.periodText.text = when (state.period) {
            ReportPeriod.Daily -> DateLabels.weekdayDate(from)
            ReportPeriod.Weekly -> DateLabels.range(from, to)
            ReportPeriod.Monthly -> DateLabels.monthYear(from)
        }
        binding.periodCaptionText.text = when {
            state.isCurrent -> getString(
                when (state.period) {
                    ReportPeriod.Daily -> R.string.date_today
                    ReportPeriod.Weekly -> R.string.report_this_week
                    ReportPeriod.Monthly -> R.string.report_this_month
                },
            )
            else -> getString(R.string.report_back_to_now)
        }
        binding.periodLabel.isClickable = !state.isCurrent
        binding.nextButton.isEnabled = state.canGoNext
        binding.nextButton.alpha = if (state.canGoNext) 1f else 0.3f
    }

    private fun showEmpty(state: ReportDetailUiState) {
        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_nav_reports)
        binding.emptyState.emptyTitle.setText(R.string.report_empty_title)
        binding.emptyState.emptyBody.setText(
            when (state.period) {
                ReportPeriod.Daily -> R.string.report_empty_day
                ReportPeriod.Weekly -> R.string.report_empty_week
                ReportPeriod.Monthly -> R.string.report_empty_month
            },
        )
    }

    /** Headline tile full width, then the rest two per row. */
    private fun showTiles(tiles: List<ReportContent.Tile>) {
        val container = binding.tilesContainer
        container.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.space_sm)

        val (headline, others) = tiles.partition { it.highlight }
        headline.forEach { tile -> container.addView(tileRow(listOf(tile), gap)) }
        others.chunked(2).forEach { pair -> container.addView(tileRow(pair, gap)) }
    }

    private fun tileRow(tiles: List<ReportContent.Tile>, gap: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = gap }
        }
        tiles.forEachIndexed { index, tile ->
            val item = ItemReportStatBinding.inflate(layoutInflater, row, false)
            item.labelText.text = tile.label
            item.valueText.text = tile.value
            item.hintText.text = tile.hint
            item.hintText.isVisible = tile.hint != null
            if (tile.highlight) {
                item.root.setBackgroundResource(R.drawable.bg_sales_hero)
                item.valueText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
                item.valueText.setTextColor(MaterialColors.getColor(item.root, androidx.appcompat.R.attr.colorPrimary))
                item.valueText.paint.isFakeBoldText = true
            }
            if (index > 0) item.root.updateLayoutParams<LinearLayout.LayoutParams> { marginStart = gap }
            row.addView(item.root)
        }
        // A lone tile in the last row keeps half width so tiles stay the same size.
        if (tiles.size == 1 && !tiles[0].highlight) {
            row.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = gap })
        }
        return row
    }

    private fun showChart(chart: ReportContent.Chart?) {
        binding.chartCard.isVisible = chart != null
        if (chart == null) return
        binding.chartTitle.text = chart.title
        binding.chart.setBars(chart.bars, chart.description)
    }

    private fun showSections(sections: List<ReportContent.Section>) {
        val container = binding.sectionsContainer
        container.removeAllViews()
        sections.forEach { section ->
            val sectionBinding = ItemReportSectionBinding.inflate(layoutInflater, container, false)
            sectionBinding.sectionTitle.text = section.title
            if (section.rows.isEmpty()) {
                sectionBinding.rowsContainer.addView(emptyRow())
            }
            section.rows.forEach { row ->
                val rowBinding = ItemReportRowBinding.inflate(layoutInflater, sectionBinding.rowsContainer, false)
                rowBinding.titleText.text = row.title
                bindOptional(rowBinding.subtitleText, row.subtitle)
                bindOptional(rowBinding.trailingText, row.trailing)
                bindOptional(rowBinding.trailingCaptionText, row.trailingCaption)
                sectionBinding.rowsContainer.addView(rowBinding.root)
            }
            container.addView(sectionBinding.root)
        }
    }

    private fun emptyRow(): View = ItemReportRowBinding.inflate(layoutInflater).apply {
        titleText.setText(R.string.report_section_empty)
        titleText.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant))
        subtitleText.isVisible = false
        trailingText.isVisible = false
        trailingCaptionText.isVisible = false
    }.root

    private fun bindOptional(view: TextView, text: String?) {
        view.text = text
        view.isVisible = !text.isNullOrBlank()
    }

    private fun renderToggle(option: TextView, selected: Boolean) {
        option.setBackgroundResource(if (selected) R.drawable.bg_segmented_thumb else 0)
        option.isSelected = selected
        option.alpha = if (selected) 1f else 0.75f
    }

    companion object {
        const val EXTRA_TYPE = "report_type"
        const val EXTRA_PERIOD = "report_period"

        fun newIntent(context: Context, type: ReportType, period: ReportPeriod): Intent =
            Intent(context, ReportDetailActivity::class.java)
                .putExtra(EXTRA_TYPE, type.ordinal)
                .putExtra(EXTRA_PERIOD, period.apiValue)
    }
}
