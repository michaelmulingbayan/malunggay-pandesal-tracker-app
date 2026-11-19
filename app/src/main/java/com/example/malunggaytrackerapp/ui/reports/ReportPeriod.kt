package com.example.malunggaytrackerapp.ui.reports

import androidx.annotation.StringRes
import com.example.malunggaytrackerapp.R
import com.example.malunggaytrackerapp.ui.common.DateLabels

/** The filter shared by the Reports tab and every report screen. */
enum class ReportPeriod(val apiValue: String, @StringRes val label: Int) {
    Daily("daily", R.string.reports_daily),
    Weekly("weekly", R.string.reports_weekly),
    Monthly("monthly", R.string.reports_monthly),
    ;

    /** First and last day ("yyyy-MM-dd") of the period containing [isoDate]; matches the server. */
    fun rangeOf(isoDate: String): Pair<String, String> = when (this) {
        Daily -> isoDate to isoDate
        Weekly -> DateLabels.mondayOf(isoDate).let { it to DateLabels.shift(it, days = 6) }
        Monthly -> DateLabels.monthBounds(isoDate)
    }

    /** The same kind of period [steps] earlier (negative) or later (positive) than [isoDate]. */
    fun shift(isoDate: String, steps: Int): String = when (this) {
        Daily -> DateLabels.shift(isoDate, days = steps)
        Weekly -> DateLabels.shift(isoDate, days = 7 * steps)
        Monthly -> DateLabels.shift(isoDate, months = steps)
    }

    companion object {
        fun fromApiValue(value: String?): ReportPeriod = entries.firstOrNull { it.apiValue == value } ?: Daily
    }
}

/** The five detailed reports reachable from the Reports tab. */
enum class ReportType(@StringRes val title: Int) {
    Production(R.string.reports_production),
    Sales(R.string.reports_sales),
    Inventory(R.string.reports_inventory),
    Distribution(R.string.reports_distribution),
    RiderPerformance(R.string.reports_rider_performance),
}
