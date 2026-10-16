package com.ridesniper.app.util

import java.util.Locale
import kotlin.math.abs

/**
 * Single place that decides how money, distance and fuel are shown to the
 * driver. Currently set up for South Africa: rand, kilometres, litres and a
 * 24-hour clock. Supporting another market should only need changes here
 * (plus the offer-card patterns in OcrParser).
 */
object Fmt {
    const val CURRENCY = "R"
    const val DISTANCE_UNIT = "km"
    const val FUEL_UNIT = "L"

    /** Used for dates and times shown to the driver. */
    val DISPLAY_LOCALE: Locale = Locale("en", "ZA")

    // Numbers are rendered with a dot decimal ("R85.40") regardless of the
    // phone's locale so values always look the same as in exports and logs.
    private val NUMBER_LOCALE: Locale = Locale.US

    /** "R85.40", or "-R12.00" for a loss. */
    fun money(value: Double): String {
        val sign = if (value < 0) "-" else ""
        return "%s%s%.2f".format(NUMBER_LOCALE, sign, CURRENCY, abs(value))
    }

    fun perKm(value: Double): String = "${money(value)}/$DISTANCE_UNIT"
    fun perMinute(value: Double): String = "${money(value)}/min"
    fun perHour(value: Double): String = "${money(value)}/hr"

    /** "12.3 km" */
    fun km(value: Double, decimals: Int = 1): String =
        "%.${decimals}f %s".format(NUMBER_LOCALE, value, DISTANCE_UNIT)

    /** "18 min" */
    fun minutes(value: Double, decimals: Int = 0): String =
        "%.${decimals}f min".format(NUMBER_LOCALE, value)

    /** Plain number for pre-filling edit fields, e.g. "22.50". */
    fun number(value: Double, decimals: Int = 2): String =
        "%.${decimals}f".format(NUMBER_LOCALE, value)

    /** Formats an hour of the day (0-23) on the 24-hour clock, e.g. "17:00". */
    fun hour(hour24: Int): String = "%02d:00".format(NUMBER_LOCALE, hour24)

    /**
     * Parses a number typed by the driver. Accepts either "22.50" or "22,50"
     * (South African keyboards often use a comma) and ignores spaces.
     */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()
}
