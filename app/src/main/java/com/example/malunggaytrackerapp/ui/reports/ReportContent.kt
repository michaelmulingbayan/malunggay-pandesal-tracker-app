package com.example.malunggaytrackerapp.ui.reports

import android.content.Context
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.data.remote.DistributionReportDto
import com.example.malunggaytrackerapp.data.remote.InventoryReportDto
import com.example.malunggaytrackerapp.data.remote.ProductionReportDto
import com.example.malunggaytrackerapp.data.remote.ReportPeriodDto
import com.example.malunggaytrackerapp.data.remote.RiderPerformanceReportDto
import com.example.malunggaytrackerapp.data.remote.SalesReportDto
import com.example.malunggaytrackerapp.ui.common.BarChartView
import com.example.malunggaytrackerapp.ui.common.DateLabels
import com.example.malunggaytrackerapp.ui.common.formatCount
import com.example.malunggaytrackerapp.ui.common.formatPeso
import java.math.BigDecimal
import java.math.RoundingMode

/** Everything a report screen shows, independent of which report it is. */
data class ReportContent(
    val tiles: List<Tile>,
    val chart: Chart?,
    val sections: List<Section>,
    /** True when the period has no activity at all: the screen shows an empty state instead of zeros. */
    val isEmpty: Boolean,
) {
    /** A summary number. [highlight] marks the headline figure (drawn on the brand color, full width). */
    data class Tile(val label: String, val value: String, val hint: String? = null, val highlight: Boolean = false)

    data class Chart(val title: String, val bars: List<BarChartView.Bar>, val description: String)

    data class Section(val title: String, val rows: List<Row>)

    data class Row(val title: String, val subtitle: String? = null, val trailing: String? = null, val trailingCaption: String? = null)
}

/** Turns each report's data into [ReportContent]. Uses [context] for strings and plurals. */
class ReportContentBuilder(private val context: Context) {

    fun build(report: ReportPeriodDto): ReportContent = when (report) {
        is ProductionReportDto -> production(report)
        is SalesReportDto -> sales(report)
        is InventoryReportDto -> inventory(report)
        is DistributionReportDto -> distribution(report)
        is RiderPerformanceReportDto -> riders(report)
        else -> ReportContent(emptyList(), null, emptyList(), isEmpty = true)
    }

