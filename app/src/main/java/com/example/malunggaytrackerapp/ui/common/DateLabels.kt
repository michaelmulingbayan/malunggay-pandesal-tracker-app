package com.example.malunggaytrackerapp.ui.common

import android.content.Context
import com.example.malunggaytrackerapp.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Friendly date labels for ISO dates ("2026-11-16") using the device's local date:
 * "Today", "Yesterday", "3 days ago", then "Nov 9".
 */
object DateLabels {

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0
    private const val RELATIVE_DAYS_LIMIT = 6

    fun todayIso(): String = isoFormat().format(Date())

    /** Whole days between [isoDate] and today (0 = today, 1 = yesterday). */
    fun daysAgo(isoDate: String): Int {
        val date = startOfDay(isoFormat().parse(isoDate) ?: return 0)
        val today = startOfDay(Date())
        return ((today - date) / DAY_MILLIS).roundToInt()
    }

    fun relative(context: Context, isoDate: String): String = when (val days = daysAgo(isoDate)) {
        0 -> context.getString(R.string.date_today)
        1 -> context.getString(R.string.date_yesterday)
        in 2..RELATIVE_DAYS_LIMIT -> context.resources.getQuantityString(R.plurals.date_days_ago, days, days)
        else -> format(isoDate, "MMM d")
    }

    /** e.g. "Mon, Nov 16". */
    fun weekdayDate(isoDate: String): String = format(isoDate, "EEE, MMM d")

    /** e.g. "Monday, November 16". */
    fun longToday(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

    fun dayOfMonth(isoDate: String): String = format(isoDate, "d")

    /** e.g. "Tue" — chart labels for days. */
    fun weekdayShort(isoDate: String): String = format(isoDate, "EEE")

    /** e.g. "Nov" — chart labels for months. */
    fun monthName(isoDate: String): String = format(isoDate, "MMM")

    /** e.g. "11/16" — compact chart labels for weeks and days of a month. */
    fun monthDay(isoDate: String): String = format(isoDate, "M/d")

    /** e.g. "November 2026". */
    fun monthYear(isoDate: String): String = format(isoDate, "MMMM yyyy")

    /** A short range: "Nov 16 – 22", or "Nov 30 – Dec 6" when it crosses a month. */
    fun range(fromIso: String, toIso: String): String {
        val sameMonth = fromIso.take(7) == toIso.take(7)
        return "${format(fromIso, "MMM d")} – ${format(toIso, if (sameMonth) "d" else "MMM d")}"
    }

    /** The Monday of [isoDate]'s week (weeks run Monday–Sunday, like the server's reports). */
    fun mondayOf(isoDate: String): String {
        val date = isoFormat().parse(isoDate) ?: return isoDate
        val dayOfWeek = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)
        return shift(isoDate, days = -((dayOfWeek + 5) % 7)) // Monday → 0 … Sunday → 6
    }

    /** First and last day of [isoDate]'s month. */
    fun monthBounds(isoDate: String): Pair<String, String> {
        val first = isoDate.take(8) + "01"
        return first to shift(shift(first, months = 1), days = -1)
    }

    /** Moves an ISO date by days / months (months keep to the 1st so Jan 31 + 1 month never overflows). */
    fun shift(isoDate: String, days: Int = 0, months: Int = 0): String {
        val date = isoFormat().parse(isoDate) ?: return isoDate
        val calendar = Calendar.getInstance().apply { time = date }
        if (months != 0) {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MONTH, months)
        }
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return isoFormat().format(calendar.time)
    }

    /** Laravel UTC timestamp ("2026-11-16T04:59:32.000000Z") → local time, e.g. "12:59 PM". */
    fun timeOf(isoTimestamp: String): String {
        val date = parseUtcTimestamp(isoTimestamp) ?: return ""
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(date)
    }

    /** Laravel UTC timestamp → "Today, 6:30 PM", "Yesterday, 9:05 AM", "Nov 9, 6:30 PM". */
    fun relativeDateTime(context: Context, isoTimestamp: String): String {
        val date = parseUtcTimestamp(isoTimestamp) ?: return ""
        val localIsoDate = isoFormat().format(date)
        return "${relative(context, localIsoDate)}, ${timeOf(isoTimestamp)}"
    }

    private fun parseUtcTimestamp(isoTimestamp: String): Date? {
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return runCatching { utc.parse(isoTimestamp.take(19)) }.getOrNull()
    }

    fun monthShort(isoDate: String): String = format(isoDate, "MMM").uppercase(Locale.getDefault())

    private fun format(isoDate: String, pattern: String): String {
        val date = isoFormat().parse(isoDate) ?: return isoDate
        return SimpleDateFormat(pattern, Locale.getDefault()).format(date)
    }

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    private fun startOfDay(date: Date): Long = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** 1200 → "1,200" (device locale). */
fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** Server money string "1380.00" → "₱1,380.00". Uses BigDecimal so amounts are never rounded wrong. */
fun formatPeso(amount: String): String {
    val value = amount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
    return NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("PH").build()).apply {
        currency = java.util.Currency.getInstance("PHP")
    }.format(value)
}