    private fun production(report: ProductionReportDto): ReportContent {
        val summary = report.summary
        return ReportContent(
            tiles = listOf(
                ReportContent.Tile(s(R.string.report_produced), pcs(summary.produced), highlight = true),
                ReportContent.Tile(s(R.string.report_batches), formatCount(summary.batches)),
                ReportContent.Tile(
                    s(R.string.report_average_per_baking_day), pcs(summary.averagePerProductionDay),
                    hint = context.resources.getQuantityString(R.plurals.report_baking_days, summary.productionDays, summary.productionDays),
                ),
                ReportContent.Tile(s(R.string.report_distributed), pcs(summary.distributed), hint = percentOf(summary.distributed, summary.produced)),
                ReportContent.Tile(s(R.string.report_not_distributed), pcs(summary.undistributed)),
            ),
            chart = chart(report, s(R.string.report_chart_produced), report.daily.map { it.date to it.produced }),
            sections = listOf(
                ReportContent.Section(
                    s(R.string.report_section_batches),
                    report.batches.map { batch ->
                        ReportContent.Row(
                            title = pcs(batch.quantityProduced),
                            subtitle = listOfNotNull(
                                DateLabels.weekdayDate(batch.productionDate),
                                batch.recordedBy?.let { context.getString(R.string.report_by, it) },
                                batch.remarks?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            trailing = pcs(batch.distributed),
                            trailingCaption = s(R.string.report_distributed_lower),
                        )
                    },
                ),
            ),
            isEmpty = summary.produced == 0 && summary.batches == 0,
        )
    }

    private fun sales(report: SalesReportDto): ReportContent {
        val summary = report.summary
        val total = summary.totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return ReportContent(
            tiles = listOf(
                ReportContent.Tile(s(R.string.report_total_sales), formatPeso(summary.totalAmount), highlight = true),
                ReportContent.Tile(s(R.string.report_pieces_sold), pcs(summary.piecesSold)),
                ReportContent.Tile(s(R.string.report_sales_recorded), formatCount(summary.salesCount)),
                ReportContent.Tile(s(R.string.report_average_price), formatPeso(summary.averagePrice), hint = s(R.string.report_per_piece)),
                ReportContent.Tile(s(R.string.report_riders_selling), formatCount(summary.ridersCount)),
            ),
            chart = chart(report, s(R.string.report_chart_sold), report.daily.map { it.date to it.piecesSold }),
            sections = listOfNotNull(
                ReportContent.Section(
                    s(R.string.report_section_by_rider),
                    report.riders.map { rider ->
                        ReportContent.Row(
                            title = rider.riderName,
                            subtitle = context.getString(
                                R.string.report_rider_sales_detail,
                                pcs(rider.piecesSold),
                                context.resources.getQuantityString(R.plurals.report_sales_count, rider.salesCount, rider.salesCount),
                            ),
                            trailing = formatPeso(rider.totalAmount),
                            trailingCaption = shareOf(rider.totalAmount, total),
                        )
                    },
                ),
                byDay(report) {
                    report.daily.filter { it.piecesSold > 0 }.map { day ->
                        ReportContent.Row(
                            title = DateLabels.weekdayDate(day.date),
                            subtitle = pcs(day.piecesSold),
                            trailing = formatPeso(day.totalAmount),
                        )
                    }
                },
            ),
            isEmpty = summary.salesCount == 0,
        )
    }

    private fun inventory(report: InventoryReportDto): ReportContent {
        val summary = report.summary
        return ReportContent(
            tiles = listOf(
                ReportContent.Tile(s(R.string.report_sell_through), "${summary.sellThroughPercent}%", hint = s(R.string.report_sell_through_hint), highlight = true),
                ReportContent.Tile(s(R.string.report_produced), pcs(summary.produced)),
                ReportContent.Tile(s(R.string.report_distributed), pcs(summary.distributed), hint = percentOf(summary.distributed, summary.produced)),
                ReportContent.Tile(s(R.string.report_sold), pcs(summary.sold), hint = percentOf(summary.sold, summary.distributed)),
                ReportContent.Tile(s(R.string.report_returned), pcs(summary.returned), hint = percentOf(summary.returned, summary.distributed)),
                ReportContent.Tile(s(R.string.report_with_riders), pcs(summary.withRiders), hint = s(R.string.report_with_riders_hint)),
                ReportContent.Tile(s(R.string.report_not_distributed), pcs(summary.undistributed), hint = s(R.string.report_not_distributed_hint)),
            ),
            chart = chart(report, s(R.string.report_chart_sold), report.daily.map { it.date to it.sold }),
            sections = listOfNotNull(
                ReportContent.Section(
                    s(R.string.report_section_returns),
                    listOf(
                        ReportContent.Row(s(R.string.inventory_verified), s(R.string.report_returns_verified_hint), pcs(summary.returnedVerified)),
                        ReportContent.Row(s(R.string.return_status_pending), s(R.string.report_returns_pending_hint), pcs(summary.returnedPending)),
                        ReportContent.Row(s(R.string.inventory_rejected), s(R.string.report_returns_rejected_hint), pcs(summary.returnedRejected)),
                    ),
                ),
                byDay(report) {
                    report.daily.filter { it.produced + it.distributed + it.sold + it.returned > 0 }.map { day ->
                        ReportContent.Row(
                            title = DateLabels.weekdayDate(day.date),
                            subtitle = context.getString(
                                R.string.report_inventory_day_detail,
                                formatCount(day.produced), formatCount(day.distributed), formatCount(day.returned),
                            ),
                            trailing = pcs(day.sold),
                            trailingCaption = s(R.string.report_sold_lower),
                        )
                    }
                },
            ),
            isEmpty = summary.produced == 0 && summary.distributed == 0,
        )
    }

    private fun distribution(report: DistributionReportDto): ReportContent {
        val summary = report.summary
        return ReportContent(
            tiles = listOf(
                ReportContent.Tile(s(R.string.report_distributed), pcs(summary.distributed), highlight = true),
                ReportContent.Tile(s(R.string.report_assignments), formatCount(summary.assignments)),
                ReportContent.Tile(s(R.string.report_riders), formatCount(summary.ridersCount)),
                ReportContent.Tile(s(R.string.report_sold), pcs(summary.sold), hint = percentOf(summary.sold, summary.distributed)),
                ReportContent.Tile(s(R.string.report_returned), pcs(summary.returned), hint = percentOf(summary.returned, summary.distributed)),
                ReportContent.Tile(s(R.string.report_with_riders), pcs(summary.withRiders), hint = s(R.string.report_with_riders_hint)),
            ),
            chart = chart(report, s(R.string.report_chart_distributed), report.daily.map { it.date to it.distributed }),
            sections = listOf(
                ReportContent.Section(
                    s(R.string.report_section_by_rider),
                    report.riders.map { rider ->
                        ReportContent.Row(
                            title = rider.riderName,
                            subtitle = context.getString(
                                R.string.report_flow_detail,
                                context.resources.getQuantityString(R.plurals.report_assignments_count, rider.assignments, rider.assignments),
                                formatCount(rider.sold),
                                formatCount(rider.returned),
                            ),
                            trailing = pcs(rider.distributed),
                            trailingCaption = context.getString(R.string.report_left, formatCount(rider.remaining)),
                        )
                    },
                ),
                ReportContent.Section(
                    s(R.string.report_section_assignments),
                    report.assignments.map { assignment ->
                        ReportContent.Row(
                            title = assignment.riderName,
                            subtitle = context.getString(
                                R.string.report_assignment_detail,
                                DateLabels.weekdayDate(assignment.distributionDate),
                                formatCount(assignment.sold),
                                formatCount(assignment.returned),
                            ),
                            trailing = pcs(assignment.distributed),
                            trailingCaption = context.getString(R.string.report_left, formatCount(assignment.remaining)),
                        )
                    },
                ),
            ),
            isEmpty = summary.assignments == 0,
        )
    }

    private fun riders(report: RiderPerformanceReportDto): ReportContent {
        val summary = report.summary
        return ReportContent(
            tiles = listOf(
                ReportContent.Tile(s(R.string.report_total_sales), formatPeso(summary.salesAmount), highlight = true),
                ReportContent.Tile(s(R.string.report_sell_through), "${summary.sellThroughPercent}%", hint = s(R.string.report_sell_through_hint)),
                ReportContent.Tile(s(R.string.report_pieces_sold), pcs(summary.sold), hint = context.getString(R.string.report_of_assigned, formatCount(summary.assigned))),
                ReportContent.Tile(s(R.string.report_riders), formatCount(summary.ridersCount)),
                ReportContent.Tile(s(R.string.report_top_rider), summary.topRiderName ?: "–"),
            ),
            chart = null,
            sections = listOf(
                ReportContent.Section(
                    s(R.string.report_section_ranking),
                    report.riders.mapIndexed { index, rider ->
                        ReportContent.Row(
                            title = context.getString(R.string.report_rank, index + 1, rider.riderName),
                            subtitle = context.getString(
                                R.string.report_rider_performance_detail,
                                formatCount(rider.sold),
                                formatCount(rider.assigned),
                                rider.sellThroughPercent,
                                context.resources.getQuantityString(R.plurals.report_days_active, rider.daysActive, rider.daysActive),
                                formatCount(rider.averageSoldPerDay),
                            ),
                            trailing = formatPeso(rider.salesAmount),
                            trailingCaption = context.getString(R.string.report_returned_count, formatCount(rider.returned)),
                        )
                    },
                ),
            ),
            isEmpty = summary.ridersCount == 0,
        )
    }

    /** A per-day bar chart, only when the period has more than one day. */
    private fun chart(report: ReportPeriodDto, title: String, values: List<Pair<String, Int>>): ReportContent.Chart? {
        if (values.size <= 1) return null
        val monthly = report.period == ReportPeriod.Monthly.apiValue
        val bars = values.map { (date, value) ->
            BarChartView.Bar(
                // A month has up to 31 bars: label only the 1st, every 7th day and the last day.
                label = when {
                    !monthly -> DateLabels.weekdayShort(date)
                    date.takeLast(2).toInt().let { it == 1 || it % 7 == 0 } -> DateLabels.dayOfMonth(date)
                    else -> ""
                },
                value = value,
                valueLabel = formatCount(value),
            )
        }
        val description = context.getString(
            R.string.report_chart_description,
            title,
            values.joinToString(", ") { (date, value) -> "${DateLabels.weekdayDate(date)} ${formatCount(value)}" },
        )
        return ReportContent.Chart(title, bars, description)
    }

    /** "By day" list for weekly / monthly reports; a daily report is already one day. */
    private fun byDay(report: ReportPeriodDto, rows: () -> List<ReportContent.Row>): ReportContent.Section? {
        if (report.period == ReportPeriod.Daily.apiValue) return null
        return rows().takeIf { it.isNotEmpty() }?.let { ReportContent.Section(s(R.string.report_section_by_day), it) }
    }

    private fun pcs(value: Int) = context.getString(R.string.inventory_pcs, formatCount(value))

    private fun percentOf(part: Int, whole: Int): String? =
        if (whole > 0) context.getString(R.string.report_percent_of, part * 100 / whole) else null

    private fun shareOf(amount: String, total: BigDecimal): String? {
        if (total.signum() <= 0) return null
        val share = (amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            .multiply(BigDecimal(100)).divide(total, 0, RoundingMode.HALF_UP).toInt()
        return context.getString(R.string.report_share, share)
    }

    private fun s(id: Int) = context.getString(id)
}
